import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;
import org.openqa.selenium.By;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class CompleteJourneys {


    public Set<String> completeStandardJourney(String baseUrl) throws IOException, IllegalBrowserException, InterruptedException {
        ScanPage scanPage = new ScanPage();
        ArrayList<Journey> allJourneys = new ArrayList<>();
        Set<String> allUrls = new HashSet<>();
        int journeyCounter = 1;

        Journey currentJourney = new Journey();

        boolean mappingCurrentJourney;

        Browser.navigate().get(baseUrl);

        journeyLooper:
        do {
            String currentUrl = Browser.navigate().getCurrentUrl();

            allUrls.add(currentUrl);

            AnswerBot.completeForm(); // TODO: WILL NEED TO ADD SPECIFIC CHOICES IN LATER.

            Set<String> scannedUrls = scanPage.scanForSubTreeLoopingPagination();
            Set<String> submitIds = scanPage.scanForSubmitButtonIds();

            mappingCurrentJourney = true;
            if (scannedUrls.size() > 0) {
                for (String url : scannedUrls) {
                    Page page = new Page();
                    page.link = url;

                    if (mappingCurrentJourney) {
                        currentJourney.pages.put(currentUrl, page);
                        mappingCurrentJourney = false;

                    } else {
                        Journey forkedJourney = currentJourney.forkJourney();
                        forkedJourney.pages.put(currentUrl, page);
                        allJourneys.add(forkedJourney);
                    }
                }
            }

            if (submitIds.size() > 0) {
                for (String submitId : submitIds) {
                    Page page = new Page();
                    page.submitSelector = submitId;

                    if (mappingCurrentJourney) {
                        currentJourney.pages.put(currentUrl, page);
                        mappingCurrentJourney = false;
                    } else {
                        Journey forkedJourney = currentJourney.forkJourney();
                        forkedJourney.pages.put(currentUrl, page);
                        allJourneys.add(forkedJourney);
                    }
                }
            }

            Page currentPageInJourney = currentJourney.pages.get(currentUrl);

            if (currentPageInJourney.link != null) {
                Browser.navigate().get(currentPageInJourney.link);
            } else {
                Browser.navigate().findElement(By.id(currentPageInJourney.submitSelector)).click();
            } // What happens if it's neither?

            for (String url : currentJourney.pages.keySet()) {
                if (currentUrl.equals(url)) {
                    if (allJourneys.size() == journeyCounter) {
                        break journeyLooper;
                    }
                    journeyCounter++;

                    //TODO: Need to enact way to repeat steps until the last page stored
                    currentJourney = allJourneys.get(journeyCounter);
                    for (String previousUrl : currentJourney.pages.keySet()) {
                        Page previousPage = currentJourney.getPage(previousUrl);

                        AnswerBot.completeForm();
//                      previousPage.rePerformActions();

                        if (previousPage.link != null) {
                            Browser.navigate().get(previousPage.link);
                        } else {
                            Browser.navigate().findElement(By.id(previousPage.submitSelector)).click();
                        }
                    }
                    //TODO: Add way of resetting url to the base url or the first url of that journey and repeat previous steps (cycle journey until journey is empty)
                }
            }

        } while (true); // refactor this to have allJourneys.size() == journeyCounter instead of having break.

        //choose between links and submits, keep going until journey is finished (returns to previously seen page on same journey)

        mappingCurrentJourney = true;

        return allUrls;
    }
}


//    TODO:  basic run_________________________________________________________________________________
//    Create initial Journey instance and Journeys array


//    loop over journeys in array
//    if there is a current stored submit, then fill in form as same as before (set choices for the moment) and click that instead.
//    else do underneath.

//    do {
//      for each page, store url in uniqueUrlList (same licences so ignore numbers?).
//      Fill in page, loop noOfSubmits {
//          duplicate journeys, append journeys onto array and append different submits on journeys with url as key.
//      }
//      Fill in page, loop noOflinks {
//          duplicate journeys, append journeys onto array and append different submits on journeys with url as key.
//      }
//    click submit in the current journey instance relating to the url stored
//    get current url and check if it has been stored in current journey at all? - mark journey as completed.
//    } while(journeyNotComplete)

// Loop over all journeys and if marked as broken (mark in try catch and continue next journey).


//    }



//    Get url
//    Get links
//    Fill in radio buttons set way, fill in checkboxes set way, fill in all other fields.
//    Store submit buttons.
//    Click the first submit button,






//    TODO: full permutations_________________________________________________________________________________

//    get url
//    get links (ignore fill address in yourself links)
//    fill in Y/checked on everything
//    click all links for fill in yourself
//    fill in text, dates, (might need to make search if it's available on a page)/ find all "fill in yourself"

//    do all combinations on a page while storing them and then loop over all stored journeys?


//    store radio buttons and checkboxes.
//    for numberOfRadioButtons.size() create instances - get possible choices and store them. loop within loop within loop
//    create instance duplicates but with all different combinations of radio buttons, checkboxes, and submit buttons.
//    add instances to global array of journeys.
//    fill in radio buttons
//    fill in checkboxes