package com.hixon.financialApp.view.text;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.*;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;

import java.io.*;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;


/*
 * A report that shows the remaining amounts in each item in the current period of the specified forecast that is of
 * interest to a specified user, or all users:
 */
public class ItemsOfInterestReport extends ForecastReport {

    private final User user;

    public ItemsOfInterestReport(Forecast forecast, User user, List<Entity> items, File reportFile) throws FileNotFoundException {

        super(forecast, items, reportFile);
        this.user = user;
    }


    /*
     * Output the report:
     */
    @Override
    public void openReportOutput() throws FileNotFoundException, UnsupportedEncodingException, ViewException {
        boolean append = false;
        boolean autoFlush = true;
        String charset = "UTF-8";

        FileOutputStream fos = new FileOutputStream(reportFile, append);
        OutputStreamWriter osw = new OutputStreamWriter(fos, charset);
        BufferedWriter bw = new BufferedWriter(osw);
        pw = new PrintWriter(bw, autoFlush);
    }

    @Override
    public void renderReportFrontMatter() {
        pw.println("Items of Interest to " + user.getFirstName() + ":");
        pw.println("Item, Remaining, Budgeted/Spent");
        pw.println("------------------------------------");
    }

    @Override
    public void renderItemRow(Entity item) throws EntityException, ForecastException, SQLException, BudgetException,
            RegisterException {
        ForecastTransaction forecastTransaction = (ForecastTransaction) item;
        BudgetItem budgetItem = forecastTransaction.getForecastItem().getBudgetItem();
        String remainingAmountString = Utility.formatRoundedDollarAmount(-forecastTransaction.getRemainingAmount());
        Calendar periodEndDate = budgetItem.getFirstDateOnOrAfter(Calendar.getInstance());
        periodEndDate.add(Calendar.DATE, -1);
        remainingAmountString += " (" + periodEndDate.get(Calendar.DATE) + ")";
        double amountSpentMTD = budgetItem.getAmountSpentMTD();
        String totalAmountForMonth = Utility.formatRoundedDollarAmount(-amountSpentMTD);
        double amountBudgetedForMonth = Math.abs(budgetItem.getBudgetedAmountForCurrentMonth());
        String amountBudgetedForMonthString = Utility.formatRoundedDollarAmount(amountBudgetedForMonth);
        pw.println(forecastTransaction.getForecastItem().getPayee() + "  " + remainingAmountString + ", " +
                amountBudgetedForMonthString + "/" + totalAmountForMonth);
    }

    /**
     * Output the projected daily balances for today through the end of the current pay period and the balance at the
     * end of the month.
     *
     * @throws EntityException
     * @throws Exception
     * @throws BudgetException
     */
    @Override
    public void renderReportBackMatter() throws EntityException, Exception, BudgetException, RegisterException {

        // Calculate the start date and end date for the list of daily balances to be printed.  The start date is always
        // toady.  The end date is the last day of the current semi-monthly period:
        Calendar today = Calendar.getInstance();
        Calendar endDate = Calendar.getInstance();
        endDate.set(Calendar.DATE, endDate.getActualMaximum(Calendar.DATE));

        // Output the projected daily balances for today through the end of the current pay period:
        pw.println("\nProjected balances:");
        List<DailyBalance> dailyBalances = Forecast.getDailyBalanceList(forecast, today, endDate);
        int limit = (today.get(Calendar.DATE) < 15) ? 14 : endDate.get(Calendar.DATE);
        for (DailyBalance dailyBalance : dailyBalances
        ) {
            pw.println(Utility.calendarDateToMonthDayStringDate(dailyBalance.getDate()) + ":  " +
                    Utility.formatDollarAmount(dailyBalance.getBalance()));
            if (dailyBalance.getDate().get(Calendar.DATE) == limit) break;
        }

        // Output the projected balance at the end of the month.  If we already printed it as part of the list of daily
        // balances in the current semi-monthly period, then don't print it again.
        if (today.get(Calendar.DATE) < 15) {

            // The last daily balance in the list should be the last day of the month.  Output it's balance:
            pw.println("\nProjected balance at the end of the month:  " +
                    Utility.formatDollarAmount(dailyBalances.get(dailyBalances.size() - 1).getBalance()));

        }
    }
}
