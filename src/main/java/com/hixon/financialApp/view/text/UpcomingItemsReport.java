package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastUtilities;
import com.hixon.financialApp.utility.Utility;
import lombok.Getter;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

public class UpcomingItemsReport extends ForecastReport {

    public static final String PAY_PERIOD_HEADER = "Pay Period Ending Balance ============>";

    /*
     * Getters and Setters:
     */
    // Get the current pay period start date:
    @Getter
    protected Calendar currentPayPeriodStartDate = null;

    // Get the current pay period end date:
    @Getter
    protected Calendar currentPayPeriodEndDate = null;

    protected ForecastUtilities forecastUtilities = new ForecastUtilities();


    /*
     * Constructors:
     */
    public UpcomingItemsReport(Forecast forecast, List<Entity> items, File reportFile) throws FileNotFoundException {
        super(forecast, items, reportFile);

    }

    /*
    * Helper Methods:
     */
    /**
     * Determine if a forecast transaction starts a new pay period.  A new pay period is defined according to the
     * declared period type of the register.  If the period type is "monthly", then a new pay period starts on the first
     * day of the month.  If the period type is "semi-monthly", then a new pay period starts on the first day of the
     * month and the 15th day of the month.  If the period type is "bi-weekly", then a new pay period starts every 14
     * days.  If the period type is "weekly", then a new pay period starts every 7 days.
     * <p>
     * The algorithm is to first determine if the forecast transaction's planned date is greater than the start date of
     * the current pay period.  If it is not, then it can't be the start of a new pay period, so return false.
     * <p>
     * If it is greater than the start date of the current pay period, then determine if the forecast transaction's
     * planned date is greater than the end date of the current pay period, which depends on the type of pay period.  If
     * it is, then it is the start of a new pay period, so update the start date of the current pay period and return
     * true.  If it is not, then it is not the start of a new pay period, so return false.
     *
     * @param transaction The forecast transaction that may or may not start a new pay period.
     * @return True if the forecast transaction starts a new pay period, else false.
     * @throws SQLException    If there is a database error.
     * @throws EntityException If there is an error non-database error.
     */
    public boolean isNewPayPeriod(ForecastTransaction transaction) throws Exception {

        // If the current pay period start date is null, then determine the start date of the first pay period:
        if (currentPayPeriodStartDate == null) {
            currentPayPeriodStartDate = forecastUtilities.getFirstPayPeriodStartDate(transaction.getPlannedDate());
            currentPayPeriodEndDate = forecastUtilities.getPayPeriodEndDate(currentPayPeriodStartDate);
            return false;
        }

        if (currentPayPeriodEndDate == null) {
            throw new RuntimeException("Current pay period end date is null. This should never happen, please report " +
                    "this bug to the developers.");
        }

        // then if the transaction's date is greater than the end date of the current pay period:
         if (transaction.getPlannedDate().compareTo(currentPayPeriodEndDate) > 0) {

            // then it is the start of a new pay period, so update the start date of the current pay period to the
            // day after the end date of the current pay period and the end date of the current pay period to the end
            // date of the new pay period:
            Utility.copyDate(currentPayPeriodEndDate, currentPayPeriodStartDate);
            currentPayPeriodStartDate.add(Calendar.DATE, 1);
            currentPayPeriodEndDate = forecastUtilities.getPayPeriodEndDate(currentPayPeriodStartDate);
            return true;
        }
        else {
            // The transaction doesn't start a new pay period:
            return false;
        }
    }


    /*
     * Main Methods:
     */
    @Override
    public void renderReportFrontMatter() {
        String title = "UPCOMING ITEMS - " + forecast.getDescription();
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
    public void renderItemRow(Entity item) throws Exception {

        // Cast the entity passed in to what it really is. This is required because we are using generics:
        ForecastTransaction forecastTransaction = (ForecastTransaction) item;

        // Use a short version of the date to take less space:
        String date = Utility.calendarDateToMonthDayStringDate(forecastTransaction.getPlannedDate());

        // Format the payee field:
        String payee = forecastTransaction.getForecastItem().getPayee();
        payee = padStringWithTabs(payee, 7, iPhone11FontSizes);

        // Format the amount field:
        String amountString = formatRoundedDollarAmountField(forecastTransaction.getRemainingAmount(),
                2, iPhone11FontSizes);

        // Format the running balance field:
        String runningBalanceString = formatRoundedDollarAmountField(forecastTransaction.getRunningBalance(),
                2, iPhone11FontSizes);

        // If this is the start of a new pay period, then render the pay period header row:
        if (isNewPayPeriod(forecastTransaction)) {
            pw.println(PAY_PERIOD_HEADER + TAB +
                    formatRoundedDollarAmountField(
                            forecastTransaction.getRunningBalance() - forecastTransaction.getRemainingAmount(),
                            2,
                            iPhone11FontSizes));
        }

        // Output the forecast transaction line:
        pw.println(date + TAB + payee + amountString + TAB + runningBalanceString);

        // Indented under the payee, output the memo:
        String memo = forecastTransaction.getMemo();
        if (memo != null && !memo.isEmpty()) {
            pw.println(TAB + TAB + SPACE + SPACE + SPACE + memo);
        }
    }
}
