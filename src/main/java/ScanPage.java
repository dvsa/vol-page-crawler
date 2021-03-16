import Util.DataHandler;
import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Map.*;


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

    Set<String> parentURLS = new HashSet<>();

    private static final Logger LOGGER = LogManager.getLogger(ScanPage.class);

    public void scanForSubTree(String baseURL) throws IOException, IllegalBrowserException {
        Browser.navigate().get(baseURL);
        List<WebElement> links = Browser.navigate().findElements(By.tagName("a"));
        for (WebElement link : links) {
            url = link.getAttribute("href");
            if (url.contains(getBaseURL())) {
                parentURLS.add(url);
            } else {
                LOGGER.info(getUrl().concat(" did not get scanned"));
            }
        }
    }

    public String getLastElement(){
        String lastElement = "";
       for(String last : parentURLS){
           lastElement = last;
       }
       return lastElement;
    }

    public void scanALL(String internalUrl) throws IOException, IllegalBrowserException {
        scanForSubTree(internalUrl);
        for (Iterator<String> iterator = parentURLS.iterator(); iterator.hasNext(); ) {
            String previous = iterator.next();
            scanForSubTree(previous);
            if (!getLastElement().isEmpty()) {
                iterator.remove();
                System.out.println("REMOVED " + previous);
                System.out.println(parentURLS.size() + " SIZE");
            }
        }
    }
}