package org.dvsa.testing.framework.jsoup;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.axe.AXEScanner;
import org.dvsa.testing.framework.bots.AnswerBot;
import org.dvsa.testing.framework.utils.DomainValidator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.openqa.selenium.WebDriver;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;


public class SpiderCrawler {

    private static final Logger LOGGER = LogManager.getLogger(SpiderCrawler.class);
    private static final int MAX_CRAWL_DEPTH = 30;
    private static final int MAX_URLS_PER_DOMAIN = 1000;

    private static final List<String> BLACKLISTED_PATHS = Arrays.asList(
            System.getProperty("scanner.exclude.paths", "/topsreport").split(",")
    );

    private static boolean isInvalidUrl(String url) {
        if (url == null || url.trim().isEmpty() || url.startsWith("about:")) {
            return true;
        }

        if (ACTION_PATTERN.matcher(url).matches()) {
            LOGGER.warn("Skipping potential state-changing URL: {}", url);
            return true;
        }

        boolean isBlacklisted = BLACKLISTED_PATHS.stream()
                .anyMatch(path -> url.toLowerCase().contains(path.toLowerCase()));

        if (isBlacklisted) {
            LOGGER.info("URL ignored (Blacklisted path): {}", url);
            return true;
        }

        String pathOnly = url.split("\\?")[0];
        return EXCLUDE_PATTERN.matcher(pathOnly).matches();
    }

    private static final Pattern EXCLUDE_PATTERN = Pattern.compile(
            ".*\\.(pdf|zip|gz|tar|mp[34]|avi|png|jpg|jpeg|gif|svg|ico|css|js|map|csv|docx?|xlsx?|exe|dmg|woff2?|ttf|eot|json|xml)(\\?.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ACTION_PATTERN = Pattern.compile(
            ".*(logout|signout|delete|remove|cancel|auth|login|abort).*",
            Pattern.CASE_INSENSITIVE
    );


    public static void crawler(int level, String url, Set<String> visited, Object driver) {
        crawler(level, url, visited, driver, false);
    }

    /**
     * @param formFill when true, pages containing a form are handed to the AnswerBot,
     *                 which fills inputs and clicks through the journey, scanning every
     *                 step. This SUBMITS FORMS against the target service and is
     *                 intentionally random, so it is opt-in; the default crawl remains
     *                 read-only.
     */
    public static void crawler(int level, String url, Set<String> visited, Object driver, boolean formFill) {
        String baseDomain = DomainValidator.extractDomain(url);
        if (baseDomain == null) {
            LOGGER.error("Cannot extract domain from starting URL: {}", url);
            return;
        }
        crawlerWithDomain(level, url, visited, driver, baseDomain, false, formFill);
    }

    public static void crawlerWithDomain(int level, String url, Set<String> visited, Object driver, String baseDomain, boolean allowSubdomains) {
        crawlerWithDomain(level, url, visited, driver, baseDomain, allowSubdomains, false);
    }

    public static void crawlerWithDomain(int level, String url, Set<String> visited, Object driver, String baseDomain, boolean allowSubdomains, boolean formFill) {
        if (level >= MAX_CRAWL_DEPTH || visited.size() >= MAX_URLS_PER_DOMAIN) {
            return;
        }

        String normalisedUrl = normaliseUrl(url);

        if (isInvalidUrl(normalisedUrl)) {
            return;
        }

        if (!visited.add(normalisedUrl)) {
            LOGGER.debug("Skipping: Base URL already visited -> {}", normalisedUrl);
            return;
        }

        if (!DomainValidator.isSameDomain(normalisedUrl, baseDomain, allowSubdomains)) {
            return;
        }

        LOGGER.info("Crawling [Level {}]: {}", level, normalisedUrl);

        try {
            String actualUrl;
            if (driver instanceof Page page) {
                page.navigate(normalisedUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
                page.waitForLoadState(LoadState.NETWORKIDLE);
                page.evaluate("() => { document.querySelectorAll('a[target]').forEach(l => l.removeAttribute('target')); }");
                actualUrl = page.url();
            } else if (driver instanceof WebDriver seleniumDriver) {
                seleniumDriver.get(normalisedUrl);
                actualUrl = seleniumDriver.getCurrentUrl();
            } else {
                throw new IllegalArgumentException("Unsupported driver type");
            }

            if (!DomainValidator.isSameDomain(actualUrl, baseDomain, allowSubdomains)) {
                LOGGER.debug("Redirected outside domain: {}", actualUrl);
                return;
            }

            AXEScanner.scan(driver);

            String freshContent = getPageSource(driver);
            assert actualUrl != null;
            Document doc = Jsoup.parse(freshContent, actualUrl);
            var links = doc.select("a[href]");

            if (formFill && !doc.select("form").isEmpty()) {
                LOGGER.info("Form detected on {} — handing over to AnswerBot", actualUrl);
                AnswerBot.formAutoFill(driver, actualUrl, baseDomain, allowSubdomains, visited);
            }

            for (Element link : links) {
                String nextUrl = link.absUrl("href");
                if (shouldFollow(nextUrl, baseDomain, visited, allowSubdomains)) {
                    crawlerWithDomain(level + 1, nextUrl, visited, driver, baseDomain, allowSubdomains, formFill);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to crawl URL [Level {}]: {}", level, normalisedUrl, e);
        }
    }

    public static String normaliseUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) return "";

        try {
            URI uri = new URI(urlString.trim());
            String normalized = new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath() != null ? uri.getPath() : "/",
                    null,
                    null
            ).toString().toLowerCase();

            return (normalized.endsWith("/") && normalized.length() > 8) // > 8 to avoid stripping 'https://'
                    ? normalized.substring(0, normalized.length() - 1)
                    : normalized;

        } catch (Exception e) {
            String fallback = urlString.trim().toLowerCase();
            if (fallback.contains("#")) {
                fallback = fallback.split("#")[0];
            }
            if (fallback.contains("?")) {
                fallback = fallback.split("\\?")[0];
            }
            if (fallback.endsWith("/") && fallback.length() > 8) {
                fallback = fallback.substring(0, fallback.length() - 1);
            }
            return fallback;
        }
    }

    private static boolean shouldFollow(String url, String baseDomain, Set<String> visited, boolean allowSubdomains) {
        if (isInvalidUrl(url)) return false;

        if (!DomainValidator.isSameDomain(url, baseDomain, allowSubdomains)) return false;

        String normalized = normaliseUrl(url);
        return !visited.contains(normalized);
    }


    private static String getPageSource(Object driver) {
        if (driver instanceof Page page) {
            return page.content();
        } else if (driver instanceof WebDriver selenium) {
            return selenium.getPageSource();
        }
        return "";
    }
}