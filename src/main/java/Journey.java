import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;

public class Journey {

    HashMap<String, String> urlAndSubmitButton;

    HashMap<String, String> urlAndRadioButtons;

    boolean JourneyComplete;

//  LinkedHashmap<url, Page>
    LinkedHashMap<String, Page> urlAndPages = new LinkedHashMap<>();


    public Page getPage(String url) {
        return urlAndPages.get(url);
    }

    public Journey forkJourney() {
        Journey journey = new Journey();

        Set<String> urls = journey.urlAndPages.keySet();
        for(String url : urls) {
            journey.urlAndPages.put(url, this.urlAndPages.get(url));
        }

        return journey;
    }

}
