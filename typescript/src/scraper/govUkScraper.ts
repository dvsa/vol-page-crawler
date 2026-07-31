import * as cheerio from 'cheerio';
import { logger } from '../logger.js';

/** Port of org.dvsa.testing.framework.jsoup.GovUkScraper (cheerio replaces JSoup). */
export class GovUkScraper {
  private readonly cache = new Map<string, string>();

  async getLiveGuidance(path: string): Promise<string> {
    if (!path) return '';

    const cached = this.cache.get(path);
    if (cached !== undefined) return cached;

    const guidance = await this.fetchGuidance(path);
    this.cache.set(path, guidance);
    return guidance;
  }

  private async fetchGuidance(path: string): Promise<string> {
    const targetUrl = this.resolveGovUkUrl(path);

    try {
      logger.info(`Fetching live GOV.UK guidance from: ${targetUrl}`);
      const response = await fetch(targetUrl, {
        headers: { 'User-Agent': 'Accessibility-Audit-Tool/1.0' },
        signal: AbortSignal.timeout(10_000),
      });

      if (response.status === 404) {
        logger.error(`404 Error: Path '${path}' not found on GOV.UK. URL tried: ${targetUrl}`);
        return `Guidance link broken for ${path}`;
      }
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const $ = cheerio.load(await response.text());

      const accessibilityHeading = $('h2')
        .filter((_, el) => $(el).text().includes('Accessibility'))
        .first();

      let content = '';
      if (accessibilityHeading.length > 0) {
        content = accessibilityHeading
          .nextUntil('h2')
          .toArray()
          .map((el) => $(el).text())
          .join('\n');
        if (content) content += '\n';
      } else {
        const mainContent = $('main').first();
        if (mainContent.length > 0) {
          content = mainContent.find('p, ul').text();
        } else {
          return `Guidance available at: ${targetUrl}`;
        }
      }

      return content.length === 0 ? `Guidance available at: ${targetUrl}` : content;
    } catch (e) {
      logger.error(`Failed to scrape GOV.UK for path: ${path}`, e);
      return `Guidance unavailable for ${path}`;
    }
  }

  private resolveGovUkUrl(path: string): string {
    const slug = path.toLowerCase().trim();

    const styles = [
      'headings',
      'typeface',
      'type-scale',
      'paragraphs',
      'links',
      'lists',
      'colour',
      'layout',
      'spacing',
      'images',
    ];

    const category = styles.includes(slug) ? 'styles' : 'components';
    return `https://design-system.service.gov.uk/${category}/${slug}/`;
  }
}
