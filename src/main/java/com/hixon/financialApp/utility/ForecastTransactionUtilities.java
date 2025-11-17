package com.hixon.financialApp.utility;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.utility.Utility.calendarDateToStringDate;

/**
 * Utility class for forecast transaction operations that don't belong in the core ForecastTransaction entity class.
 * Contains helper methods for querying, filtering, and analyzing forecast transactions.
 */
public class ForecastTransactionUtilities {

    /**
     * Get forecast transactions within a date range for a specific forecast.
     * This is used for matching cleared transactions to planned forecast transactions.
     *
     * @param idForecast The forecast ID to query
     * @param startDate The start of the date range (inclusive)
     * @param endDate The end of the date range (inclusive)
     * @return A list of ForecastTransaction objects in the date range, ordered by planned date
     * @throws EntityException if a database error occurs
     * @throws SQLException if a SQL error occurs
     * @throws ForecastException if a forecast error occurs
     * @throws BudgetException if a budget error occurs
     */
    public static List<ForecastTransaction> getForecastTransactionsInDateRange(
            UUID idForecast,
            Calendar startDate,
            Calendar endDate) throws EntityException, SQLException, ForecastException, BudgetException {

        List<ForecastTransaction> transactions = new ArrayList<>();

        String selectQuery = ForecastTransaction.getSelectQuery() + " " +
                "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                "where fi.Forecast_idForecast = uuid_to_bin('" + idForecast + "') " +
                "and ft.plannedDate >= '" + calendarDateToStringDate(startDate) + "' " +
                "and ft.plannedDate <= '" + calendarDateToStringDate(endDate) + "' " +
                "order by ft.plannedDate asc";

        ResultSet rs = EntityInt.getRS(selectQuery, "Database error occurred attempting to " +
                "get forecast transactions in date range for forecast " + idForecast);

        while (rs.next()) {
            transactions.add(new ForecastTransaction(rs));
        }

        return transactions;
    }
}

