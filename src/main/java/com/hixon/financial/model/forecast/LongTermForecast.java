package com.hixon.financial.model.forecast;

import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.EntityInt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

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
}