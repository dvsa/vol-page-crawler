package org.dvsa.testing.framework.bots;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import net.datafaker.Faker;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.axe.AXEScanner;
import org.dvsa.testing.framework.config.AppConfig;
import org.dvsa.testing.framework.jsoup.SpiderCrawler;
import org.dvsa.testing.framework.utils.DomainValidator;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.dvsa.testing.framework.axe.AXEScanner.scan;


public class AnswerBot {
    private static final Logger LOGGER = LogManager.getLogger(AnswerBot.class);

    private static final Faker faker = new Faker(new Locale("en-GB"));

    public static String getPostcode() {
        return faker.address().zipCode();
    }

    public static void formAutoFill(Object driver, String url, String baseDomain, boolean allowSubdomains) {
        formAutoFill(driver, url, baseDomain, allowSubdomains, null);
    }

    /**
     * Fills and clicks through a form journey, scanning every step.
     * Pass the crawler's {@code visited} set to record each page the bot traverses,
     * so a surrounding crawl does not re-scan them.
     */
    public static void formAutoFill(Object driver, String url, String baseDomain, boolean allowSubdomains, Set<String> visited) {
        if (driver instanceof Page page) {
            runPlaywrightEngine(page, url, baseDomain, allowSubdomains, visited);
        } else if (driver instanceof WebDriver seleniumDriver) {
            runSeleniumEngine(seleniumDriver, url, baseDomain, allowSubdomains, visited);
        } else {
            LOGGER.warn("Unsupported driver type: {}", driver.getClass());
        }
    }

    private static void markVisited(Set<String> visited, String url) {
        if (visited != null && url != null) {
            visited.add(SpiderCrawler.normaliseUrl(url));
        }
    }


    private static void runPlaywrightEngine(Page page, String url,
                                            String baseDomain,
                                            boolean allowSubdomains,
                                            Set<String> visited) {

        if (!DomainValidator.isSameDomain(url, baseDomain, allowSubdomains)) {
            LOGGER.warn("Skipping form fill: outside domain");
            return;
        }

        setupPlaywrightPopupHandler(page, baseDomain, allowSubdomains);

        try {
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            handleCookieBanners(page);

            int attempts = 0;
            int maxAttempts = 30;

            while (attempts++ < maxAttempts) {

                if (!DomainValidator.isSameDomain(page.url(), baseDomain, allowSubdomains)) {
                    LOGGER.warn("Redirected outside domain.");
                    return;
                }
                markVisited(visited, page.url());

                // --- Fill Inputs (Locator based, no ElementHandle) ---
                Locator inputs = page.locator("input:not([type='hidden']), textarea, select");
                int count = inputs.count();

                for (int i = 0; i < count; i++) {
                    try {
                        Locator el = inputs.nth(i);
                        if (!el.isVisible() || el.isDisabled()) continue;
                        fillLogicPlaywright(el);
                    } catch (Exception ignored) {}
                }

                List<Locator> buttons = getAllClickableButtonsPlaywright(page)
                        .stream()
                        .filter(btn -> isSafeButtonPlaywright(page, btn, baseDomain, allowSubdomains))
                        .toList();

                if (buttons.isEmpty()) {
                    LOGGER.info("No clickable buttons found — assuming end of flow.");
                    return;
                }

                Locator selectedButton = buttons.get(
                        ThreadLocalRandom.current().nextInt(buttons.size())
                );

                String beforeUrl = page.url();

                selectedButton.evaluate("el => el.removeAttribute('target')");
                selectedButton.click(new Locator.ClickOptions().setTimeout(10000).setForce(true));

                try {
                        page.waitForURL(
                            u -> !u.equals(beforeUrl),
                            new Page.WaitForURLOptions().setTimeout(10000)
                        );
                } catch (PlaywrightException ignored) {}

                page.waitForLoadState(LoadState.DOMCONTENTLOADED);

                scan(page);
            }

        } catch (Exception e) {
            LOGGER.error("Playwright Engine failed", e);
        }
    }

