import { Builder, type WebDriver } from 'selenium-webdriver';
import { Options as ChromeOptions } from 'selenium-webdriver/chrome.js';

/**
 * Port of org.dvsa.testing.framework.browser.SeleniumManager.
 * Driver binaries are resolved automatically by Selenium Manager (built into
 * selenium-webdriver 4.6+), replacing Java's WebDriverManager dependency.
 */
export class SeleniumManager {
  private driver: WebDriver | null = null;

  getDriver(): WebDriver {
    if (!this.driver) throw new Error('Browser not started: call selectBrowser() first');
    return this.driver;
  }

  async selectBrowser(browserName: string): Promise<void> {
    const headless = browserName.toLowerCase().includes('headless');
    const baseBrowser = browserName.toLowerCase().replace('headless', '').trim();

    switch (baseBrowser) {
      case 'chrome':
      case '': {
        const options = new ChromeOptions();
        if (headless) options.addArguments('--headless=new');
        this.driver = await new Builder().forBrowser('chrome').setChromeOptions(options).build();
        break;
      }
      case 'firefox':
        this.driver = await new Builder().forBrowser('firefox').build();
        break;
      default:
        throw new Error(`Unsupported Selenium browser: ${browserName}`);
    }
  }

  async quit(): Promise<void> {
    if (this.driver) {
      await this.driver.quit();
      this.driver = null;
    }
  }
}
