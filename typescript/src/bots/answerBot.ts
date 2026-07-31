import { fakerEN_GB as faker } from '@faker-js/faker';
import type { Locator, Page } from 'playwright';
import { By, type WebDriver, type WebElement } from 'selenium-webdriver';
import { AxeScanner } from '../axe/axeScanner.js';
import { AppConfig } from '../config/appConfig.js';
import { logger } from '../logger.js';
import { isPlaywrightPage, type Driver } from '../types.js';
import { DomainValidator } from '../utils/domainValidator.js';
import { normaliseUrl } from '../utils/urls.js';
import {
  randomAlphabetic,
  randomAlphanumeric,
  randomIntBetween,
  randomNumeric,
} from '../utils/random.js';

/** Port of org.dvsa.testing.framework.bots.AnswerBot. */

const MAX_ATTEMPTS = 30;
const UNSAFE_BUTTON_TEXTS = ['sign out', 'logout', 'remove', 'cookies', 'abort'];

export function getPostcode(): string {
  return faker.location.zipCode();
}

/**
 * Fills and clicks through a form journey, scanning every step.
 * Pass the crawler's `visited` set to record each page the bot traverses,
 * so a surrounding crawl does not re-scan them.
 */
export async function formAutoFill(
  driver: Driver,
  url: string,
  baseDomain: string,
  allowSubdomains: boolean,
  visited?: Set<string>,
): Promise<void> {
  if (isPlaywrightPage(driver)) {
    await runPlaywrightEngine(driver, url, baseDomain, allowSubdomains, visited);
  } else {
    await runSeleniumEngine(driver, url, baseDomain, allowSubdomains, visited);
  }
}

function markVisited(visited: Set<string> | undefined, url: string | null | undefined): void {
  if (visited && url) {
    visited.add(normaliseUrl(url));
  }
}

// --- PLAYWRIGHT ENGINE ---

async function runPlaywrightEngine(
  page: Page,
  url: string,
  baseDomain: string,
  allowSubdomains: boolean,
  visited?: Set<string>,
): Promise<void> {
  if (!DomainValidator.isSameDomain(url, baseDomain, allowSubdomains)) {
    logger.warn('Skipping form fill: outside domain');
    return;
  }

  setupPlaywrightPopupHandler(page, baseDomain, allowSubdomains);

  try {
    await page.goto(url);
    await page.waitForLoadState('domcontentloaded');
    await handleCookieBanners(page);

    let attempts = 0;

    while (attempts++ < MAX_ATTEMPTS) {
      if (!DomainValidator.isSameDomain(page.url(), baseDomain, allowSubdomains)) {
        logger.warn('Redirected outside domain.');
        return;
      }
      markVisited(visited, page.url());

      const inputs = page.locator("input:not([type='hidden']), textarea, select");
      const count = await inputs.count();

      for (let i = 0; i < count; i++) {
        try {
          const el = inputs.nth(i);
          if (!(await el.isVisible()) || (await el.isDisabled())) continue;
          await fillLogicPlaywright(el);
        } catch {
          /* element may have gone stale mid-loop — keep going */
        }
      }

      const allButtons = await getAllClickableButtonsPlaywright(page);
      const buttons: Locator[] = [];
      for (const button of allButtons) {
        if (await isSafeButtonPlaywright(page, button, baseDomain, allowSubdomains)) {
          buttons.push(button);
        }
      }

      if (buttons.length === 0) {
        logger.info('No clickable buttons found — assuming end of flow.');
        return;
      }

      const selectedButton = buttons[randomIntBetween(0, buttons.length)];

      const beforeUrl = page.url();

      await selectedButton.evaluate((el) => el.removeAttribute('target'));
      await selectedButton.click({ timeout: 10_000, force: true });

      try {
        await page.waitForURL((u) => u.toString() !== beforeUrl, { timeout: 10_000 });
      } catch {
        /* URL may legitimately stay the same (validation errors etc.) */
      }

      await page.waitForLoadState('domcontentloaded');

      await AxeScanner.scan(page);
    }
  } catch (e) {
    logger.error('Playwright Engine failed', e);
  }
}

