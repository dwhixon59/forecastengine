package com.hixon.financialApp.view.csv;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.utility.FinancialException;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.ForecastView;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.utility.Utility.*;

public class CsvForecastView extends ForecastView {

   /*
    * Fields:
    */
   private String importForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses" +
           "\\Expenses.csv";
   private String importForecastSaveFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses" +
           "\\Expenses.old.csv";
   private FileReader in;
   private CSVParser records;


   /*
    * Getters and Setters:
    */
   public String getImportForecastFilename() {
      return importForecastFilename;
   }

   public void setImportForecastFilename(String importForecastFilename) {
      this.importForecastFilename = importForecastFilename;
   }


   /*
    * Constructors:
    */
   public CsvForecastView() {

   }


   /*
    * Helper methods:
    */
   private void setStartingPlannedDate(int forecastMonth, Calendar plannedDate) {
      Calendar currentDate = Calendar.getInstance();
      int currentMonth = currentDate.get(Calendar.MONTH);
      if (forecastMonth > currentMonth) plannedDate.set(Calendar.YEAR, currentDate.get(Calendar.YEAR) - 1);
      if (currentMonth >= forecastMonth) {
         plannedDate.add(Calendar.MONTH, currentMonth - forecastMonth + 1);
      } else {

      }

   }


   /*
    * Main methods:
    */
   @Override
   public void openLongTermForecastOutput() throws FileNotFoundException, UnsupportedEncodingException {

   }

   @Override
   protected void renderLongTermForecastFrontMatter() {

   }

   @Override
   public void renderMonthHeader(Calendar plannedDate) {

   }

   @Override
   public void renderForecastTransaction(ForecastTransaction forecastTransaction, int credit, int debit) throws EntityException,
           SQLException, ForecastException, BudgetException {

   }

   @Override
   protected void renderLongTermForecastBackMatter() {

   }

   @Override
   protected void closeLongTermForecastOutput() {

   }

   // Forecast transaction headers in a CSV file:
   public enum ForecastTransactionHeaders {
      DATE, CATEGORY, PAYEE, CREDIT, DEBIT, BALANCE, BLANK, IMPORTANCE, HOW_OCCURS, TRANSACTION_ID, VERSION, AMOUNT
   }

