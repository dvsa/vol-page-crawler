import Util.DataHandler;
import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;

import java.io.IOException;
import java.util.Set;

public class UrlTest {
    String internalUrl = "https://iuap1.qa.olcs.dev-dvsacloud.uk/";

    @Before
    public void setup() {
        System.setProperty("baseURL", internalUrl);
        System.setProperty("browser", "chrome");
    }

    @Test
    public void internalScan() throws IOException, IllegalBrowserException {
        ScanPage scanPage = new ScanPage();
        Browser.navigate().get(internalUrl);
        Browser.navigate().findElement(By.name("username")).sendKeys("usr291");
        Browser.navigate().findElement(By.name("password")).sendKeys("Get755Cob!VoxPig");
        Browser.navigate().findElement(By.name("submit")).click();
        Set<String> allUrs = scanPage.scanForAllURLs(internalUrl);

        for (String url : allUrs) {
            DataHandler.writeToFile("allUrls.txt", url);
        }


//        WebElement table = Browser.navigate().findElement(By.xpath("//td"));
//        if (table!= null) {
//            DataHandler.writeToFile("tableUrls.txt", scanPage.getUrl());
//        }

    }
}