async function fillLogicPlaywright(el: Locator): Promise<void> {
  const tagName = await el.evaluate((node) => node.tagName.toLowerCase());

  if (tagName === 'select') {
    if ((await el.locator('option').count()) > 1) {
      await el.selectOption({ index: 1 });
    }
  } else {
    const type = (await el.getAttribute('type')) ?? 'text';
    if (type === 'radio' || type === 'checkbox') {
      if (!(await el.isChecked())) {
        await el.check({ force: true });
      }
    } else {
      await handleTextInput(el);
    }
  }
}

async function handleTextInput(locator: Locator): Promise<void> {
  const nameAttr = ((await locator.getAttribute('name')) ?? '').toLowerCase();
  const typeAttr = ((await locator.getAttribute('type')) ?? 'text').toLowerCase();
  const inputMode = ((await locator.getAttribute('inputmode')) ?? '').toLowerCase();

  const hintText = await locator.evaluate((el) => {
    const group = el.closest('.govuk-form-group');
    const error = group?.querySelector('.govuk-error-message');
    return error ? (error as HTMLElement).innerText.trim() : '';
  });

  const isNumericField =
    typeAttr === 'number' ||
    inputMode.includes('numeric') ||
    hintText.includes('Can only contain numbers') ||
    hintText.includes('must be a number cylinder-capacity');

  if (isNumericField) {
    const val = await generateSmartNumericValue(locator);
    await waitAndEnterText(locator, val);
    logger.info(`Numeric Smart-Fill: Entered ${val} for field ${nameAttr}`);
  } else if (nameAttr.includes('email')) {
    await waitAndEnterText(locator, 'answer.bot@dvsa.gov.uk');
  } else if (nameAttr.includes('phone')) {
    await waitAndEnterText(locator, `07${randomNumeric(9)}`);
  } else if (nameAttr.includes('postcode')) {
    await waitAndEnterText(locator, getPostcode());
  } else if (nameAttr.includes('aeusername')) {
    await waitAndEnterText(locator, 'ARYA7524');
  } else if (nameAttr.includes('registration')) {
    // Probabilistic toggle: 30/70 split
    const reg =
      Math.random() < 0.3 ? AppConfig.getString('registration', generateRandomVRN()) : generateRandomVRN();
    await waitAndEnterText(locator, reg);
    logger.info(`Registration flow: used ${reg}`);
  } else if (nameAttr.includes('vin')) {
    // 70/30 split
    const vin =
      Math.random() < 0.7
        ? AppConfig.getString('vin', randomAlphanumeric(17).toUpperCase())
        : randomAlphanumeric(17).toUpperCase();
    await waitAndEnterText(locator, vin);
    logger.info(`VIN flow: used ${vin}`);
  } else if (nameAttr.includes('weight') || nameAttr.includes('code')) {
    const val = generateNumericValueByLimit(await locator.getAttribute('maxlength'));
    await waitAndEnterText(locator, val);
  } else {
    await waitAndEnterText(locator, randomAlphabetic(11).toLowerCase());
  }
}

async function waitAndEnterText(locator: Locator, text: string): Promise<void> {
  await locator.scrollIntoViewIfNeeded();
  await locator.fill(text);
}

function setupPlaywrightPopupHandler(page: Page, baseDomain: string, allowSubdomains: boolean): void {
  page.context().on('page', async (newPage) => {
    try {
      await newPage.waitForLoadState('domcontentloaded');
      if (!DomainValidator.isSameDomain(newPage.url(), baseDomain, allowSubdomains)) {
        logger.info(`Closing external popup: ${newPage.url()}`);
        await newPage.close();
      }
    } catch (e) {
      logger.debug(`Popup handling skipped: ${e instanceof Error ? e.message : e}`);
    }
  });
}

async function handleCookieBanners(page: Page): Promise<void> {
  try {
    await page
      .locator("button:has-text('Accept'), .govuk-cookie-banner__button-accept")
      .first()
      .click({ timeout: 1000 });
  } catch {
    /* no cookie banner present */
  }
}

