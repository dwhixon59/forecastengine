package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

public class ForecastTransactionAndItemDatabaseIterator extends com.hixon.financialApp.model.forecast.ForecastTransactionDatabaseIterator {

   /*
    * Fields::
    */
   private Calendar startDate;
   ResultSet rsCredits;
   ForecastTransaction forecastCreditTransaction;
   ResultSet rsDebits;
   ForecastTransaction forecastDebitTransaction;
   ResultSet rsPlaceholders;
   ForecastTransaction forecastPlaceholderTransaction;


   /*
    * Constructors:
    */
   public ForecastTransactionAndItemDatabaseIterator(Forecast forecast, Calendar startDate) throws
           EntityException, SQLException, ForecastException, BudgetException {
      super();
      setForecast(forecast);
      this.startDate = startDate;

      // Get a result set with all the credits:
      String selectCreditsQuery = "select" + ForecastTransaction.getSelectColumns() + ", " + ForecastItem.getSelectColumns() +
              " from forecast_transaction ft inner join forecast_item fi on ft.ForecastItem_idForecastItem = " +
              "fi.idForecastItem where fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') and " +
              "ft.remainingAmount > 0.00 and ft.plannedDate >= " + Utility.calendarDateToSqlDateString(startDate) +
              " order by ft.plannedDate asc, ft.remainingAmount desc";
      rsCredits = EntityInt.getRS(selectCreditsQuery, "Database error occurred attempting to " +
              "get a list of credit forecast transactions by date.");
      if (rsCredits.next()) {
         forecastCreditTransaction = new ForecastTransaction(rsCredits);
         forecastCreditTransaction.setForecastItem(new ForecastItem(rsCredits));
      }

      // Get a result set with all the debits:
      String selectDebitsQuery = "select" + ForecastTransaction.getSelectColumns() + "," + ForecastItem.getSelectColumns() +
              "from forecast_transaction ft inner join forecast_item fi on ft.ForecastItem_idForecastItem = " +
              "fi.idForecastItem where fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') and " +
              "ft.remainingAmount < 0.00 and ft.plannedDate >= " + Utility.calendarDateToSqlDateString(startDate) +
              " order by ft.plannedDate asc, ft.remainingAmount asc";
      rsDebits = EntityInt.getRS(selectDebitsQuery, "Database error occurred attempting to " +
              "get a list of debit forecast transactions by date.");
      if (rsDebits.next()) {
         forecastDebitTransaction = new ForecastTransaction(rsDebits);
         forecastDebitTransaction.setForecastItem(new ForecastItem(rsDebits));
      }

      // Get a result set with all the placeholders (zero amounts):
      String selectPlaceholdersQuery = "select" + ForecastTransaction.getSelectColumns() + "," + ForecastItem.getSelectColumns() +
              "from forecast_transaction ft inner join forecast_item fi on ft.ForecastItem_idForecastItem = " +
              "fi.idForecastItem where fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') and " +
              "ft.remainingAmount = 0.00 and fi.amount = 0.00 and fi.howOccurs <> 'U' and ft.plannedDate >= " +
              Utility.calendarDateToSqlDateString(startDate) + " order by ft.plannedDate asc, fi.payee asc";
      rsPlaceholders = EntityInt.getRS(selectPlaceholdersQuery, "Database error occurred attempting to " +
              "get a list of placeholder forecast transactions by date.");
      if (rsPlaceholders.next()) {
         forecastPlaceholderTransaction = new ForecastTransaction(rsPlaceholders);
         forecastPlaceholderTransaction.setForecastItem(new ForecastItem(rsPlaceholders));
      }
   }


   /*
    * Main methods:
    */
   @Override
   public ForecastTransaction getNext() throws Exception, BudgetException {

      ForecastTransaction forecastTransaction = null;

      // For any given day, iterate over credits first:
      if (
            forecastCreditTransaction != null &&
            (forecastDebitTransaction == null ||
               forecastCreditTransaction.getPlannedDate().compareTo(forecastDebitTransaction.getPlannedDate()) <= 0) &&
            (forecastPlaceholderTransaction == null ||
               forecastCreditTransaction.getPlannedDate().compareTo(forecastPlaceholderTransaction.getPlannedDate()) <= 0)
         )
      {
         forecastTransaction = forecastCreditTransaction;
         if (rsCredits.next()) {
            forecastCreditTransaction = new ForecastTransaction(rsCredits);
            forecastCreditTransaction.setForecastItem(new ForecastItem(rsCredits));
         } else {
            forecastCreditTransaction = null;
         }
      }
      else
      // then debits:
      if (
              forecastDebitTransaction != null &&
              (forecastPlaceholderTransaction == null ||
                 forecastCreditTransaction.getPlannedDate().compareTo(forecastPlaceholderTransaction.getPlannedDate()) <= 0)
      )
      {
         forecastTransaction = forecastDebitTransaction;
         if (rsDebits.next()) {
            forecastDebitTransaction = new ForecastTransaction(rsDebits);
            forecastDebitTransaction.setForecastItem(new ForecastItem(rsDebits));
         } else {
            forecastDebitTransaction = null;
         }
      }
      else
      // then pkaceholders:
      if (forecastPlaceholderTransaction != null) {
         forecastTransaction = forecastPlaceholderTransaction;
         if (rsPlaceholders.next()) {
            forecastPlaceholderTransaction = new ForecastTransaction(rsPlaceholders);
            forecastPlaceholderTransaction.setForecastItem(new ForecastItem(rsPlaceholders));
         } else {
            forecastPlaceholderTransaction = null;
         }
      }

      return forecastTransaction;
   }
}
