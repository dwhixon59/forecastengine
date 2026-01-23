package com.hixon.financialApp.view.base;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastTransactionIterator;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.model.user.UserResource;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.text.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.*;

import static com.hixon.financialApp.model.forecast.Forecast.SignificantEvents.daysBelowMinimumBalance;
import static com.hixon.financialApp.utility.Utility.*;

/**
 * The AbstractForecastView class implements the {@link ForecastViewInt}, provides default implementations of its
 * methods where appropriate, and contains the logic common to the different methods of user interaction with the
 * forecast model.  For example all the code for interacting with the forecast database, for the purpose of rendering
 * a forecast for the user, is here.  Code specific to a particular type of rendering of the forecast (spreadsheet, html,
 * etc.) is contained in the concrete ForecastView classes in the packages associated with the particular type of view
 * (text, JSON, XML, spreadsheet, HTML, etc.).
 */
public abstract class AbstractForecastView extends AbstractView implements ForecastViewInt {

    /*
     * Fields:
     */
    protected Forecast forecast;
    protected boolean firstItem = true;
    protected boolean firstItemInMonth = true;
    protected int firstItemInMonthRowNum = 0;
    protected boolean firstMonth = true;

    // A map of forecast item ID's to a list of forecast transactions that are based on it and the rows in the
    // spreadsheet rendering where they are displayed:
    protected Map<UUID, List<RowTransactionPair>> forecastItemToRowsMap = new HashMap<>();


    /*
     * Getters and setters:
     */
    public Forecast getForecast() {
        return forecast;
    }

    public void setForecast(Forecast forecast) {
        this.forecast = forecast;
    }


    /*
     * Constructors:
     */

    public AbstractForecastView(Forecast forecast) {
        this.forecast = forecast;
    }

    /*
     * Helper methods:
     */
    protected abstract void openLongTermForecastOutput(String reportType) throws FileNotFoundException,
            UnsupportedEncodingException, ForecastException;

    protected abstract void renderLongTermForecastFrontMatter(String reportType) throws ForecastException;

    protected abstract void renderLongTermForecastMonthHeader(String reportType, Calendar plannedDate, double runningBalance)
            throws ForecastException;

    protected abstract int renderLongTermForecastTransaction(String reportType, ForecastTransaction forecastTransaction,
                                                             double credit, double debit)
            throws EntityException, SQLException, ForecastException, BudgetException;

    protected abstract void renderLongTermForecastBackMatter(String reportType) throws IOException, ForecastException;

    protected abstract void closeLongTermForecastOutput(String reportType) throws IOException, ForecastException;

    public abstract void editLongTermForecast() throws Exception;

    public abstract void closeForecastTransactionSource(String sourceName) throws ViewException;

    public abstract List<ForecastTransaction> openForecastTransactionSource(String sourceName) throws IOException,
            ControllerException, BudgetException;

    protected abstract TrackingItemsOfInterestReport getTrackingItemsOfInterestReport(User user, List<Entity> items,
                                                                                      File reportFile)
            throws FileNotFoundException;

    protected abstract UpcomingItemsOfInterestReport getUpcomingItemsOfInterestReport(User user, List<Entity> items,
                                                                                      File reportFile)
            throws FileNotFoundException;

    protected abstract OverdueItemsReport getOverdueItemsReport(Forecast forecast, List<Entity> items, File reportFile)
            throws FileNotFoundException;

    protected abstract UpcomingItemsReport getUpcomingItemsReport(Forecast forecast, List<Entity> items, File reportFile)
            throws FileNotFoundException;

    public abstract EnvelopeReport getEnvelopeReport(Forecast forecast, List<Entity> items, File reportFile)
            throws Exception;


