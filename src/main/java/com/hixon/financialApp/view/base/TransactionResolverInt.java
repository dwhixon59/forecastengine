package com.hixon.financialApp.view.base;

import com.hixon.financialApp.controller.Importer;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.controller.SkipException;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.IndependentEntityInt;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastTransactionSplit;
import com.hixon.financialApp.model.register.*;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.view.ViewException;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;

public interface TransactionResolverInt {

    public boolean ALLOW_NONE = true;
    public boolean DO_NOT_ALLOW_NONE = false;

    /*
     * Helper methods for TransactionResolverCmdLine:
     */
    void say();

    void say(String s);

    Importer.TerminationCondition getTerminationCondition();

    List<BudgetItemMerchant> assignBudgetItems(Budget budget, Merchant transaction)
            throws BudgetException, ParseException, SQLException, ViewException, EntityException, RegisterException;

    Merchant assignMerchant(String merchantPayeeString, String transactionPayeeString, double transactionAmount)
            throws ViewException, RegisterException, EntityException, QuitException, BudgetException;

    Register resolveUnmatchedAccount(Calendar date, double amount, String payee) throws RegisterException, SkipException,
            QuitException;

    // Assign new budget items to an existing list of budget items:
    Importer.TerminationCondition assignMoreBudgetItems(Budget budget, Merchant merchant, List<BudgetItemMerchant>
            budgetItems) throws BudgetException, ViewException, EntityException, RegisterException;

    BudgetItem getUserSelectedBudgetItem(List<BudgetItem> budgetItems) throws Exception;

    List<TransactionSplit> assignAmountsToBudgetItems(Transaction transaction, Merchant merchant,
                                                      Budget budget, List<BudgetItemMerchant> budgetItems)
            throws Exception;

    void ask(String s);

    boolean getYesOrNo(String question);

    /**
     * Ask the user if they want to continue.  Usually this is asked after a recoverable error has occurred.
     *
     * @param prompt What happened?
     * @return true if the user wants to continue.  Otherwise false.
     */
    boolean askContinue(String prompt);

    /**
     * This method gets an integer from the user in the specified range.  The purpose of this routine is to get the
     * number of an item in a list of items, presumably a menu.
     *
     * @param prompt The prompt to give to the user before asking them to enter an integer in a range.
     * @param min    The smallest integer allowed, usually 1.
     * @param max    The greatest integer allowed, usually the number of items in a list displayed to the user.
     * @return The number entered by the user.
     */
    int getNumberBetween(String prompt, int min, int max) throws SkipException, QuitException;

    /**
     * This method gets an integer from the user in the specified range.  If allowed, the user may also specify skip
     * or quit.  The purpose of this routine is to get the number of an item in a list of items, presumably a menu.  If
     * skip or quit is allowed, then the SkipException or QuitException may be thrown.
     *
     * @param prompt        The prompt to give to the user before asking them to enter an integer in a range.
     * @param min           The smallest integer allowed, usually 1.
     * @param max           The greatest integer allowed, usually the number of items in a list displayed to the user.
     * @param isSkipAllowed Is the user allowed to skip this item and not enter an iteger.
     * @param isQuitAllowed Is the user allowed to quit the process and terminate the program here.
     * @return The number entered by the user.
     */
    int getNumberBetween(String prompt, int min, int max, boolean isSkipAllowed, boolean isQuitAllowed)
            throws SkipException, QuitException;

