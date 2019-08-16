package com.hixon.financial.model.forecast;

import com.hixon.financial.Utility;

import java.sql.*;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class ForecastEngine {

    // Constructor:
    public ForecastEngine() throws Exception {
        if (Utility.getDbConnection() == null) {
            throw new Exception("[SEVERE]  Database connection must not be null.");
        }
    } // End ForecastEngine().

    // Connect to the MySQL database:
    public boolean generateLongTermForcast(LongTermForecast forecast) throws Exception {

        try {
            /*
             Read each of the items in the budget database and add transactions for them to the forecast in the correct
             order. There is a "forecasting" array with elements for each day of the forecasting.  The day elements point to
             a linked list of planned transactions for that day.  The list of budget items is scanned one at a time.
             For each item in the budget, a transaction is added to each day of the forecasting that that transaction is
             planned to occur.  After they have all been added, the array is traversed once and the transactions are
             inserted into the forecasting transactions table.  Finally, the forecasting object is returned.
            */
            Statement stmt = null;
            try {
                stmt = Utility.getDbConnection().createStatement();
            } catch (SQLException e) {
                System.out.println("[SEVERE]  dbConnection.createStatement() threw exception");
                e.printStackTrace();
                if (stmt != null) stmt.close();
            }

            // Retrieve a handle to the list of items in the budget:
            ResultSet rs = null;
            try {
                rs = stmt.executeQuery("select bin_to_uuid(idBudgetItem), category, payee, period, AMOUNT, " +
                        "startDate, numberOfPayments, endDate, ItemType, howPaid, bin_to_uuid(Budget_idBudget) from " +
                        "ForecastDatabase.BudgetItem order by amount desc");
            } catch (SQLException e) {
                try {
                    if (stmt != null) stmt.close();
                    if (rs != null) rs.close();
                } finally {
                    ForecastException fe = new ForecastException("Database error attempting to retrieve a list of items " +
                            "in the budget.");
                    fe.initCause(e);
                    throw fe;
                }
            }

            // Setup the start, current and end dates for the forecast:
            Calendar startDate = forecast.getStartDate();
            Calendar nextDate = new GregorianCalendar();
            nextDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH),
                    startDate.get(Calendar.DATE));
            System.out.println("Start Date: " + Utility.calendarDateToStringDate(startDate) + "  Current Date: " +
                    Utility.calendarDateToStringDate(nextDate) + "  End Date: " +
                    Utility.calendarDateToStringDate(forecast.getEndDate()));

            // For each item in the budget:
            ForecastItem forecastItem = null;
            ForecastTransaction transaction = null;
            while (rs.next()) {

                boolean skip = false;

                // If this is an on-demand (unscheduled) item, then skip it:
                if (rs.getString("Period").equalsIgnoreCase("On-Demand")) skip = true;

                // If this budget item expires before the beginning of the forecast window:
                Date budgetItemEndDateDb = rs.getDate("endDate");
                if (budgetItemEndDateDb != null) {
                    Calendar budgetItemEndDate = new GregorianCalendar();
                    budgetItemEndDate.setTime(budgetItemEndDateDb);
                    if (budgetItemEndDate.compareTo(forecast.getStartDate()) < 0) skip = true;
                }
                if (!skip) {

                    forecastItem = new ForecastItem(forecast, rs);
                    forecast.addForecastItem(forecastItem);
                    System.out.println(forecastItem);

                    // Set the current date to the first date after the start date of the forecast window:
                    nextDate = forecastItem.getFirstDateOnOrAfter(startDate);

                    // For each instance of the period between the start date and the end of the forecast period:
                    while (forecast.fallsWithinForecastWindow(nextDate)) {

                        // Add the forecast transaction to the forecast:
                        forecast.addTransactionOnDate(forecastItem, nextDate);

                        // Go to the next instance of this budget item:
                        nextDate = forecastItem.getNextDate();

                    } // End for each instance of this item in the forecast window.
                } // End if this item didn't expire before the forecast window begins.
            } // End for each item in the budget.

        } catch (SQLException e) {
            System.out.println("[SEVERE]  Database error on 'select * from item where Budget_ID = 2'");
            e.printStackTrace();
            throw e;
        }
        return true;

    } // End generateLongTermForcast().


    public boolean generateShortTermForcast(ShortTermForecast forecast) throws Exception {

        try {
            /*
             Read each of the items in the budget database and add transactions for them to the forecast in the correct
             order. There is a "forecasting" array with elements for each day of the forecasting.  The day elements point to
             a linked list of planned transactions for that day.  The list of budget items is scanned one at a time.
             For each item in the budget, a transaction is added to each day of the forecasting that that transaction is
             planned to occur.  After they have all been added, the array is traversed once and the transactions are
             inserted into the forecasting transactions table.  Finally, the forecasting object is returned.
            */
            Statement stmt = null;
            try {
                stmt = Utility.getDbConnection().createStatement();
            } catch (SQLException e) {
                System.out.println("[SEVERE]  dbConnection.createStatement() threw exception");
                e.printStackTrace();
                if (stmt != null) stmt.close();
            }

            // Retrieve a handle to the list of items in the budget:
            ResultSet rs = null;
            try {
                rs = stmt.executeQuery("select bin_to_uuid(idBudgetItem), category, payee, period, AMOUNT, " +
                        "startDate, numberOfPayments, endDate, ItemType, howPaid, searchString, " +
                        "bin_to_uuid(Budget_idBudget) from ForecastDatabase.BudgetItem order by AMOUNT desc");
            } catch (SQLException e) {
                System.out.println("[SEVERE]  SQL Error attempting to retrieve a list of items in the budget.");
                stmt.close();
                if (rs != null) rs.close();
            }

            // Setup the start, current and first-day-of-the-month dates for the forecast:
            Calendar startDate = forecast.getStartDate();
            Calendar nextDate = new GregorianCalendar();
            nextDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH),
                    startDate.get(Calendar.DATE));
            Calendar endDate = new GregorianCalendar();
            System.out.println("Start Date: " + Utility.calendarDateToStringDate(startDate) + "  Current Date: " +
                    Utility.calendarDateToStringDate(nextDate));

            // For each item in the budget:
            ForecastItem forecastItem = null;
            ForecastTransaction transaction = null;
            while (rs.next()) {

                // If this is an on-demand (unscheduled) item, then skip it:
                if (rs.getString("Period").equalsIgnoreCase("On-Demand")) continue;

                // If this item expires before the beginning of the forecast window, then skip it:
                Date budgetItemEndDateDb = rs.getDate("endDate");
                if (budgetItemEndDateDb != null) {
                    Calendar budgetItemEndDate = new GregorianCalendar();
                    budgetItemEndDate.setTime(budgetItemEndDateDb);
                    if (budgetItemEndDate.compareTo(forecast.getStartDate()) < 0) continue;
                }

                // If the item doesn't occur this month, then skip it:
                if (!forecast.fallsWithinForecastWindow(
                        Utility.SqlDateToCalendarDate(rs.getDate("startDate")))
                   ) continue;

                // If the item occurs only once this month:

                    // then if the transaction has already occurred:

                        // then if the actual date that it occurred on is significantly different than the planned date:

                            // then create a "date discrepancy" significant event:

                        // and if the actual amount was significantly different than the planned amount:

                            // then create a "amount discrepancy" significant event:

                    // but if the transaction has not occurred yet:

                            // then if the planned date is before today:

                                // then make the planned date today:

                            // add the forecast transaction to the forecast:

                // but if the item occurs more than once this month:

            // then compute the total amount spent on this budget item since the first occurrence of it this
            // month:

            // then compute the total amount budgeted for that period:

            // Compute the to go" amount and add it to the current transaction:

            // if the difference is positive

            // set that as the amount of the current occurrence:

            // else if the difference is negative:

            // then compute the remaining budgeted amount:

            // and if it is > 0, then distribute the remaining amount over the current and remaining
            // occurrences:

            // but if it is not >0, then set the amount to 0 for the rest of the occurrences this month:

            // For each occurrence ot this item between the beginning of the month and the end of the month:

            // Add the forecast transaction to the forecast:

                forecastItem = new ForecastItem(forecast, rs);
                forecast.addForecastItem(forecastItem);
                System.out.println(forecastItem);

                // Set the current date to the first date after the start date of the forecast window:
                nextDate = forecastItem.getFirstDateOnOrAfter(startDate);

                // For each instance of the period between the start date and the end of the forecast period:
                while (forecast.fallsWithinForecastWindow(nextDate)) {

                    // Add the forecast transaction to the forecast:
                    forecast.addTransactionOnDate(forecastItem, nextDate);

                    // Go to the next instance of this budget item:
                    nextDate = forecastItem.getNextDate();

                } // End for each instance of this item in the forecast window.
            } // End for each item in the budget.

        } catch (SQLException e) {
            System.out.println("[SEVERE]  Database error on 'select * from item where Budget_ID = 2'");
            e.printStackTrace();
            throw e;
        }
        return true;
    }
} // End class ForecastEngine.
