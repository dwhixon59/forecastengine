package com.hixon.financialApp.view.base;

import com.hixon.financialApp.controller.Importer;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.controller.SkipException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastTransactionSplit;
import com.hixon.financialApp.model.register.Merchant;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.view.ViewException;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;

public interface TransactionResolverInt {

   /*
    * Helper methods for TransactionResolverCmdLine:
    */
   void say();

   void say(String s);

   Importer.TerminationCondition getTerminationCondition();

   List<BudgetItemMerchant> assignBudgetItems(Merchant transaction)
           throws BudgetException, ParseException, SQLException, ViewException, EntityException, RegisterException;

   Merchant assignMerchant(String merchantPayeeString, String transactionPayeeString, double transactionAmount)
           throws ViewException, RegisterException, EntityException, QuitException, BudgetException;

   String resolveUnmatchedAccount(String payee, double amount) throws RegisterException, SkipException, QuitException;

   // Assign new budget items to an existing list of budget items:
   Importer.TerminationCondition assignMoreBudgetItems(Merchant merchant, List<BudgetItemMerchant> budgetItems)
           throws BudgetException, ViewException, EntityException, RegisterException;

   List<TransactionSplit> assignAmountsToBudgetItems(Transaction transaction, Merchant merchant,
                                                     List<BudgetItemMerchant> budgetItems)
           throws EntityException, RegisterException, SQLException, ViewException, BudgetException, ParseException;

   void ask(String s);

   boolean getYesOrNo(String question);

   /**
    * This method gets an integer from the user in the specified range.  The purpose of this routine is to get the
    * number of an item in a list of items, presumably a menu.
    *
    * @param prompt The prompt to give to the user before asking them to enter an integer in a range.
    * @param min The smallest integer allowed, usually 1.
    * @param max The greatest integer allowed, usually the number of items in a list displayed to the user.
    * @return The number entered by the user.
    */
   int getNumberBetween(String prompt, int min, int max) throws SkipException, QuitException;

   /**
    * This method gets an integer from the user in the specified range.  If allowed, the user may also specify skip
    * or quit.  The purpose of this routine is to get the number of an item in a list of items, presumably a menu.  If
    * skip or quit is allowed, then the SkipException or QuitException may be thrown.
    *
    * @param prompt The prompt to give to the user before asking them to enter an integer in a range.
    * @param min The smallest integer allowed, usually 1.
    * @param max The greatest integer allowed, usually the number of items in a list displayed to the user.
    * @param isSkipAllowed Is the user allowed to skip this item and not enter an iteger.
    * @param isQuitAllowed Is the user allowed to quit the process and terminate the program here.
    * @return The number entered by the user.
    */
   int getNumberBetween(String prompt, int min, int max, boolean isSkipAllowed, boolean isQuitAllowed) throws SkipException, QuitException;

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
   void getSplits(Transaction transaction, List<TransactionSplit> splits, Merchant merchant,
                  List<BudgetItemMerchant> budgetItemsForMerchant, Boolean skipAllowed, Boolean inquireAllowed)
           throws ViewException, EntityException, BudgetException, RegisterException;

   boolean askRegenerateForecast();

   UserResponse transactionAmountDiscrepancy(Transaction transaction, TransactionSplit split,
                                             ForecastTransaction forecastTransaction) throws BudgetException, SQLException, EntityException, ForecastException;

   // Print a prompt, get a response, parse it based on commas and return it in a string array:
   String[] getAndParseCsvLine(String prompt, int numberOfRequiredValues, boolean allowNullEntry, boolean allowSingleValue);

   // Show a list of the assigned budget items for a transaction, and the amount of the transaction:
   void showAssignedBudgetItems(List<BudgetItemMerchant> budgetItems, double amount);

   // What to do if the split amount exceeds the budgeted amount:
   ForecastTransactionSplit.SplitDisposition assignOverageAmount(String prompt) throws IOException;

   // What to do if we're not sure which forecast transaction to assign a split to because the amount differs:
   UserResponse assignSplitAmountToForecastTransaction(TransactionSplit split, ForecastTransaction forecastTransaction);

   // What to do if we're not sure which forecast transaction to assign a split to because the date differs:
   UserResponse assignSplitDateToForecastTransaction(TransactionSplit split, ForecastTransaction forecastTransaction) throws EntityException, SQLException;

   // Get the start date of the portion of the forecast to update:
   UserResponse getForecastStartDate() throws QuitException;

   // Get the start date for a spending report:
   Calendar getSpendingReportMonth() throws QuitException;

   // Ask the user if they want to delete a provisional transction in the register because it appears to have fallen off:
   boolean askDeleteRegisterTransaction(Transaction transaction);

   // Send a notification consisting of the notification string to the user.
   int selectFromNumberedList(String s, List<String> notificationMessage, Boolean allowNone) throws SQLException, EntityException, SkipException, QuitException;

   // Have the user select a username from a list of usernames (taken from a list of users):
   User getUser(String prompt, List<User> users, Boolean allowNull) throws SQLException, EntityException, SkipException, QuitException;

   // Ask the user to enter a dollar amount:
   double getDollarAmount();

   /**
    * This method takes a comma separated list of menu options and allows the user to select one of the options.
    *
    * @param menuOptionList A comma separated list of menu items.
    * @return The selected menu item.
    */
    String selectFromFirstLetterList(String prompt, String menuOptionList);
}

