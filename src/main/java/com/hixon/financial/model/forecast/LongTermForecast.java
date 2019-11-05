package com.hixon.financial.model.forecast;

import com.hixon.financial.Utility;
import com.hixon.financial.controller.QuitException;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.EntityInt;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItem;
import com.hixon.financial.model.register.RegisterException;
import com.hixon.financial.view.register.UserResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

import static com.hixon.financial.model.EntityInt.SaveMethod.INSERT;
import static com.hixon.financial.model.EntityInt.executeQuery;
import static java.util.Calendar.DATE;
import static java.util.Calendar.MONTH;

// This class represents a forecast of transaction over a period of time.
public class LongTermForecast extends Forecast {

   public int getNumberOfMonths() {
      return numberOfMonths;
   }


   // Constructors:
   public LongTermForecast(String budgetName, Calendar startDate, double startingBalance, int numberOfMonths,
                           double minimumBalance) throws SQLException, ForecastException {

      super(budgetName, startDate, startingBalance, minimumBalance, numberOfMonths);

      System.out.println("The long term forecast object was successfully initialized with " + transactions.length +
              " entries.");
   }

   public LongTermForecast(ResultSet rs) throws SQLException {
      super(rs);
   }


   /*
    *  Helper methods:
    */

   public void createTransactionsArray() {
      this.transactions = new ForecastTransaction[numberOfMonths * 31];
   }

   // Load and save methods:
   public static LongTermForecast getMostRecent() throws EntityException, SQLException {
      String selectMostRecentQuery = Forecast.getSelectQuery() + "order by dateGenerated desc";
      ResultSet rs = EntityInt.getSingletonRS(selectMostRecentQuery, "Database error occurred " +
              "trying to retrieve the most recent forecast.");
      LongTermForecast longTermForecast = null;
      if (rs != null) {
         longTermForecast = new LongTermForecast(rs);
      }
      return longTermForecast;
   }


   /*
    *  Main methods:
    */

   // Update the long term forcast which means regenerate the forecast beginning on the first day of the next month:
   public enum updateStart {TODAY, FIRST_OF_NEXT_MONTH, ONE_MONTH_FROM_TODAY, ARBITRARY_DATE}
   public void updateForecast() throws Exception, EntityException, BudgetException, QuitException, RegisterException {

      // Get the starting date of the transactions to update:
      UserResponse response = Utility.getResolver().getForecastUpdateStartDate();

      // Compute the start date for the update:
      Calendar startDate = Calendar.getInstance();
      switch (response.getUpdateStart()) {
         case TODAY:
            break;

         case FIRST_OF_NEXT_MONTH:
            startDate.add(MONTH, 1);
            startDate.set(DATE, 1);
            break;

         case ONE_MONTH_FROM_TODAY:
            startDate.add(MONTH, 1);
            break;

         case ARBITRARY_DATE:
            startDate = response.getDate();
            break;
      }

      // Fix up the end date:
      endDate = Calendar.getInstance();
      endDate.set(startDate.get(Calendar.YEAR), startDate.get(MONTH), startDate.get(DATE));
      endDate.add(MONTH, numberOfMonths);

      // Update all the forecast items in the forecast from the current budget items:
      String query = "update ForecastDatabase.Forecast_Item fi inner join ForecastDatabase.Budget_Item bi on " +
              "fi.BudgetItem_idBudgetItem = bi.idBudgetItem set fi.category = bi.category, fi.payee = bi.payee, " +
              "fi.period = bi.period, fi.amount = bi.amount, fi.startDate = bi.startDate, fi.numberOfPayments = " +
              "bi.numberOfPayments, fi.endDate = bi.endDate, fi.itemType = bi.itemType, fi.howImportant =" +
              " bi.howImportant, fi.howOccurs = bi.howOccurs, fi.howPaid = bi.howPaid where fi.Forecast_idForecast =" +
              " uuid_to_bin('" + id  + "')";
      executeQuery(query, "updating the forecast items from the budget items");

      // Get a list of current budget items that weren't included in the forecast:
      query = BudgetItem.getSelectQuery() + "where idBudgetItem not in (select distinct BudgetItem_idBudgetItem from " +
              "ForecastDatabase.Forecast_Item)";
      ResultSet rs = EntityInt.getRS(query, "retrieving the budget items not included in the forecast");

      // Insert any new forecast items that weren't originally included:
      ForecastItem forecastItem;
      while (rs.next()) {
         forecastItem = new ForecastItem(this, rs);
         forecastItem.save(INSERT);
      }

      // Note:  we don't have to worry about forecast items based upon budget items that no longer exist because
      // CASCADE DELETE referential integrity constraints would have deleted them at the time the budget item was
      // deleted.

      // Delete all of the forecast transactions that occur after the start date:
      query = ForecastTransaction.getDeleteQuery() + "where plannedDate >= " +
              Utility.calendarDateToSqlDateString(startDate);
      executeQuery(query, "deleting all the forecast transactions after " +
              Utility.calendarDateToStringDate(startDate));

      // Generate the updated portion of the forecast starting on start date:
      this.transactions = new ForecastTransaction[numberOfMonths * 31];
      ForecastEngine forecastEngine = new ForecastEngine();
      forecastEngine.generateLongTermForecast(this, startDate);
   }
}