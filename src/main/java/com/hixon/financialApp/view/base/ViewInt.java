package com.hixon.financialApp.view.base;

import com.hixon.financialApp.controller.CancelException;
import com.hixon.financialApp.controller.ImportController;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.controller.SkipException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.IndependentEntityInt;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.function.Function;

public interface ViewInt {

    public boolean ALLOW_NONE = true;
    public boolean DO_NOT_ALLOW_NONE = false;
    public boolean ALLOW_CREATE = true;
    public boolean DO_NOT_ALLOW_CREATE = false;
    public boolean ALLOW_CANCEL = true;
    public boolean DO_NOT_ALLOW_CANCEL = false;
    public boolean ALLOW_QUIT = true;
    public boolean DO_NOT_ALLOW_QUIT = false;
    public boolean ALLOW_SKIP = true;
    public boolean DO_NOT_ALLOW_SKIP = false;
    public String YES = "yes";


    /*
     * Helper methods for TransactionResolverCmdLine:
     */
    void say();

    void say(String s);

    ImportController.TerminationCondition getTerminationCondition();

    /**
     * Ask the user a question.  Getting the answer is handled by a different method: (getYesOrNo(), getNumberBetween(),
     * etc.):
     *
     * @param question The question to ask the user.
     */
    void ask(String question);

