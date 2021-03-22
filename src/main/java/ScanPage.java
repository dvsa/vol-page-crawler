import Util.Waits;
import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import static java.lang.Thread.sleep;


public class ScanPage {

    private static final String BASEURL = System.getProperty("baseURL");
    static URI uri = URI.create(BASEURL);

    private String url = "";
    final static String tempFileName = "URLList.txt";
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
    Set<String> urlSet = new HashSet<>();
    Set<String> allUniqueUrls = new HashSet<>();


    public void scanForSubTreeLoopingPagination() throws IOException, IllegalBrowserException, InterruptedException {

        boolean paginationPresent = Browser.navigate().findElements(By.xpath("//a[@rel='Next']")).size() > 0;

        if (paginationPresent) {
            Browser.navigate().findElement(By.xpath("//a[contains(@href,'limit=50') and text()='50']")).click();
            Waits.waitForTableToLoad50Rows(Browser.navigate());
            try {
                scanForSubTree();
            } catch (StaleElementReferenceException e) {
                sleep(5000);
                scanForSubTree();
            }
            paginationPresent = Browser.navigate().findElements(By.xpath("//a[@rel='Next']")).size() > 0;

            int pageCount = 1;
            while(paginationPresent && pageCount <= 10){
                Browser.navigate().findElement(By.xpath("//a[@rel='Next']")).click();
                try {
                    Waits.waitForElementToBePresent(Browser.navigate(), String.format("//a[text()='%s' and contains(@href,'page=')]", pageCount));
                } catch (NoSuchElementException e) {
                    Browser.navigate().findElement(By.xpath("//a[@rel='Next']")).click();
                }
                pageCount++;
                try {
                    scanForSubTree();
                } catch (StaleElementReferenceException e) {
                    sleep(5000);
                    scanForSubTree();
                }
                paginationPresent = Browser.navigate().findElements(By.xpath("//a[@rel='Next']")).size() > 0;
            }
        }
        else {
            scanForSubTree();
        }

    }

    public void scanForSubTree() throws IOException, IllegalBrowserException {

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
                    urlSet.add(getUrl());
                }
            }
        }
    }

    public Set<String> scanForAllURLs(String internalUrl) throws IOException, IllegalBrowserException, InterruptedException {
        //TODO: All ability to enter set of urls.
        allUniqueUrls.add(internalUrl);

        Browser.navigate().get(internalUrl);
        scanForSubTreeLoopingPagination();

        String urlRegex;
        int previousAllUrlsSize = 0;
        Set<String> newUrls = new HashSet<>();

        while (true) {

            newUrls.clear();

            for (String url : urlSet) {
                urlRegex = url.replaceAll("\\/[0-9]{1,45}\\/", "/[0-9]{1,45}/");
                String finalUrlRegex = urlRegex;
                boolean containsUrl = allUniqueUrls.stream().anyMatch(x -> x.matches(finalUrlRegex));
                if (!containsUrl) {
                    allUniqueUrls.add(url);
                    newUrls.add(url);
                }
            }

            if (allUniqueUrls.size() == previousAllUrlsSize) {
                break;
            }
            previousAllUrlsSize = allUniqueUrls.size();

            urlSet.clear();
            for (String url : newUrls) {
                Browser.navigate().get(url);
                scanForSubTreeLoopingPagination();
            }
        }

        return allUniqueUrls;
    }
}