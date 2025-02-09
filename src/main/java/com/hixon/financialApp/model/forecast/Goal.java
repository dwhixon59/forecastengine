package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.entity.EntityException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

/**
 * The Goal class represents a specific type of ForecastTransaction that includes additional methods
 * for handling goals within a forecast.
 */
public class Goal extends ForecastTransaction {

    /**
     * Constructs a Goal object from a ResultSet.
     *
     * @param rs the ResultSet containing the goal data
     * @throws SQLException if a database access error occurs
     * @throws EntityException if an entity-related error occurs
     */
    public Goal(ResultSet rs) throws SQLException, EntityException {
        super(rs);
    }

    /**
     * Gets the description of the goal.
     *
     * @return the description of the goal
     * @throws Exception if an error occurs
     */
    public String getDescription() throws Exception {
        return getForecastItem().getPayee();
    }

    /**
     * Gets the required amount for the goal.
     *
     * @return the goal amount
     */
    public double getAmount() {
        return getRemainingAmount();
    }

    /**
     * Gets the date of the goal.
     *
     * @return the date of the goal
     */
    public Calendar getGoalDate() {
        return getPlannedDate();
    }

    /**
     * Calculates the number of months remaining until the goal's end date from the report date.
     *
     * @param startDate the date of the report
     * @return the number of months remaining
     */
    public int getMonthsRemaining(Calendar startDate) {
        Calendar endDate = getGoalDate();
        int months = endDate.get(Calendar.MONTH) - startDate.get(Calendar.MONTH);
        int years = endDate.get(Calendar.YEAR) - startDate.get(Calendar.YEAR);
        return months + years * 12;
    }
}