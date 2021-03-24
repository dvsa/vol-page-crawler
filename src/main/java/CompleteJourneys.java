import activesupport.IllegalBrowserException;
import activesupport.driver.Browser;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class CompleteJourneys {



    public void completeStandardJourney(String baseUrls) throws IOException, IllegalBrowserException {

        Browser.navigate().get(baseUrls);





    }
}


// Store links, need to concatenate these in arrays - new arrays for each choice afterwards. - need to also store submit buttons with this.
// go to link, if there is a submit button, fill form and submit values, then store new url. Need to repeat the journey through the arrays - use basic fill in form atm.


// Store links, store submit buttons, if the url equals a previous one in the list, complete journey.


// POSSIBLE SOLUTION DUPLICATE ARRAY BUT SET DIFFERENT ANSWERS IN EACH ARRAY FOR THE NEW FORK

// [URL, CHOICE 1, CHOICE 2, CHOICE 3] - create array for each of these possible choices.
// [URL, CHOICE 1 YES, CHOICE 2 YES, CHOICE 3 YES]
// [URL, CHOICE 1 YES, CHOICE 2 YES, CHOICE 3 NO]
// [URL, CHOICE 1 YES, CHOICE 2 NO, CHOICE 3 YES] etc.
// Then loop over all these arrays for the different journeys.
//TODO: ^^ IGNORE THIS FOR THE MOMENT ^^ focus on the basic journey first.
// USE HASHMAPS FOR URL AND ARRAY OF CHOICES!!!
// NEED CLASS FOR POSSIBLE ANSWERS TO INPUT FIELDS.

// Array takes an array stored in it, duplicates it and stores both arrays in a hashmap with the url as the title?... nope.


//TODO: Do journeys as whole thing. Can't revisit half done journey sometimes.
// Need to wrap up journeys as if they reach a previously seen page then it's looped back round.
// How to decide when something is a new journey

//TODO: Way to end journey is if it reaches a page already seen. Going to have to loop until page is previously stored page?
