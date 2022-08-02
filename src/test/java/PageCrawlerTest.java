import activesupport.driver.Browser;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;

import java.util.ArrayList;


public class PageCrawlerTest {

    String baseURL = "https://ssweb.qa.olcs.dev-dvsacloud.uk/auth/login/";

    @Before
    public void setUp() {
        System.setProperty("browser", "headless");
    }

    @Test
    public void someTest() {
        SpiderCrawler.crawler(1, baseURL, new ArrayList<>());
    }
}
