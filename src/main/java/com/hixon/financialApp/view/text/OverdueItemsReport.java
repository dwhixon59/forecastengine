package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.utility.Utility;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.List;

public class OverdueItemsReport extends ForecastReport {

    /*
     * Constructors:
     */
    protected OverdueItemsReport(Forecast forecast, List<Entity> items, File reportFile) throws FileNotFoundException {
        super(forecast, items, reportFile);
    }

    /*
     * Main methods:
     */
    @Override
    public void renderReportFrontMatter() {
        String title = "OVERDUE ITEMS - " + forecast.getDescription();
        pw.println(title);

        // Calculate the visual width of the title on iPhone and create an underline that matches
        double titleWidth = 0.0;
        for (int i = 0; i < title.length(); i++) {
            titleWidth += iPhone11FontSizes[title.charAt(i)];
        }

        // Calculate how many equals signs we need to match the title width
        double equalsWidth = iPhone11FontSizes['='];
        int numEquals = (int) Math.round(titleWidth / equalsWidth);
        pw.println("=".repeat(numEquals));
    }

    @Override
    public void renderItemRow(Entity item) throws EntityException, ForecastException, SQLException, BudgetException {

        // Cast the entity passed in to what it really is. This is required because we are using generics:
        ForecastTransaction forecastTransaction = (ForecastTransaction) item;

        // Use a short version of the date to take less space:
        String date = Utility.calendarDateToMonthDayStringDate(forecastTransaction.getPlannedDate());

        // Round off the amount to save space by not displaying the cents:
        String amount = Utility.formatRoundedDollarAmount(Math.abs(forecastTransaction.getRemainingAmount()));

        // Seems like we have about another 25 characters before text wrap on the iPhone 11, so get as much of the payee
        // as possible based on the length of the amount:
        String payee = forecastTransaction.getForecastItem().getPayee();
        int truncatedPayeeLength = 25 - amount.length();
        if (payee.length() > truncatedPayeeLength) {
            payee = payee.substring(0, truncatedPayeeLength);
        }

        // Output the forecast transaction line:
        pw.println(date + SPACE + payee + SPACE + amount);
    }
}
