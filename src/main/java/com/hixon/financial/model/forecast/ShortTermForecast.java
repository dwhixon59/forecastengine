package com.hixon.financial.model.forecast;

import java.sql.SQLException;
import java.util.Calendar;

public class ShortTermForecast extends Forecast{

    public ShortTermForecast(String budgetName, double startingBalance, double minimumBalance)
            throws ForecastException, SQLException {
        super(budgetName, null, startingBalance, minimumBalance, 1);
    }

    @Override
    public boolean fallsWithinForecastWindow(Calendar date) {
        boolean decision = false;

        if (date != null) {
            Calendar monthStartDate = Calendar.getInstance();
            monthStartDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), 1);
            if (date.compareTo(monthStartDate) >= 0 && date.compareTo(endDate) <= 0) {
                decision = true;
            } else {
                decision = false;
            }
        }

        return decision;
    }
}
