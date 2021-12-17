import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import activesupport.faker.FakerUtils;
import Util.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.WebElement;

import java.net.MalformedURLException;
import java.util.List;

public abstract class AnswerBot {

    public static void completeForm() throws MalformedURLException, IllegalBrowserException {
        FakerUtils faker = new FakerUtils();

        if (Browser.navigate().findElements(By.tagName("input")).size() != 0) {
            SeleniumUtils
                    .waitForLoad(Browser.navigate());
            List<WebElement> inputElements = Browser.navigate().findElements(By.tagName("input"));
            for (WebElement webElement : inputElements) {
                String type = webElement.getAttribute("type");
                SeleniumUtils.waitForLoad(Browser.navigate());

                String isElementDisabled = webElement.getAttribute("disabled");

                try {
                    if (isElementDisabled == null || isElementDisabled.equals("false")) {

                        if (type.equals("radio")) {
                            if (webElement.getAttribute("value").equals("Y") && !webElement.isSelected()) {
                                webElement.click();
                            }
                        }

                        if (type.equals("text")) {
                            webElement.sendKeys(faker.generateFirstName());
                        }

                        if (type.equals("checkbox")) {
                            webElement.click();
                        }
//                TODO: if a search, enter valid postcode just to get a response and utilise search
                    }

                } catch (ElementNotInteractableException e) {
                    e.printStackTrace();
                }

            }
        }
    }
}