package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The Envelope class represents a specific type of ForecastItem that includes additional methods
 * for handling envelopes within a forecast.
 */
public class Envelope extends ForecastItem {

    /**
     * Constructs an Envelope object from a ResultSet.
     *
     * @param rs the ResultSet containing the envelope data
     * @throws SQLException if a database access error occurs
     * @throws EntityException if an entity-related error occurs
     */
    public Envelope(ResultSet rs) throws Exception {
        super(rs);
    }

    /**
     * Constructs an Envelope object from a ForecastItem.
     */
    public Envelope(ForecastItem item) {
        super(item);
    }

    // Get the name of the envelope:
    public String getName() {
        return getPayee();
    }

    // Get the buffer amount for the envelope:
    public double getBufferAmount() {
        return getMinimumBalance();
    }

    /**
     * Retrieves a list of goals for this envelope.
     *
     * @param reportDate the date of the report
     * @return a list of goals
     * @throws SQLException if a database access error occurs
     * @throws EntityException if an entity-related error occurs
     */
    public List<Goal> getGoals(Calendar reportDate) throws Exception {
        String query =
                ForecastTransaction.getSelectQuery() + " " +
                 "WHERE " +
                    "ForecastItem_idForecastItem = uuid_to_bin('" + idForecast + "') AND " +
                    "remainingAmount < 0 AND plannedDate > " + Utility.calendarDateToSqlDateString(reportDate) +
                " ORDER BY " +
                    "plannedDate";
        ResultSet rs = EntityInt.getRS(query, "retrieve goals for envelope " + toStringVeryConcise());
        List<Goal> goals = new ArrayList<>();
        while (rs.next()) {
            goals.add(new Goal(rs));
        }
        return goals;
    }
}