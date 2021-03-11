import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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

    public void scanForSubTree(String baseURL) throws IOException, IllegalBrowserException {
        Browser.navigate().get(baseURL);
        List<WebElement> links = Browser.navigate().findElements(By.tagName("a"));
        for (WebElement link : links) {
            setUrl(link.getAttribute("href"));
            if (!url.contains(getBaseURL()) || (url.contains("logout")) || (url.contains("?sort"))) {
                LOGGER.info(getUrl().concat(" did not get scanned"));
            } else {
                urlSet.add(getUrl());
            }
        }
    }

    public Set<String> scanForAllURLs(String internalUrl) throws IOException, IllegalBrowserException {
        Set<String> allUrls = new HashSet<>();

        scanForSubTree(internalUrl);

        String urlRegex = null;

        boolean stillAdding = true;
        int previousAllUrlsSize = 0;
        while (stillAdding) {
            for (String url : urlSet) {
                urlRegex = url.replaceAll("\\/[0-9]{1,45}\\/", "\\/[0-9]{1,45}\\/");
                if (!allUrls.contains(urlRegex)) {
                    allUrls.add(url);
                }
            }
            if (allUrls.size() == previousAllUrlsSize) {
                stillAdding = false;
            }
            previousAllUrlsSize = allUrls.size();

            for (String url : allUrls) {
                scanForSubTree(url);
            }
        }

        return allUrls;
    }
}