    /*
     * Main methods:
     */
    @Override
    public boolean renderShortTermForecast(Forecast forecast) throws Exception, EntityException, BudgetException {

        this.forecast = forecast;

        getView().say("\n\nRender the short term forecast.");

        // To clue the user into what things to look for in the spreadsheet, run the forecast summary routine
        // requesting below minimum balance events:
        Forecast.SignificantEvents[] events = {daysBelowMinimumBalance};
        forecast.summarize();

        // Print out the starting and ending balances:
        getView().say("The starting balance is: " + Utility.formatDollarAmount(forecast.getStartingBalance()));
        getView().say("The ending balance is:   " + Utility.formatDollarAmount(forecast.getEndingBalance()));
        getView().say("The savings rate is:   " + Utility.formatDollarAmount(forecast.getEndingBalance() /
                forecast.getNumberOfMonths()) + " per month.");

        // TODO:  Render the short term forecast (whatever that means . . . .).
        System.out.println("The short term forecast was successfully rendered.");

        // and print out the significant events list:
        ForecastTransaction forecastTransaction = forecast.getFirstSignificantEvent();
        while (forecastTransaction != null) {
            getView().say("The balance on " + Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) +
                    " is $" + forecastTransaction.getRunningBalance());
            if (forecastTransaction.getRunningBalance() < forecast.getMinimumBalance()) {
                getView().say("Balance below minimum balance!");
            }
            forecastTransaction = forecastTransaction.getNextSignificantEvent();
        }
        return true;
    }


    @Override
    public boolean renderLongTermForecast(Forecast forecast) throws Exception, EntityException, BudgetException,
            RegisterException {

        this.forecast = forecast;

        // Get the first day of the forecast rendering:
        Calendar startDate = Forecast.getFirstNonZeroTransactionDate(forecast);

        // Get the starting balance.  Take if from the first register associated with the budget for now:
        List<Register> registers = forecast.getBudget().getRegisters();
        String reportType = registers.get(0).getReportType();
        double startingBalance = registers.get(0).getBalance();
        double runningBalance = startingBalance;

        // Variables to save significant events over the period of the forecast and the date on which they occurred:
        double lowestBalance = startingBalance;
        Calendar dateOfLowestBalance = null;
        double highestBalance = startingBalance;
        Calendar dateOfHighestBalance = null;
        double firstNegativeBalance = 0.0;
        Calendar dateOfFirstNegativBalance = null;
        double totalIncome = 0.0;
        double totalExpense = 0.0;
        double totalSavings = 0.0;
        double totalDebtExpense = 0.0;

        // Variables to hold the date of the first first-of-the-month and balance on that date.  This is used to
        // calculate whether the forecast is solvent over the period of the forecast, and also the required amount of
        // float to keep the forecast solvent.
        Calendar firstFirstOfMonth = getNextFirstOfMonth(Calendar.getInstance());
        double firstFirstOfMonthBalance = 0.0;

        // Open and initialize the forecast rendering output file:
        openLongTermForecastOutput(reportType);
        renderLongTermForecastFrontMatter(reportType);

        // Set all the running balances to zero in the database for THIS forecast only:
        ForecastTransaction.zeroRunningBalances(forecast);

        // Iterate over all the forecast transactions in chronological order beginning on the start date:
        ForecastTransactionIterator forecastTransactions =
                ForecastTransaction.getForecastTransactionsStartingOn(forecast, startDate);
        ForecastTransaction forecastTransaction = forecastTransactions.getNext();
        ForecastTransaction firstForecastTransaction = forecastTransaction;
        ForecastTransaction lastForecastTransaction = null;
        int currentMonth = -1;
        boolean noNegativeBalanceYet = true;
        while (forecastTransaction != null) {

            // If the month changed:
            if (forecastTransaction.getPlannedDate().get(Calendar.MONTH) != currentMonth) {

                // The first month is special because it cannot reference anything from the previous month, there are no
                // balances to carry forward, etc.  Turn off the first month indicator if we are no longer in the first
                // month:
                if (currentMonth != -1) {
                    firstMonth = false;
                }

                // Update the current month to the month of the current forecast transaction:
                currentMonth = forecastTransaction.getPlannedDate().get(Calendar.MONTH);

                // Write out a month header if this report type requires it:
                renderLongTermForecastMonthHeader(reportType, forecastTransaction.getPlannedDate(), runningBalance);

                // If this is the first first-of-the-month, then save off the balance on that date for reporting purposes:
                if (dateOnlyCompare(firstFirstOfMonth, forecastTransaction.getPlannedDate()) == 0) {
                    firstFirstOfMonthBalance = runningBalance;
                    lowestBalance = runningBalance;
                    dateOfLowestBalance = firstFirstOfMonth;
                }
            }

            // Update the running balance:
            runningBalance += forecastTransaction.getRemainingAmount();
            forecastTransaction.setRunningBalance(runningBalance);

            // Record the first negative balance and the date on which it occurred:
            if (runningBalance < 0 && noNegativeBalanceYet) {
                noNegativeBalanceYet = false;
                firstNegativeBalance = runningBalance;
                dateOfFirstNegativBalance = forecastTransaction.getPlannedDate();
            }

            // If we are within the forecast summary period (from the firstFirstOfMonth data to the end of the forecast),
            // then update the summary:
            if (dateOnlyCompare(forecastTransaction.getPlannedDate(), firstFirstOfMonth) >= 0) {
                if (runningBalance < lowestBalance) {
                    lowestBalance = runningBalance;
                    dateOfLowestBalance = forecastTransaction.getPlannedDate();
                }

                if (runningBalance > highestBalance) {
                    highestBalance = runningBalance;
                    dateOfHighestBalance = forecastTransaction.getPlannedDate();
                }

                // Record the total income and the date on which it occurred:
                if (forecastTransaction.getRemainingAmount() > 0) {
                    totalIncome += forecastTransaction.getRemainingAmount();
                }

                // Record the total expense:
                if (forecastTransaction.getRemainingAmount() < 0) {
                    totalExpense += forecastTransaction.getRemainingAmount();
                }

                // Record the total savings:
                if (forecastTransaction.getForecastItem().getPayee().equalsIgnoreCase("Savings")) {
                    totalSavings -= forecastTransaction.getRemainingAmount();
                }

                // Record the total debt expense:
                if (forecastTransaction.getForecastItem().getCategory().length()  >= 4) {
                    if (forecastTransaction.getForecastItem().getCategory().substring(0, 4).equalsIgnoreCase("Debt")) {
                        totalDebtExpense += forecastTransaction.getRemainingAmount();
                    }
                }
            }

            // Save off the forecast transaction with tne updated running balance:
            forecastTransaction.save(EntityInt.SaveMethod.UPDATE);

            double credit;
            double debit;
            if (Utility.doubleToInt(forecastTransaction.getRemainingAmount()) > 0) {
                credit = forecastTransaction.getRemainingAmount();
                debit = 0;
            } else {
                credit = 0;
                debit = -forecastTransaction.getRemainingAmount();
            }

            // Write out the forecast line:
            int rowNumber = renderLongTermForecastTransaction(reportType, forecastTransaction, credit, debit);

            // Save the row number and the forecast transaction in the map:
            RowTransactionPair rowTransactionPair = new RowTransactionPair(rowNumber, forecastTransaction);
            List<RowTransactionPair> rowTransactionPairList =
                    forecastItemToRowsMap.get(forecastTransaction.getForecastItem().getId());
            if (rowTransactionPairList == null) {
                rowTransactionPairList = new LinkedList<>();
            }
            rowTransactionPairList.add(rowTransactionPair);
            forecastItemToRowsMap.put(forecastTransaction.getForecastItem().getId(), rowTransactionPairList);

            // Move to the next transaction:
            lastForecastTransaction = forecastTransaction;
            forecastTransaction = forecastTransactions.getNext();
        }

        // Finish up and closeout the forecast rendering:
        renderLongTermForecastBackMatter(reportType);
        closeLongTermForecastOutput(reportType);

/*
      // requesting below minimum balance events:
      LongTermForecast.SignificantEvents[] events = {daysBelowMinimumBalance};
      longTermForecast.summarize(events);

      // and print out the significant events list:
      forecastTransaction = longTermForecast.getFirstSignificantEvent();
      while (forecastTransaction != null) {
         System.out.println("The balance on " + Utility.calendarDateToStringDate(forecastTransaction.getPlannedDate()) +
                 " is $" + forecastTransaction.getRunningBalance());
         if (forecastTransaction.getRunningBalance() < longTermForecast.getMinimumBalance()) {
            System.out.println("Balance below minimum balance!");
         }
         forecastTransaction = forecastTransaction.getNextSignificantEvent();
      }
*/

        // Print out the forecast summary:
        getView().say("\nForecast Summary:");

        // Check if we have any forecast transactions
        if (lastForecastTransaction == null) {
            getView().say("No forecast transactions found in the forecast period.");
            getView().say("The starting balance is: " + Utility.formatRoundedDollarAmount(startingBalance));
            return true;
        }

        // Display the forecast summary period:
        int numberOfMonthsInForecast = Utility.monthsBetweenDatesInclusive(firstFirstOfMonth,
                lastForecastTransaction.getPlannedDate());
        getView().say(new StringBuilder().append("The forecast summary period is the ").
                append(numberOfMonthsInForecast).append(" month period from ").
                append(Utility.calendarDateToStringDate(firstFirstOfMonth)).append(" to ").
                append(Utility.calendarDateToStringDate(
                        Utility.getLastDayOfMonth(lastForecastTransaction.getPlannedDate()))).append(".").toString());

        // Display the starting balance:
        getView().say(new StringBuilder().append("The starting balance is: ").
                append(Utility.formatRoundedDollarAmount(firstFirstOfMonthBalance)).toString());

        // Display the ending balance:
        getView().say(new StringBuilder().append("The ending balance is: ").
                append(Utility.formatRoundedDollarAmount(runningBalance)).append(".").toString());

        // Display the net change in balance:
        double netChangeInBalance = runningBalance - firstFirstOfMonthBalance;
        getView().say(new StringBuilder().append("The net change in balance is: ").
                append(Utility.formatRoundedDollarAmount(netChangeInBalance)).append(".").toString());

        // Display the average monthly change in balance:
        double rateOfChangeInBalance = netChangeInBalance / numberOfMonthsInForecast;
        if (netChangeInBalance > 0) {
            getView().say(new StringBuilder().append("The average accumulation rate is: ").
                    append(Utility.formatRoundedDollarAmount(rateOfChangeInBalance)).
                    append(" per month.").toString());
        } else {
            getView().say(new StringBuilder().append("The average depletion rate is: ").
                    append(Utility.formatRoundedDollarAmount(rateOfChangeInBalance)).append(".").toString());
        }

        // Display the highest balance and the date on which it occurred:
        getView().say(new StringBuilder().append("The highest balance is: ").
                append(Utility.formatRoundedDollarAmount(highestBalance)).append(" on ").
                append(Utility.calendarDateToStringDate(dateOfHighestBalance)).append(".").toString());

        // Display the lowest balance and the date on which it occurred
        getView().say(new StringBuilder().append("The lowest balance is: ").
                append(Utility.formatRoundedDollarAmount(lowestBalance)).append(" on ").
                append(Utility.calendarDateToStringDate(dateOfLowestBalance)).append(".").toString());

        // If there are one or more negative balances, display the first negative balance and the date on which it
        // occurred:
        if (firstNegativeBalance < 0) {
            getView().say(new StringBuilder().append("The first negative balance is: ").
                    append(Utility.formatRoundedDollarAmount(firstNegativeBalance)).append(" on ").
                    append(Utility.calendarDateToStringDate(dateOfFirstNegativBalance)).append(".").toString());
        }

        // Display the total amount of income:
        getView().say(new StringBuilder().append("The total amount of income is: ").
                append(Utility.formatRoundedDollarAmount(totalIncome)).toString());

        // Display the total amount of expenses:
        getView().say(new StringBuilder().append("The total amount of expense is: ").
                append(Utility.formatRoundedDollarAmount(totalExpense)).toString());

        // Display the total amount of savings:
        getView().say(new StringBuilder().append("The total amount of savings is: ").
                append(Utility.formatRoundedDollarAmount(totalSavings)).toString());

        // Display the total amount of debt expense:
        getView().say(new StringBuilder().append("The total amount of debt expense is: ").
                append(Utility.formatRoundedDollarAmount(-totalDebtExpense)).toString());

        // Display the monthly amount of debt expense:
        getView().say(new StringBuilder().append("The monthly amount of debt expense is: ").
                append(Utility.formatRoundedDollarAmount(-totalDebtExpense / numberOfMonthsInForecast)).toString());

        // Display the debt to expense ratio:
        double debtToExpenseRatio = totalDebtExpense / totalExpense;
        double debtToIncomeRatio = -totalDebtExpense / totalIncome;
        getView().say(new StringBuilder().append("Debt expense comprises ").append(Math.round(debtToIncomeRatio * 100)).
                append("% of total income and ").append(Math.round(debtToExpenseRatio * 100)).
                append("% of total expense.").toString());

        // Print out the forecast analysis:
        getView().say("\nForecast Analysis:");

        // If the forecast is out of balance:
        double outOfBalanceAmount = runningBalance - firstFirstOfMonthBalance;
        double outOfBalanceMonthlyAmount = outOfBalanceAmount / numberOfMonthsInForecast;
        if (outOfBalanceAmount < 0) {

            getView().say(new StringBuilder().append("The forecast is out of balance by ").
                    append(Utility.formatRoundedDollarAmount(-outOfBalanceAmount)).
                    append(".").toString());

            // Tell the user how much they need to reduce spending or increase income to get the forecast back in
            // balance on a monthly basis:
            getView().say(new StringBuilder().append("You need to reduce spending or increase income by ").
                    append(Utility.formatRoundedDollarAmount(-outOfBalanceMonthlyAmount) + " per month.").toString());
        } else {
            getView().say("The forecast is balanced.  No action is required.");
        }

        // If there are any negative balances, then the float is insufficient.  Calculate the required float and let
        // the user know how much they need to deposit to fix the float issue:
        if (lowestBalance < 0) {

            // Recompute the required float by recomputing the running balances assuming that the user fixes the
            // out-of-balance issue:
            forecastTransactions = ForecastTransaction.getForecastTransactionsStartingOn(forecast, firstFirstOfMonth);
            forecastTransaction = forecastTransactions.getNext();
            runningBalance = 0;
            lowestBalance = 0;
            currentMonth = -1;
            while (forecastTransaction != null) {
                if (forecastTransaction.getPlannedDate().get(Calendar.MONTH) != currentMonth) {
                    runningBalance -= outOfBalanceMonthlyAmount;
                    currentMonth = forecastTransaction.getPlannedDate().get(Calendar.MONTH);
                }
                runningBalance += forecastTransaction.getRemainingAmount();
                if (runningBalance < lowestBalance) {
                    lowestBalance = runningBalance;
                    dateOfLowestBalance = forecastTransaction.getPlannedDate();
                }
                forecastTransaction = forecastTransactions.getNext();
            }
            getView().say(new StringBuilder().append("The required float on the date of the lowest balance (").
                    append(Utility.calendarDateToStringDate(dateOfLowestBalance)).append(") taking into account the " +
                            "effect of fixing the out-of-balance issue is ").
                    append(Utility.formatRoundedDollarAmount(-lowestBalance)).toString());

            // Tell the user how much they need to deposit to fix the float issue:
            if (lowestBalance + firstFirstOfMonthBalance < 0) {
                getView().say(new StringBuilder().append("To ensure you have no negative balances, you need to deposit ").
                        append(Utility.formatRoundedDollarAmount(-lowestBalance - firstFirstOfMonthBalance)).
                        append(" to the ").append(forecast.getBudget().getRegisters().get(0).getName()).append(" account.").
                        toString());
            } else if ((lowestBalance + firstFirstOfMonthBalance) > -1 && (lowestBalance + firstFirstOfMonthBalance < 1)) {
                getView().say(new StringBuilder().append("You have sufficient float to ensure no negative balances.").
                        append(" in the ").append(forecast.getBudget().getRegisters().get(0).getName()).
                        append(" account.").toString());
            } else {
                getView().say(new StringBuilder().append("You have excess float in the amount of ").
                        append(Utility.formatRoundedDollarAmount(lowestBalance + firstFirstOfMonthBalance)).
                        append(" in the ").append(forecast.getBudget().getRegisters().get(0).getName()).
                        append(" account.").toString());
            }
        }

        // Update the forecast's lastRenderedDate to track when we rendered the file
        forecast.setLastRenderedDate(Calendar.getInstance());
        try {
            forecast.save(EntityInt.SaveMethod.UPDATE);
        } catch (Exception e) {
            getView().say("Warning: Could not save lastRenderedDate: " + e.getMessage());
        }

        return true;
    }

    @Override
    public List<UserResource> renderItemsOfInterestReport() throws EntityException, Exception, BudgetException,
            ViewException, RegisterException {

        // Create a holder for the individual user reports:
        List<UserResource> reports = new ArrayList<>();

        // Get a list of users:
        List<User> users = User.getAllUsers();

        // For each user:
        for (User user : users
        ) {
            // Render an items of interest report for the current user:
            UserResource userResource = renderItemsOfInterestReport(user);
            if (userResource != null) {
                reports.add(userResource);
            }
        }
        return reports;
    }

    @Override
    public UserResource renderItemsOfInterestReport(User user) throws EntityException, Exception, BudgetException,
            ViewException, RegisterException {

        UserResource userResource = null;
        File itemsOfInterestReportFile = File.createTempFile("ItemsOfInterestReport_" + user.getFirstName() + "_",
                ".txt");
        if (renderItemsOfInterestReport(user, itemsOfInterestReportFile)) {
            userResource = new UserResource(user, UserResource.ResourceType.ItemsOfInterestReport,
                    itemsOfInterestReportFile);
        } else {
            itemsOfInterestReportFile.delete();
        }
        return userResource;
    }

    @Override
    public boolean renderItemsOfInterestReport(User user, File file) throws EntityException, Exception, BudgetException,
            ViewException, RegisterException {

        // Get a set of the items of interest of the current user:
        List<Entity> items = Collections.unmodifiableList(ForecastTransaction.getTrackingForecastTransactionsOfInterest(user));

        // Render an items of interest report for those items:
        boolean result = false;
        if (items.size() > 0) {
            TrackingItemsOfInterestReport trackingReport = getTrackingItemsOfInterestReport(user, items, file);
            ReportRenderer<TrackingItemsOfInterestReport> renderer = new ReportRenderer<>(trackingReport);
            renderer.renderReport();
            result = true;
        }

        if (result) {
            result = false;

            // Get a set of the items of interest of the current user:
            items = Collections.unmodifiableList(ForecastTransaction.getUpcomingForecastTransactionsOfInterest(user));

            // Render an items of interest report for those items:
            result = false;
            if (items.size() > 0) {
                UpcomingItemsOfInterestReport upcomingReport = getUpcomingItemsOfInterestReport(user, items, file);
                ReportRenderer<UpcomingItemsOfInterestReport> renderer = new ReportRenderer<>(upcomingReport);
                renderer.renderReport();
                result = true;
            }
        }
        return result;
    }


    @Override
    public List<UserResource> renderUpcomingItemsReport(Forecast forecast) throws EntityException, ViewException, Exception, BudgetException, RegisterException {

        // Create a holder for the individual user reports:
        List<UserResource> reports = new ArrayList<>();

        // Get a list of users:
        List<User> users = User.getAllUsers();

        // For each user:
        for (User user : users) {
            // Render an upcoming items report for the current user:
            UserResource userResource = renderUpcomingItemsReport(forecast, user);
            reports.add(userResource);
        }
        return reports;
    }

    @Override
    public UserResource renderUpcomingItemsReport(Forecast forecast, User user) throws EntityException, ViewException,
            Exception, BudgetException, RegisterException {

        UserResource userResource = null;
        File upcomingItemsReportFile = File.createTempFile("UpcomingItemsReport_" + user.getFirstName() + "_",
                ".txt");
        if (renderUpcomingItemsReport(forecast, user, upcomingItemsReportFile)) {
            userResource = new UserResource(user, UserResource.ResourceType.upcomingItemsReport,
                    upcomingItemsReportFile);
        } else {
            upcomingItemsReportFile.delete();
        }
        return userResource;
    }

    @Override
    public boolean renderUpcomingItemsReport(Forecast forecast, User user, File file) throws EntityException, ViewException,
            Exception, BudgetException, RegisterException {

        // Calculate the end date: always the end of the month after the current month
        Calendar endDate = Calendar.getInstance();
        endDate.add(Calendar.MONTH, 1);  // Move to next month
        endDate.set(Calendar.DATE, endDate.getActualMaximum(Calendar.DATE));  // Set to last day of that month

        // Get a list of upcoming items through the end date:
        List<Entity> items = Collections.unmodifiableList(ForecastTransaction.getItemsUpToDate(forecast, endDate));

        // Render an upcoming items report for those items:
        boolean result = false;
        if (!items.isEmpty()) {
            UpcomingItemsReport report = getUpcomingItemsReport(forecast, items, file);
            ReportRenderer<UpcomingItemsReport> renderer = new ReportRenderer<>(report);
            renderer.renderReport();
            result = true;
        }

        return result;
    }


    @Override
    public List<UserResource> renderOverdueItemsReport(Forecast forecast) throws EntityException, ViewException,
            Exception, BudgetException, RegisterException {

        // Create a holder for the individual user reports:
        List<UserResource> reports = new ArrayList<>();

        // Get a list of users:
        List<User> users = User.getAllUsers();

        // For each user:
        for (User user : users) {
            // Render an items of interest report for the current user:
            UserResource userResource = renderOverdueItemsReport(forecast, user);
            reports.add(userResource);
        }
        return reports;
    }

    @Override
    public UserResource renderOverdueItemsReport(Forecast forecast, User user) throws EntityException, ViewException,
            Exception, BudgetException, RegisterException {

        UserResource userResource = null;
        File overdueItemsReportFile = File.createTempFile("OverdueItemsReport" + user.getFirstName() + "_",
                ".txt");
        if (renderOverdueItemsReport(forecast, user, overdueItemsReportFile)) {
            userResource = new UserResource(user, UserResource.ResourceType.overdueItemsReport,
                    overdueItemsReportFile);
        } else {
            overdueItemsReportFile.delete();
        }
        return userResource;
    }

    @Override
    public boolean renderOverdueItemsReport(Forecast forecast, User user, File file) throws EntityException,
            ViewException, Exception,
            RegisterException, BudgetException {

        // Get a list of the overdue items:
        List<Entity> items = Collections.unmodifiableList(ForecastTransaction.getOverdueItems(user, forecast));

        // Render an overdue items report for those items:
        boolean result = false;
        if (!items.isEmpty()) {
            OverdueItemsReport report = getOverdueItemsReport(forecast, items, file);
            ReportRenderer<OverdueItemsReport> renderer = new ReportRenderer<>(report);
            renderer.renderReport();
            result = true;
        }
        return result;
    }

    @Override
    public List<UserResource> renderEnvelopeReport(Forecast forecast) throws EntityException, ViewException,
            Exception, BudgetException, RegisterException {

        // Create a holder for the individual user reports:
        List<UserResource> reports = new ArrayList<>();

        // Get a list of users:
        List<User> users = User.getAllUsers();

        // For each user:
        for (User user : users) {
            // Render an items of interest report for the current user:
            UserResource userResource = renderEnvelopeReport(forecast, user);
            reports.add(userResource);
        }
        return reports;
    }

    @Override
    public UserResource renderEnvelopeReport(Forecast forecast, User user) throws EntityException, ViewException,
            Exception, BudgetException, RegisterException {

        UserResource userResource = null;
        File envelopeReportFile = File.createTempFile("EnvelopeReport" + user.getFirstName() + "_",
                ".txt");
        if (renderEnvelopeReport(forecast, user, envelopeReportFile)) {
            userResource = new UserResource(user, UserResource.ResourceType.EnvelopeReport,
                    envelopeReportFile);
        } else {
            envelopeReportFile.delete();
        }
        return userResource;
    }

    @Override
    public boolean renderEnvelopeReport(Forecast forecast, User user, File file) throws Exception {

        // Create an envelope report object for the forecast:
        EnvelopeReport envelopeReport = new EnvelopeReport(forecast, Calendar.getInstance(), file);

        // Create a renderer for the report:
        ReportRenderer<EnvelopeReport> renderer = new ReportRenderer<>(envelopeReport);

        // Render the envelope report:
        return renderer.renderReport();
    }
} // End class AbstractForecastView.
