import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import activesupport.faker.FakerUtils;
import Util.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.net.MalformedURLException;
import java.util.List;

public abstract class AnswerBot {

    public static void completeAndSubmitForm() throws MalformedURLException, IllegalBrowserException {
        FakerUtils faker = new FakerUtils();

        if (Browser.navigate().findElements(By.tagName("input")).size() != 0) {
            SeleniumUtils
                    .waitForLoad(Browser.navigate());
            List<WebElement> inputTag = Browser.navigate().findElements(By.tagName("input"));
            for (WebElement webElement : inputTag) {
                String type = webElement.getAttribute("type");

                if (type.equals("radio")) {
                    SeleniumUtils
                            .waitForLoad(Browser.navigate());
                    inputTag.forEach(WebElement::click);
                }
                if (type.equals("text")) {
                    SeleniumUtils
                            .waitForLoad(Browser.navigate());
                    inputTag.forEach(x -> x.sendKeys(faker.generateFirstName()));
                }
                if (type.equals("checkbox")) {
                    SeleniumUtils
                            .waitForLoad(Browser.navigate());
                    inputTag.forEach(WebElement::click);
                }
                if (type.equals("submit")) {
                    SeleniumUtils
                            .waitForLoad(Browser.navigate());
                    for (WebElement buttonName : inputTag) {
                        if (!buttonName.getText().equals("close")) {
                            SeleniumUtils.clickUsingJavaScript(buttonName.getText());
                        }
                    }
                }
            }
        }
    }
}