    private static void runSeleniumEngine(WebDriver driver,
                                          String url,
                                          String baseDomain,
                                          boolean allowSubdomains,
                                          Set<String> visited) {

        try {
            driver.get(url);
            handleCookieBannersSelenium(driver);

            int attempts = 0;
            int maxAttempts = 30;

            while (attempts++ < maxAttempts) {

                String currentUrl = driver.getCurrentUrl();
                if (!DomainValidator.isSameDomain(currentUrl, baseDomain, allowSubdomains)) {
                    LOGGER.warn("Redirected outside domain.");
                    return;
                }
                markVisited(visited, currentUrl);

                List<WebElement> inputs =
                        driver.findElements(By.cssSelector("input:not([type='hidden']), textarea, select"));

                for (WebElement el : inputs) {
                    try {
                        if (!el.isDisplayed() || !el.isEnabled()) continue;
                        fillLogicSelenium(driver, el);
                    } catch (StaleElementReferenceException ignored) {}
                }

                List<WebElement> buttons =
                        getAllClickableButtonsSelenium(driver);

                if (buttons.isEmpty()) {
                    LOGGER.info("No clickable buttons found — assuming end of flow.");
                    return;
                }

                WebElement selected =
                        buttons.get(ThreadLocalRandom.current().nextInt(buttons.size()));

                performAggressiveSeleniumClick(driver, selected);

                try {
                    assert currentUrl != null;
                    new WebDriverWait(driver, Duration.ofSeconds(3))
                            .until(ExpectedConditions.not(
                                    ExpectedConditions.urlToBe(currentUrl)));
                } catch (Exception ignored) {}

                AXEScanner.scan(driver);
            }

        } catch (Exception e) {
            LOGGER.error("Selenium Engine failed", e);
        }
    }

    // --- HELPERS & LOGIC ---
    private static void fillLogicPlaywright(Locator el) {
        String tagName = (String) el.evaluate("el => el.tagName.toLowerCase()");

        if ("select".equals(tagName)) {
            if (el.locator("option").count() > 1) {
                el.selectOption(new SelectOption().setIndex(1));
            }
        } else {
            String type = Objects.requireNonNullElse(el.getAttribute("type"), "text");
            if (type.equals("radio") || type.equals("checkbox")) {
                if (!el.isChecked()) {
                    el.check(new Locator.CheckOptions().setForce(true));
                }
            } else {
                handleTextInput(el);
            }
        }
    }

    private static void handleTextInput(Locator locator) {
        String nameAttr = Objects.requireNonNullElse(locator.getAttribute("name"), "").toLowerCase();

        String typeAttr = Objects.requireNonNullElse(locator.getAttribute("type"), "text").toLowerCase();
        String inputMode = Objects.requireNonNullElse(locator.getAttribute("inputmode"), "").toLowerCase();

        String hintText = locator.evaluate("""
    el => {
        const group = el.closest('.govuk-form-group');
        const error = group?.querySelector('.govuk-error-message');
        return error ? error.innerText.trim() : '';
    }
""").toString();

        boolean isNumericField = typeAttr.equals("number") ||
                inputMode.contains("numeric") ||
                hintText.contains("Can only contain numbers") ||
                hintText.contains("must be a number cylinder-capacity");

        if (isNumericField) {
            String val = generateSmartNumericValue(locator);
            waitAndEnterText(locator, val);
            LOGGER.info("Numeric Smart-Fill: Entered {} for field {}", val, nameAttr);
        }
        else if (nameAttr.contains("email")) {
            waitAndEnterText(locator, "answer.bot@dvsa.gov.uk");
        }
        else if (nameAttr.contains("phone")) {
            waitAndEnterText(locator, "07" + ThreadLocalRandom.current().nextLong(100000000L, 999999999L));
        }
        else if (nameAttr.contains("postcode")) {
            waitAndEnterText(locator, getPostcode());
        }
        else if (nameAttr.toLowerCase().contains("aeusername")) {
            waitAndEnterText(locator, "ARYA7524");
        }
        else if (nameAttr.contains("registration")) {
            // Probabilistic toggle: 30/70 split
            String reg = (ThreadLocalRandom.current().nextDouble() < 0.3)
                    ? AppConfig.getString("registration")
                    : generateRandomVRN(); // Helper for formatted random VRN
            waitAndEnterText(locator, reg);
            LOGGER.info("Registration flow: used {}", reg);
        }
        else if (nameAttr.contains("vin")) {
            // 70/30 split
            String vin = (ThreadLocalRandom.current().nextDouble() < 0.7)
                    ? AppConfig.getString("vin")
                    : RandomStringUtils.secure().nextAlphanumeric(17).toUpperCase();
            waitAndEnterText(locator, vin);
            LOGGER.info("VIN flow: used {}", vin);
        }
        else if (nameAttr.contains("weight") || nameAttr.contains("code")) {
            String val = generateNumericValueByLimit(locator.getAttribute("maxlength"));
            waitAndEnterText(locator, val);
        }
        else {
            waitAndEnterText(locator, RandomStringUtils.secure().nextAlphabetic(11).toLowerCase());
        }
    }

