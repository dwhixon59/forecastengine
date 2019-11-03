package com.hixon.financial.view.excel;

import com.hixon.financial.Utility;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItem;
import com.hixon.financial.model.forecast.ForecastTransaction;
import com.hixon.financial.model.forecast.ForecastTransactionIterator;
import com.hixon.financial.model.forecast.LongTermForecast;
import com.hixon.financial.model.forecast.forecastTransactionMemoryIterator;
import com.hixon.financial.model.register.RegisterException;
import com.hixon.financial.model.register.TransactionSplit;
import com.hixon.financial.view.ForecastView;
import com.hixon.financial.view.register.TransactionResolver;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class excelView implements ForecastView {

   private LongTermForecast shortTermForecast = null;
   private LongTermForecast longTermForecast = null;

   public excelView() {
   }

   @Override
   public void setShortTermForecast(LongTermForecast forecastToRender) {
      longTermForecast = forecastToRender;
   }

   @Override
   public boolean renderShortTermForecast(String filename, String encoding) throws Exception, EntityException, BudgetException {

      // To clue the user into what things to look for in the spreadsheet, run the forecast summarization routine
      // requesting below minimum balance events:
      LongTermForecast.SignificantEvents[] events = {LongTermForecast.SignificantEvents.daysBelowMinimumBalance};
      longTermForecast.summarize(events);

      // Print out the starting and ending balances:
      System.out.println("The starting balance is: " + Utility.formatDollarAmount(longTermForecast.getStartingBalance()));
      System.out.println("The ending balance is:   " + Utility.formatDollarAmount(longTermForecast.getEndingBalance()));
      System.out.println("The savings rate is:   " + Utility.formatDollarAmount(longTermForecast.getEndingBalance() /
              longTermForecast.getNumberOfMonths()) + " per month.");

      // and print out the significant events list:
      ForecastTransaction forecastTransaction = longTermForecast.getFirstSignificantEvent();
      while (forecastTransaction != null) {
         System.out.println("The balance on " + Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) +
                 " is $" + forecastTransaction.getRunningBalance());
         if (forecastTransaction.getRunningBalance() < longTermForecast.getMinimumBalance()) {
            System.out.println("Balance below minimum balance!");
         }
         forecastTransaction = forecastTransaction.getNextSignificantEvent();
      }

      // Create the tab delimited file with the forecast data to import into Excel:
      PrintWriter writer = new PrintWriter(filename, encoding);
      ForecastTransactionIterator forecastTransactions = new forecastTransactionMemoryIterator();
      forecastTransactions.setForecast(longTermForecast);
      forecastTransaction = forecastTransactions.getNext();
      int currentMonth = 0;
      while (forecastTransaction != null) {
         int amount;
         if (forecastTransaction.getRemainingAmount() == 0) {
            amount = Utility.doubleToInt(forecastTransaction.getForecastItem().getAmount());
         } else {
            amount = Utility.doubleToInt(forecastTransaction.getRemainingAmount());
         }
         int credit;
         int debit;
         if (amount > 0) {
            credit = amount;
            debit = 0;
         } else {
            credit = 0;
            debit = -amount;
         }
         // The month changed, so write out a header line with the name of the month:
         if (forecastTransaction.getPlannedDate().get(Calendar.MONTH) != currentMonth) {
            writer.println("\n" + forecastTransaction.getPlannedDate().getDisplayName(Calendar.MONTH, Calendar.LONG,
                    Locale.US));
            currentMonth = forecastTransaction.getPlannedDate().get(Calendar.MONTH);
         }

         // Write out the forecast line:
         writer.println(
                 Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) + "\t" +
                         forecastTransaction.getForecastItem().getPayee() + "\t" +
                         credit + "\t" +
                         debit + "\t" +
                         Utility.doubleToInt(forecastTransaction.getRunningBalance()) + "\t" +
                         forecastTransaction.getForecastItem().getCategory() + "\t" +
                         "\t" +
                         forecastTransaction.getId().toString()
         );
         forecastTransaction = forecastTransactions.getNext();
      }
      writer.close();
      return true;
   }

   @Override
   public void setLongTermForecast(LongTermForecast forecastToRender) {
      longTermForecast = forecastToRender;
   }

   @Override
   public boolean renderLongTermForecast(String filename, String encoding) throws Exception, EntityException, BudgetException {

      // To clue the user into what things to look for in the spreadsheet, run the forecast summarization routine
      // requesting below minimum balance events:
      LongTermForecast.SignificantEvents[] events = {LongTermForecast.SignificantEvents.daysBelowMinimumBalance};
      longTermForecast.summarize(events);

      // Print out the starting and ending balances:
      System.out.println("The starting balance is: " + Utility.formatDollarAmount(longTermForecast.getStartingBalance()));
      System.out.println("The ending balance is:   " + Utility.formatDollarAmount(longTermForecast.getEndingBalance()));
      System.out.println("The savings rate is:   " + Utility.formatDollarAmount(longTermForecast.getEndingBalance() /
              longTermForecast.getNumberOfMonths()) + " per month.");

      // and print out the significant events list:
      ForecastTransaction forecastTransaction = longTermForecast.getFirstSignificantEvent();
      while (forecastTransaction != null) {
         System.out.println("The balance on " + Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) +
                 " is $" + forecastTransaction.getRunningBalance());
         if (forecastTransaction.getRunningBalance() < longTermForecast.getMinimumBalance()) {
            System.out.println("Balance below minimum balance!");
         }
         forecastTransaction = forecastTransaction.getNextSignificantEvent();
      }

      // Create the tab delimited file with the forecast data to import into Excel:
      PrintWriter writer = new PrintWriter(filename, encoding);
      ForecastTransactionIterator forecastTransactions = new forecastTransactionMemoryIterator();
      forecastTransactions.setForecast(longTermForecast);
      forecastTransaction = forecastTransactions.getNext();
      int currentMonth = 0;
      while (forecastTransaction != null) {
         int amount;
         if (forecastTransaction.getRemainingAmount() == 0) {
            amount = Utility.doubleToInt(forecastTransaction.getForecastItem().getAmount());
         } else {
            amount = Utility.doubleToInt(forecastTransaction.getRemainingAmount());
         }
         int credit;
         int debit;
         if (amount > 0) {
            credit = amount;
            debit = 0;
         } else {
            credit = 0;
            debit = -amount;
         }
         // The month changed, so write out a header line with the name of the month:
         if (forecastTransaction.getPlannedDate().get(Calendar.MONTH) != currentMonth) {
            writer.println("\n" + forecastTransaction.getPlannedDate().getDisplayName(Calendar.MONTH, Calendar.LONG,
                    Locale.US));
            currentMonth = forecastTransaction.getPlannedDate().get(Calendar.MONTH);
         }

         // Write out the forecast line:
         writer.println(
                 Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) + "\t" +
                         forecastTransaction.getForecastItem().getPayee() + "\t" +
                         credit + "\t" +
                         debit + "\t" +
                         Utility.doubleToInt(forecastTransaction.getRunningBalance()) + "\t" +
                         forecastTransaction.getForecastItem().getCategory() + "\t" +
                         "\t" +
                         forecastTransaction.getId().toString()
         );
         forecastTransaction = forecastTransactions.getNext();
      }
      writer.close();
      return true;
   }

   @Override
   // Create a month-to-date spending report as a tab-delimited file that can be imported into Excel:
   public void renderSpendingReportMTD(TransactionResolver resolver) throws FileNotFoundException,
           UnsupportedEncodingException, EntityException, SQLException, BudgetException, RegisterException {

      // Open the output file:
      String filename = "C:\\Users\\dwhix\\Downloads\\MTD Spending Report.xml";
      PrintWriter writer = new PrintWriter(filename, "UTF-8");

      // Write the header information to the output file:
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      writer.println("<?mso-application progid=\"Excel.Sheet\"?>");
      writer.println("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"");
      writer.println("xmlns:o=\"urn:schemas-microsoft-com:office:office\"");
      writer.println("xmlns:x=\"urn:schemas-microsoft-com:office:excel\"");
      writer.println("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"");
      writer.println("xmlns:html=\"http://www.w3.org/TR/REC-html40\">");
      writer.println("<Worksheet ss:Name=\"Table1\">");
      writer.println("<Table>");
      writer.println("<Column ss:Index=\"1\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");
      writer.println("<Column ss:Index=\"2\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");
      writer.println("<Column ss:Index=\"3\" ss:AutoFitWidth=\"0\" ss:Width=\"110\"/>");
      writer.println("<Row>");
      writer.println("<Cell><Data ss:Type=\"String\">Category</Data></Cell>");
      writer.println("<Cell><Data ss:Type=\"String\">Payee</Data></Cell>");
      writer.println("<Cell><Data ss:Type=\"String\">Budgeted Amount</Data></Cell>");
      writer.println("<Cell><Data ss:Type=\"String\">Actual Amount</Data></Cell>");
      writer.println("</Row>");

      // For each budget item in the budget:
      ResultSet rsbi = BudgetItem.getAllBudgetItems();
      BudgetItem budgetItem;
      String lastCategory = "";
      while (rsbi.next()) {

         // Create the budget item:
         budgetItem = new BudgetItem(rsbi);

         // Get all the transaction splits for the current budget item:
         List<TransactionSplit> splits = TransactionSplit.getSplitsListForBudgetItemMTD(budgetItem);

         // If there are any splits for this budget item in the current month:
         if (splits.size() > 0) {

            // Total the splits:
            double total = 0;
            for (TransactionSplit split : splits
            ) {
               total += split.getAmount();
            }

            // Output the budget item:
            writer.println("<Row>");
            String category = rsbi.getString("category");
            if (category.equals(lastCategory)) {
               category = " ";
            } else {
               lastCategory = category;
            }
            writer.println("<Cell><Data ss:Type=\"String\">" + category + "</Data></Cell>");
            writer.println("<Cell><Data ss:Type=\"String\">" + rsbi.getString("payee") + "</Data></Cell>");
            writer.println("<Cell><Data ss:Type=\"Number\">" + rsbi.getDouble("amount") + "</Data></Cell>");
            writer.println("<Cell><Data ss:Type=\"Number\">" + total + "</Data></Cell>");
            writer.println("</Row>");

            // Output the splits:
            for (TransactionSplit split : splits
            ) {
               writer.println("<Row>");
               writer.println("<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
               writer.println("<Cell><Data ss:Type=\"String\">" + " " + "</Data></Cell>");
               writer.println("<Cell><Data ss:Type=\"String\">" + split + "</Data></Cell>");
               writer.println("</Row>");
            }
            writer.println("<Row>");
            writer.println("</Row>");
         }
      }

      // Write the trailer matter to output file:
      writer.println("</Table>");
      writer.println("</Worksheet>");
      writer.println("</Workbook>");

      // Close the output file:
      writer.close();
      resolver.say("MTD Spending Report written to the file " + filename);
   }
}
