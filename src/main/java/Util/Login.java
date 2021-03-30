package Util;

import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import com.typesafe.config.Config;
import org.openqa.selenium.By;

import java.net.MalformedURLException;

public class Login {

    public static Config config = new activesupport.config.Configuration("").getConfig();
    public static String internalUrl = "https://iuap1.qa.olcs.dev-dvsacloud.uk/";
    private static String internalUserName = config.getString("internalUserName");
    private static String internalPassword = config.getString("internalNewPassword");

    public static String selfServeUrl = "https://ssap1.qa.olcs.dev-dvsacloud.uk/";
    private static String selfServeUserName = config.getString("selfServeUserName");
    private static String selfServePassword = config.getString("internalNewPassword");


    public static void toInternal() throws MalformedURLException, IllegalBrowserException {
        Browser.navigate().get(internalUrl);
        Browser.navigate().findElement(By.name("username")).sendKeys(internalUserName);
        Browser.navigate().findElement(By.name("password")).sendKeys(internalPassword); // put into config or properties file.
        Browser.navigate().findElement(By.name("submit")).click();
    }

    public static void toSelfServe() throws MalformedURLException, IllegalBrowserException {
        Browser.navigate().get(selfServeUrl);
        Browser.navigate().findElement(By.name("username")).sendKeys(selfServeUserName);
        Browser.navigate().findElement(By.name("password")).sendKeys(selfServePassword); // put into config or properties file.
        Browser.navigate().findElement(By.name("submit")).click();
    }
}
