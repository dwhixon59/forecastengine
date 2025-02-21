package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.Transaction;

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
    public Envelope(ForecastItem item) throws Exception {
        super(item);
    }

    // Get the name of the envelope:
    public String getName() {
        return getPayee();
    }

    // Get the contribution amount for the envelope:
    public double getContributionAmount() {
        return getAmount();
    }

    // Get the envelope balance:
    public double getEnvelopeBalance() {
        return getRunningBalance();
    }

    // Get the buffer amount for the envelope:
    public double getBufferTarget() {
        return getMinimumBalance();
    }

    // Retrieve the new transactions for this envelope from the database:
    public List<Transaction> getNewTransactions() throws Exception {
        return  TransactionSplit.getNewTransactionsForBudgetItem(getBudgetItem());
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

        // Retrieve the forecast transactions for this envelope that are in the future and have a negative amount:
        List<ForecastTransaction> forecastTransactions =
                ForecastTransaction.getNegativeForecastTransForItemOnOrAfter(this, reportDate);

        // Create goals for the forecast transaction and add the goals to the list:
        List<Goal> goals = new ArrayList<>();
        for (ForecastTransaction forecastTransaction : forecastTransactions) {
            goals.add(new Goal(forecastTransaction));
        }

        return goals;
    }
}