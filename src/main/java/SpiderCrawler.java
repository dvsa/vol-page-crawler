import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;


public class SpiderCrawler{
    private static final Logger LOGGER = LogManager.getLogger(SpiderCrawler.class);

    public static Document request(String url, ArrayList<String> visitedURL) {
        Document doc;
        try {
            if (isUrlValid(url)) {
                 doc = Jsoup.connect(url).get();
                int statusCode = doc.connection().response().statusCode();
                if (statusCode == 200)
                    LOGGER.info(doc.title());
                    LOGGER.info("Link: " + url);
                visitedURL.add(url);
                return doc;
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }


    public static void crawler(int level, String url, ArrayList<String> visited) {
        if (level <= 3) {
            Document doc = request(url, visited);
            if (doc != null) {
                for (Element link : doc.select("a[href]")) {
                    String formattedLink = link.absUrl("href");
                    if (!visited.contains(formattedLink))
                        crawler(level++, formattedLink, visited);
                }
            }
        }
    }

    private static boolean isUrlValid(String url) {
        try {
            URL obj = new URL(url);
            obj.toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }
}