    boolean getYesOrNo(String question, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * Ask the user if they want to continue.  Usually this is asked after a recoverable error has occurred.
     *
     * @param prompt What happened?
     * @return true if the user wants to continue.  Otherwise false.
     */
    boolean askContinue(String prompt);

    /**
     * This method asks the user if they want to retry a failed operation, continue without retrying the operation, or
     * quit whatever procedure they are currently running (like the daily update).
     *
     * @return True if they want to retry the failed operation, false if they want to continue without retrying.
     * @throws QuitException    if the user wants to quit the current process.
     * @throws RuntimeException if any exception occurs.  The encountered exception is the cause.
     */
    boolean askRetryContinueQuit() throws QuitException, RuntimeException;

    /**
     * This method asks the user to enter a dollar amount.  If the user enters a valid dollar amount, then it is
     * returned.  Otherwise, the user is asked to re-enter the dollar amount.
     *
     * @return The dollar amount entered by the user.
     */
    double getDollarAmount();

    /**
     * This method prompts the user to enter a double by outputting the prompt message.  Then it gets a double from the
     * user.  If the user enters a string that can be converted to a double, then the double is returned.  Otherwise,
     * the error message is displayed and the user is asked to re-enter the double.
     *
     * @param prompt       The prompt to display to the user.
     * @param errorMessage The error message to display to the user if the double entered is invalid.
     * @return The double entered by the user.
     */
    double getDouble(String prompt, String errorMessage);

    /**
     * This method gets an integer from the user in the specified range.  The purpose of this routine is to get the
     * number of an item in a list of items, presumably a menu.
     *
     * @param prompt The prompt to give to the user before asking them to enter an integer in a range.
     * @param min    The smallest integer allowed, usually 1.
     * @param max    The greatest integer allowed, usually the number of items in a list displayed to the user.
     * @return The number entered by the user.
     */
    int getNumberBetween(String prompt, int min, int max) throws SkipException, QuitException, CancelException;

    /**
     * This method gets an integer from the user in the specified range.  If allowed, the user may also specify skip
     * or quit.  The purpose of this routine is to get the number of an item in a list of items, presumably a menu.  If
     * skip or quit is allowed, then the SkipException or QuitException may be thrown.
     *
     * @param prompt          The prompt to give to the user before asking them to enter an integer in a range.
     * @param min             The smallest integer allowed, usually 1.
     * @param max             The greatest integer allowed, usually the number of items in a list displayed to the user.
     * @param isCancelAllowed
     * @param isQuitAllowed   Is the user allowed to quit the process and terminate the program here.
     * @param isSkipAllowed   Is the user allowed to skip this item and not enter an iteger.
     * @return The number entered by the user.
     */
    int getNumberBetween(String prompt, int min, int max, boolean isCancelAllowed, boolean isQuitAllowed,
                         boolean isSkipAllowed)
            throws SkipException, QuitException, CancelException;

    /**
     * This method gets a number between min and max inclusive from the user, presumably the number of an item in a list
     * that the user wants to select, or an arbitrary string.  The string is presumably an instruction on how to
     * regenerate the list of items to select from.
     *
     * @param prompt The prompt to display to the user.
     * @param min    The smallest number allowed.
     * @param max    The largest number allowed.
     * @return The number entered by the user or the string entered by the user.
     */
    NumberOrStringResponse getNumberBetweenOrString(String prompt, int min, int max);

    /**
     * This method gets a number between min and max inclusive from the user, presumably the number of an item in a list
     * that the user wants to select, or an arbitrary string.  The string is presumably an instruction on how to
     * regenerate the list of items to select from.  It also allows the user to cancel, quit or skip the current
     * operation.  If the user cancels, quits or skips the current operation, then the appropriate exception is thrown.
     *
     * @param prompt          The prompt to display to the user.
     * @param min             The smallest number allowed.
     * @param max             The largest number allowed.
     * @param isCancelAllowed Is the user allowed to cancel the current operation?
     * @param isQuitAllowed   Is the user allowed to quit the program at this point?
     * @param isSkipAllowed   Is the user allowed to skip the current operation?
     * @return The number entered by the user or the string entered by the user.
     * @throws CancelException
     * @throws QuitException
     * @throws SkipException
     */
    NumberOrStringResponse getNumberBetweenOrString(
            String prompt,
            int min,
            int max,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * This method gets a string from the user.  If the user enters a string, then it is returned.  Otherwise a empty
     * string is returned.
     *
     * @return The string entered by the user (may be empty).
     */
    String getResponseString();

    /**
     * This method gets a string from the user.  The user is also given options to cancel, quit or skip the current
     * operation.  If the user enters a string, then it is returned.  If the user cancels, quits or skips the current
     * operation, then the appropriate exception is thrown.
     *
     * @param isCancelAllowed Is the user allowed to cancel the current operation?
     * @param isQuitAllowed   Is the user allowed to quit the program at this point?
     * @param isSkipAllowed   Is the user allowed to skip the current operation?
     * @return The string entered by the user.
     * @throws CancelException
     * @throws QuitException
     * @throws SkipException
     */
    String getResponseString(boolean allowNone, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * This method outputs a prompt and then gets a string from the user.  The user is also given options to cancel,
     * quit or skip the current operation.  If the user enters a string, then it is returned.  If the user cancels,
     * quits or skips the current operation, then the appropriate exception is thrown.
     *
     * @param prompt
     * @return The string entered by the user.
     */
    String getResponseString(String prompt)
            throws CancelException, QuitException, SkipException;

    /**
     * This method outputs a prompt and then gets a string from the user.  The user is also given options to cancel,
     * quit or skip the current operation.  If the user enters a string, then it is returned.  If the user cancels,
     * quits or skips the current operation, then the appropriate exception is thrown.
     *
     * @param prompt
     * @param isCancelAllowed Is the user allowed to cancel the current operation?
     * @param isQuitAllowed   Is the user allowed to quit the program at this point?
     * @param isSkipAllowed   Is the user allowed to skip the current operation?
     * @return The string entered by the user.
     * @throws CancelException
     * @throws QuitException
     * @throws SkipException
     */
    String getResponseString(String prompt, boolean allowNone, boolean isCancelAllowed, boolean isQuitAllowed,
                             boolean isSkipAllowed) throws CancelException, QuitException, SkipException;

    /**
     * This method gets a date from the user in MM-DD-YY format.  If the user enters a valid date, then it is returned.
     * Otherwise, the user is asked to re-enter the date.
     *
     * @return The date entered by the user.
     */
    Calendar getStartDate() throws QuitException;

    /**
     * This method is a call to the implementation of the method allowing it to perform any necessary initialization.
     *
     * @param transaction The transaction being imported.
     */
    void beginImportItem(Transaction transaction);

    /**
     * This method prints a prompt, gets a response from the user, parses the response based on commas and returns the
     * response in a string array.
     *
     * @param prompt                 The prompt to display to the user.
     * @param numberOfRequiredValues The number of values required in the response.
     * @param allowNullEntry         Is the user allowed to enter a blank line?
     * @param allowSingleValue       Is the user allowed to enter a single value?
     * @return The response from the user parsed into a string array.
     */
    String[] getAndParseCsvLine(String prompt, int numberOfRequiredValues, boolean allowNullEntry, boolean allowSingleValue);

    /**
     * This method returns a standard prompt offering the options to cancel, quit or skip the current operation.  It
     * is used by methods that query the user and want to provide standard escapes from the query if the user changes
     * their mind.
     *
     * @param isCancelAllowed Is the user allowed to cancel the current operation?
     * @param isQuitAllowed   Is the user allowed to quit the program at this point?
     * @param isSkipAllowed   Is the user allowed to skip the current operation?
     * @return The standard prompt offering the options to cancel, quit or skip the current operation.
     */
    String getCancelSkipOrQuitPrompt(boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed);

    /**
     * Have the user select a username from a list of usernames (taken from a list of users):
     *
     * @param prompt
     * @param users
     * @param allowNull
     * @return The selected user.
     * @throws SQLException
     * @throws EntityException
     */
    User getUser(String prompt, List<User> users, boolean allowNull);

    /**
     * Have the user select a username from a list of usernames (taken from a list of users):
     *
     * @param prompt
     * @param users
     * @param allowNull
     * @return The selected user.
     * @throws CancelException
     * @throws QuitException
     * @throws SkipException
     */
    User getUser(String prompt, List<User> users, boolean allowNull, boolean isCancelAllowed, boolean isQuitAllowed,
                 boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    boolean getYesOrNo(String question);

    /**
     * This method takes an integer that was provided by the user.  If the user entered string  can be converted to an
     * integer, then the integer is returned.  Otherwise, the error message is displayed and the user is asked to
     * re-enter the integer.
     *
     * @param intString    The string to convert to an integer.
     * @param errorMessage The error message to display to the user if the integer entered is invalid.
     * @return
     */
    int parseInt(String intString, String errorMessage);

    /**
     * Have the user select an entity from a numbered list of entities by number.  The getName() method of the
     * IndependentEntityInt is used to get the names of the entities as strings and then selectFromNumberedList()
     * is used to display the list to the user and get their selection.
     *
     * @param prompt    The prompt to display to the user.
     * @param list      A list of entities to select from.
     * @param allowNone
     * @param <T>       The type of entity to select.
     * @return The selected entity or null if none was selected.
     * @throws EntityException
     */
    <T extends IndependentEntityInt> T selectByNameFromList(String prompt, List<T> list, boolean allowNone)
            throws SQLException, EntityException;

    /**
     * Have the user select an entity from a numbered list of entities by number.  The getName() method of the
     * IndependentEntityInt is used to get the names of the entities as strings and then selectFromNumberedList()
     * is used to display the list to the user and get their selection.
     *
     * @param prompt    The prompt to display to the user.
     * @param list      A list of entities to select from.
     * @param allowNone
     * @param <T>       The type of entity to select.
     * @return The selected item or null if none was selected.
     * @throws CancelException
     * @throws QuitException
     * @throws SkipException
     * @throws EntityException
     */
    <T extends IndependentEntityInt> T selectByNameFromList(String prompt, List<T> list, boolean allowNone,
                                                            boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws SQLException, EntityException, CancelException, QuitException, SkipException;

    /**
     * Have the user select an entity from a numbered list of entities by number, or enter an arbitrary string.  The
     * string is presumably to indicate that none of the options are what they are looking for and provide instructions
     * on how to regenerate the list.
     *
     * @param prompt      The prompt to display to the user.
     * @param list        A list of entities to select from.
     * @param allowNone   Is the user allowed to select none of the items?
     * @param allowCreate
     * @return The selected item, or null if none was selected and none allowed, or a new search string.
     * @throws EntityException
     */
    <T extends IndependentEntityInt> EntityOrStringResult<T> selectByNameFromListOrString(
            String prompt,
            List<T> list,
            boolean allowNone, boolean allowCreate)
            throws EntityException;

    /**
     * Have the user select an entity from a numbered list of entities by number, or enter an arbitrary string.  The
     * string is presumably to indicate that none of the options are what they are looking for and provide instructions
     * on how to regenerate the list.
     *
     * @param prompt           The prompt to display to the user.
     * @param list             A list of entities to select from.
     * @param allowNone        Is the user allowed to select none of the items?
     * @param allowCreate
     * @param isCancelAllowed  Is the user allowed to cancel the current process?
     * @param isQuitAllowed    Is the user allowed to quit the program?
     * @param isSkipAllowed    Is the user allowed to skip this item and not enter an integer?
     * @param getDisplayString
     * @return The selected item, or null if none was selected and none allowed, or a new search string.
     * @throws EntityException
     * @throws CancelException
     * @throws QuitException
     * @throws SkipException
     */
    <T extends IndependentEntityInt> EntityOrStringResult<T> selectByNameFromListOrString(
            String prompt,
            List<T> list,
            Function<T, String> getDisplayString,
            boolean allowNone,
            boolean allowCreate,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed)
            throws EntityException, CancelException, QuitException, SkipException;

    /**
     * This method takes a string and returns a Calendar object.  If the string can be parsed into a date, then the
     * Calendar object is returned.  Otherwise, the error message is displayed and the user is asked to re-enter the
     * date.
     *
     * @param prompt      The prompt to display to the user.
     * @param defaultDate The default date to display to the user.
     * @return The date entered by the user.
     */
    Calendar parseCalendarDate(String prompt, Calendar defaultDate);

    /**
     * This method takes a string and returns a Calendar object.  If the string can be parsed into a date, then the
     * Calendar object is returned.  Otherwise, the error message is displayed and the user is asked to re-enter the
     * date.
     *
     * @param prompt      The prompt to display to the user.
     * @param defaultDate The default date to display to the user.
     * @return The date entered by the user.
     */
    public String parseStringDate(String prompt, Calendar defaultDate);

    /**
     * This method takes a string and returns a double.  If the string can be parsed into a double, then the double is
     * returned.  Otherwise, the error message is displayed and the user is asked to re-enter the double.
     *
     * @param prompt        The prompt to display to the user.
     * @param defaultAmount The default amount to display to the user.
     * @return The amount entered by the user.
     */
    String parseDollarAmount(String prompt, double defaultAmount);

    /**
     * Have the user select a string from a numbered list of strings by number.  If allowed, the user may also specify
     * cancel, skip or quit.  If cancel, skip or quit is allowed, then the CancelException, SkipException or
     * QuitException may be thrown.
     *
     * @param prompt    The prompt to display to the user.
     * @param items     A list of strings to select from.
     * @param allowNone Is the user allowed to select none of the items?
     * @return The selected item or null if none was selected.
     */
    int selectFromNumberedList(String prompt, List<String> items, Boolean allowNone);

    /**
     * Have the user select a string from a numbered list of strings by number.  If allowed, the user may also specify
     * cancel, skip or quit.  If cancel, skip or quit is allowed, then the CancelException, SkipException or
     * QuitException may be thrown.
     *
     * @param prompt          The prompt to display to the user.
     * @param items           A list of strings to select from.
     * @param allowNone       Is the user allowed to select none of the items?
     * @param isCancelAllowed Is the user allowed to cancel the current process?
     * @param isQuitAllowed   Is the user allowed to quit the program?
     * @param isSkipAllowed   Is the user allowed to skip this item and not enter an integer?
     * @return The selected item or null if none was selected.
     * @throws CancelException
     * @throws QuitException
     * @throws SkipException
     */
    int selectFromNumberedList(String prompt, List<String> items, Boolean allowNone, boolean isCancelAllowed,
                               boolean isQuitAllowed, boolean isSkipAllowed) throws CancelException, QuitException,
            SkipException;

    /**
     * This method takes a comma separated list of menu options and allows the user to select one of the options.
     *
     * @param menuOptionList A comma separated list of menu items.
     * @return The selected menu item.
     */
    String selectFromFirstLetterList(String prompt, String menuOptionList);

    /**
     * Have the user select a string from a numbered list of strings by number, or enter an arbitrary string, presumably
     * to indicate that none of the options are what they are looking for and provide instructions on how to regenerate
     * the list.
     *
     * @param prompt    The prompt to display to the user.
     * @param items     A list of strings to select from.
     * @param allowNone Is the user allowed to select none of the items?
     * @return The selected item or null if none was selected.
     */
    NumberOrStringResponse selectFromNumberedListOrString(
            String prompt,
            List<String> items,
            boolean allowNone);

    /**
     * Have the user select a string from a numbered list of strings by number, or enter an arbitrary string, presumably
     * to indicate that none of the options are what they are looking for and provide instructions on how to regenerate
     * the list.  If allowed, the user may also specify cancel, skip or quit.  If cancel, skip or quit is allowed, then
     * the CancelException, SkipException or QuitException may be thrown.
     *
     * @param prompt          The prompt to display to the user.
     * @param items           A list of strings to select from.
     * @param allowNone       Is the user allowed to select none of the items?
     * @param allowCreate
     * @param isCancelAllowed Is the user allowed to cancel the current process?
     * @param isQuitAllowed   Is the user allowed to quit the program?
     * @param isSkipAllowed   Is the user allowed to skip this item and not enter an integer?
     * @return The selected item or null if none was selected.
     * @throws CancelException
     * @throws QuitException
     * @throws SkipException
     */
    NumberOrStringResponse selectFromNumberedListOrString(
            String prompt,
            List<String> items,
            boolean allowNone,
            boolean allowCreate, boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * This method checks to see if a file exists, is not a directory and is not empty.  If any of the previous are true,
     * then this method asks the user if they want to fix the problem and retry.
     *
     * @param fileContent Description of the file.
     * @param filename    The filename, optionally prefaced by the path.
     * @return True if file exists, is not a directory and not empty.
     */
    boolean existsFileWithRetry(String fileContent, String filename) throws QuitException;

}
