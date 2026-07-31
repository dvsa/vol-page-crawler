import * as fs from 'node:fs';
import * as path from 'node:path';
import { AxeBuilder as PlaywrightAxeBuilder } from '@axe-core/playwright';
import WebdriverAxeBuilder from '@axe-core/webdriverjs';
import type { Result } from 'axe-core';
import type { Page } from 'playwright';
import type { WebDriver } from 'selenium-webdriver';
import { BedrockAgentAnalyser } from '../ai/bedrockAgentAnalyser.js';
import type { BedrockRecommendation } from '../ai/bedrockRecommendation.js';
import { logger } from '../logger.js';
import { isPlaywrightPage, type Driver } from '../types.js';
import { generateHtmlReport } from './htmlReportGenerator.js';

/** Port of org.dvsa.testing.framework.axe.AXEScanner. */

export interface ViolationEntry {
  rule: Result;
  pageUrl: string;
}

const REPORT_DIR = process.env.REPORT_DIR ?? path.join('reports');
const SCREENSHOT_DIR = path.join(REPORT_DIR, 'screenshots');

export class AxeScanner {
  private static readonly allViolations: ViolationEntry[] = [];
  private static readonly pageScreenshots = new Map<string, string>();
  private static reportGenerated = false;

  static async scan(driver: Driver): Promise<void> {
    if (isPlaywrightPage(driver)) {
      await this.scanPlaywright(driver);
    } else {
      await this.scanSelenium(driver);
    }
  }

  private static async scanPlaywright(page: Page): Promise<void> {
    try {
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(500);

      const currentUrl = page.url();
      if (this.isInvalidUrl(currentUrl)) return;

      let retries = 3;
      let axeResponse;
      for (;;) {
        try {
          axeResponse = await new PlaywrightAxeBuilder({ page })
            .withTags(this.getScanTags())
            .analyze();
          break;
        } catch (e) {
          const message = e instanceof Error ? e.message : String(e);
          if (message.includes('unable to inject') && --retries > 0) {
            await page.waitForTimeout(1000);
          } else throw e;
        }
      }
      await this.processResults(currentUrl, axeResponse.violations, page);
    } catch (e) {
      logger.error(`Playwright scan failed: ${e instanceof Error ? e.message : e}`);
    }
  }

  private static async scanSelenium(driver: WebDriver): Promise<void> {
    try {
      const currentUrl = await driver.getCurrentUrl();
      if (this.isInvalidUrl(currentUrl)) return;

      const results = await new WebdriverAxeBuilder(driver)
        .withTags(this.getScanTags())
        .analyze();

      await this.processResults(currentUrl, results.violations, driver);
    } catch (e) {
      logger.error(`Selenium scan failed: ${e instanceof Error ? e.message : e}`);
    }
  }

  private static async captureScreenshot(driver: Driver, url: string): Promise<string | undefined> {
    try {
      fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

      const now = new Date();
      const stamp =
        now.toISOString().slice(0, 10).replace(/-/g, '') +
        '-' +
        now.toTimeString().slice(0, 8).replace(/:/g, '');
      const sanitized = url.replace(/[^a-zA-Z0-9]/g, '_').slice(0, 30);
      const filename = `${stamp}_${sanitized}.png`;
      const filePath = path.join(SCREENSHOT_DIR, filename);

      if (isPlaywrightPage(driver)) {
        await driver.screenshot({ path: filePath, fullPage: true });
      } else {
        const base64 = await driver.takeScreenshot();
        fs.writeFileSync(filePath, base64, 'base64');
      }
      return filename;
    } catch (e) {
      logger.error(`Screenshot failed: ${e instanceof Error ? e.message : e}`);
      return undefined;
    }
  }

  private static getScanTags(): string[] {
    const configured = process.env.STANDARDS_SCAN;
    if (configured && configured.trim()) {
      return configured.split(',').map((tag) => tag.trim());
    }
    return ['wcag22a', 'wcag22aa', 'wcag412', 'wcag143', 'cat.**', 'best-practice'];
  }

  private static isInvalidUrl(url: string | null | undefined): boolean {
    return !url || url.startsWith('about:');
  }

  static getAllViolations(): ViolationEntry[] {
    return this.allViolations;
  }

  static getPageScreenshots(): Map<string, string> {
    return this.pageScreenshots;
  }

  static async generateFinalReport(): Promise<void> {
    if (this.reportGenerated) {
      logger.info('Report already generated; skipping duplicate invocation.');
      return;
    }
    this.reportGenerated = true;

    if (this.allViolations.length === 0) {
      logger.info('No accessibility issues found; skipping report generation.');
      return;
    }

    const snapshot = [...this.allViolations];
    const pageCount = new Set(snapshot.map((entry) => entry.pageUrl)).size;
    logger.info(
      `Generating report for ${snapshot.length} total violation instances across ${pageCount} pages...`,
    );

    const uniqueRules = new Map<string, Result>();
    for (const entry of snapshot) {
      if (!uniqueRules.has(entry.rule.id)) {
        uniqueRules.set(entry.rule.id, entry.rule);
      }
    }

    logger.info(
      `Deduplicated to ${uniqueRules.size} unique rule IDs for Bedrock analysis (from ${snapshot.length} total violations)`,
    );

    try {
      logger.info('Generating report with Bedrock recommendations & Live Scraped Context...');

      const analyser = new BedrockAgentAnalyser();
      const recommendationMap = await analyser.analyseUniqueViolations(uniqueRules);

      if (recommendationMap.size === 0) {
        logger.warn('No AI recommendations received from Bedrock. Falling back to basic report.');
        this.generateBasicReport(snapshot);
        return;
      }

      logger.info(`Bedrock recommendations received for rules: ${[...recommendationMap.keys()].join(', ')}`);

      const htmlContent = generateHtmlReport(
        snapshot,
        recommendationMap,
        new Map(),
        this.pageScreenshots,
      );

      this.writeReportFile(htmlContent);

      logger.info('Accessibility report generated successfully with live scraped AI guidance.');
    } catch (e) {
      logger.error(`Failed to generate AI report: ${e instanceof Error ? e.message : e}`, e);
      this.generateBasicReport(snapshot);
    }
  }

  static generateBasicReport(snapshot: ViolationEntry[]): void {
    try {
      const htmlContent = generateHtmlReport(snapshot, new Map(), new Map(), this.pageScreenshots);
      this.writeReportFile(htmlContent);
      logger.info('Basic accessibility report generated successfully (without AI recommendations).');
    } catch (e) {
      logger.error('Failed to generate basic report', e);
    }
  }

  private static writeReportFile(content: string): void {
    const now = new Date();
    const stamp = `${now.toISOString().slice(0, 10)}-${now.toTimeString().slice(0, 8).replace(/:/g, '.')}-`;
    const fileName = 'accessibility.html';

    try {
      fs.mkdirSync(REPORT_DIR, { recursive: true });
      fs.writeFileSync(path.join(REPORT_DIR, stamp + fileName), content);
    } catch (e) {
      logger.error('Failed to write report file', e);
    }
  }

  private static async processResults(url: string, violations: Result[], driver: Driver): Promise<void> {
    if (violations.length > 0) {
      if (!this.pageScreenshots.has(url)) {
        const filename = await this.captureScreenshot(driver, url);
        if (filename) this.pageScreenshots.set(url, filename);
      }

      for (const rule of violations) {
        this.allViolations.push({ rule, pageUrl: url });
      }

      logger.info(`Found ${violations.length} violations on ${url}`);
    } else {
      logger.info(`No violations found on ${url}`);
    }
  }
}
