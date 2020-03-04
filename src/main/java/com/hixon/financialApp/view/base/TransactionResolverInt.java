package com.hixon.financialApp.view.base;

import com.hixon.financialApp.controller.Importer;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastTransactionSplit;
import com.hixon.financialApp.model.register.Merchant;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionSplit;
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

   public void say(String s);

   public Importer.TerminationCondition getTerminationCondition();

   public List<BudgetItemMerchant> assignBudgetItems(Merchant transaction)
           throws BudgetException, ParseException, SQLException, ViewException, EntityException, RegisterException;

   public Merchant assignMerchant(String merchantPayeeString, String transactionPayeeString) throws ViewException, RegisterException, EntityException;

   String resolveUnmatchedAccount(String payee) throws RegisterException;

   // Assign new budget items to an existing list of budget items:
   void assignMoreBudgetItems(Merchant merchant, List<BudgetItemMerchant> budgetItems)
           throws BudgetException, ViewException, EntityException, RegisterException;

   List<TransactionSplit> assignAmountsToBudgetItems(Transaction transaction, Merchant merchant,
                                                     List<BudgetItemMerchant> budgetItems)
           throws EntityException, RegisterException, SQLException, ViewException, BudgetException, ParseException;

   void ask(String s);

   boolean getYesOrNo(String question);

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

   void beginImportItem();

   /*
    * getSplits()
    *
    * Interact with the user to confirm or override the budget item amounts and then create splits for them.  Allow the
    * user to and add new budget items and create splits for them as well.
    */
   void getSplits(Transaction transaction, List<TransactionSplit> splits, Merchant merchant,
                  List<BudgetItemMerchant> budgetItemsForMerchant)
           throws ViewException, EntityException, BudgetException, RegisterException;

   boolean askRegenerateForecast();

   UserResponse transactionAmountDiscrepancy(Transaction transaction, TransactionSplit split,
                                             ForecastTransaction forecastTransaction);

   // Print a prompt, get a response, parse it based on commas and return it in a string array:
   String[] getAndParseCsvLine(String prompt, int numberOfRequiredValues, boolean allowNullEntry, String specialChar);

   // Show a list of the assigned budget items for a transaction, and the amount of the transaction:
   void showAssignedBudgetItems(List<BudgetItemMerchant> budgetItems, double amount);

   // What to do if the split amount exceeds the budgeted amount:
   ForecastTransactionSplit.SplitDisposition assignOverageAmount(double amount) throws IOException;

   // What to do if we're not sure which forecast transaction to assign a split to because the amount differs:
   UserResponse assignSplitAmountToForecastTransaction(TransactionSplit split, ForecastTransaction forecastTransaction);

   // What to do if we're not sure which forecast transaction to assign a split to because the date differs:
   UserResponse assignSplitDateToForecastTransaction(TransactionSplit split, ForecastTransaction forecastTransaction) throws EntityException, SQLException;

   // Get the start date of the portion of the forecast to update:
   UserResponse getForecastStartDate() throws QuitException;

   // Get the start date for a spending report:
   public Calendar getSpendingReportMonth() throws QuitException;
}

