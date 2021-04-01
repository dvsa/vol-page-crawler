import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;

public class Journey {

    HashMap<String, String> urlAndSubmitButton;

    HashMap<String, String> urlAndRadioButtons;

    boolean JourneyComplete;

//  LinkedHashmap<url, Page>
    LinkedHashMap<String, Page> pages = new LinkedHashMap<>();


    public Page getPage(String url) {
        return pages.get(url);
    }

    public Journey forkJourney() {
        Journey journey = new Journey();

        Set<String> urls = journey.pages.keySet();
        for(String url : urls) {
            journey.pages.put(url, this.pages.get(url));
        }

        return journey;
    }

}
