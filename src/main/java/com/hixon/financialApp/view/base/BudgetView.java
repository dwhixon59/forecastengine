package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.utility.Utility;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

public abstract class BudgetView extends View implements BudgetViewInt
{

   /*
    *  Helper methods:
    */
   public abstract void openSpendingReportOutput() throws FileNotFoundException, UnsupportedEncodingException;
   public abstract void renderSpendingReportFrontMatter();
   public abstract void renderBudgetItem(BudgetItem budgetItem, Calendar startDate, Calendar endDate, double total)
           throws ForecastException;
   public abstract void renderTransactionSplit(TransactionSplit split);
   public abstract void renderSpendingReportBackMatter();
   public abstract void closeSpendingReportOutput();


   /*
    *  Main methods:
    */
   // Create and render a month-to-date spending report as an XML spreadsheet file that can be imported into a spreadsheet:
   @Override
   public void renderPlannedVsActualReport(Calendar startDateParm) throws FileNotFoundException, UnsupportedEncodingException,
           EntityException, SQLException, BudgetException, RegisterException, ForecastException {

      // Insulate the parameter from side effects:
      Calendar startDate = (Calendar) startDateParm.clone();

      // Open the output and output the header:
      openSpendingReportOutput();
      renderSpendingReportFrontMatter();

      // Set the start and end dates to be the last business days of the previous month and the requested month since
      // that is when I get paid:
      startDate.set(Calendar.DATE, 1);
      Calendar endDate = (Calendar) startDateParm.clone();
      endDate.add(Calendar.MONTH, 1);
      Utility.setToLastBusinessDayBefore(startDate);
      Utility.setToLastBusinessDayBefore(endDate);
      endDate.add(Calendar.DATE, -1);

      // For each budget item in the budget:
      ResultSet rsbi = BudgetItem.getAllBudgetItems();
      BudgetItem budgetItem;
      String lastCategory = "";
      while (rsbi.next()) {

         // Create the budget item:
         budgetItem = new BudgetItem(rsbi);

         // Get all the transaction splits for the current budget item:
         List<TransactionSplit> splits = TransactionSplit.getSplitsListForBudgetItemInPeriod(budgetItem, startDate, endDate);

         // If there are any splits for this budget item in the current month:
          if (splits.size() > 0) {

            // Total the splits:
            double total = 0;
            for (TransactionSplit split : splits
            ) {
               total += split.getAmount();
            }

            // Render the budget item:
            renderBudgetItem(budgetItem, startDate, endDate, total);

            // Render the splits for the budget item:
            for (TransactionSplit split : splits
            ) {
               renderTransactionSplit(split);
            }
         }
      }

      // Render any trailer matter:
      renderSpendingReportBackMatter();

      // Close the output file:
      closeSpendingReportOutput();
      com.hixon.financialApp.utility.Utility.getResolver().say("MTD Spending Report successfully rendered.");
   }
}
