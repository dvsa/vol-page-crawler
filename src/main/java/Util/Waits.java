package Util;

import activesupport.IllegalBrowserException;
import com.google.common.base.Function;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import java.net.MalformedURLException;
import java.time.Duration;
import java.util.List;

public class Waits {

    private static final int TIME_OUT_SECONDS = 60;
    private static final int POLLING_SECONDS = 1;


    public static void waitForElementToBePresent(WebDriver driver, String selector) {
        Wait<WebDriver> wait = new FluentWait<WebDriver>(driver)
                .withTimeout(Duration.ofSeconds(TIME_OUT_SECONDS))
                .pollingEvery(Duration.ofSeconds(POLLING_SECONDS))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);

        WebElement element = wait.until(new Function<WebDriver, WebElement>() {

            public WebElement apply(WebDriver driver) {
                WebElement submit = null;
                submit = wait.until(ExpectedConditions.visibilityOf(driver.findElement(By.xpath(
                        selector))));

                return submit;
            }
        });
    }

    public static void waitForTableToLoad50Rows(WebDriver driver) {
        Wait<WebDriver> wait = new FluentWait<WebDriver>(driver)
                .withTimeout(Duration.ofSeconds(TIME_OUT_SECONDS))
                .pollingEvery(Duration.ofSeconds(POLLING_SECONDS))
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);

        WebElement element = wait.until(new Function<WebDriver, WebElement>() {

            public WebElement apply(WebDriver driver) {
                List<WebElement> submit = null;
                submit = wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.xpath("//tbody//tr"), 10));

                return submit.get(0);
            }
        });
    }

}
