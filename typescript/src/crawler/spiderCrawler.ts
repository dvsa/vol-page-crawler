import * as cheerio from 'cheerio';
import type { Page } from 'playwright';
import { AxeScanner } from '../axe/axeScanner.js';
import { formAutoFill } from '../bots/answerBot.js';
import { logger } from '../logger.js';
import { isPlaywrightPage, type Driver } from '../types.js';
import { DomainValidator } from '../utils/domainValidator.js';
import { normaliseUrl } from '../utils/urls.js';

export { normaliseUrl };

/** Port of org.dvsa.testing.framework.jsoup.SpiderCrawler (cheerio replaces JSoup). */

export interface CrawlerOptions {
  /**
   * When true, pages containing a <form> are handed to the AnswerBot, which
   * fills inputs and clicks through the journey, scanning every step. This
   * SUBMITS FORMS against the target service and is intentionally random, so
   * it is opt-in; the default crawl remains read-only.
   */
  formFill?: boolean;
}

const MAX_CRAWL_DEPTH = 30;
const MAX_URLS_PER_DOMAIN = 1000;

const BLACKLISTED_PATHS = (process.env.SCANNER_EXCLUDE_PATHS ?? '/topsreport').split(',');

const EXCLUDE_PATTERN =
  /.*\.(pdf|zip|gz|tar|mp[34]|avi|png|jpg|jpeg|gif|svg|ico|css|js|map|csv|docx?|xlsx?|exe|dmg|woff2?|ttf|eot|json|xml)(\?.*)?$/i;

const ACTION_PATTERN = /.*(logout|signout|delete|remove|cancel|auth|login|abort).*/i;

function isInvalidUrl(url: string | null | undefined): boolean {
  if (!url || !url.trim() || url.startsWith('about:')) {
    return true;
  }

  if (ACTION_PATTERN.test(url)) {
    logger.warn(`Skipping potential state-changing URL: ${url}`);
    return true;
  }

  const isBlacklisted = BLACKLISTED_PATHS.some((path) =>
    url.toLowerCase().includes(path.toLowerCase()),
  );
  if (isBlacklisted) {
    logger.info(`URL ignored (Blacklisted path): ${url}`);
    return true;
  }

  const pathOnly = url.split('?')[0];
  return EXCLUDE_PATTERN.test(pathOnly);
}

function shouldFollow(
  url: string,
  baseDomain: string,
  visited: Set<string>,
  allowSubdomains: boolean,
): boolean {
  if (isInvalidUrl(url)) return false;
  if (!DomainValidator.isSameDomain(url, baseDomain, allowSubdomains)) return false;
  return !visited.has(normaliseUrl(url));
}

async function getPageSource(driver: Driver): Promise<string> {
  if (isPlaywrightPage(driver)) {
    return driver.content();
  }
  return driver.getPageSource();
}

export async function crawler(
  level: number,
  url: string,
  visited: Set<string>,
  driver: Driver,
  options: CrawlerOptions = {},
): Promise<void> {
  const baseDomain = DomainValidator.extractDomain(url);
  if (!baseDomain) {
    logger.error(`Cannot extract domain from starting URL: ${url}`);
    return;
  }
  await crawlerWithDomain(level, url, visited, driver, baseDomain, false, options);
}

export async function crawlerWithDomain(
  level: number,
  url: string,
  visited: Set<string>,
  driver: Driver,
  baseDomain: string,
  allowSubdomains: boolean,
  options: CrawlerOptions = {},
): Promise<void> {
  if (level >= MAX_CRAWL_DEPTH || visited.size >= MAX_URLS_PER_DOMAIN) {
    return;
  }

  const normalisedUrl = normaliseUrl(url);

  if (isInvalidUrl(normalisedUrl)) {
    return;
  }

  if (visited.has(normalisedUrl)) {
    logger.debug(`Skipping: Base URL already visited -> ${normalisedUrl}`);
    return;
  }
  visited.add(normalisedUrl);

  if (!DomainValidator.isSameDomain(normalisedUrl, baseDomain, allowSubdomains)) {
    return;
  }

  logger.info(`Crawling [Level ${level}]: ${normalisedUrl}`);

  try {
    let actualUrl: string;
    if (isPlaywrightPage(driver)) {
      const page: Page = driver;
      await page.goto(normalisedUrl, { waitUntil: 'load' });
      await page.waitForLoadState('networkidle');
      await page.evaluate(() => {
        document.querySelectorAll('a[target]').forEach((link) => link.removeAttribute('target'));
      });
      actualUrl = page.url();
    } else {
      await driver.get(normalisedUrl);
      actualUrl = await driver.getCurrentUrl();
    }

    if (!DomainValidator.isSameDomain(actualUrl, baseDomain, allowSubdomains)) {
      logger.debug(`Redirected outside domain: ${actualUrl}`);
      return;
    }

    await AxeScanner.scan(driver);

    const freshContent = await getPageSource(driver);
    const $ = cheerio.load(freshContent);

    const links: string[] = [];
    $('a[href]').each((_, element) => {
      const href = $(element).attr('href');
      if (!href) return;
      try {
        links.push(new URL(href, actualUrl).toString());
      } catch {
        /* unresolvable href — skip */
      }
    });

    if (options.formFill && $('form').length > 0) {
      logger.info(`Form detected on ${actualUrl} — handing over to AnswerBot`);
      await formAutoFill(driver, actualUrl, baseDomain, allowSubdomains, visited);
    }

    for (const nextUrl of links) {
      if (shouldFollow(nextUrl, baseDomain, visited, allowSubdomains)) {
        await crawlerWithDomain(
          level + 1,
          nextUrl,
          visited,
          driver,
          baseDomain,
          allowSubdomains,
          options,
        );
      }
    }
  } catch (e) {
    logger.error(`Failed to crawl URL [Level ${level}]: ${normalisedUrl}`, e);
  }
}
