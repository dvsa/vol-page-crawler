import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;

import java.io.IOException;

public class QuickTest {
//    String baseURL = "https://iuap1.qa.olcs.dev-dvsacloud.uk/auth/login/";
    String baseURL = "https://www.bbc.co.uk";

    @Before
    public void setUp() {
        System.setProperty("baseURL", baseURL);
        System.setProperty("browser", "chrome");
    }

    @Test
    public void crawlPage() throws IOException, IllegalBrowserException {
        ScanPage scanPage = new ScanPage();
        Browser.navigate().get(baseURL);
//        Browser.navigate().findElement(By.name("username")).sendKeys("usr291");
//        Browser.navigate().findElement(By.name("password")).sendKeys("Get755Cob!VoxPig");
//        Browser.navigate().findElement(By.name("submit")).click();
        scanPage.scanALL(Browser.navigate().getCurrentUrl());
    }
}
