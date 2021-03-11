import Util.DataHandler;
import Util.SeleniumUtils;
import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.List;


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

    public void scanForSubTree(String baseURL) throws IOException, IllegalBrowserException {
        Browser.navigate().get(baseURL);
        List<WebElement> links = Browser.navigate().findElements(By.tagName("a"));
        for (WebElement link : links) {
            setUrl(link.getAttribute("href"));
            if (!url.contains(getBaseURL()) || (url.contains("logout"))) {
                LOGGER.info(getUrl().concat(" did not get scanned"));
            } else {
                DataHandler.writeToFile(tempFileName, getUrl());
            }
        }
    }

    public void scanSubTree() throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(tempFileName));
            while ((url = reader.readLine()) != null) {
                Browser.navigate().get(url);
                SeleniumUtils.waitForLoad(Browser.navigate());
            }
            reader.close();
            DataHandler.DeleteFile(tempFileName);
        } catch (FileNotFoundException | IllegalBrowserException ignored) {
        }
    }
}