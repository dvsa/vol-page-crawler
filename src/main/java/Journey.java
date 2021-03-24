import java.util.HashMap;

public class Journey {

    HashMap<String, String> urlAndSubmitButton;

    HashMap<String, String> urlAndRadioButtons;

    HashMap[] submitButtons;
    HashMap[] radioButtons;
    HashMap[] checkboxes;


    HashMap[] textInputs;
    HashMap[] dateInputs;
    HashMap[] dateTimeInputs;
    HashMap[] emailInputs;
    HashMap[] dropdowns;
    HashMap[] files;
//    String[] hiddens;
    HashMap[] month;
    HashMap[] number;
    HashMap[] passwords;
    HashMap[] radio;
//    String[] range;
//    String[] reset;
    HashMap[] search;
    HashMap[] tel;
    HashMap[] text;
    HashMap[] time;
    HashMap[] url;
    HashMap[] week;



//  HashMap<url, forkingInputType[choices[]]> (radio buttons, checkboxes, submit buttons)
    HashMap<String, String[][]> choices = new HashMap<>();
//    (Make choices and then fill in form?)



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


}
