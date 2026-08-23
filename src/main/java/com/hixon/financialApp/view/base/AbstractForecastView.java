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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
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

    private static final class MonthlyCashFlow {
        private final String label;
        private double income;
        private double expense;
        private double endingBalance;

        private MonthlyCashFlow(String label) {
            this.label = label;
            this.income = 0.0;
            this.expense = 0.0;
            this.endingBalance = 0.0;
        }

        private double getNet() {
            return roundCurrency(income - expense);
        }
    }

    static double roundCurrency(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    static String monthKey(Calendar date) {
        return new SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(date.getTime());
    }

    static String monthLabel(Calendar date) {
        return new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(date.getTime());
    }

    static Calendar copyCalendar(Calendar date) {
        return date == null ? null : (Calendar) date.clone();
    }

    static String normalizedLabel(String rawLabel, String fallback) {
        if (rawLabel == null || rawLabel.trim().isEmpty()) {
            return fallback;
        }
        return rawLabel.trim();
    }

    static double monthsOfRunway(double startingBalance, double monthlyNet) {
        if (monthlyNet >= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return roundCurrency(startingBalance / -monthlyNet);
    }

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

    /**
     * Returns the filename of the long term forecast output file.
     * Used to read the file's lastModified timestamp after rendering.
     *
     * @return The full path to the long term forecast output file, or null if not set.
     */
    protected abstract String getLongTermForecastFilename();

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

        // Get the starting balance from the register this forecast belongs to:
        Register forecastRegister = forecast.getRegister();
        if (forecastRegister == null) {
            throw new ForecastException("Forecast '" + forecast.getDescription() + "' does not belong to a " +
                    "register, so there is no balance to start the long term forecast from.");
        }
        String reportType = forecastRegister.getReportType();
        double startingBalance = forecastRegister.getBalance();
        double runningBalance = roundCurrency(startingBalance);

        // Variables to save significant events over the period of the forecast and the date on which they occurred:
        double lowestBalance = runningBalance;
        Calendar dateOfLowestBalance = null;
        double highestBalance = runningBalance;
        Calendar dateOfHighestBalance = null;
        double firstNegativeBalance = 0.0;
        Calendar dateOfFirstNegativBalance = null;

        // The first negative balance tracked above is the first one anywhere in the rendering, which may fall before
        // the summary period begins.  The timeline needs the first deficit INSIDE the summary period as well, because
        // a balance that went negative earlier and has not recovered is still negative once the period opens:
        double firstPeriodNegativeBalance = 0.0;
        Calendar dateOfFirstPeriodNegativeBalance = null;
        boolean periodOpensInDeficit = false;

        double totalIncome = 0.0;
        double totalExpense = 0.0;
        double totalSavings = 0.0;
        double totalDebtExpense = 0.0;

        // Track richer summary analytics for actionable reporting.
        Map<String, MonthlyCashFlow> monthlyCashFlowMap = new TreeMap<>();
        Map<String, Double> expenseByCategory = new HashMap<>();

        // The payees making up each category, keyed by category then by payee.  Only payees with an expense in the
        // summary period appear, so a category breaks down into exactly the payees that drove it:
        Map<String, Map<String, Double>> expenseByCategoryAndPayee = new HashMap<>();

        Map<String, Double> incomeBySource = new HashMap<>();

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

                    // The balance carried into the summary period seeds the period low point on the line above, so it
                    // has to count as a balance within the period here as well.  Otherwise a period that opens in the
                    // red gets reported as non-negative while the low point names that very balance:
                    if (runningBalance < 0) {
                        periodOpensInDeficit = true;
                        firstPeriodNegativeBalance = runningBalance;
                        dateOfFirstPeriodNegativeBalance = copyCalendar(firstFirstOfMonth);
                    }
                }
            }

            // Update the running balance:
            double remainingAmount = roundCurrency(forecastTransaction.getRemainingAmount());
            runningBalance = roundCurrency(runningBalance + remainingAmount);
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
                String periodKey = monthKey(forecastTransaction.getPlannedDate());
                String periodLabel = monthLabel(forecastTransaction.getPlannedDate());
                MonthlyCashFlow monthSummary = monthlyCashFlowMap.computeIfAbsent(periodKey,
                        ignored -> new MonthlyCashFlow(periodLabel));
                monthSummary.endingBalance = runningBalance;

                // Record the first negative balance within the summary period and the date it occurred on:
                if (runningBalance < 0 && dateOfFirstPeriodNegativeBalance == null) {
                    firstPeriodNegativeBalance = runningBalance;
                    dateOfFirstPeriodNegativeBalance = forecastTransaction.getPlannedDate();
                }

                if (runningBalance < lowestBalance) {
                    lowestBalance = runningBalance;
                    dateOfLowestBalance = forecastTransaction.getPlannedDate();
                }

                if (runningBalance > highestBalance) {
                    highestBalance = runningBalance;
                    dateOfHighestBalance = forecastTransaction.getPlannedDate();
                }

                // Record income totals and source breakdown.
                if (remainingAmount > 0) {
                    totalIncome = roundCurrency(totalIncome + remainingAmount);
                    monthSummary.income = roundCurrency(monthSummary.income + remainingAmount);
                    String source = normalizedLabel(forecastTransaction.getForecastItem().getPayee(), "Unspecified income source");
                    double updatedSource = roundCurrency(incomeBySource.getOrDefault(source, 0.0) + remainingAmount);
                    incomeBySource.put(source, updatedSource);
                }

                // Record expense totals and category breakdown (expense is shown as positive in breakdowns).
                if (remainingAmount < 0) {
                    totalExpense = roundCurrency(totalExpense + remainingAmount);
                    double expenseAmount = roundCurrency(-remainingAmount);
                    monthSummary.expense = roundCurrency(monthSummary.expense + expenseAmount);
                    String category = normalizedLabel(forecastTransaction.getForecastItem().getCategory(), "Uncategorized expense");
                    double updatedCategory = roundCurrency(expenseByCategory.getOrDefault(category, 0.0) + expenseAmount);
                    expenseByCategory.put(category, updatedCategory);

                    // Record the payee within the category as well, so a category can be traced to what drove it:
                    String expensePayee = normalizedLabel(forecastTransaction.getForecastItem().getPayee(),
                            "Unspecified payee");
                    Map<String, Double> payeeExpenses =
                            expenseByCategoryAndPayee.computeIfAbsent(category, ignored -> new HashMap<>());
                    payeeExpenses.put(expensePayee,
                            roundCurrency(payeeExpenses.getOrDefault(expensePayee, 0.0) + expenseAmount));
                }

                // Record the total savings:
                if (forecastTransaction.getForecastItem().getPayee().equalsIgnoreCase("Savings")) {
                    totalSavings = roundCurrency(totalSavings - remainingAmount);
                }

                // Record the total debt expense:
                if (forecastTransaction.getForecastItem().getCategory().length() >= 4) {
                    if (forecastTransaction.getForecastItem().getCategory().substring(0, 4).equalsIgnoreCase("Debt")) {
                        totalDebtExpense = roundCurrency(totalDebtExpense + remainingAmount);
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
        double netChangeInBalance = roundCurrency(runningBalance - firstFirstOfMonthBalance);
        getView().say(new StringBuilder().append("The net change in balance is: ").
                append(Utility.formatRoundedDollarAmount(netChangeInBalance)).append(".").toString());

        // Display the average monthly change in balance:
        double rateOfChangeInBalance = roundCurrency(netChangeInBalance / numberOfMonthsInForecast);
        if (netChangeInBalance > 0) {
            getView().say(new StringBuilder().append("The average accumulation rate is: ").
                    append(Utility.formatRoundedDollarAmount(rateOfChangeInBalance)).
                    append(" per month.").toString());
        } else {
            getView().say(new StringBuilder().append("The average depletion rate is: ").
                    append(Utility.formatRoundedDollarAmount(rateOfChangeInBalance)).append(" per month.").toString());
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
            Calendar today = Calendar.getInstance();
            if (dateOnlyCompare(dateOfFirstNegativBalance, today) < 0) {
                // The first negative balance date is in the past — it already occurred
                getView().say(new StringBuilder().append("The first negative balance was: ")
                        .append(Utility.formatRoundedDollarAmount(firstNegativeBalance)).append(" on ")
                        .append(Utility.calendarDateToStringDate(dateOfFirstNegativBalance))
                        .append(" (already occurred).").toString());
            } else {
                getView().say(new StringBuilder().append("The first negative balance is: ").
                        append(Utility.formatRoundedDollarAmount(firstNegativeBalance)).append(" on ").
                        append(Utility.calendarDateToStringDate(dateOfFirstNegativBalance)).append(".").toString());
            }
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
        double debtToExpenseRatio = totalExpense == 0 ? 0 : totalDebtExpense / totalExpense;
        double debtToIncomeRatio = totalIncome == 0 ? 0 : -totalDebtExpense / totalIncome;
        getView().say(new StringBuilder().append("Debt expense comprises ").append(Math.round(debtToIncomeRatio * 100)).
                append("% of total income and ").append(Math.round(debtToExpenseRatio * 100)).
                append("% of total expense.").toString());

        // Improvement 1: monthly cash-flow breakdown.
        getView().say("\nMonthly Cash Flow Breakdown:");
        monthlyCashFlowMap.forEach((ignored, monthlyCashFlow) -> {
            String trend;
            if (monthlyCashFlow.getNet() > 0) {
                trend = "positive";
            } else if (monthlyCashFlow.getNet() < 0) {
                trend = "deficit";
            } else {
                trend = "break-even";
            }
            getView().say(new StringBuilder().append("  ").append(monthlyCashFlow.label).append(": net ").
                    append(Utility.formatRoundedDollarAmount(monthlyCashFlow.getNet())).append(" (").append(trend).
                    append(") | income ").append(Utility.formatRoundedDollarAmount(monthlyCashFlow.income)).
                    append(" | expense ").append(Utility.formatRoundedDollarAmount(-monthlyCashFlow.expense)).
                    append(" | ending balance ").append(Utility.formatRoundedDollarAmount(monthlyCashFlow.endingBalance)).
                    toString());
        });

        // Improvement 2: expense breakdown by category, and within each category by payee.
        getView().say("\nExpense Breakdown by Category and Payee:");
        final double finalTotalExpense = totalExpense;
        expenseByCategory.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .forEach(entry -> {
                    double categoryTotal = entry.getValue();
                    double percentOfTotal = finalTotalExpense == 0 ? 0 :
                            roundCurrency((categoryTotal / -finalTotalExpense) * 100);
                    double monthlyAverage = roundCurrency(categoryTotal / numberOfMonthsInForecast);
                    getView().say(new StringBuilder().append("  - ").append(entry.getKey()).append(": ").
                            append(Utility.formatRoundedDollarAmount(-categoryTotal)).append(" total | ").
                            append(Utility.formatRoundedDollarAmount(-monthlyAverage)).append("/month | ").
                            append(Math.round(percentOfTotal)).append("% of expense").toString());

                    // and the payees that make up the category, largest first.  The share is expressed against the
                    // category rather than against total expense, because the question a payee line answers is which
                    // payee is driving the category above it:
                    expenseByCategoryAndPayee.getOrDefault(entry.getKey(), Map.of()).entrySet().stream()
                            .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                            .forEach(payeeEntry -> {
                                double percentOfCategory = categoryTotal == 0 ? 0 :
                                        roundCurrency((payeeEntry.getValue() / categoryTotal) * 100);
                                double payeeMonthlyAverage =
                                        roundCurrency(payeeEntry.getValue() / numberOfMonthsInForecast);
                                getView().say(new StringBuilder().append("      - ").append(payeeEntry.getKey()).
                                        append(": ").
                                        append(Utility.formatRoundedDollarAmount(-payeeEntry.getValue())).
                                        append(" total | ").
                                        append(Utility.formatRoundedDollarAmount(-payeeMonthlyAverage)).
                                        append("/month | ").
                                        append(Math.round(percentOfCategory)).append("% of category").toString());
                            });
                });

        // Improvement 3: income breakdown by source.
        getView().say("\nIncome Breakdown by Source:");
        final double finalTotalIncome = totalIncome;
        incomeBySource.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .forEach(entry -> {
                    double percentOfTotal = finalTotalIncome == 0 ? 0 :
                            roundCurrency((entry.getValue() / finalTotalIncome) * 100);
                    double monthlyAverage = roundCurrency(entry.getValue() / numberOfMonthsInForecast);
                    getView().say(new StringBuilder().append("  - ").append(entry.getKey()).append(": ").
                            append(Utility.formatRoundedDollarAmount(entry.getValue())).append(" total | ").
                            append(Utility.formatRoundedDollarAmount(monthlyAverage)).append("/month | ").
                            append(Math.round(percentOfTotal)).append("% of income").toString());
                });

        // Print out the forecast analysis:
        getView().say("\nForecast Analysis:");

        // If the forecast is out of balance:
        double outOfBalanceAmount = roundCurrency(runningBalance - firstFirstOfMonthBalance);
        double outOfBalanceMonthlyAmount = roundCurrency(outOfBalanceAmount / numberOfMonthsInForecast);
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

        // Save the pre-adjustment low-balance event for risk and timeline reporting.
        double periodLowestBalance = lowestBalance;
        Calendar dateOfPeriodLowestBalance = copyCalendar(dateOfLowestBalance);

        double requiredFloat = 0.0;
        double requiredDeposit = 0.0;
        double excessFloat = 0.0;

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
            requiredFloat = roundCurrency(-lowestBalance);

            // Tell the user how much they need to deposit to fix the float issue:
            if (lowestBalance + firstFirstOfMonthBalance < 0) {
                requiredDeposit = roundCurrency(-lowestBalance - firstFirstOfMonthBalance);
                getView().say(new StringBuilder().append("To ensure you have no negative balances, you need to deposit ").
                        append(Utility.formatRoundedDollarAmount(requiredDeposit)).
                        append(" to the ").append(forecastRegister.getName()).append(" account.").
                        toString());
            } else if ((lowestBalance + firstFirstOfMonthBalance) > -1 && (lowestBalance + firstFirstOfMonthBalance < 1)) {
                getView().say(new StringBuilder().append("You have sufficient float to ensure no negative balances.").
                        append(" in the ").append(forecastRegister.getName()).
                        append(" account.").toString());
            } else {
                excessFloat = roundCurrency(lowestBalance + firstFirstOfMonthBalance);
                getView().say(new StringBuilder().append("You have excess float in the amount of ").
                        append(Utility.formatRoundedDollarAmount(excessFloat)).
                        append(" in the ").append(forecastRegister.getName()).
                        append(" account.").toString());
            }
        }

        // Improvement 4: explicit risk warnings.
        getView().say("\nRisk Warnings:");
        Calendar today = Calendar.getInstance();
        if (firstNegativeBalance < 0 && dateOfFirstNegativBalance != null) {
            if (dateOnlyCompare(dateOfFirstNegativBalance, today) < 0) {
                getView().say(new StringBuilder().append("  - Note: The account went negative on ")
                        .append(Utility.calendarDateToStringDate(dateOfFirstNegativBalance))
                        .append(" at ").append(Utility.formatRoundedDollarAmount(firstNegativeBalance))
                        .append(" — this is in the past.").toString());
            } else {
                getView().say(new StringBuilder().append("  - Critical: The account first goes negative on ").
                        append(Utility.calendarDateToStringDate(dateOfFirstNegativBalance)).append(" at ").
                        append(Utility.formatRoundedDollarAmount(firstNegativeBalance)).append(".").toString());
            }
        } else {
            getView().say("  - No negative balances are forecast in this period.");
        }
        if (dateOfPeriodLowestBalance != null) {
            getView().say(new StringBuilder().append("  - Lowest projected balance is ").
                    append(Utility.formatRoundedDollarAmount(periodLowestBalance)).append(" on ").
                    append(Utility.calendarDateToStringDate(dateOfPeriodLowestBalance)).append(".").toString());
        }
        if (requiredFloat > 0) {
            getView().say(new StringBuilder().append("  - Required float to remain solvent is at least ").
                    append(Utility.formatRoundedDollarAmount(requiredFloat)).append(".").toString());
        }
        if (excessFloat > 0 && requiredFloat > excessFloat) {
            getView().say(new StringBuilder().append("  - Current excess float is ").
                    append(Utility.formatRoundedDollarAmount(excessFloat)).append(", which is insufficient for the worst-case month.").
                    toString());
        }

        // Improvement 5: actionable recommendations.
        getView().say("\nActionable Recommendations:");
        double monthlyGap = outOfBalanceMonthlyAmount < 0 ? roundCurrency(-outOfBalanceMonthlyAmount) : 0.0;
        if (monthlyGap > 0) {
            getView().say(new StringBuilder().append("  Option A: Reduce monthly spending by at least ").
                    append(Utility.formatRoundedDollarAmount(monthlyGap)).append(".").toString());
            expenseByCategory.entrySet().stream()
                    .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                    .limit(3)
                    .forEach(entry -> {
                        double monthlyAverage = roundCurrency(entry.getValue() / numberOfMonthsInForecast);
                        double suggestedCut = roundCurrency(monthlyAverage * 0.25);
                        getView().say(new StringBuilder().append("    * ").append(entry.getKey()).append(": current avg ").
                                append(Utility.formatRoundedDollarAmount(-monthlyAverage)).append("/month, 25% cut saves about ").
                                append(Utility.formatRoundedDollarAmount(-suggestedCut)).append("/month.").toString());
                    });
            getView().say(new StringBuilder().append("  Option B: Increase monthly income by at least ").
                    append(Utility.formatRoundedDollarAmount(monthlyGap)).append(".").toString());
            getView().say(new StringBuilder().append("  Option C: Combine smaller changes (about ").
                    append(Utility.formatRoundedDollarAmount(monthlyGap / 2)).
                    append(" spending reduction + ").append(Utility.formatRoundedDollarAmount(monthlyGap / 2)).
                    append(" income increase per month).").toString());
        } else {
            getView().say("  - The forecast is already in balance; maintain current plan and monitor category drift.");
        }
        if (requiredDeposit > 0) {
            getView().say(new StringBuilder().append("  - One-time float action: deposit ").
                    append(Utility.formatRoundedDollarAmount(requiredDeposit)).append(" to prevent temporary overdrafts.").toString());
        }

        // Improvement 6: financial runway analysis.
        getView().say("\nFinancial Runway Analysis:");
        double monthlyIncomeAverage = totalIncome == 0 ? 0 : roundCurrency(totalIncome / numberOfMonthsInForecast);
        double monthlyExpenseAverage = totalExpense == 0 ? 0 : roundCurrency(-totalExpense / numberOfMonthsInForecast);
        double baselineMonthlyNet = roundCurrency(monthlyIncomeAverage - monthlyExpenseAverage);
        double baselineRunway = monthsOfRunway(firstFirstOfMonthBalance, baselineMonthlyNet);
        if (Double.isInfinite(baselineRunway)) {
            getView().say("  - Current monthly net is non-negative, so runway is not constrained by burn rate.");
        } else {
            getView().say(new StringBuilder().append("  - At the current net burn of ").
                    append(Utility.formatRoundedDollarAmount(baselineMonthlyNet)).append("/month, runway is about ").
                    append(Math.round(baselineRunway)).append(" months.").toString());
        }

        List<Map.Entry<String, Double>> topIncomeSources = incomeBySource.entrySet().stream()
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .limit(2)
                .toList();
        for (Map.Entry<String, Double> source : topIncomeSources) {
            double sourceMonthly = roundCurrency(source.getValue() / numberOfMonthsInForecast);
            double netIfRemoved = roundCurrency(baselineMonthlyNet - sourceMonthly);
            double runwayIfRemoved = monthsOfRunway(firstFirstOfMonthBalance, netIfRemoved);
            if (Double.isInfinite(runwayIfRemoved)) {
                getView().say(new StringBuilder().append("  - If '").append(source.getKey()).
                        append("' stopped, the forecast still remains non-negative month-to-month.").toString());
            } else {
                getView().say(new StringBuilder().append("  - If '").append(source.getKey()).append("' stopped (").
                        append(Utility.formatRoundedDollarAmount(sourceMonthly)).append("/month), runway drops to about ").
                        append(Math.round(runwayIfRemoved)).append(" months.").toString());
            }
        }

        // Improvement 7: timeline visualization as key milestones.
        getView().say("\nForecast Timeline:");
        // A deficit before the summary period began is reported as history.  It says nothing about whether the
        // balance has recovered by the time the period opens, so it must not be used to claim that the balances
        // within the period are sound — that claim is only true when no deficit occurs inside the period:
        boolean historicalDeficit = firstNegativeBalance < 0 && dateOfFirstNegativBalance != null
                && dateOnlyCompare(dateOfFirstNegativBalance, firstFirstOfMonth) < 0;
        if (historicalDeficit) {
            getView().say(new StringBuilder().append("  - Historical deficit occurred on ")
                    .append(Utility.calendarDateToStringDate(dateOfFirstNegativBalance))
                    .append(" (before the forecast summary period).").toString());
        }

        if (periodOpensInDeficit) {

            // The account was already in the red when the summary period opened, so there is no positive phase and no
            // new deficit event to report — the deficit simply has not been cleared:
            getView().say(new StringBuilder().append("  - The balance is still in deficit when the summary period " +
                            "opens on ").
                    append(Utility.calendarDateToStringDate(firstFirstOfMonth)).append(" (").
                    append(Utility.formatRoundedDollarAmount(firstPeriodNegativeBalance)).append(").").toString());
        } else if (dateOfFirstPeriodNegativeBalance != null) {

            // The period opened in the black and went into deficit during it, so there is a genuine positive phase
            // leading up to the deficit whether or not an older deficit was reported as history above:
            Calendar preCrisisDate = copyCalendar(dateOfFirstPeriodNegativeBalance);
            preCrisisDate.add(Calendar.DAY_OF_MONTH, -1);
            getView().say(new StringBuilder().append("  - Positive balance phase: ").
                    append(Utility.calendarDateToStringDate(firstFirstOfMonth)).append(" through ").
                    append(Utility.calendarDateToStringDate(preCrisisDate)).append(".").toString());

            getView().say(new StringBuilder().append("  - First deficit within the forecast summary period: ").
                    append(Utility.calendarDateToStringDate(dateOfFirstPeriodNegativeBalance)).append(" (").
                    append(Utility.formatRoundedDollarAmount(firstPeriodNegativeBalance)).append(").").toString());
        } else if (historicalDeficit) {
            getView().say("  - All balances within the forecast summary period are non-negative.");
        } else {
            getView().say("  - All projected balances remain non-negative.");
        }

        Optional<MonthlyCashFlow> firstMonthEndNegative = monthlyCashFlowMap.values().stream()
                .filter(month -> month.endingBalance < 0)
                .findFirst();
        if (firstMonthEndNegative.isPresent()) {
            getView().say(new StringBuilder().append("  - Persistent deficit period begins by month-end in ").
                    append(firstMonthEndNegative.get().label).append(".").toString());
        }
        if (dateOfPeriodLowestBalance != null) {
            getView().say(new StringBuilder().append("  - Lowest point occurs on ").
                    append(Utility.calendarDateToStringDate(dateOfPeriodLowestBalance)).append(" at ").
                    append(Utility.formatRoundedDollarAmount(periodLowestBalance)).append(".").toString());
        }

        // Improvement 8: immediate actions checklist.
        getView().say("\nImmediate Actions Required:");
        Calendar actionDate1 = copyCalendar(firstFirstOfMonth);
        actionDate1.add(Calendar.MONTH, 1);
        Calendar actionDate2 = copyCalendar(firstFirstOfMonth);
        actionDate2.add(Calendar.MONTH, 2);
        // For dates derived from the (potentially historical) first-negative-balance date, clamp to
        // at least the first month of the summary period so we never show past action deadlines.
        Calendar crisisAnchor = (dateOfFirstNegativBalance != null
                && dateOnlyCompare(dateOfFirstNegativBalance, firstFirstOfMonth) >= 0)
                ? dateOfFirstNegativBalance : firstFirstOfMonth;
        Calendar troughAnchor = (dateOfPeriodLowestBalance != null
                && dateOnlyCompare(dateOfPeriodLowestBalance, firstFirstOfMonth) >= 0)
                ? dateOfPeriodLowestBalance
                : (lastForecastTransaction != null ? lastForecastTransaction.getPlannedDate() : firstFirstOfMonth);
        Calendar actionDate3 = copyCalendar(crisisAnchor);
        Calendar actionDate4 = copyCalendar(troughAnchor);
        if (actionDate3 != null) {
            actionDate3.add(Calendar.DAY_OF_MONTH, -14);
        }
        if (actionDate4 != null) {
            actionDate4.add(Calendar.MONTH, -2);
        }
        // Final clamp: never show a date in the past
        Calendar clampFloor = copyCalendar(firstFirstOfMonth);
        if (actionDate3 != null && dateOnlyCompare(actionDate3, clampFloor) < 0) actionDate3 = clampFloor;
        if (actionDate4 != null && dateOnlyCompare(actionDate4, clampFloor) < 0) actionDate4 = clampFloor;

        getView().say(new StringBuilder().append("  [ ] By ").append(Utility.calendarDateToStringDate(actionDate1)).
                append(": identify at least ").append(Utility.formatRoundedDollarAmount(monthlyGap)).
                append("/month in spending cuts, new income, or a combination.").toString());
        getView().say(new StringBuilder().append("  [ ] By ").append(Utility.calendarDateToStringDate(actionDate2)).
                append(": implement and verify the plan against actual account activity.").toString());
        getView().say(new StringBuilder().append("  [ ] By ").append(Utility.calendarDateToStringDate(actionDate3)).
                append(": have contingency float ready before projected negative-balance risk.").toString());
        getView().say(new StringBuilder().append("  [ ] By ").append(Utility.calendarDateToStringDate(actionDate4)).
                append(": maintain minimum float target of ").append(Utility.formatRoundedDollarAmount(requiredFloat > 0 ? requiredFloat : 0)).
                append(" to avoid trough-period shortfalls.").toString());

        // Update the forecast's lastRenderedDate to track when we rendered the file.
        // Use the file's actual lastModified timestamp rather than the current time, so that
        // the comparison in isExternalForecastFileNewer() compares the file's timestamp against
        // itself. This prevents false positives caused by OneDrive sync updating the file's
        // lastModified timestamp after we write it.
        Calendar renderedDate = Calendar.getInstance();
        String outputFilename = getLongTermForecastFilename();
        if (outputFilename != null) {
            File outputFile = new File(outputFilename);
            if (outputFile.exists()) {
                renderedDate.setTimeInMillis(outputFile.lastModified());
            }
        }
        forecast.setLastRenderedDate(renderedDate);
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