export async function getAllClickableButtonsPlaywright(page: Page): Promise<Locator[]> {
  const allButtons: Locator[] = [];

  try {
    const combinedSelector = [
      'button:visible:enabled',
      "input[type='submit']:visible:enabled",
      "input[type='button']:visible:enabled",
      "input[type='reset']:visible:enabled",
      "a:visible[class*='button']",
      "a:visible[class*='btn']",
      "[role='button']:visible",
    ].join(', ');

    allButtons.push(...(await page.locator(combinedSelector).all()));

    const essentialButtonTexts = ['submit', 'continue', 'next', 'start', 'confirm', 'ok', 'apply', 'save'];

    for (const text of essentialButtonTexts) {
      const caseInsensitiveSelector = `button:visible:enabled:has-text(/${text}/i), a:visible:has-text(/${text}/i)`;
      try {
        allButtons.push(...(await page.locator(caseInsensitiveSelector).all()));
      } catch {
        allButtons.push(
          ...(await page
            .locator(`button:visible:enabled:has-text('${text}'), a:visible:has-text('${text}')`)
            .all()),
        );
      }
    }

    allButtons.push(
      ...(await page.locator('div:visible[onclick]:first-of-type, span:visible[onclick]:first-of-type').all()),
    );
  } catch (e) {
    logger.warn(`Error collecting buttons, falling back to basic selector: ${e instanceof Error ? e.message : e}`);
    allButtons.push(
      ...(await page.locator("button:visible:enabled, input[type='submit']:visible:enabled").all()),
    );
  }

  const valid: Locator[] = [];
  for (const button of allButtons) {
    if (valid.length >= 10) break; // Limit to avoid excessive button clicks
    if (await isValidClickableButtonPlaywright(button)) {
      valid.push(button);
    }
  }
  return valid;
}

async function isValidClickableButtonPlaywright(button: Locator): Promise<boolean> {
  try {
    if (!(await button.isVisible()) || (await button.isDisabled())) return false;

    const text = ((await button.textContent()) ?? '').toLowerCase();
    const value = ((await button.getAttribute('value')) ?? '').toLowerCase();

    return (
      !UNSAFE_BUTTON_TEXTS.some((unsafe) => text.includes(unsafe)) &&
      !value.includes('logout') &&
      text.length <= 120
    );
  } catch {
    return false;
  }
}

async function isSafeButtonPlaywright(
  page: Page,
  button: Locator,
  baseDomain: string,
  allowSubdomains: boolean,
): Promise<boolean> {
  try {
    const href = await button.getAttribute('href');
    if (!href || href.startsWith('#') || href.startsWith('/')) return true;

    const resolved = new URL(href, page.url()).toString();
    return DomainValidator.isSameDomain(resolved, baseDomain, allowSubdomains);
  } catch {
    return true;
  }
}

async function generateSmartNumericValue(locator: Locator): Promise<string> {
  const id = ((await locator.getAttribute('id')) ?? '').toLowerCase();
  const name = ((await locator.getAttribute('name')) ?? '').toLowerCase();
  const maxLenAttr = await locator.getAttribute('maxlength');

  // 1. Check by ID/Name first (Most reliable for Dates)
  if (id.includes('month') || name.includes('month')) {
    return String(randomIntBetween(1, 13));
  }
  if (id.includes('day') || name.includes('day')) {
    return String(randomIntBetween(1, 29));
  }
  if (id.includes('year') || name.includes('year')) {
    return String(randomIntBetween(1980, 2025));
  }

  // 2. Fallback to maxlength if it exists
  if (maxLenAttr) {
    const limit = Number.parseInt(maxLenAttr, 10);
    const min = 10 ** (limit - 1);
    const max = 10 ** limit - 1;
    return String(randomIntBetween(min, max));
  }

  // 3. Final generic fallback
  return String(randomIntBetween(1, 100));
}

