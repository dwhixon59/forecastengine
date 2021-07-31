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
//        pw.println(TAB + "|" + TAB + "|" + TAB + "|" + TAB + "|" + TAB + "|" + TAB + "|" + TAB + "|" + TAB +
//                    "|" + TAB + "|" + TAB + "|");
//        for (int i = 0; i < fontCounts.length; i++) {
//            if (fontCounts[i] > 0) {
//                pw.println(TAB + TAB + TAB + TAB + TAB + TAB + TAB + TAB + TAB + TAB + "|");
//                pw.print("\t\t|");
//                for (int j = 0; j < Math.round(fontCounts[i]); j++) {
//                    pw.print(Character.toString((char) i));
//                }
//                if (i == 32) {
//                    pw.println("|");
//                } else {
//                    pw.println("     " + i + ", " + Math.round(fontCounts[i]));
//                }
//            }
//        }
    }

    @Override
    public void renderItemRow(Entity item) throws EntityException, ForecastException, SQLException, BudgetException, RegisterException {

        // Cast the entity passed in to what it really is. This is required because we are using generics:
        ForecastTransaction forecastTransaction = (ForecastTransaction) item;

        // Use a short version of the date to take less space:
        String date = Utility.calendarDateToMonthDayStringDate(forecastTransaction.getPlannedDate());

        // Format the payee field:
        String payee = forecastTransaction.getForecastItem().getPayee();
        payee = padStringWithTabs(payee, 8, iPhone11FontSizes);

        // Format the amount field:
        String amountString = formatRoundedDollarAmountField(forecastTransaction.getRemainingAmount(),
                2, iPhone11FontSizes);

        // Format the running balance field:
        String runningBalanceString = formatRoundedDollarAmountField(forecastTransaction.getRunningBalance(),
                2, iPhone11FontSizes);

        // Output the forecast transaction line:
        pw.println(date + TAB + payee + amountString + TAB + runningBalanceString);
    }
}
