import type { Page } from 'playwright';
import type { WebDriver } from 'selenium-webdriver';

/**
 * Unified driver type. Mirrors the Java framework where AXEScanner.scan(Object)
 * accepts either a Playwright Page or a Selenium WebDriver.
 */
export type Driver = Page | WebDriver;

export function isPlaywrightPage(driver: Driver): driver is Page {
  return typeof (driver as Page).waitForLoadState === 'function';
}
