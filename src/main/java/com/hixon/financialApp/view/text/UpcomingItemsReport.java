package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.utility.Utility;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.List;

public class UpcomingItemsReport extends ForecastReport {

    /*
     * Constructors:
     */
    public UpcomingItemsReport(Forecast forecast, List<Entity> items, File reportFile) throws FileNotFoundException {
        super(forecast, items, reportFile);

    }

    /*
     * Main Methods:
     */
    @Override
    public void renderReportFrontMatter() {
        pw.println("UPCOMING ITEMS:");
        pw.println("--------------");
    }

    @Override
    public void renderItemRow(Entity item) throws EntityException, ForecastException, SQLException, BudgetException, RegisterException {

        // Cast the entity passed in to what it really is. This is required because we are using generics:
        ForecastTransaction forecastTransaction = (ForecastTransaction) item;

        // Use a short version of the date to take less space:
        String date = Utility.calendarDateToMonthDayStringDate(forecastTransaction.getPlannedDate());

        // Round off the amounts to save space by not displaying the cents:
        String amount = Utility.formatRoundedDollarAmount(Math.abs(forecastTransaction.getRemainingAmount()));
        String runningBalance = Utility.formatRoundedDollarAmount(forecastTransaction.getRunningBalance());

        // Seems like we have about another 25 characters before text wrap on the iPhone 11, so get as much of the payee
        // as possible based on the length of the amount:
        String payee = forecastTransaction.getForecastItem().getPayee();
        int truncatedPayeeLength = 25 - amount.length();
        if (payee.length() > truncatedPayeeLength) {
            payee = payee.substring(0, truncatedPayeeLength);
        }

        // Output the forecast transaction line:
        pw.println(date + SPACE + payee + SPACE + amount + COMMA + SPACE + runningBalance);
    }
}
