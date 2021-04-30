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
        int journeyCounter = 0;

        Journey currentJourney = new Journey();
        allJourneys.add(currentJourney);

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
            if (scannedUrls != null) {
                for (String url : scannedUrls) {
                    Page page = new Page();
                    page.nextLink = url;

                    if (mappingCurrentJourney) {
                        currentJourney.addPage(currentUrl, page);
                        mappingCurrentJourney = false;

                    } else {
                        Journey forkedJourney = currentJourney.forkJourney();
                        forkedJourney.addPage(currentUrl, page);
                        allJourneys.add(forkedJourney);
                    }
                }
            }

            if (submitIds != null) {
                for (String submitId : submitIds) {
                    Page page = new Page();
                    page.submitSelector = submitId;

                    if (mappingCurrentJourney) {
                        currentJourney.addPage(currentUrl, page);
                        mappingCurrentJourney = false;
                    } else {
                        Journey forkedJourney = currentJourney.forkJourney();
                        forkedJourney.addPage(currentUrl, page);
                        allJourneys.add(forkedJourney);
                    }
                }
            }

            Page currentPageInJourney = currentJourney.getPage(currentUrl);
            if (currentPageInJourney.nextLink != null) {
                Browser.navigate().get(currentPageInJourney.nextLink);
            } else {
                Browser.navigate().findElement(By.id(currentPageInJourney.submitSelector)).click();
            } // TODO: What happens if it's neither?
            // TODO: Move onto next journey (need method still)

            for (String url : currentJourney.getAllPageUrls()) {
                if (Browser.navigate().getCurrentUrl().equals(url)) {

                    // Kick out if I have reached the end of all journeys in allJourneys. NEED WAY OF CHECKING IF END OF LIST THEN KICK OUT OF LOOP.
                    if (allJourneys.size() == journeyCounter) {
                        break journeyLooper;
                    } else {
                        journeyCounter++;
                    }

                    //TODO: Need to enact way to repeat steps until the last page stored
                    Journey nextJourney = allJourneys.get(journeyCounter);
                    for (String previousUrl : nextJourney.getAllPageUrls()) {
                        Page previousPage = nextJourney.getPage(previousUrl);

                        AnswerBot.completeForm();
//                      previousPage.rePerformActions();

                        if (previousPage.nextLink != null) {
                            Browser.navigate().get(previousPage.nextLink);
                        } else {
                            Browser.navigate().findElement(By.id(previousPage.submitSelector)).click();
                        } // Either will always exist because page has already been reached in previous Journey.
                    }
                    currentJourney = nextJourney;
                    //TODO: Add way of resetting url to the base url or the first url of that journey and repeat previous steps (cycle journey until journey is empty)
                }
                // (Else move onto the next page and continue the same journey.
            }

        } while (true); // refactor this to have allJourneys.size() == journeyCounter instead of having break.

        //choose between links and submits, keep going until journey is finished (returns to previously seen page on same journey)

        return allUrls;
    }
}

// TODO: Need clause for variations as the link redirects to same page. Or just issue licence on variation?


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