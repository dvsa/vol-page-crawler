import Util.DataHandler;
import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

public class UrlTest {
    String internalUrl = "https://iuap1.qa.olcs.dev-dvsacloud.uk/";
    String internalLicencePSV = "https://iuap1.qa.olcs.dev-dvsacloud.uk/licence/21074";
    String internalLicenceGV = "https://iuap1.qa.olcs.dev-dvsacloud.uk/licence/369461";
    String internalApplication = "https://iuap1.qa.olcs.dev-dvsacloud.uk/application/1246831";
    String internalVariation = "https://iuap1.qa.olcs.dev-dvsacloud.uk/variation/1215538/";
    String internalBusRegsitration = "https://iuap1.qa.olcs.dev-dvsacloud.uk/operator/73513/business-details/";
    String internalTM = "https://iuap1.qa.olcs.dev-dvsacloud.uk/transport-manager/228246/details/";
    String internalUser = "https://iuap1.qa.olcs.dev-dvsacloud.uk/admin/user-management/users/edit/65739";

    @Before
    public void setup() {
        System.setProperty("baseURL", internalUrl);
        System.setProperty("browser", "chrome");
    }

    @Test
    public void internalScan() throws IOException, IllegalBrowserException, InterruptedException {
        ScanPage scanPage = new ScanPage();
        Browser.navigate().get(internalUrl);
        Browser.navigate().findElement(By.name("username")).sendKeys("usr291");
        Browser.navigate().findElement(By.name("password")).sendKeys("Get755Cob!VoxPig");
        Browser.navigate().findElement(By.name("submit")).click();
        String[] baseUrls = {internalUrl, internalLicencePSV, internalLicenceGV, internalApplication,
                internalVariation, internalBusRegsitration, internalTM, internalUser};
        Set<String> allUrs = scanPage.scanForAllURLs(baseUrls);
        Object[] allUrlsArray = allUrs.toArray();
        Arrays.sort(allUrlsArray);

        for (Object url : allUrlsArray) {
            DataHandler.writeToFile("allUrls.txt", url.toString());
        }
    }
}

//TODO: Need to go down different routes for different choices.
// Need to look into storing choices for each route?
// Maybe have something that goes through an entire journey and keep going until it loops to a previously stored page within that single journey
// Proceed to do the same journey with different data.
// Look at different choices and what could create a new route and how to store that as a fork to return to. Submit buttons, radio buttons.
// Look at where forms go via actions and that stuff.

// If there are errors, store the pages for assessing and possibly create loop to fill in missing data.

// Store the url regex and then store elements and answer to that page and loop over those.

// Add way to pass in list of ids that are expected submit buttons? Or just find all submit buttons?