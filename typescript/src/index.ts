export { AxeScanner, type ViolationEntry } from './axe/axeScanner.js';
export { generateHtmlReport } from './axe/htmlReportGenerator.js';
export { BedrockAgentAnalyser } from './ai/bedrockAgentAnalyser.js';
export type { BedrockRecommendation } from './ai/bedrockRecommendation.js';
export { GovUkScraper } from './scraper/govUkScraper.js';
export {
  crawler,
  crawlerWithDomain,
  normaliseUrl,
  type CrawlerOptions,
} from './crawler/spiderCrawler.js';
export { formAutoFill, getPostcode, getAllClickableButtonsPlaywright } from './bots/answerBot.js';
export { DriverManager } from './browser/driverManager.js';
export { PlaywrightManager } from './browser/playwrightManager.js';
export { SeleniumManager } from './browser/seleniumManager.js';
export { AppConfig } from './config/appConfig.js';
export { DomainValidator } from './utils/domainValidator.js';
export { AccessibilityMapper } from './utils/accessibilityMapper.js';
export { logger } from './logger.js';
export { type Driver, isPlaywrightPage } from './types.js';
