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
    Set<String> allUniqueUrls = new HashSet<>();

    public void scanForSubTree(String baseURL) throws IOException, IllegalBrowserException {
        Browser.navigate().get(baseURL);
        List<WebElement> links = Browser.navigate().findElements(By.tagName("a"));
        for (WebElement link : links) {

            setUrl(link.getAttribute("href"));
            if (!(url == null)) {

                if (!url.contains(getBaseURL()) || (url.contains("logout")) || (url.contains("?sort")) || url.contains("?page") || url.contains(".rtf")) {
                    LOGGER.info(getUrl().concat(" did not get scanned"));
                } else {
                    urlSet.add(getUrl());
                }
//TODO: return the set found in this function. Makes it easier for end user to manipulate afterwards.
            }
        }
    }

    public Set<String> scanForAllURLs(String internalUrl) throws IOException, IllegalBrowserException {
//        Set<String> allUrls = new HashSet<>();
        allUniqueUrls.add(internalUrl);

        scanForSubTree(internalUrl);

        String urlRegex;
        int previousAllUrlsSize = 0;

        while (true) {
            for (String url : urlSet) {
                urlRegex = url.replaceAll("\\/[0-9]{1,45}\\/", "/[0-9]{1,45}/");
                String finalUrlRegex = urlRegex;
                boolean containsUrl = allUniqueUrls.stream().anyMatch(x -> x.matches(finalUrlRegex));

                if (!containsUrl) {
                    allUniqueUrls.add(url);
                }
            }
            System.out.println(urlSet);
            System.out.println(allUniqueUrls);
            if (allUniqueUrls.size() == previousAllUrlsSize) {
                break;
            }
            previousAllUrlsSize = allUniqueUrls.size();

            urlSet.clear();
            for (String url : allUniqueUrls) {
                scanForSubTree(url);
            }
        }

        //TODO: OPTIMIZE TO ONLY SCANNED PAGES NOT YET SCANNED> FIND OUT HOW THEY ARE ADDED TO THE SET AND EXCLUDE A SET COUNT EACH TIME.
        //TODO: COME UP WITH WAY TO EXCLUDE URLS WITH FILE/0432894324

        return allUniqueUrls;
    }
}