function generateNumericValueByLimit(maxLenAttr: string | null): string {
  const limit = maxLenAttr ? Number.parseInt(maxLenAttr, 10) : 5;

  if (limit === 2) {
    return String(randomIntBetween(1, 13)).padStart(2, '0');
  }
  if (limit === 4) {
    return String(randomIntBetween(1980, 2025));
  }

  const min = 10 ** (limit - 1);
  const max = 10 ** limit - 1;
  return String(randomIntBetween(min, max + 1));
}

function generateRandomVRN(): string {
  return randomAlphabetic(2).toUpperCase() + randomNumeric(2) + randomAlphabetic(3).toUpperCase();
}

// --- SELENIUM ENGINE ---

async function runSeleniumEngine(
  driver: WebDriver,
  url: string,
  baseDomain: string,
  allowSubdomains: boolean,
  visited?: Set<string>,
): Promise<void> {
  try {
    await driver.get(url);
    await handleCookieBannersSelenium(driver);

    let attempts = 0;

    while (attempts++ < MAX_ATTEMPTS) {
      const currentUrl = await driver.getCurrentUrl();
      if (!DomainValidator.isSameDomain(currentUrl, baseDomain, allowSubdomains)) {
        logger.warn('Redirected outside domain.');
        return;
      }
      markVisited(visited, currentUrl);

      const inputs = await driver.findElements(
        By.css("input:not([type='hidden']), textarea, select"),
      );

      for (const el of inputs) {
        try {
          if (!(await el.isDisplayed()) || !(await el.isEnabled())) continue;
          await fillLogicSelenium(driver, el);
        } catch {
          /* stale element — keep going */
        }
      }

      const buttons = await getAllClickableButtonsSelenium(driver);

      if (buttons.length === 0) {
        logger.info('No clickable buttons found — assuming end of flow.');
        return;
      }

      const selected = buttons[randomIntBetween(0, buttons.length)];

      await performAggressiveSeleniumClick(driver, selected);

      try {
        await driver.wait(async () => (await driver.getCurrentUrl()) !== currentUrl, 3000);
      } catch {
        /* URL may legitimately stay the same */
      }

      await AxeScanner.scan(driver);
    }
  } catch (e) {
    logger.error('Selenium Engine failed', e);
  }
}

async function fillLogicSelenium(driver: WebDriver, el: WebElement): Promise<void> {
  const tagName = (await el.getTagName()).toLowerCase();

  if (tagName === 'select') {
    const options = await el.findElements(By.css('option'));
    if (options.length > 1) {
      await options[1].click();
    }
  } else {
    const type = (await el.getAttribute('type')) ?? 'text';
    if (type === 'radio' || type === 'checkbox') {
      if (!(await el.isSelected())) {
        await driver.executeScript('arguments[0].click();', el);
      }
    } else {
      await handleTextInputSelenium(driver, el);
    }
  }
}

async function handleTextInputSelenium(driver: WebDriver, element: WebElement): Promise<void> {
  const nameAttr = ((await element.getAttribute('name')) ?? '').toLowerCase();
  const typeAttr = ((await element.getAttribute('type')) ?? 'text').toLowerCase();
  const inputMode = ((await element.getAttribute('inputmode')) ?? '').toLowerCase();

  let hintText = '';
  try {
    hintText = (await driver.executeScript(
      "const group = arguments[0].closest('.govuk-form-group');" +
        "const error = group ? group.querySelector('.govuk-error-message') : null;" +
        "return error ? error.innerText.trim() : '';",
      element,
    )) as string;
  } catch (e) {
    logger.debug(`Could not retrieve hint text via JS: ${e instanceof Error ? e.message : e}`);
  }

  const isNumericField =
    typeAttr === 'number' ||
    inputMode.includes('numeric') ||
    hintText.includes('Can only contain numbers') ||
    hintText.includes('must be a number cylinder-capacity');

  if (isNumericField) {
    const val = generateNumericValueByLimit(await element.getAttribute('maxlength'));
    await waitAndEnterTextSelenium(element, val);
    logger.info(`Numeric Smart-Fill (Selenium): Entered ${val} for field ${nameAttr}`);
  } else if (nameAttr.includes('email')) {
    await waitAndEnterTextSelenium(element, 'answer.bot@dvsa.gov.uk');
  } else if (nameAttr.includes('phone')) {
    await waitAndEnterTextSelenium(element, `07${randomNumeric(9)}`);
  } else if (nameAttr.includes('postcode')) {
    await waitAndEnterTextSelenium(element, getPostcode());
  } else if (nameAttr.includes('registration')) {
    // Probabilistic toggle: 30/70 split
    const reg =
      Math.random() < 0.3 ? AppConfig.getString('registration', generateRandomVRN()) : generateRandomVRN();
    await waitAndEnterTextSelenium(element, reg);
    logger.info(`Registration flow: used ${reg}`);
  } else if (nameAttr.includes('vin')) {
    // 70/30 split
    const vin =
      Math.random() < 0.7
        ? AppConfig.getString('vin', randomAlphanumeric(17).toUpperCase())
        : randomAlphanumeric(17).toUpperCase();
    await waitAndEnterTextSelenium(element, vin);
    logger.info(`VIN flow: used ${vin}`);
  } else if (nameAttr.includes('weight') || nameAttr.includes('code')) {
    const val = generateNumericValueByLimit(await element.getAttribute('maxlength'));
    await waitAndEnterTextSelenium(element, val);
  } else {
    await waitAndEnterTextSelenium(element, randomAlphabetic(11).toLowerCase());
  }
}

