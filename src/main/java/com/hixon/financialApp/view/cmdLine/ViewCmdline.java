package com.hixon.financialApp.view.cmdLine;

import com.hixon.financialApp.controller.CancelException;
import com.hixon.financialApp.controller.ImportController.TerminationCondition;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.controller.SkipException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.IndependentEntityInt;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.EntityOrStringResult;
import com.hixon.financialApp.view.base.NumberOrStringResponse;
import com.hixon.financialApp.view.base.ViewInt;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

import static com.hixon.financialApp.controller.ImportController.TerminationCondition.QUIT;

public class ViewCmdline implements ViewInt {

    /*
     * Fields for ViewCmdline:
     */
    private TerminationCondition terminationCondition;
    private final Scanner in;


    /*
     * Getters and setters for ViewCmdline:
     */
    public TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }


    /*
     * Constructors for ViewCmdline:
     */
    public ViewCmdline() {
        terminationCondition = QUIT;
        in = new Scanner(System.in);
    }


    /*
     * Helper methods for TransactionResolverCmdLine:
     */
    public void say() {
        System.out.println();
    }

    public void say(String s) {
        System.out.println(s);
    }

    public void ask(String s) {
        System.out.print(s);
    }

    public boolean getYesOrNo(String question) {

        // Call the full version of getYesOrNo() with the false values for the cancel, quit and skip parameters:
        try {
            return getYesOrNo(question, ViewInt.DO_NOT_ALLOW_CANCEL, ViewInt.DO_NOT_ALLOW_QUIT,
                    ViewInt.DO_NOT_ALLOW_SKIP);
        } catch (CancelException | QuitException | SkipException e) {
            // These exceptions since we specified that they should not be allowed:
            throw new RuntimeException("Logic error, received a cancel, skip or quit exception which should not happen.");
        }
    }

    public boolean getYesOrNo(String question, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {

        // Add the cancel, skip and quit prompt strings if they are allowed:
        String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
        ask(question + " ('y' or 'n'" + cancelSkipOrQuitPrompt + "): ");
        while (true) {
            String line = in.nextLine();
            if (line.equalsIgnoreCase("y")) return true;
            if (line.equalsIgnoreCase("n")) return false;

            // If the user entered a special value, then throw the appropriate exception:
            if (isCancelAllowed) {
                if (line.equalsIgnoreCase("c")) {
                    throw new CancelException("User asked to cancel this operation.");
                }
            }
            if (isQuitAllowed) {
                if (line.equalsIgnoreCase("q")) {
                    throw new QuitException("User asked to abort processing.");
                }
            }
            if (isSkipAllowed) {
                if (line.equalsIgnoreCase("s")) {
                    throw new SkipException("User asked to skip this item.");
                }
            }

            ask("\nPlease enter 'y' or 'n'" + cancelSkipOrQuitPrompt + ": ");
        }
    }

    public boolean askContinue(String prompt) {
        ask(prompt + "  Do you want to continue?  " + "(y/n): ");
        while (true) {
            String line = in.nextLine();
            if (line.equalsIgnoreCase("y")) return true;
            if (line.equalsIgnoreCase("n")) return false;
            ask("\nPlease enter 'y' or 'n': ");
        }
    }

    /**
     * @inheritDoc
     */
    public int getNumberBetween(String prompt, int min, int max) throws CancelException, SkipException, QuitException {
        return getNumberBetween(prompt, min, max, false, false, false);
    }

    /**
     * @inheritDoc
     */
    public int getNumberBetween(String prompt, int min, int max, boolean isCancelAllowed, boolean isQuitAllowed,
                                boolean isSkipAllowed) throws CancelException, QuitException, SkipException {
        int result;

        // Set up the skip and quit prompt strings if they are allowed.
        String canceSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);

        ask(prompt + " (" + min + " to " + max + canceSkipOrQuitPrompt + "): ");
        while (true) {
            try {
                String response = getResponseString("", false, isCancelAllowed, isQuitAllowed, isSkipAllowed);
                result = Integer.parseInt(response);
                if (result >= min && result <= max) {
                    break;
                }
                ask("Please enter a number from " + min + " to " + max + canceSkipOrQuitPrompt + ":");
            } catch (NumberFormatException numberFormatException) {
                say("Please enter a number from " + min + " to " + max + canceSkipOrQuitPrompt + ":");
            }
        }
        return result;
    }

    /**
     * @inheritDoc
     */
    @Override
    public NumberOrStringResponse getNumberBetweenOrString(String prompt, int min, int max) {
        try {
            return getNumberBetweenOrString(prompt, min, max, false, false, false);
        } catch (CancelException | QuitException | SkipException ignored) {
            return new NumberOrStringResponse(0);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public NumberOrStringResponse getNumberBetweenOrString(
            String prompt,
            int min,
            int max,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {

        // Set up the skip and quit prompt strings if they are allowed.
        String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
        String fullPrompt = prompt + " (" + min + " to " + max + ", a string" + cancelSkipOrQuitPrompt + "): ";
        while (true) {
            String response = "";
            try {
                ask(fullPrompt);
                response = getResponseString(false, isCancelAllowed, isQuitAllowed, isSkipAllowed);
                int result = Integer.parseInt(response);
                if (result >= min && result <= max) {
                    return new NumberOrStringResponse(result);
                } else {
                    say("The number you entered is not between " + min + " and " + max + ".");
                }
            } catch (NumberFormatException e) {
                return new NumberOrStringResponse(response);
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseString() {
        String response;
        response = getResponseString("");
        return response;
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseString(String prompt) {
        String response;
        try {
            response = getResponseString(prompt, true, false, false, false);
        } catch (CancelException | QuitException | SkipException ignored) {
            response = "";
        }
        return response;
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseString(boolean allowNone, boolean isCancelAllowed, boolean isQuitAllowed,
                                    boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {
        return getResponseString("", allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed);
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getResponseString(String prompt, boolean allowNone, boolean isCancelAllowed, boolean isQuitAllowed,
                                    boolean isSkipAllowed) throws CancelException, QuitException, SkipException {

        // If a prompt was provided:
        if (!prompt.isEmpty()) {

            // then add the cancel, skip, and quit prompt strings if they are allowed:
            String cancelSkipOrQuitPrompt = getCancelSkipOrQuitPrompt(isCancelAllowed, isQuitAllowed, isSkipAllowed);
            String fullPrompt = prompt + cancelSkipOrQuitPrompt + ":  ";

            // and ask the user to enter a response:
            ask(fullPrompt);
        }

        // Until the user enters a response, or nothing if allowed:
        String response = "";
        while (true) {

            // Get the user's response:
            response = in.nextLine();

            // Check if the response is empty:
            if (response.isEmpty()) {
                if (allowNone) {
                    return ""; // Return empty string if allowed
                } else {
                    ask("Please enter a value:  "); // Ask again if empty response is not allowed
                    continue;
                }
            }

            // If the user entered a special value, then throw the appropriate exception:
            if (isCancelAllowed && response.equalsIgnoreCase("c")) {
                throw new CancelException("User asked to cancel this operation.");
            }
            if (isQuitAllowed && response.equalsIgnoreCase("q")) {
                throw new QuitException("User asked to abort processing.");
            }
            if (isSkipAllowed && response.equalsIgnoreCase("s")) {
                throw new SkipException("User asked to skip this item.");
            }

            break; // Exit the loop if a valid response is entered
        }

        return response;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Calendar getStartDate() throws QuitException {
        Calendar startDate = null;
        boolean stop = false;
        while (!stop) {
            System.out.print("Enter the starting date (MM-DD-YY) of the register export: ");
            Scanner in = new Scanner(System.in);
            String line = in.nextLine();
            try {
                startDate = com.hixon.financialApp.utility.Utility.stringDateDashToCalendarDate(line);
                stop = true;

            } catch (ParseException e) {
                if (line.equalsIgnoreCase("quit")) {
                    throw new QuitException("User requested to quit.");
                } else {
                    System.out.println("Invalid date.  Please re-enter or type 'quit' to quit.");
                }
            }
        }
        return startDate;
    }

    /**
     * @inheritDoc
     */
    @NotNull
    public String getCancelSkipOrQuitPrompt(boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed) {
        String cancelPrompt = isCancelAllowed ? "c - cancel" : "";
        String quitPrompt = isQuitAllowed ? "q - quit" : "";
        String skipPrompt = isSkipAllowed ? "s - skip" : "";
        String canceSkipOrQuitPrompt = "";
        if (isCancelAllowed || isSkipAllowed || isQuitAllowed) {
            canceSkipOrQuitPrompt = ", or " +
                    cancelPrompt + (isSkipAllowed ? ", " : "") +
                    skipPrompt + (isQuitAllowed ? ", " : "") +
                    quitPrompt;
        }
        return canceSkipOrQuitPrompt;
    }

    /**
     * {@inheritDoc}
     */
    public double getDollarAmount() {
        return getDouble("Please enter the dollar amount:  ", "Invalid dollar amount,");
    }

    /**
     * {@inheritDoc}
     */
    public double getDouble(String prompt, String errorMessage) {
        if (!prompt.isEmpty()) {
            ask(prompt);
        }
        double doubleValue;
        while (true) {
            try {
                String doubleString = in.nextLine();
                doubleValue = Double.parseDouble(doubleString);
                return doubleValue;
            } catch (NumberFormatException nfe) {
                ask(errorMessage + " please re-enter:  ");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public int parseInt(String intString, String errorMessage) {
        int intValue = 0;
        while (true) {
            try {
                if (!intString.isEmpty()) intValue = Integer.parseInt(intString);
                return intValue;
            } catch (NumberFormatException nfe) {
                ask(errorMessage + " please re-enter:  ");
                intString = in.nextLine();
            }
        }
    }

    public String parseDollarAmount(String prompt, double defaultAmount) {
        say(prompt + ", or just press enter to accept the amount " +
                Utility.formatDollarAmount(Math.abs(defaultAmount)) + ":  ");
        String newAmount = in.nextLine();
        if (newAmount.isEmpty()) {
            newAmount = Double.toString(defaultAmount);
        } else {
            newAmount = String.valueOf(getDouble(newAmount, "Invalid amount,"));
        }
        return newAmount;
    }

    // Parse a date in mm/dd/yy format:
    public String parseStringDate(String prompt, Calendar defaultDate) {
        ask(prompt);
        if (defaultDate == null) {
            say(" (mm/dd/yy)");
        } else {
            say(" (mm/dd/yy) or just hit enter to accept the date " + Utility.calendarDateToStringDate(defaultDate));
        }
        String line = in.nextLine();
        boolean done = false;
        while (!done) {
            try {
                if (defaultDate != null && line.isEmpty()) {
                    line = Utility.calendarDateToStringDate(defaultDate);
                    done = true;
                } else {
                    if (Utility.stringDateDashToCalendarDate(line) != null) {
                        done = true;
                    } else {
                        say("Invalid date format.  Please re-enter:");
                        line = in.nextLine();
                    }
                }
            } catch (ParseException e) {
                say("Invalid date format.  Please re-enter:");
                line = in.nextLine();
            }
        }
        return line;
    }

    // Parse a date in mm/dd/yy format:
    public Calendar parseCalendarDate(String prompt, Calendar defaultDate) {
        ask(prompt);
        if (defaultDate == null) {
            say(" (MM-DD-YYYY)");
        } else {
            say(" (MM-DD-YYYY) or just hit enter to accept the date " + Utility.calendarDateToStringDate(defaultDate));
        }
        String line = in.nextLine();
        boolean done = false;
        Calendar date = null;
        while (!done) {
            try {
                if (defaultDate != null && line.isEmpty()) {
                    line = Utility.calendarDateToStringDate(defaultDate);
                }
                date = Utility.stringDateDashToCalendarDate(line);
                done = true;
            } catch (ParseException e) {
                say("Invalid date format.  Please re-enter:");
                line = in.nextLine();
            }
        }
        return date;
    }

    /**
     * This method checks if a file exists on the file with the name matching the passed in filename.  If the file is
     * not found, then it will ask the user if they want to try again allowing them to create, find, etc. the file.
     *
     * @param fileType A description of the file to be used when interacting with the user if not found.
     * @param fileName The name of the file to check for existence.
     * @return True if the file exists.  Otherwise, false.
     */
    public boolean existsFileWithRetry(String fileType, String fileName) throws QuitException {

        boolean found = false;
        boolean done = false;
        while (!done) {
            try {
                Path path = Paths.get(fileName);
                if (Files.exists(path) && !Files.isDirectory(path) && Files.size(path) > 0) {
                    done = true;
                    found = true;
                } else {
                    say("\n" + fileType + " file " + fileName + " does not exist or is empty.");
                    if (!getYesOrNo("Do you want to try again?")) {
                        done = true;
                    }
                }
            } catch (Exception e) {
                say("\n" + "Exception occurred trying to access " + fileType + " file " + fileName);
                say("\n" + "Exception was:  " + e);
                if (!askRetryContinueQuit()) {
                    found = false;
                }
            }
        }
        return found;
    }

    /**
     * {@inheritDoc}
     */
    public boolean askRetryContinueQuit() throws QuitException {

        // Until the user makes a valid selection:
        boolean choice = false;

        // Ask the use if they would like to retry the operation, continue without retrying, or quit:
        say();
        String prompt = "What would you like to do:  retry, continue without retrying, or quit?";
        String option = selectFromFirstLetterList(prompt, "r,c,q");

        // Invoke a function to execute the user's request:
        switch (option) {
            case "r":
                choice = true;
                break;

            case "c":
                choice = false;
                break;

            case "q":
                throw new QuitException("Operation aborted at user request.");
        }
        return choice;
    }


    /*
     * Main methods for TransactionResolverCmdLine:
     */

    /**
     * {@inheritDoc}
     */
    public void beginImportItem(Transaction transaction) {
        // Nothing to do for this type of command line resolver.
    }

    public User getUser(String prompt, List<User> users, boolean allowNull) {
        try {
            return getUser(prompt, users, allowNull, false, false, false);
        } catch (CancelException | QuitException | SkipException ignored) {
            return null;
        }
    }

    public User getUser(String prompt, List<User> users, boolean allowNull, boolean isCancelAllowed,
                        boolean isQuiteAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {

        // Get a list of all the users and create a list of user first names from it:
        List<String> userFirstNames = new ArrayList<>();
        for (User user : users
        ) {
            userFirstNames.add(user.getFirstName());
        }

        // Ask the user to select one of the users from the list of user first names:
        int index = selectFromNumberedList(prompt, userFirstNames, allowNull, isCancelAllowed,
                isQuiteAllowed, isSkipAllowed);

        // Return the user corresponding to the selected first name, or null if none was selected:
        return (index > -1) ? users.get(index) : null;
    }


    // Print a prompt, get a response, parse it based on commas and return it in a string array:
    public String[] getAndParseCsvLine(String prompt, int numberOfRequiredValues, boolean allowNullEntry, boolean
            allowSingleValue) {
        String[] tokens = null;
        boolean done = false;
        while (!done) {
            ask(prompt);
            String line = in.nextLine();
            // if the user just hit enter and that's allowed:
            if (line.isEmpty()) {
                if (allowNullEntry) {
                    // then return an empty array:
                    done = true;
                } else {
                    say("Please enter a value.");
                }
                continue;
            }
            tokens = line.split(",");

            // If we are dealing with special values, and the user entered a single value then were done:
            if (allowSingleValue && (tokens.length == 1)) {
                done = true;
                continue;
            }

            if (numberOfRequiredValues != 0 && (tokens.length < numberOfRequiredValues || tokens.length > numberOfRequiredValues)) {
                ask("Wrong number of values entered.  Please enter " + numberOfRequiredValues + " value(s).");
            } else {
                done = true;
            }
        }
        return tokens;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends IndependentEntityInt> T selectByNameFromList(
            String prompt,
            List<T> list,
            boolean allowNone)
            throws EntityException {

        // Call the full selectByNameFromNumberedList method specifying no cancel, quit or skip:
        try {
            return selectByNameFromList(prompt, list, allowNone, false, false, false);
        } catch (CancelException | SkipException | QuitException ignored) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends IndependentEntityInt> T selectByNameFromList(
            String prompt, List<T> list,
            boolean allowNone,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed)
            throws EntityException, CancelException, QuitException, SkipException {

        // A list to store the names
        List<String> names = new ArrayList<>();

        // Iterate over the list of objects and add the name of each object to the list of names:
        for (T entity : list) {
            // Execute the method String getName() for each object and add the name to the list
            names.add(entity.getName());
        }

        // Ask the user to select one of the names from the list:
        int index = selectFromNumberedList(prompt, names, allowNone, isCancelAllowed, isQuitAllowed, isSkipAllowed);

        // Return the object corresponding to the selected name, or null if none was selected:
        return index == -1 ? null : list.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends IndependentEntityInt> EntityOrStringResult<T> selectByNameFromListOrString(
            String prompt,
            List<T> list,
            boolean allowNone,
            boolean allowCreate)
            throws EntityException {

        try {
            return selectByNameFromListOrString(
                    prompt,
                    list,
                    t -> {
                        try {
                            return t.getName();
                        } catch (EntityException e) {
                            throw new RuntimeException("Error while getting the display string for an entity.", e);
                        }
                    },
                    allowNone,
                    allowCreate,
                    false,
                    false,
                    false);
        } catch (CancelException | SkipException | QuitException e) {
            throw new RuntimeException("Logic error, received a cancel, skip or quit exception which should not happen.", e);
        }
     }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends IndependentEntityInt> EntityOrStringResult<T> selectByNameFromListOrString(
            String prompt,
            List<T> list,
            Function<T, String> getDisplayString,
            boolean allowNone,
            boolean allowCreate,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed)
            throws EntityException, CancelException, QuitException, SkipException {


        // A list to store the names
        List<String> names = new ArrayList<>();

        // Iterate over the list of objects and add the name of each object to the list of names:
        for (T entity : list) {
            // Execute the method String getName() for each object and add the name to the list
            names.add(getDisplayString.apply(entity));
        }

        // Ask the user to select one of the names from the list:
        NumberOrStringResponse response = selectFromNumberedListOrString(prompt, names, allowNone, allowCreate,
                isCancelAllowed, isQuitAllowed, isSkipAllowed);

        // If the user selected a number, return the corresponding object.  Otherwise, return the search string:
        if (response.isNumber()) {
            if (response.getSelectedIndex() == -1) {
                return new EntityOrStringResult<T>();
            } else {
                return new EntityOrStringResult<T>(list.get(response.getSelectedIndex()));
            }
        } else {
            return new EntityOrStringResult<T>(response.getSearchString());
        }
    }

    /**
     * {@inheritDoc}
     */
    public String selectFromFirstLetterList(String prompt, String menuOptionList) {

        // Parse the menu options out of the menuOptionList:
        String[] options = menuOptionList.split(",");

        // Ask the user to enter one of the values:
        ask(prompt + "  ");

        // Until they enter a valid value:
        String selected = null;
        while (true) {

            // Get the user selection:
            String line = in.nextLine();

            // See if the value entered matches any of the options:
            for (String option : options
            ) {
                if (option.equalsIgnoreCase(line)) {
                    selected = option;
                    break;
                }
            }

            // If the user didn't select one of the options, ask them to do it again:
            if (selected == null) {
                ask("\nPlease enter one of the following letters:  " + menuOptionList + ":  ");
            } else {
                break;
            }
        }
        return selected;
    }

    /**
     * {@inheritDoc}
     */
    public int selectFromNumberedList(String prompt, List<String> items, Boolean allowNone) {

        try {
            return selectFromNumberedList(prompt, items, allowNone, ViewInt.DO_NOT_ALLOW_CANCEL,
                    ViewInt.DO_NOT_ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP);
        } catch (CancelException | QuitException | SkipException e) {

            // Ignore these exceptions and return the "none" value:
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    public int selectFromNumberedList(String prompt, List<String> items, Boolean allowNone, boolean isCancelAllowed,
                                      boolean isQuitAllowed, boolean isSkipAllowed) throws CancelException, QuitException, SkipException {

        // Ask which item in the list they want to select:
        say(prompt + ":  ");
        if (allowNone) say("\t0 - None");
        int i = 1;
        for (String item : items) {
            say("\t" + i++ + " - " + item);
        }
        return getNumberBetween("Enter the number corresponding to the item:", (allowNone) ? 0 : 1, i - 1,
                isCancelAllowed, isQuitAllowed, isSkipAllowed) - 1;
    }

    /**
     * {@inheritDoc}
     */
    public NumberOrStringResponse selectFromNumberedListOrString(
            String prompt,
            List<String> items,
            boolean allowNone) {
        try {
            return selectFromNumberedListOrString(prompt, items, allowNone, false, false, false, false);
        } catch (CancelException | SkipException | QuitException ignored) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public NumberOrStringResponse selectFromNumberedListOrString(
            String prompt,
            List<String> items,
            boolean allowNone,
            boolean allowCreate,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException {


        // Display the list of items to select from to the user:
        say(prompt + ":  ");
        if (allowNone) say("\t0 - None");
        int i = 1;
        for (String item : items) {
            if (allowCreate && i == items.size()) {
                say("\t" + i++ + " - Create a new item called " + item);
            } else {
                say("\t" + i++ + " - " + item);
            }
        }

        // Ask which item in the list they want to select:
        NumberOrStringResponse numberOrStringResponse = getNumberBetweenOrString("Enter the number corresponding " +
                        "to the item, or a new search string:", (allowNone) ? 0 : 1, i - 1, isCancelAllowed,
                isQuitAllowed, isSkipAllowed);

        // Subtract one from the selected index to get the index into the list of items:
        if (numberOrStringResponse.isNumber()) {
            numberOrStringResponse.setSelectedIndex(numberOrStringResponse.getSelectedIndex() - 1);
        }

        return numberOrStringResponse;
    }

} // End class TransactionResolverCmdLine.
