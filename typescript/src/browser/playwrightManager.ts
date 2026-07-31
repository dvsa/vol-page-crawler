import { chromium, firefox, type Browser, type Page } from 'playwright';

/** Port of org.dvsa.testing.framework.browser.PlayWrightManager. */
export class PlaywrightManager {
  private browser: Browser | null = null;
  private page: Page | null = null;

  getPage(): Page {
    if (!this.page) throw new Error('Browser not started: call selectBrowser() first');
    return this.page;
  }

  async selectBrowser(browserName: string): Promise<void> {
    const headlessMode = browserName.toLowerCase() === 'headless';

    let browserType;
    switch (browserName.toLowerCase()) {
      case 'headless':
      case 'chrome':
        browserType = chromium;
        break;
      case 'firefox':
        browserType = firefox;
        break;
      default:
        throw new Error(`Unsupported browser: ${browserName}`);
    }

    this.browser = await browserType.launch({ headless: headlessMode });
    const context = await this.browser.newContext();
    this.page = await context.newPage();
  }

  async closeBrowserAndPage(): Promise<void> {
    if (this.page) {
      await this.page.close();
      this.page = null;
    }
    if (this.browser) {
      await this.browser.close();
      this.browser = null;
    }
  }
}