   @Override
   public List<ForecastTransaction> openForecastTransactionSource() throws ControllerException, BudgetException {
      int i = 0;
      List<ForecastTransaction> forecastTransactions = new ArrayList<>();

      try (BOMInputStream bis = new BOMInputStream(new FileInputStream(new File(importForecastFilename)))) {

         getResolver().say("Update the forecast from the forecast transactions in the CSV file " + importForecastFilename);

         // Work on the most recent forecast:
         Forecast forecast = Forecast.getMostRecent();

         // Iterate over the CSV records and create a list of forecast transactions from them:
         BufferedReader in = new BufferedReader(new InputStreamReader(bis, StandardCharsets.UTF_8));
         Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader(ForecastTransactionHeaders.class).parse(in);
         Calendar plannedDate = Calendar.getInstance();
         int previousMonth;
         boolean firstTime = true;
         for (CSVRecord record : records) {

            // Keep track of the line number for debugging purposes:
            i++;

            // If there is something in the Date column:
            String dateColumn = record.get(ForecastTransactionHeaders.DATE);
            if (!dateColumn.isEmpty()) {

               // If the current record is a month header then update the month and year portion of the planned date:
               try {
                  plannedDate = MonthYearLongDateToCalendarDate(dateColumn);
                  continue;
               } catch (ParseException pe) {
                  // Parse exception is OK.  Just not a month header row.
                  if (dateColumn.matches("[0-9]{1,2}(st|nd|rd|th)")) {
                     // It's got a date in the first column, so set the current date and create a forecast transaction:
                     int length = (dateColumn.length() - 2);
                     plannedDate.set(Calendar.DATE, Integer.parseInt(dateColumn.substring(0, length)));
                  } else {
                     // This is neither a header row, or a forecast transaction row, so skip it:
                     continue;
                  }
               }
            } else if (record.get(ForecastTransactionHeaders.PAYEE).isEmpty()) {
               // The first and second columns are blank, so this is not a forecast transaction row; skip it:
               continue;
            }

            // If we get this far, we are working on a forecast transaction view, so copy the values from the CSV row
            // into a viewForecastTransaction:
            ForecastTransactionView forecastTransactionView = new ForecastTransactionView();
            forecastTransactionView.getForecastItem().setForecast(forecast);
            forecastTransactionView.setDate((Calendar) plannedDate.clone());
            forecastTransactionView.setCategory(record.get(ForecastTransactionHeaders.CATEGORY));
            forecastTransactionView.setPayee(record.get(ForecastTransactionHeaders.PAYEE));
            forecastTransactionView.setCredit(parseDollarAmount(record.get(ForecastTransactionHeaders.CREDIT)));
            forecastTransactionView.setDebit(parseDollarAmount(record.get(ForecastTransactionHeaders.DEBIT)));
            forecastTransactionView.setRunningBalance(parseDollarAmount(record.get(ForecastTransactionHeaders.BALANCE)));
            forecastTransactionView.getForecastItem().setPeriod(Item.PeriodType.ON_DEMAND);
            forecastTransactionView.getForecastItem().setItemType(Item.ItemType.EXPENSE);
            if (parseDollarAmount(record.get(ForecastTransactionHeaders.DEBIT)) > 0) {
               forecastTransactionView.getForecastItem().setHowPaid(Item.HowPaid.DEBIT_CARD);
            } else {
               forecastTransactionView.getForecastItem().setHowPaid(Item.HowPaid.DIRECT_DEPOSIT);
            }
            if (!record.get(ForecastTransactionHeaders.IMPORTANCE).isEmpty()) {
               forecastTransactionView.setHowImportant(Item.parseHowImportant(record.get(ForecastTransactionHeaders.IMPORTANCE)));
            } else {
               forecastTransactionView.setHowImportant(Item.HowImportant.FIXED_ESSENTIAL);
            }
            if (!record.get(ForecastTransactionHeaders.HOW_OCCURS).isEmpty()) {
               forecastTransactionView.setHowOccurs(Item.parseHowOccurs(record.get(ForecastTransactionHeaders.HOW_OCCURS)));
            } else {
               forecastTransactionView.setHowOccurs(Item.HowOccurs.UNPLANNED);
            }
            if (!record.get(ForecastTransactionHeaders.TRANSACTION_ID).isEmpty()) {
               forecastTransactionView.setTransactionID(UUID.fromString(record.get(ForecastTransactionHeaders.TRANSACTION_ID)));
            } else {
               forecastTransactionView.setTransactionID(null);
            }
            if (!record.get(ForecastTransactionHeaders.VERSION).isEmpty()) {
               forecastTransactionView.setVersion(stringTimeStampToCalendarDate(
                       record.get(ForecastTransactionHeaders.VERSION)));
            } else {
               forecastTransactionView.setVersion(null);
            }
            if (!record.get(ForecastTransactionHeaders.AMOUNT).isEmpty()) {
               forecastTransactionView.setAmount(parseDollarAmount(record.get(ForecastTransactionHeaders.AMOUNT)));
            } else {
               forecastTransactionView.setAmount(parseDollarAmount(record.get(ForecastTransactionHeaders.CREDIT)) -
                       parseDollarAmount(record.get(ForecastTransactionHeaders.DEBIT)));
            }

            // Add the forecast transaction to the list
            forecastTransactions.add(forecastTransactionView);
         }

      } catch (FileNotFoundException e) {
         ControllerException ce = new ControllerException("Transactions file " + importForecastFilename + " not found.");
         forecastTransactions = null;
      } catch (IOException e) {
         ControllerException ce = new ControllerException("I/O error reading from the transactions file " +
                 importForecastFilename + "on line " + i + ".");
         ce.initCause(e);
         throw (ce);
      } catch (Exception e) {
         ControllerException ce = new ControllerException("Exception while processing the transactions file " +
                 importForecastFilename + " on line " + i + ".");
         ce.initCause(e);
         throw ce;
      } catch (EntityException e) {
         ControllerException ce = new ControllerException("Exception while loading the most recent forecast.");
         ce.initCause(e);
         throw ce;
      }

      return forecastTransactions;
   }


   // Close the forecast transactions CSV file and remove it:
   @Override
   public void closeForecastTransactionSource() throws ViewException {

      // Create a previous version of the import file:
      try {
         Utility.makeSaveFile(importForecastFilename, importForecastSaveFilename);
      } catch (FinancialException e) {
         ViewException ve =  new ViewException("Error occured while creating a previous version of the forecast " +
                 "transaction import file.");
         ve.initCause(e);
         throw ve;
      }
   }
}
