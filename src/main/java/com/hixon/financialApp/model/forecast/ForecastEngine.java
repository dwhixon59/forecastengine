package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class ForecastEngine {

    // Constructor:
    public ForecastEngine() throws Exception {
        if (Utility.getDbConnection() == null) {
            throw new Exception("[SEVERE]  Database connection must not be null.");
        }
    } // End ForecastEngine().

    // Create a new forecast:
    public boolean generateForecast(Forecast forecast, Calendar startDate) throws Exception,
            BudgetException, EntityException {

        // Create a new set of items in the forecast:
        boolean result = generateForecastItems(forecast, startDate);
        if (result == true) {

            // Generate the forecast transactions for the new forecast items:
            generateForecastTransactions(forecast, startDate);
        }
        return result;
    }

    // Create a new set of forecast items in a new forecast from the budget items:
    private boolean generateForecastItems(Forecast forecast, Calendar startDate) throws ForecastException {
        try {
            /*
             Read each of the budget items in the budget database and for each budget item that is expected to occur
             in the forecast window, add a forecast item for it to the forecast.
            */
            // Retrieve a handle to the list of items in the budget:
            String query = BudgetItem.getSelectQuery() + " where bi.idBudgetItem = uuid_to_bin('" +
                    forecast.getBudget().getId() + "')";
            ResultSet rs = EntityInt.getRS(query, "Database error attempting to retrieve a list of items in the budget.");

            // Setup the start, next and end dates for the forecast:
            Calendar nextDate = new GregorianCalendar();
            nextDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH),
                    startDate.get(Calendar.DATE));
            System.out.println("Start Date: " + Utility.calendarDateToStringDate(startDate) + "  Next Date: " +
                    Utility.calendarDateToStringDate(nextDate) + "  End Date: " +
                    Utility.calendarDateToStringDate(forecast.getEndDate()));

            // For each item in the budget:
            ForecastItem forecastItem;
            forecast.createTransactionsArray();
            while (rs.next()) {

                // If this is an on-demand (unscheduled) item, then skip it:
                if (rs.getString("bi.period").equalsIgnoreCase("On-Demand"))
                    continue;

                // If this budget item expires before the beginning of the forecast window then skip it:
                Calendar budgetItemEndDateDb = Utility.localDateToCalendarDate(rs.getObject("bi.endDate",
                        LocalDate.class));
                if (budgetItemEndDateDb != null) {
                    if (budgetItemEndDateDb.compareTo(forecast.getStartDate()) < 0)
                        continue;
                }

                // Add a forecast item to the forecast for the current budget item:
                forecastItem = new ForecastItem(forecast, rs);
                forecast.addForecastItem(forecastItem);

            } // End for each item in the budget.

        } catch (SQLException | EntityException | BudgetException se) {
            ForecastException fe = new ForecastException("[SEVERE]  Database error traversing the list of budget items.");
            fe.initCause(se);
            throw fe;
        }
        return (forecast.getFirstForecastItem() != null) ? true : false;
    }

    // Generate the forecast transactions for the forecast items in a forecast starting at the specified start date:
    public boolean generateForecastTransactions(Forecast forecast, Calendar startDate) throws Exception,
                BudgetException, EntityException {
        try {
            /*
             Read each of the forecast items in the database and add transactions for them to the forecast in the correct
             order. There is a "forecasting" array with elements for each day of the forecasting.  The day elements point to
             a linked list of planned transactions for that day.  The list of budget items is scanned one at a time.
             For each item in the budget, a transaction is added to each day of the forecasting that that transaction is
             planned to occur.  After they have all been added, the array is traversed once and the transactions are
             inserted into the forecasting transactions table.  Finally, the forecasting object is returned.
            */
            // Retrieve a handle to the list of items in the forecast:
            ResultSet rs = EntityInt.getRS(ForecastItem.getSelectQuery() +
                    " where Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "')",
                    "Database error attempting to retrieve a list of items in the forecast.");

            Calendar nextDate = (Calendar) startDate.clone();
            System.out.println("Start Date: " + Utility.calendarDateToStringDate(startDate) +
                    "  Next Date:  " + Utility.calendarDateToStringDate(nextDate) + "  End Date: " +
                    Utility.calendarDateToStringDate(forecast.getEndDate()));

            // For each item in the forecast:
            ForecastItem forecastItem;
            forecast.createTransactionsArray();
            while (rs.next()) {

                // If this is an on-demand (unscheduled) item, then skip it:
                if (rs.getString("fi.period").equalsIgnoreCase("On-Demand")) {
                    continue;
                }

                // If this forecast item expires before the beginning of the forecast window then skip it:
                Calendar forecastItemEndDateDb = Utility.localDateToCalendarDate(rs.getObject("fi.endDate",
                        LocalDate.class));
                if (forecastItemEndDateDb != null) {
                    if (forecastItemEndDateDb.compareTo(startDate) < 0) {
                        continue;
                    }
                }

                // This item will be in the forecast, so create a forecast item object for it:
                forecastItem = new ForecastItem(rs);

                // Set the current date to the first date after the start date of the forecast window:
                nextDate = forecastItem.getFirstDateOnOrAfter(startDate);

                // For each instance of the period between the start date and the end of the forecast period:
                boolean firstOccurrence = true;
                while (forecast.fallsWithinForecastWindow(nextDate) && !forecastItem.isExpired(nextDate)) {

                    // If there is an overridden or reconciled forecast transaction in the database for this date,
                    // skip *adding* it, but still advance to the next date.
                    // This prevents duplicates when updating a forecast that has already been reconciled.
                    if (forecast.hasOverriddenForecastTransactionOnDate(forecastItem, nextDate) ||
                        forecast.hasReconciledForecastTransactionOnDate(forecastItem, nextDate)) {
                        firstOccurrence = false; // we did "see" the first occurrence
                        nextDate = forecastItem.getNextDateOfOccurrence(nextDate);
                        continue;
                    }

                    // Add the forecast transaction to the forecast:
                    forecast.addTransactionOnDate(forecastItem, startDate, nextDate, firstOccurrence);
                    firstOccurrence = false;

                    // Go to the next instance of this budget item:
                    nextDate = forecastItem.getNextDateOfOccurrence(nextDate);
                }
           } // End for each item in the budget.

        } catch (SQLException se) {
            System.out.println("[SEVERE]  Database error traversing the list of budget items.");
            throw se;
        }
        return true;

    } // End generateForecastTransactions().


    public boolean generateShortTermForecast(Forecast forecast) throws Exception, BudgetException {

        try {
            /*
             Read each of the items in the budget database and add transactions for them to the forecast in the correct
             order. There is a "forecasting" array with elements for each day of the forecasting.  The day elements point to
             a linked list of planned transactions for that day.  The list of budget items is scanned one at a time.
             For each item in the budget, a transaction is added to each day of the forecasting that that transaction is
             planned to occur.  After they have all been added, the array is traversed once and the transactions are
             inserted into the forecasting transactions table.  Finally, the forecasting object is returned.
            */
            Statement stmt;
            try {
                stmt = Utility.getDbConnection().createStatement();
            } catch (SQLException e) {
                System.out.println("[SEVERE]  dbConnection.createStatement() threw exception");
                e.printStackTrace();
                ForecastException fe = new ForecastException("Database error occurred trying to create a statement.");
                fe.initCause(e);
                throw fe;
            }

            // Retrieve a handle to the list of items in the budget:
            ResultSet rs;
            try {
                rs = stmt.executeQuery("select bin_to_uuid(idBudgetItem), category, payee, period, AMOUNT, " +
                        "startDate, numberOfPayments, endDate, ItemType, howPaid, searchString, " +
                        "bin_to_uuid(Budget_idBudget) from budgetItem order by AMOUNT desc");
            } catch (SQLException e) {
                stmt.close();
                ForecastException fe = new ForecastException(" SQL Error attempting to retrieve a list of items in the budget.");
                fe.initCause(e);
                throw fe;
            }

            // Setup the start, current and first-day-of-the-month dates for the forecast:
            Calendar startDate = forecast.getStartDate();
            Calendar nextDate = new GregorianCalendar();
            nextDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH),
                    startDate.get(Calendar.DATE));
            System.out.println("Start Date: " + Utility.calendarDateToStringDate(startDate) + "  Current Date: " +
                    Utility.calendarDateToStringDate(nextDate));

            // For each item in the budget:
            ForecastItem forecastItem;
            while (rs.next()) {

                // If this is an on-demand (unscheduled) item, then skip it:
                if (rs.getString("Period").equalsIgnoreCase("On-Demand")) continue;

                // If this item expires before the beginning of the forecast window, then skip it:
                Calendar budgetItemEndDateDb = Utility.localDateToCalendarDate(rs.getObject("endDate",
                        LocalDate.class));
                if (budgetItemEndDateDb != null) {
                    if (budgetItemEndDateDb.compareTo(forecast.getStartDate()) < 0) continue;
                }

                // If the item doesn't occur this month, then skip it:
                if (!forecast.fallsWithinForecastWindow(
                        Utility.localDateToCalendarDate(rs.getObject("startDate", LocalDate.class)))
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
                boolean firstOccurrence = true;
                while (forecast.fallsWithinForecastWindow(nextDate)) {

                    // Add the forecast transaction to the forecast:
                    forecast.addTransactionOnDate(forecastItem, startDate, nextDate, firstOccurrence);
                    firstOccurrence = false;

                    // Go to the next instance of this budget item:
                    nextDate = forecastItem.getNextDateOfOccurrence(nextDate);

                } // End for each instance of this item in the forecast window.
            } // End for each item in the budget.

            } catch (SQLException | BudgetException e) {
                System.out.println("[SEVERE]  Database error on 'select * from item where Budget_ID = 2'");
                e.printStackTrace();
                throw e;
            }
        return true;
    }
} // End class ForecastEngine.
