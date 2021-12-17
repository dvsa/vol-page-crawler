import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;


public class PageCrawlerTest {

    String baseURL = System.getProperty("url");

    @Before
    public void setUp() {
        System.setProperty("browser", "chrome");
    }

    @Test
    public void someTest(){
        SpiderCrawler.crawler(1, baseURL, new ArrayList<>());
    }
}