    private static void waitAndEnterText(Locator locator, String text) {
        locator.scrollIntoViewIfNeeded();
        locator.fill(text);
    }

    private static void fillLogicSelenium(WebDriver driver, WebElement el) {
        String tagName = el.getTagName().toLowerCase();

        if ("select".equals(tagName)) {
            Select select = new Select(el);
            if (select.getOptions().size() > 1) {
                select.selectByIndex(1);
            }
        } else {
            String type = Objects.requireNonNullElse(el.getAttribute("type"), "text");
            if (type.equals("radio") || type.equals("checkbox")) {
                if (!el.isSelected()) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
                }
            } else {
                handleTextInputSelenium(driver, el);
            }
        }
    }

    private static void handleTextInputSelenium(WebDriver driver, WebElement element) {
        String nameAttr = Objects.requireNonNullElse(element.getAttribute("name"), "").toLowerCase();
        String typeAttr = Objects.requireNonNullElse(element.getAttribute("type"), "text").toLowerCase();
        String inputMode = Objects.requireNonNullElse(element.getAttribute("inputmode"), "").toLowerCase();

        String hintText = "";
        try {
            hintText = (String) ((JavascriptExecutor) driver).executeScript(
                    "const group = arguments[0].closest('.govuk-form-group');" +
                            "const error = group ? group.querySelector('.govuk-error-message') : null;" +
                            "return error ? error.innerText.trim() : '';",
                    element
            );
        } catch (Exception e) {
            LOGGER.debug("Could not retrieve hint text via JS: {}", e.getMessage());
        }

        boolean isNumericField = typeAttr.equals("number") ||
                inputMode.contains("numeric") ||
                Objects.requireNonNull(hintText).contains("Can only contain numbers") ||
                hintText.contains("must be a number cylinder-capacity");

        if (isNumericField) {
            String val = generateNumericValueByLimit(element.getAttribute("maxlength"));
            waitAndEnterTextSelenium(element, val);
            LOGGER.info("Numeric Smart-Fill (Selenium): Entered {} for field {}", val, nameAttr);
        }
        else if (nameAttr.contains("email")) {
            waitAndEnterTextSelenium(element, "answer.bot@dvsa.gov.uk");
        }
        else if (nameAttr.contains("phone")) {
            waitAndEnterTextSelenium(element, "07" + ThreadLocalRandom.current().nextLong(100000000L, 999999999L));
        }
        else if (nameAttr.contains("postcode")) {
            waitAndEnterTextSelenium(element, getPostcode());
        }
        else if (nameAttr.contains("registration")) {
            // Probabilistic toggle: 30/70 split
            String reg = (ThreadLocalRandom.current().nextDouble() < 0.3)
                    ? AppConfig.getString("registration")
                    : generateRandomVRN();
            waitAndEnterTextSelenium(element, reg);
            LOGGER.info("Registration flow: used {}", reg);
        }
        else if (nameAttr.contains("vin")) {
            // 70/30 split
            String vin = (ThreadLocalRandom.current().nextDouble() < 0.7)
                    ? AppConfig.getString("vin")
                    : RandomStringUtils.secure().nextAlphanumeric(17).toUpperCase();
            waitAndEnterTextSelenium(element, vin);
            LOGGER.info("VIN flow: used {}", vin);
        }
        else if (nameAttr.contains("weight") || nameAttr.contains("code")) {
            String val = generateNumericValueByLimit(element.getAttribute("maxlength"));
            waitAndEnterTextSelenium(element, val);
        }
        else {
            waitAndEnterTextSelenium(element, RandomStringUtils.secure().nextAlphabetic(11).toLowerCase());
        }
    }

    private static void waitAndEnterTextSelenium(WebElement element, String text) {
        try {
            element.clear();
            element.sendKeys(text);
        } catch (Exception e) {
            LOGGER.warn("Failed to enter text into Selenium element: {}", e.getMessage());
        }
    }

    private static void performAggressiveSeleniumClick(WebDriver driver, WebElement el) {
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'}); arguments[0].focus();", el);
            ((JavascriptExecutor) driver).executeScript("var ev = new MouseEvent('click', {bubbles:true}); arguments[0].dispatchEvent(ev);", el);
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }
    }

    private static List<WebElement> getAllClickableButtonsSelenium(WebDriver driver) {
        List<WebElement> allButtons = new ArrayList<>();

        try {
            String combinedSelector = String.join(", ",
                    "button:not([disabled])",
                    "input[type='submit']:not([disabled])",
                    "input[type='button']:not([disabled])",
                    "input[type='reset']:not([disabled])",
                    "a[class*='button']",
                    "a[class*='btn']",
                    "[role='button']",
                    "div[onclick]",
                    "span[onclick]"
            );

            allButtons.addAll(driver.findElements(By.cssSelector(combinedSelector)));

            String[] essentialTexts = {
                    "submit", "continue", "next", "start", "confirm", "ok", "apply", "save"
            };

            for (String text : essentialTexts) {
                String xpathSelector = String.format(
                        "//button[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '%s')] | " +
                                "//a[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '%s')]",
                        text, text
                );
                allButtons.addAll(driver.findElements(By.xpath(xpathSelector)));
            }

        } catch (Exception e) {
            LOGGER.warn("Error collecting Selenium buttons, falling back to basic: {}", e.getMessage());
            allButtons.addAll(driver.findElements(By.cssSelector("button:not([disabled]), input[type='submit']")));
        }

        return allButtons.stream()
                .distinct()
                .filter(e -> {
                    try {
                        return e.isDisplayed() && e.isEnabled() && isSafeButtonSelenium(e);
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .limit(10)
                .collect(Collectors.toList());
    }

    private static boolean isSafeButtonSelenium(WebElement b) {
        String txt = b.getText().toLowerCase();
        return !txt.contains("sign out") &&
                !txt.contains("logout") &&
                !txt.contains("remove") &&
                !txt.contains("cookies") &&
                !txt.contains("abort") &&
                txt.length() <= 120;
    }

    private static void handleCookieBanners(Page page) {
        try {
            page.locator("button:has-text('Accept'), .govuk-cookie-banner__button-accept").first().click(new Locator.ClickOptions().setTimeout(1000));
        } catch (Exception ignored) {}
    }

    private static void handleCookieBannersSelenium(WebDriver driver) {
        try {
            driver.findElement(By.xpath("//button[contains(text(),'Accept')]")).click();
        } catch (Exception ignored) {}
    }

    private static void setupPlaywrightPopupHandler(Page page, String baseDomain, boolean allowSubdomains) {
        page.context().onPage(newPage -> {
            try {
                newPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
                if (!DomainValidator.isSameDomain(newPage.url(), baseDomain, allowSubdomains)) {
                    LOGGER.info("Closing external popup: {}", newPage.url());
                    newPage.close();
                }
            } catch (Exception e) {
                LOGGER.debug("Popup handling skipped: {}", e.getMessage());
            }
        });
    }

    public static List<Locator> getAllClickableButtonsPlaywright(Page page) {
        List<Locator> allButtons = new ArrayList<>();

        try {
            String combinedSelector = String.join(", ",
                    "button:visible:enabled",
                    "input[type='submit']:visible:enabled",
                    "input[type='button']:visible:enabled",
                    "input[type='reset']:visible:enabled",
                    "a:visible[class*='button']",
                    "a:visible[class*='btn']",
                    "[role='button']:visible"
            );

            allButtons.addAll(page.locator(combinedSelector).all());

            String[] essentialButtonTexts = {
                    "submit", "continue", "next", "start", "confirm", "ok", "apply", "save"
            };

            for (String text : essentialButtonTexts) {
                String caseInsensitiveSelector = String.format(
                        "button:visible:enabled:has-text(/%s/i), a:visible:has-text(/%s/i)",
                        text, text);
                try {
                    allButtons.addAll(page.locator(caseInsensitiveSelector).all());
                } catch (Exception e) {
                    allButtons.addAll(page.locator("button:visible:enabled:has-text('" + text + "'), a:visible:has-text('" + text + "')").all());
                }
            }

            allButtons.addAll(page.locator("div:visible[onclick]:first-of-type, span:visible[onclick]:first-of-type").all());

        } catch (Exception e) {
            LOGGER.warn("Error collecting buttons, falling back to basic selector: {}", e.getMessage());
            allButtons.addAll(page.locator("button:visible:enabled, input[type='submit']:visible:enabled").all());
        }

        return allButtons.stream()
                .distinct()
                .filter(AnswerBot::isValidClickableButtonPlaywright)
                .limit(10) // Limit to avoid excessive button clicks
                .collect(Collectors.toList());
    }

    private static boolean isValidClickableButtonPlaywright(Locator button) {
        try {
            if (!button.isVisible() || button.isDisabled()) return false;

            String text = Objects.toString(button.textContent(), "").toLowerCase();
            String value = Objects.toString(button.getAttribute("value"), "").toLowerCase();

            return !text.contains("sign out") &&
                    !text.contains("logout") &&
                    !text.contains("remove") &&
                    !text.contains("cookies") &&
                    !value.contains("logout") &&
                    text.length() <= 120;

        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSafeButtonPlaywright(Page page,
                                                  Locator button,
                                                  String baseDomain,
                                                  boolean allowSubdomains) {
        try {
            String href = button.getAttribute("href");
            if (href == null || href.startsWith("#") || href.startsWith("/")) return true;

            String resolved = URI.create(page.url()).resolve(href).toString();
            return DomainValidator.isSameDomain(resolved, baseDomain, allowSubdomains);
        } catch (Exception e) {
            return true;
        }
    }

    private static String generateSmartNumericValue(Locator locator) {
        String id = locator.getAttribute("id").toLowerCase();
        String name = locator.getAttribute("name").toLowerCase();
        String maxLenAttr = locator.getAttribute("maxlength");

        // 1. Check by ID/Name first (Most reliable for Dates)
        if (id.contains("month") || name.contains("month")) {
            return String.valueOf(ThreadLocalRandom.current().nextInt(1, 13));
        }
        if (id.contains("day") || name.contains("day")) {
            return String.valueOf(ThreadLocalRandom.current().nextInt(1, 29));
        }
        if (id.contains("year") || name.contains("year")) {
            return String.valueOf(ThreadLocalRandom.current().nextInt(1980, 2025));
        }

        // 2. Fallback to maxlength if it exists
        if (maxLenAttr != null) {
            int limit = Integer.parseInt(maxLenAttr);
            long min = (long) Math.pow(10, limit - 1);
            long max = (long) Math.pow(10, limit) - 1;
            return String.valueOf(ThreadLocalRandom.current().nextLong(min, max));
        }

        // 3. Final generic fallback
        return String.valueOf(ThreadLocalRandom.current().nextInt(1, 100));
    }

    private static String generateNumericValueByLimit(String maxLenAttr) {
        int limit = (maxLenAttr != null && !maxLenAttr.isEmpty()) ? Integer.parseInt(maxLenAttr) : 5;

        if (limit == 2) {
            return String.format("%02d", ThreadLocalRandom.current().nextInt(1, 13));
        }
        if (limit == 4) {
            return String.valueOf(ThreadLocalRandom.current().nextInt(1980, 2025));
        }

        long min = (long) Math.pow(10, limit - 1);
        long max = (long) Math.pow(10, limit) - 1;
        return String.valueOf(ThreadLocalRandom.current().nextLong(min, max + 1));
    }

    private static String generateRandomVRN() {
        return RandomStringUtils.secure().nextAlphabetic(2).toUpperCase() +
                RandomStringUtils.secure().nextNumeric(2) +
                RandomStringUtils.secure().nextAlphabetic(3).toUpperCase();
    }
}