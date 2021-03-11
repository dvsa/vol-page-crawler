package Util;

import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.MalformedURLException;

public class SeleniumUtils {
    public static void waitForLoad(WebDriver driver) {
        ExpectedCondition<Boolean> pageLoadCondition = driver1 -> {
            assert driver1 != null;
            return ((JavascriptExecutor) driver1).executeScript("return document.readyState").equals("complete");
        };
        WebDriverWait wait = new WebDriverWait(driver, 60);
        wait.until(pageLoadCondition);
    }

    public static void clickUsingJavaScript(String attribute) throws MalformedURLException, IllegalBrowserException {
        WebElement element = Browser.navigate().findElement(By.xpath("//*[contains(text(),'" + attribute + "')]"));
        JavascriptExecutor executor = (JavascriptExecutor)Browser.navigate();
        executor.executeScript("arguments[0].click();", element);
    }
}