    static Calendar getStartDate() throws QuitException {
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

    void beginImportItem(Transaction transaction);

    /*
     * getSplits()
     *
     * Interact with the user to confirm or override the budget item amounts and then create splits for them.  Allow the
     * user to and add new budget items and create splits for them as well.
     */
    void getSplits(Transaction transaction, List<TransactionSplit> splits, Merchant merchant, Budget budget,
                   List<BudgetItemMerchant> budgetItemsForMerchant, Boolean skipAllowed, Boolean inquireAllowed)
            throws Exception;

    boolean askRegenerateForecast();

    UserResponse transactionAmountDiscrepancy(Transaction transaction, TransactionSplit split,
                                              ForecastTransaction forecastTransaction) throws BudgetException, SQLException, EntityException, ForecastException;

    // Print a prompt, get a response, parse it based on commas and return it in a string array:
    String[] getAndParseCsvLine(String prompt, int numberOfRequiredValues, boolean allowNullEntry, boolean allowSingleValue);

    // Generate a list of displayable budget item strings for a list of budget items:
    List<String> generateDisplayableBudgetItemList(List<BudgetItem> budgetItems) throws Exception;

    // Show a list of the assigned budget items for a transaction, and the amount of the transaction:
    void showBudgetItemsForMerchant(List<BudgetItemMerchant> budgetItems, double amount) throws Exception;

    // What to do if the split amount exceeds the budgeted amount:
    ForecastTransactionSplit.SplitDisposition assignOverageAmount(String prompt) throws IOException;

    // What to do if we're not sure which forecast transaction to assign a split to because the amount differs:
    UserResponse assignSplitAmountToForecastTransaction(TransactionSplit split, ForecastTransaction forecastTransaction);

    // What to do if we're not sure which forecast transaction to assign a split to because the date differs:
    UserResponse assignSplitDateToForecastTransaction(TransactionSplit split, ForecastTransaction forecastTransaction)
            throws EntityException, SQLException;

    // Get the start date of the portion of the forecast to update:
    UserResponse getForecastStartDate() throws QuitException;

    // Get the start date for a spending report:
    Calendar getSpendingReportMonth() throws QuitException;

    // Ask the user if they want to delete a provisional transction in the register because it appears to have fallen off:
    boolean askDeleteRegisterTransaction(Transaction transaction);

    // Have the user select a string from a numbered list of string by number:
    int selectFromNumberedList(String prompt, List<String> notificationMessage, Boolean allowNone) throws SQLException,
            EntityException, SkipException, QuitException;

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
     * @throws SQLException
     * @throws EntityException
     * @throws SkipException
     * @throws QuitException
     */
    <T extends IndependentEntityInt> T selectByNameFromNumberedList(String prompt, List<T> list, Boolean allowNone)
            throws SQLException, EntityException, SkipException, QuitException;

    // Have the user select a username from a list of usernames (taken from a list of users):
    User getUser(String prompt, List<User> users, Boolean allowNull) throws SQLException, EntityException, SkipException,
            QuitException;

    // Ask the user to enter a dollar amount:
    double getDollarAmount();

    /**
     * This method takes a comma separated list of menu options and allows the user to select one of the options.
     *
     * @param menuOptionList A comma separated list of menu items.
     * @return The selected menu item.
     */
    String selectFromFirstLetterList(String prompt, String menuOptionList);

    /**
     * This method gets a new budget item from the user.
     *
     * @return A budget item.
     */
    BudgetItem getBudgetItemFromUser() throws BudgetException, SQLException, EntityException, ParseException;

    /**
     * This method checks to see if a file exists, is not a directory and is not empty.  If any of the previous are true,
     * then this method asks the user if they want to fix the problem and retry.
     *
     * @param fileContent Description of the content of the file.
     * @param filename    The filename, optionally prefaced by the path.
     * @return True if file exists, is not a directory and not empty.
     */
    boolean existsFileWithRetry(String fileContent, String filename) throws QuitException;

    /**
     * This method asks the user if they want to retry a failed operation, continue without retrying the operation, or
     * quit whatever procedure they are currently running (like the daily update).
     *
     * @return True if they want to retry the failed operation, false if they want to continue without retrying.
     * @throws QuitException    if the user wants to quit the current process.
     * @throws RuntimeException if any exception occurs.  The encountered exception is the cause.
     */
    boolean askRetryContinueQuit() throws QuitException, RuntimeException;
}
