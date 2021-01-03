package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.AbstractForecastReport;

import java.io.*;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;


/*
 * A report that shows the remaining amounts in each item in the current period of the specified forecast that is of
 * interest to a specified user, or all users:
 */
public class ItemsOfInterestReport extends AbstractForecastReport {

   private final List<Entity> items;
   private final User user;
   private final File reportFile;
   private PrintWriter pw;

   public ItemsOfInterestReport(Forecast forecast, User user, List<Entity> items, File file) {

      super(forecast);
      this.user = user;
      this.items = items;
      this.reportFile = file;
   }


   /*
    * Output the report:
    */
   @Override
   public void openReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException {
      boolean append = false;
      boolean autoFlush = true;
      String charset = "UTF-8";

      FileOutputStream fos = new FileOutputStream(reportFile, append);
      OutputStreamWriter osw = new OutputStreamWriter(fos, charset);
      BufferedWriter bw = new BufferedWriter(osw);
      pw = new PrintWriter(bw, autoFlush);
   }

   @Override
   public void renderReportFrontMatter() {
      pw.println("Items of Interest to " + user.getFirstName() + ":");
      pw.println("Item, Remaining, Budgeted/Spent");
      pw.println("------------------------------------");
   }

   @Override
   public void renderHeaderRow() {

   }

   @Override
   public List<Entity> getItems() {
      return items;
   }

   @Override
   public void renderItemRow(Entity item) throws EntityException, ForecastException, SQLException, BudgetException,
           RegisterException {
      ForecastTransaction forecastTransaction = (ForecastTransaction) item;
      BudgetItem budgetItem = forecastTransaction.getForecastItem().getBudgetItem();
      String remainingAmountString = Utility.formatRoundedDollarAmount(-forecastTransaction.getRemainingAmount());
      Calendar periodEndDate = budgetItem.getFirstDateOnOrAfter(Calendar.getInstance());
      periodEndDate.add(Calendar.DATE, -1);
      remainingAmountString += " (" + periodEndDate.get(Calendar.DATE) + ")";
      double amountSpentMTD = budgetItem.getAmountSpentMTD();
      String totalAmountForMonth = Utility.formatRoundedDollarAmount(-amountSpentMTD);
      double amountBudgetedForMonth = Math.abs(budgetItem.getBudgetedAmountForCurrentMonth());
      String amountBudgetedForMonthString = Utility.formatRoundedDollarAmount(amountBudgetedForMonth);
      pw.println(forecastTransaction.getForecastItem().getPayee() + "  " + remainingAmountString + ", " +
              amountBudgetedForMonthString + "/" + totalAmountForMonth);
   }

   @Override
   public void renderSummaryRow() {

   }

   @Override
   public void renderReportBackMatter() {

   }

   @Override
   public void closeReportOutput() {
      pw.close();
   }
}
