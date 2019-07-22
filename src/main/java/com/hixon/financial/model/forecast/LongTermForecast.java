package com.hixon.financial.model.forecast;

import java.sql.SQLException;
import java.util.Calendar;

// This class represents a forecast of transaction over a period of time.
public class LongTermForecast extends Forecast {

    public int getNumberOfMonths() { return numberOfMonths; }


    // Constructors:
    public LongTermForecast(String budgetName, Calendar startDate, double startingBalance, int numberOfMonths,
                            double minimumBalance) throws SQLException, ForecastException {

        super(budgetName, startDate, startingBalance, minimumBalance, numberOfMonths);

        System.out.println("The long term forecast object was successfully initialized with " + transactions.length +
                " entries.");
    }
}