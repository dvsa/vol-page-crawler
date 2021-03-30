import Util.Waits;
import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;

import static java.lang.Thread.sleep;


public class ScanPage {

    private static final String BASEURL = System.getProperty("baseURL");
    static URI uri = URI.create(BASEURL);

    private String url = "";
    public static String getBaseURL() {
        return uri.getAuthority();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    private static final Logger LOGGER = LogManager.getLogger(ScanPage.class);



    public Set<String> scanForUniqueAllURLs(String[] baseUrls) throws IOException, IllegalBrowserException, InterruptedException {
        Set<String> allScannedUrls = new HashSet<>();
        Set<String> allUniqueUrls = new HashSet<>();

        for (String url : baseUrls) {
            allUniqueUrls.add(url);
            Browser.navigate().get(url);
            allScannedUrls.addAll(scanForSubTreeLoopingPagination());
        }

        String urlRegex;
        int previousAllUrlsSize = 0;
        Set<String> newUniqueUrls = new HashSet<>();

        while (true) {

            newUniqueUrls.clear();

            for (String scannedUrl : allScannedUrls) {
                urlRegex = scannedUrl.replaceAll("\\/[0-9]{1,45}\\/", "/[0-9]{1,45}/");
                String finalUrlRegex = urlRegex;
                boolean containsUrl = allUniqueUrls.stream().anyMatch(x -> x.matches(finalUrlRegex));
                if (!containsUrl) {
                    allUniqueUrls.add(scannedUrl);
                    newUniqueUrls.add(scannedUrl);
                }
            }

            if (allUniqueUrls.size() == previousAllUrlsSize) {
                break;
            }
            previousAllUrlsSize = allUniqueUrls.size();

            allScannedUrls.clear();
            for (String url : newUniqueUrls) {
                Browser.navigate().get(url);
                allScannedUrls.addAll(scanForSubTreeLoopingPagination());
            }
        }

        return allUniqueUrls;
    }

    // Use a do while and incorporate initial scan up to the top of the method. move urlSet.clear() downwards to the to and use
    // do {} while () where the while is the if statement with the break.

    public Set<String> scanForSubTreeLoopingPagination() throws IOException, IllegalBrowserException, InterruptedException {
        Set<String> allScannedUrls = new HashSet<>();
        boolean paginationPresent = Browser.navigate().findElements(By.xpath("//a[@rel='Next']")).size() > 0;

        if (paginationPresent) {
            Browser.navigate().findElement(By.xpath("//a[contains(@href,'limit=50') and text()='50']")).click();
            Waits.waitForTableToLoad50Rows(Browser.navigate());
            allScannedUrls.addAll(tryCatchScanForSubTree());
            paginationPresent = Browser.navigate().findElements(By.xpath("//a[@rel='Next']")).size() > 0;

            int pageCount = 1;

            while(paginationPresent && pageCount <= 10) {
                clickNextAndWaitForTableLoad(pageCount);
                pageCount++;
                allScannedUrls.addAll(tryCatchScanForSubTree());
                paginationPresent = Browser.navigate().findElements(By.xpath("//a[@rel='Next']")).size() > 0;
            }
        }
        else {
            allScannedUrls.addAll(scanForSubTree());
        }
        return allScannedUrls;
// Look at breaking early after a scan when there is no pagination. Don't need to run all the above first.
    }

    private Set<String> tryCatchScanForSubTree() throws IOException, IllegalBrowserException, InterruptedException {
        try {
            return scanForSubTree();
        } catch (StaleElementReferenceException e) {
            sleep(5000);
            return scanForSubTree();
        }
        // Look at adding a counter to try
    }

    private void clickNextAndWaitForTableLoad(int pageCount) throws MalformedURLException, IllegalBrowserException {
        Browser.navigate().findElement(By.xpath("//a[@rel='Next']")).click();
        try {
            // Look into waiting for current class on page number at the bottom of pagination.
            Waits.waitForElementToBePresent(Browser.navigate(), String.format("//a[text()='%s' and contains(@href,'page=')]", pageCount));
        } catch (NoSuchElementException | TimeoutException e) {
            Browser.navigate().findElement(By.xpath("//a[@rel='Next']")).click();
        }
    }

    private Set<String> scanForSubTree() throws IOException, IllegalBrowserException {
        Set<String> scannedUrls = new HashSet<>();

        List<WebElement> links = Browser.navigate().findElements(By.tagName("a"));


        for (WebElement link : links) {

            setUrl(link.getAttribute("href"));
            if (!(url == null)) {
                boolean isFileDownloadLink = url.matches(".*file/[0-9]{0,45}");

                if (!url.contains(getBaseURL())
                        || (url.contains("logout"))
                        || (url.contains("?sort"))
                        || url.contains("?page")
                        || isFileDownloadLink
                        || url.contains("?startDate")
                        || url.contains("?validForYear")
                        || url.contains("postScoringReport")
                        || url.contains("alignStock")
                        || url.contains("ms-word:ofe")) {
                    LOGGER.info(getUrl().concat(" did not get scanned"));
                } else {
                    scannedUrls.add(getUrl());
                }
            }
            //TODO: look into iterable classes and passing in a map or something to check exclusions.
            // Use a break if it hits any of the exclusions.
            // Write Unit Tests for methods - extract method for the exclusion sorting.
        }
        return scannedUrls;
    }

    public Set<String> scanForSubmitButtonIds() throws MalformedURLException, IllegalBrowserException {
        List<WebElement> submitButtons = Browser.navigate().findElements(By.xpath("//button[@type='submit']"));
        Set<String> submitButtonIds = null;

        for(WebElement submit : submitButtons) {
            if(!submit.getAttribute("name").contains("address")
                || submit.getAttribute("class").contains("visually-hidden")) {
                LOGGER.info("submit button ignored");
            } else {
                submitButtonIds.add(submit.getAttribute("id"));
            }
        }
        return submitButtonIds;
    }
}