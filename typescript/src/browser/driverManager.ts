import type { Driver } from '../types.js';
import { PlaywrightManager } from './playwrightManager.js';
import { SeleniumManager } from './seleniumManager.js';

/** Port of org.dvsa.testing.framework.browser.DriverManager. */
export class DriverManager {
  private static playwright: PlaywrightManager | null = null;
  private static selenium: SeleniumManager | null = null;

  static async init(framework: string, browser: string): Promise<Driver> {
    const activeFramework = framework.toLowerCase();

    if (activeFramework === 'playwright') {
      this.playwright = new PlaywrightManager();
      await this.playwright.selectBrowser(browser);
      return this.playwright.getPage();
    }

    this.selenium = new SeleniumManager();
    await this.selenium.selectBrowser(browser);
    return this.selenium.getDriver();
  }

  static async quit(): Promise<void> {
    if (this.playwright) {
      await this.playwright.closeBrowserAndPage();
      this.playwright = null;
    }
    if (this.selenium) {
      await this.selenium.quit();
      this.selenium = null;
    }
  }
}
