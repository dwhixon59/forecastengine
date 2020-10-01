package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.TransactionSplit;
import com.hixon.financialApp.view.ViewException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

import static com.hixon.financialApp.utility.Utility.getResolver;
import static com.hixon.financialApp.utility.Utility.setToLastBusinessDayBefore;

public abstract class AbstractBudgetView extends AbstractView implements BudgetViewInt
{

    private final Budget budget;

    public AbstractBudgetView(Budget budget) {
        this.budget = budget;
    }

    /*
    *  Helper methods:
    */
   public abstract void openSpendingReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException;
   public abstract void renderSpendingReportFrontMatter();
   protected abstract void renderTimePeriodRow(Calendar startDate, Calendar endDate);
   protected abstract void renderHeaderRow();
   public abstract void renderBudgetItem(BudgetItem budgetItem, Calendar startDate, Calendar endDate, double total)
           throws ForecastException;
   public abstract void renderTransactionSplit(TransactionSplit split) throws EntityException, SQLException, RegisterException;
   public abstract void renderTotalRow(double totalIncome, double totalBudgeted, double totalSpent);
   public abstract void renderSpendingReportBackMatter();
   public abstract void closeSpendingReportOutput();


   /*
    *  Main methods:
    */
   // Create and render a month-to-date spending report as an XML spreadsheet file that can be imported into a spreadsheet:
   @Override
   public void renderPlannedVsActualReport(Calendar startDateParm) throws FileNotFoundException, UnsupportedEncodingException,
           EntityException, SQLException, BudgetException, RegisterException, ForecastException, ViewException {

      // Insulate the parameter from side effects:
      Calendar startDate = (Calendar) startDateParm.clone();

      // Open the output and output the header:
      openSpendingReportOutput();
      renderSpendingReportFrontMatter();

      // Set the start and end dates to be the last business days of the previous month and the day before the last day
      // of the requested month since that is when I get paid:
      startDate.set(Calendar.DATE, 1);
      Calendar endDate = (Calendar) startDateParm.clone();
      endDate.add(Calendar.MONTH, 1);
      setToLastBusinessDayBefore(startDate);
      setToLastBusinessDayBefore(endDate);
      endDate.add(Calendar.DATE, -1);

      // Render the time period and header rows:
      renderTimePeriodRow(startDate, endDate);
      renderHeaderRow();

      // For each budget item in the budget:
      ResultSet rsbi = BudgetItem.getAllBudgetItems();
      BudgetItem budgetItem;
      double totalIncome = 0;
      double totalBudgeted = 0;
      double totalSpent = 0;
      while (rsbi.next()) {

         // Create a budget item from the database row:
         budgetItem = new BudgetItem(rsbi);

         // Get all the transaction splits for the current budget item:
         List<TransactionSplit> splits = TransactionSplit.getSplitsListForBudgetItemInPeriod(budgetItem, startDate, endDate);

         // If there are any splits for this budget item in the current month:
          if (splits.size() > 0) {

            // Total the splits:
            double subTotal = 0;
            for (TransactionSplit split : splits
            ) {
               subTotal += split.getAmount();
            }

            // Save off the amounts for the totals row:
            if (budgetItem.getAmount() < 0) {
               totalBudgeted -= budgetItem.getAmount();
            } else {
               totalIncome += budgetItem.getAmount();
            }
            if (subTotal < 0) totalSpent -= subTotal;

            // Render the budget item:
            renderBudgetItem(budgetItem, startDate, endDate, subTotal);

            // Render the splits for the budget item:
            for (TransactionSplit split : splits
            ) {
               renderTransactionSplit(split);
            }
         }
      }

      // Render the totals row:
      renderTotalRow(totalIncome, totalBudgeted, totalSpent);

      // Render any trailer matter:
      renderSpendingReportBackMatter();

      // Close the output file:
      closeSpendingReportOutput();
      getResolver().say("MTD Spending Report successfully rendered.");
   }
}
