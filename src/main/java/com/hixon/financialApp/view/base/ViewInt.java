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
import java.util.function.Supplier;

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
    public boolean SHOW_CANCEL_QUIT_SKIP = true;
    public boolean DO_NOT_SHOW_CANCEL_QUIT_SKIP = false;
     public String YES = "yes";


    /*
     * Helper methods for TransactionResolverCmdLine:
     */
    void say();

    void say(String s);

    /**
     * Displays a major section header (H1) with emphasis.
     * Format: blank line before, ALL CAPS text with equals signs above and below, blank line after.
     * The length of the decoration adapts to the text length.
     *
     * @param s the header text to display
     */
    void sayH1(String s);

    /**
     * Displays a sub-section header (H2) with moderate emphasis.
     * Format: blank line before, First Letter Capitalized with dashes below.
     * The length of the decoration adapts to the text length.
     *
     * @param s the header text to display
     */
    void sayH2(String s);

    /**
     * Displays a minor header (H3) with subtle emphasis.
     * Format: blank line before, text with a visual marker (▸).
     *
     * @param s the header text to display
     */
    void sayH3(String s);

    ImportController.TerminationCondition getTerminationCondition();

    /**
     * Ask the user a question.  Getting the answer is handled by a different method: (getYesOrNo(), getNumberBetween(),
     * etc.):
     *
     * @param question The question to ask the user.
     */
    void ask(String question);

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
    double getResponseCurrency(String prompt);

    /**
     * This method prompts the user to enter a double by outputting the prompt message.  Then it gets a double from the
     * user.  If the user enters a string that can be converted to a double, then the double is returned.  Otherwise,
     * the error message is displayed and the user is asked to re-enter the double.
     *
     * @param prompt The prompt to display to the user.
     * @return The double entered by the user.
     */
    double getResponseDouble(String prompt);

    /**
     * This method gets an integer from the user in the specified range.  The purpose of this routine is to get the
     * number of an item in a list of items, presumably a menu.
     *
     * @param prompt The prompt to give to the user before asking them to enter an integer in a range.
     * @param min    The smallest integer allowed, usually 1.
     * @param max    The greatest integer allowed, usually the number of items in a list displayed to the user.
     * @return The number entered by the user.
     */
    int getResponseIntBetween(String prompt, int min, int max);

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
    int getResponseIntBetween(String prompt, int min, int max, boolean isCancelAllowed, boolean isQuitAllowed,
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
     * @throws CancelException If the user cancels the operation
     * @throws QuitException If the user asks to quit the application
     * @throws SkipException If the user asks to skip this item.
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
     * This method gets a string from the user.
     *
     * @return The string entered by the user.
     */
    String getResponseString();

    /**
     * This method outputs a prompt and then gets a string from the user.  The user is also given options to cancel,
     * quit or skip the current operation.  If the user enters a string, then it is returned.  If the user cancels,
     * quits or skips the current operation, then the appropriate exception is thrown.
     *
     * @param prompt The prompt to display to the user.
     * @return The string entered by the user.
     */
    String getResponseString(String prompt);


    /**
     * This method gets a string from the user with configurable options for showing and allowing cancel, quit, and skip operations.
     *
     * @param prompt
     * @param isCancelAllowed If true, allows the user to cancel by entering 'C'
     * @param isQuitAllowed   If true, allows the user to quit by entering 'Q'
     * @param isSkipAllowed   If true, allows the user to skip by entering 'S'
     * @return The string entered by the user
     */
    String getResponseString(String prompt, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * Master method that handles all string input scenarios with prompt, default value, and full option support.
     * This is the comprehensive implementation that supports all features.
     *
     * @param prompt             The prompt to display to the user (can be empty)
     * @param defaultValue       The default value to show and return if user hits enter (can be null)
     * @param allowNone          If true, allows empty input (user just hits enter)
     * @param showCancelQuitSkip If true, displays the cancel/quit/skip options in the prompt
     * @param isCancelAllowed    If true, allows the user to cancel by entering 'C'
     * @param isQuitAllowed      If true, allows the user to quit by entering 'Q'
     * @param isSkipAllowed      If true, allows the user to skip by entering 'S'
     * @param helpCallback       Optional callback function to provide help text when user enters '?'
     * @return The string entered by the user
     * @throws CancelException If the user cancels the operation
     * @throws QuitException   If the user quits the operation
     * @throws SkipException   If the user skips the operation
     */
    String getResponseString(String prompt, String defaultValue, boolean allowNone, boolean showCancelQuitSkip,
                             boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed,
                             Supplier<String> helpCallback) throws CancelException, QuitException, SkipException;

    /**
     * This method outputs a prompt and then gets a string from the user. The user is also given options to cancel,
     * quit or skip the current operation. If the user enters a string, then it is returned. If the user cancels,
     * quits or skips the current operation, then the appropriate exception is thrown.
     *
     * @param prompt The prompt to display to the user.
     * @param allowNone If true, the user is allowed to enter no value (an empty string is accepted as valid input).
     *                  If false, the user must enter a non-empty string; otherwise, they will be re-prompted.
     * @param isCancelAllowed Is the user allowed to cancel the current operation?
     * @param isQuitAllowed Is the user allowed to quit the program at this point?
     * @param isSkipAllowed Is the user allowed to skip the current operation?
     * @return The string entered by the user (may be empty if allowNone is true).
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException If the user chooses to quit.
     * @throws SkipException If the user chooses to skip.
     */
    String getResponseStringMenuSelection(String prompt, boolean allowNone, boolean isCancelAllowed, boolean isQuitAllowed,
                                          boolean isSkipAllowed) throws CancelException, QuitException, SkipException;

    /**
     * This method outputs a prompt and then gets a string from the user, with an optional help callback.
     * The user is also given options to cancel, quit or skip the current operation. If the user enters a string,
     * then it is returned. If the user cancels, quits or skips the current operation, then the appropriate exception is thrown.
     *
     * @param prompt The prompt to display to the user.
     * @param allowNone If true, the user is allowed to enter no value (an empty string is accepted as valid input).
     *                  If false, the user must enter a non-empty string; otherwise, they will be re-prompted.
     * @param isCancelAllowed Is the user allowed to cancel the current operation?
     * @param isQuitAllowed Is the user allowed to quit the program at this point?
     * @param isSkipAllowed Is the user allowed to skip the current operation?
     * @param helpCallback Optional callback function to provide help text when user requests it.
     * @return The string entered by the user (may be empty if allowNone is true).
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException If the user quits the operation.
     * @throws SkipException If the user skips the operation.
     */
    String getResponseStringMenuSelection(String prompt, boolean allowNone, boolean isCancelAllowed, boolean isQuitAllowed,
                                          boolean isSkipAllowed, java.util.function.Supplier<String> helpCallback) throws CancelException, QuitException, SkipException;

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
    String[] getAndParseCsvLine(String prompt, int numberOfRequiredValues, boolean allowNullEntry, boolean allowSingleValue) throws CancelException, QuitException, SkipException;

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
    User getUser(String prompt, List<User> users, boolean allowNull) throws SQLException, EntityException;

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
            throws SQLException, EntityException, CancelException, QuitException, SkipException;

    /**
     * This method asks the user a yes or no question.  If the user enters 'y' or 'yes', then true is returned.  If the
     * user enters 'n' or 'no', then false is returned.  If the user enters an invalid response, then the user is
     * asked to re-enter their response.
     *
     * @param question The yes or no question to ask the user.
     * @return True if the user answers yes, false if the user answers no.
     */
    boolean getYesOrNo(String question);

    /**
     * This method asks the user a yes or no question.  If the user enters 'y' or 'yes', then true is returned.  If the
     * user enters 'n' or 'no', then false is returned.  If the user enters an invalid response, then the user is
     * asked to re-enter their response.
     *
     * @param question The yes or no question to ask the user.
     * @param isCancelAllowed Is the user allowed to cancel the current process?
     * @param isQuitAllowed Is the user allowed to quit the program?
     * @param isSkipAllowed Is the user allowed to skip this item and not enter an integer?
     * @return True if the user answers yes, false if the user answers no.
     * @throws CancelException The user cancelled the operation.
     * @throws QuitException The user wants to abort the program.
     * @throws SkipException The user wants to skip this item.
     */
    boolean getYesOrNo(String question, boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

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
     * Have the user select an entity from a numbered list of entities by number, with an optional default value.
     * The getName() method of the IndependentEntityInt is used to get the names of the entities as strings and
     * then selectFromNumberedList() is used to display the list to the user and get their selection.
     * If a default value is provided, it will be shown in square brackets and selected if the user presses enter.
     *
     * @param <T>                      The type of entity to select.
     * @param prompt                   The prompt to display to the user.
     * @param list                     A list of entities to select from.
     * @param defaultValue             The default entity to select (can be null).
     * @param allowNone
     * @param showCancelQuitSkipPrompt
     * @param isCancelAllowed
     * @param isQuitAllowed
     * @param isSkipAllowed
     * @param helpCallback
     * @return The selected item or null if none was selected.
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException If the user quits the operation.
     * @throws SkipException If the user skips the operation.
     * @throws EntityException If an entity-related error occurs.
     */
    <T extends IndependentEntityInt> T selectByNameFromList(
            String prompt,
            List<T> list,
            T defaultValue,
            boolean allowNone,
            boolean showCancelQuitSkipPrompt,
            boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed,
            Supplier<String> helpCallback)
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
            throws SQLException, EntityException;

    /**
     * Have the user select an entity from a numbered list of entities by number, or enter an arbitrary string.  The
     * string is presumably to indicate that none of the options are what they are looking for and provide instructions
     * on how to regenerate the list.
     *
     * @param prompt           The prompt to display to the user.
     * @param list             A list of entities to select from.
     * @param allowNone        Is the user allowed to select none of the items?
     * @param allowString
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
            boolean allowString,
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
     * @param defaultAmount The default amount to display to the user if the user presses enter.
     * @return The amount entered by the user.
     */
    String parseDollarAmount(String prompt, double defaultAmount);

    /**
     * Prompts the user for a dollar amount, allowing cancel, quit, and skip options.
     * @param prompt The prompt to display to the user.
     * @param defaultAmount The default amount to use if the user presses enter.
     * @param isCancelAllowed Whether cancel is allowed.
     * @param isQuitAllowed Whether quit is allowed.
     * @param isSkipAllowed Whether skip is allowed.
     * @return The entered or default dollar amount as a string.
     * @throws CancelException If the user cancels.
     * @throws QuitException If the user quits.
     * @throws SkipException If the user skips.
     */
    String parseDollarAmount(String prompt, double defaultAmount, boolean isCancelAllowed, boolean isQuitAllowed,
                             boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * Prompts the user to select one option from a menu of options.
     * This is targeted toward situations where there are a limited number of static (always, or nearly always the same) options.
     * Each view implementation decides how to present the options (e.g., first-letter menu, dropdown, buttons).
     * Returns the selected option as a string.
     *
     * @param prompt The prompt to display to the user.
     * @param options The list of option strings to display and select from.
     * @param allowNone If true, allows the user to select none (empty input or "none" option).
     * @param showCancelQuitSkip If true, displays the cancel/quit/skip options in the prompt.
     * @param isCancelAllowed If true, allows the user to cancel the operation.
     * @param isQuitAllowed If true, allows the user to quit.
     * @param isSkipAllowed If true, allows the user to skip.
     * @return The selected option string from the options list.
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException If the user quits.
     * @throws SkipException If the user skips.
     */
    String selectFromMenu(String prompt, List<String> options, boolean allowNone, boolean showCancelQuitSkip,
                       boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * Have the user select a string from a list of strings. This is targeted at selecting from a potentially long
     * list of items typically retrieved from a database or cache.
     * Each view implementation decides how to present the list (e.g., numbered list, searchable dropdown).
     *
     * @param prompt    The prompt to display to the user.
     * @param items     A list of strings to select from.
     * @param allowNone Is the user allowed to select none of the items?
     * @return The index of the selected item (0-based), or -1 if none was selected.
     */
    Integer selectByPositionFromList(String prompt, List<String> items, boolean allowNone);

    /**
     * Have the user select a string from a list of strings. This is targeted at selecting from a potentially long
     * list of items typically retrieved from a database or cache.
     * If allowed, the user may also specify cancel, skip or quit. If cancel, skip or quit is allowed, then the
     * CancelException, SkipException or QuitException may be thrown.
     *
     * @param prompt          The prompt to display to the user.
     * @param items           A list of strings to select from.
     * @param allowNone       Is the user allowed to select none of the items?
     * @param isCancelAllowed Is the user allowed to cancel the current process?
     * @param isQuitAllowed   Is the user allowed to quit the program?
     * @param isSkipAllowed   Is the user allowed to skip this item and not enter an integer?
     * @return The index of the selected item (0-based), or -1 if none was selected.
     * @throws CancelException
     * @throws QuitException
     * @throws SkipException
     */
    Integer selectByPositionFromList(String prompt, List<String> items, boolean allowNone, boolean isCancelAllowed,
                                     boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * Have the user select an enum value from a list. This is targeted at selecting from a potentially long
     * list of items typically retrieved from a database or cache.
     *
     * @param prompt The prompt to display.
     * @param defaultValue The default value to use if the user presses Enter.
     * @param enumType The enum class to display.
     * @param <T> The enum type.
     * @return The selected enum value, or defaultValue if Enter is pressed.
      */
    <T extends Enum<T>> T selectByPositionFromList(String prompt, T defaultValue, Class<T> enumType);

    /**
     * Have the user select an enum value from a list. This is targeted at selecting from a potentially long
     * list of items typically retrieved from a database or cache.
     * If allowed, the user may also specify cancel, skip or quit. If cancel, skip or quit is allowed, then the
     * CancelException, SkipException or QuitException may be thrown.
     *
     * @param <T>                The enum type.
     * @param prompt             The prompt to display.
     * @param defaultValue       The default value to use if the user presses Enter.
     * @param enumType           The enum class to display.
     * @param showCancelQuitSkip
     * @param isCancelAllowed    Is the user allowed to cancel the current process?
     * @param isQuitAllowed      Is the user allowed to quit the program?
     * @param isSkipAllowed      Is the user allowed to skip this item?
     * @return The selected enum value, or defaultValue if Enter is pressed.
     * @throws CancelException if the user cancels.
     * @throws QuitException   if the user quits.
     * @throws SkipException   if the user skips.
     */
    <T extends Enum<T>> T selectByPositionFromList(String prompt, T defaultValue, Class<T> enumType, boolean showCancelQuitSkip, boolean isCancelAllowed,
                                                   boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * Have the user select a string from a list, or enter an arbitrary string. This is targeted at selecting from
     * a potentially long list of items typically retrieved from a database or cache, with the ability to enter a
     * custom value if none of the options match.
     *
     * @param prompt    The prompt to display to the user.
     * @param items     A list of strings to select from.
     * @param allowNone Is the user allowed to select none of the items?
     * @return The selected item or null if none was selected, or a custom string entered by the user.
     */
    NumberOrStringResponse selectFromListOrString(
            String prompt,
            List<String> items,
            boolean allowNone);

    /**
     * Have the user select a string from a list, or enter an arbitrary string. This is targeted at selecting from
     * a potentially long list of items typically retrieved from a database or cache, with the ability to enter a
     * custom value if none of the options match.
     * If allowed, the user may also specify cancel, skip or quit. If cancel, skip or quit is allowed, then the
     * CancelException, SkipException or QuitException may be thrown.
     *
     * @param prompt          The prompt to display to the user.
     * @param items           A list of strings to select from.
     * @param allowNone       Is the user allowed to select none of the items?
     * @param allowCreate     Is the user allowed to create a new item by entering a string?
     * @param isCancelAllowed Is the user allowed to cancel the current process?
     * @param isQuitAllowed   Is the user allowed to quit the program?
     * @param isSkipAllowed   Is the user allowed to skip this item and not enter an integer?
     * @return The selected item or null if none was selected, or a custom string entered by the user.
     * @throws CancelException
     * @throws QuitException
     * @throws SkipException
     */
    NumberOrStringResponse selectFromListOrString(
            String prompt,
            List<String> items,
            boolean allowNone,
            boolean allowCreate, boolean isCancelAllowed,
            boolean isQuitAllowed,
            boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * Displays a list of items and a menu of options. The user can:
     * 1. Enter a number (1-N) to select an item from the list by position
     * 2. Enter a single letter (e.g., v,u,a,d,s) to choose a menu option
     * 3. Enter C/Q/S to cancel/quit/skip (if allowed)
     * 4. Enter a string (anything else) to provide new search/selection criteria
     *
     * This is the most flexible selection method, useful when you want to show a list of items
     * with actions/commands and allow the user to refine their search. For example, showing a list
     * of transactions with options to view, edit, delete, search again, or enter new search criteria.
     *
     * @param listPrompt      The prompt to display above the numbered list (null to skip showing list)
     * @param items           A list of strings representing the items to select from (can be null/empty)
     * @param menuPrompt      The prompt to display for the menu choices
     * @param menuOptions     A list of menu option descriptions (first letter becomes the shortcut)
     * @param allowString     If true, allows entering arbitrary strings (for new search criteria)
     * @param isCancelAllowed Is the user allowed to cancel?
     * @param isQuitAllowed   Is the user allowed to quit?
     * @param isSkipAllowed   Is the user allowed to skip?
     * @return NumberOrStringResponse containing either:
     *         - A number (0-based index if user selected from list)
     *         - A single-letter string (menu shortcut if user selected menu option)
     *         - A multi-character string (new search criteria if user entered text)
     * @throws CancelException If the user cancels
     * @throws QuitException   If the user quits
     * @throws SkipException   If the user skips
     */
    NumberOrStringResponse selectFromListByPositionOrMenuOrString(
            String listPrompt,
            List<String> items,
            String menuPrompt,
            List<String> menuOptions,
            boolean allowString,
            boolean isCancelAllowed,
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

    /**
     * Prompts the user with a text box style input for a currency amount, allowing for options to enter none, cancel, quit, or skip.
     * The prompt should be user-friendly and clearly indicate available options. If a default value is provided,
     * it is shown in square brackets and returned if the user just hits enter. Cancel/quit/skip options are only
     * shown if showCancelQuitSkip is true. The value must be a valid currency (max two decimal digits).
     *
     * @param prompt          The prompt to display to the user.
     * @param isCancelAllowed If true, the user may cancel the operation.
     * @param isQuitAllowed   If true, the user may quit the operation.
     * @param isSkipAllowed   If true, the user may skip the operation.
     * @return The double value entered by the user (or defaultValue if enter is pressed).
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException   If the user chooses to quit.
     * @throws SkipException   If the user chooses to skip.
     */
    Double getResponseCurrency(String prompt,
                               boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * Prompts the user with a text box style input for a currency amount, with an optional help callback for providing context-sensitive help.
     * The prompt should be user-friendly and clearly indicate available options. If a default value is provided,
     * it is shown in square brackets and returned if the user just hits enter. Cancel/quit/skip options are only
     * shown if showCancelQuitSkip is true. The value must be a valid currency (max two decimal digits).
     *
     * @param prompt             The prompt to display to the user.
     * @param defaultValue       The default value to show and return if the user just hits enter.
     * @param showCancelQuitSkip Whether to show cancel/quit/skip options.
     * @param allowNone          If true, the user may enter no value (empty string is accepted).
     * @param isCancelAllowed    If true, the user may cancel the operation.
     * @param isQuitAllowed      If true, the user may quit the operation.
     * @param isSkipAllowed      If true, the user may skip the operation.
     * @param helpCallback       Optional callback function to provide help text when user requests it.
     * @return The double value entered by the user (or defaultValue if enter is pressed).
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException   If the user chooses to quit.
     * @throws SkipException   If the user chooses to skip.
     */
    Double getResponseCurrency(String prompt, Double defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                               boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed, java.util.function.Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException;

    /**
     * Prompts the user with a text box style input for a double value, allowing for options to enter none, cancel, quit, or skip.
     * The prompt should be user-friendly and clearly indicate available options. If a default value is provided,
     * it is shown in square brackets and returned if the user just hits enter. Cancel/quit/skip options are only
     * shown if showCancelQuitSkip is true.
     *
     * @param prompt          The prompt to display to the user.
     * @param isCancelAllowed If true, the user may cancel the operation.
     * @param isQuitAllowed   If true, the user may quit the operation.
     * @param isSkipAllowed   If true, the user may skip the operation.
     * @return The double value entered by the user (or defaultValue if enter is pressed).
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException   If the user chooses to quit.
     * @throws SkipException   If the user chooses to skip.
     */
    Double getResponseDouble(String prompt,
                             boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * Prompts the user with a text box style input for a double value, with an optional help callback for providing context-sensitive help.
     * The prompt should be user-friendly and clearly indicate available options. If a default value is provided,
     * it is shown in square brackets and returned if the user just hits enter. Cancel/quit/skip options are only
     * shown if showCancelQuitSkip is true.
     *
     * @param prompt             The prompt to display to the user.
     * @param defaultValue       The default value to show and return if the user just hits enter.
     * @param showCancelQuitSkip Whether to show cancel/quit/skip options.
     * @param allowNone          If true, the user may enter no value (empty string is accepted).
     * @param isCancelAllowed    If true, the user may cancel the operation.
     * @param isQuitAllowed      If true, the user may quit the operation.
     * @param isSkipAllowed      If true, the user may skip the operation.
     * @param helpCallback       Optional callback function to provide help text when user requests it.
     * @return The double value entered by the user (or defaultValue if enter is pressed).
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException   If the user chooses to quit.
     * @throws SkipException   If the user chooses to skip.
     */
    Double getResponseDouble(String prompt, Double defaultValue, boolean showCancelQuitSkip, boolean allowNone,
                             boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed, java.util.function.Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException;

    /**
     * Prompts the user with a text box style input for an integer value, allowing for options to enter none, cancel, quit, or skip.
     * The prompt should be user-friendly and clearly indicate available options. If a default value is provided,
     * it is shown in square brackets and returned if the user just hits enter. Cancel/quit/skip options are only
     * shown if showCancelQuitSkip is true.
     *
     * @param prompt          The prompt to display to the user.
     * @param isCancelAllowed If true, the user may cancel the operation.
     * @param isQuitAllowed   If true, the user may quit the operation.
     * @param isSkipAllowed   If true, the user may skip the operation.
     * @return The integer value entered by the user (or defaultValue if enter is pressed).
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException   If the user chooses to quit.
     * @throws SkipException   If the user chooses to skip.
     */
    Integer getResponseInt(String prompt,
                           boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * Prompts the user with a text box style input for an integer value.
     * The prompt should be user-friendly and clearly indicate available options. If a default value is provided,
     * it is shown in square brackets and returned if the user just hits enter. Cancel/quit/skip options are only
     * shown if showCancelQuitSkip is true.
     *
     * @param prompt The prompt to display to the user.
     * @return The integer value entered by the user (or defaultValue if enter is pressed).
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException   If the user chooses to quit.
     * @throws SkipException   If the user chooses to skip.
     */
    Integer getResponseInt(String prompt)
            throws CancelException, QuitException, SkipException;

    /**
     * Prompts the user with a text box style input for an integer value, with an optional help callback for providing context-sensitive help.
     * The prompt should be user-friendly and clearly indicate available options. If a default value is provided,
     * it is shown in square brackets and returned if the user just hits enter. Cancel/quit/skip options are only
     * shown if showCancelQuitSkip is true.
     *
     * @param prompt             The prompt to display to the user.
     * @param defaultValue       The default value to show and return if the user just hits enter.
     * @param allowNone          If true, the user may enter no value (empty string is accepted).
     * @param showCancelQuitSkip Whether to show cancel/quit/skip options.
     * @param isCancelAllowed    If true, the user may cancel the operation.
     * @param isQuitAllowed      If true, the user may quit the operation.
     * @param isSkipAllowed      If true, the user may skip the operation.
     * @param helpCallback       Optional callback function to provide help text when user requests it.
     * @return The integer value entered by the user (or defaultValue if enter is pressed).
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException   If the user chooses to quit.
     * @throws SkipException   If the user chooses to skip.
     */
    Integer getResponseInt(String prompt, Integer defaultValue, boolean allowNone, boolean showCancelQuitSkip,
                           boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed,
                           Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException;

    /**
     * Prompts the user with a text box style input for a natural number (non-negative integer) value.
     * Repeatedly calls getResponseInt and rejects negative values until a valid natural number is provided.
     *
     * @param prompt The prompt to display to the user.
     * @return The natural number value entered by the user.
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException   If the user chooses to quit.
     * @throws SkipException   If the user chooses to skip.
     */
    Integer getResponseNatural(String prompt)
            throws CancelException, QuitException, SkipException;

    /**
     * Prompts the user with a text box style input for a natural number (non-negative integer) value.
     * Repeatedly calls getResponseInt and rejects negative values until a valid natural number is provided.
     *
     * @param prompt          The prompt to display to the user.
     * @param isCancelAllowed If true, the user may cancel the operation.
     * @param isQuitAllowed   If true, the user may quit the operation.
     * @param isSkipAllowed   If true, the user may skip the operation.
     * @return The natural number value entered by the user.
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException   If the user chooses to quit.
     * @throws SkipException   If the user chooses to skip.
     */
    Integer getResponseNatural(String prompt,
                               boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed)
            throws CancelException, QuitException, SkipException;

    /**
     * Prompts the user with a text box style input for a natural number (non-negative integer) value, with an optional help callback for providing context-sensitive help.
     * Repeatedly calls getResponseInt and rejects negative values until a valid natural number is provided.
     *
     * @param prompt             The prompt to display to the user.
     * @param defaultValue       The default value to show and return if the user just hits enter.
     * @param allowNone          If true, the user may enter no value (empty string is accepted).
     * @param showCancelQuitSkip Whether to show cancel/quit/skip options.
     * @param isCancelAllowed    If true, the user may cancel the operation.
     * @param isQuitAllowed      If true, the user may quit the operation.
     * @param isSkipAllowed      If true, the user may skip the operation.
     * @param helpCallback       Optional callback function to provide help text when user requests it.
     * @return The natural number value entered by the user (or defaultValue if enter is pressed).
     * @throws CancelException If the user cancels the operation.
     * @throws QuitException   If the user chooses to quit.
     * @throws SkipException   If the user chooses to skip.
     */
    Integer getResponseNatural(String prompt, Integer defaultValue, boolean allowNone, boolean showCancelQuitSkip,
                               boolean isCancelAllowed, boolean isQuitAllowed, boolean isSkipAllowed,
                               Supplier<String> helpCallback)
            throws CancelException, QuitException, SkipException;
}