async function waitAndEnterTextSelenium(element: WebElement, text: string): Promise<void> {
  try {
    await element.clear();
    await element.sendKeys(text);
  } catch (e) {
    logger.warn(`Failed to enter text into Selenium element: ${e instanceof Error ? e.message : e}`);
  }
}

async function performAggressiveSeleniumClick(driver: WebDriver, el: WebElement): Promise<void> {
  try {
    await driver.executeScript(
      "arguments[0].scrollIntoView({block: 'center'}); arguments[0].focus();",
      el,
    );
    await driver.executeScript(
      "var ev = new MouseEvent('click', {bubbles:true}); arguments[0].dispatchEvent(ev);",
      el,
    );
  } catch {
    await driver.executeScript('arguments[0].click();', el);
  }
}

async function getAllClickableButtonsSelenium(driver: WebDriver): Promise<WebElement[]> {
  const allButtons: WebElement[] = [];

  try {
    const combinedSelector = [
      'button:not([disabled])',
      "input[type='submit']:not([disabled])",
      "input[type='button']:not([disabled])",
      "input[type='reset']:not([disabled])",
      "a[class*='button']",
      "a[class*='btn']",
      "[role='button']",
      'div[onclick]',
      'span[onclick]',
    ].join(', ');

    allButtons.push(...(await driver.findElements(By.css(combinedSelector))));

    const essentialTexts = ['submit', 'continue', 'next', 'start', 'confirm', 'ok', 'apply', 'save'];

    for (const text of essentialTexts) {
      const xpathSelector =
        `//button[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '${text}')] | ` +
        `//a[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '${text}')]`;
      allButtons.push(...(await driver.findElements(By.xpath(xpathSelector))));
    }
  } catch (e) {
    logger.warn(`Error collecting Selenium buttons, falling back to basic: ${e instanceof Error ? e.message : e}`);
    allButtons.push(...(await driver.findElements(By.css("button:not([disabled]), input[type='submit']"))));
  }

  const valid: WebElement[] = [];
  for (const el of allButtons) {
    if (valid.length >= 10) break;
    try {
      if ((await el.isDisplayed()) && (await el.isEnabled()) && (await isSafeButtonSelenium(el))) {
        valid.push(el);
      }
    } catch {
      /* stale element — skip */
    }
  }
  return valid;
}

async function isSafeButtonSelenium(button: WebElement): Promise<boolean> {
  const text = (await button.getText()).toLowerCase();
  return !UNSAFE_BUTTON_TEXTS.some((unsafe) => text.includes(unsafe)) && text.length <= 120;
}

async function handleCookieBannersSelenium(driver: WebDriver): Promise<void> {
  try {
    await driver.findElement(By.xpath("//button[contains(text(),'Accept')]")).click();
  } catch {
    /* no cookie banner present */
  }
}
