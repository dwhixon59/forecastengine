package com.hixon.financialApp.view.csv;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ForecastView;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class CsvForecastView extends ForecastView {

   /*
    * Fields:
    */
   private String importForecastFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses" +
           "\\Expenses.csv";
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
      DATE, PAYEE, CREDIT, DEBIT, BALANCE, CATEGORY, IMPORTANCE, HOW_OCCURS, TRANSACTION_ID, VERSION, AMOUNT
   }

   @Override
   public List<ForecastTransaction> openForecastTransactionSource() throws ControllerException, BudgetException {
      int i = 1;
      List<ForecastTransaction> forecastTransactions = new ArrayList<>();
      try {
         Utility.getResolver().say("Update the forecast from the forecast transactions in the file " + importForecastFilename);
         in = new FileReader(importForecastFilename);

         // Setup the CSV parser:
         CSVFormat rfc4180 = CSVFormat.RFC4180;
         rfc4180.withHeader(ForecastTransactionHeaders.class);
         records = rfc4180.parse(in);

         // Iterator over the CSV records and create a list of forecast transactions from them:
         Iterable<CSVRecord> records = CSVFormat.RFC4180.withHeader(ForecastTransactionHeaders.class).parse(in);
         Calendar plannedDate = Calendar.getInstance();
         int previousMonth = plannedDate.get(Calendar.MONTH);
         for (CSVRecord record : records) {

            // If there is something in the Date column:
            if (!record.get(ForecastTransactionHeaders.DATE).isEmpty()) {

               // If the current record is a month header then update the month portion of the planned date:
               if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("January")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.JANUARY);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("February")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.FEBRUARY);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("March")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.MARCH);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("April")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.APRIL);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("May")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.MAY);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("June")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.JUNE);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("July")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.JULY);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("August")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.AUGUST);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("September")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.SEPTEMBER);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("October")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.OCTOBER);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("November")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.NOVEMBER);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).equalsIgnoreCase("December")) {
                  previousMonth = plannedDate.get(Calendar.MONTH);
                  plannedDate.set(Calendar.MONTH, Calendar.DECEMBER);
                  if (previousMonth >= plannedDate.get(Calendar.MONTH)) plannedDate.add(Calendar.YEAR, 1);
                  continue;
               } else if (record.get(ForecastTransactionHeaders.DATE).matches("[0-9]{1,2}(st|nd|rd|th)")) {
                  // It's got a date in the first column, so set the current date and create a forecast transaction:
                  int length = (record.get(ForecastTransactionHeaders.DATE).length() - 2);
                  plannedDate.set(Calendar.DATE, Integer.parseInt(record.get(Calendar.DATE).substring(1, length)));
               } else {
                  // This is neither a header row, or a forecast transaction row, so skip it:
                  continue;
               }
            } else if (record.get(ForecastTransactionHeaders.PAYEE).isEmpty()) {
               // The first and second columns are blank, so this is not a forecast transaction row; skip it:
               continue;
            }

            // Create a CSV forecast transaction from the spreadsheet row:
            CsvForecastTransaction csvForecastTransaction = new CsvForecastTransaction();
            csvForecastTransaction.setCategory(record.get(ForecastTransactionHeaders.CATEGORY));
            csvForecastTransaction.setPayee(record.get(ForecastTransactionHeaders.PAYEE));
            csvForecastTransaction.setCredit(Double.parseDouble(record.get(ForecastTransactionHeaders.CREDIT)));
            csvForecastTransaction.setDebit(Double.parseDouble(record.get(ForecastTransactionHeaders.DEBIT)));
            csvForecastTransaction.setHowImportant(Item.parseHowImportant(record.get(ForecastTransactionHeaders.IMPORTANCE)));
            csvForecastTransaction.setHowOccurs(Item.parseHowOccurs(record.get(ForecastTransactionHeaders.HOW_OCCURS)));
            csvForecastTransaction.setAmount(Double.parseDouble(record.get(ForecastTransactionHeaders.AMOUNT)));

            if (!record.get(ForecastTransactionHeaders.TRANSACTION_ID).isEmpty()) {
               csvForecastTransaction.setId(UUID.fromString(record.get(ForecastTransactionHeaders.TRANSACTION_ID)));
            } else {
               csvForecastTransaction.setId(null);
            }
            csvForecastTransaction.setVersion(Utility.stringTimeStampToCalendarDate(
                    record.get(ForecastTransactionHeaders.VERSION)));
            double remainingAmout = csvForecastTransaction.getDebit();
            if (remainingAmout > 0.01) {
               remainingAmout = -remainingAmout;
            } else {
               remainingAmout = csvForecastTransaction.getCredit();
               if (remainingAmout < 0.01) {
                  remainingAmout = 0;
               }
            }
            csvForecastTransaction.setRemainingAmount(remainingAmout);
            csvForecastTransaction.setPlannedDate(plannedDate);
            csvForecastTransaction.setRunningBalance(Double.parseDouble(record.get(ForecastTransactionHeaders.BALANCE)));

            // Create or link to an existing forecast item in the forecast:
/*
            ForecastItem forecastItem = new ForecastItem(forecast, split.getBudgetItem());
            forecastItem.setAmount(1);
            forecastItem.save(INSERT);
            forecastTransaction = new ForecastTransaction(forecastItem, split.getTransaction().getDate(), true);
            forecastTransaction.setRemainingAmount(1);
            forecastTransaction.save(INSERT);
*/

            // Add the forecast transaction to the list
            // TODO:  Add a forecast transaction and a forecast item to the list, not a csvForecastTransaction:
            forecastTransactions.add(csvForecastTransaction);
         }

      } catch (FileNotFoundException e) {
         ControllerException ce = new ControllerException("Transactions file " + importForecastFilename + " not found.");
         ce.initCause(e);
         throw (ce);
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
      }

      return forecastTransactions;
   }
}
