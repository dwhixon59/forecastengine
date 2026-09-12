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
        // A balance that is already at or below zero has no runway to run down.  Dividing anyway
        // produced a negative duration and printed it as one:  on 09-07-2026, a starting balance of
        // -$181 against a burn of -$309 gave -0.59, reported as "runway is about -1 months".  There
        // is no such quantity;  the honest answer is none left, and the caller says so in words.
        if (startingBalance <= 0) {
            return 0.0;
        }
        return roundCurrency(startingBalance / -monthlyNet);
    }

    /**
     * A runway in words.  Under a month is said as such:  "about 0 months" -- which is what Math.round made of $54
     * against a $3,962 monthly shortfall on 09-11-2026 -- reads as though the money were already gone.
     */
    static String runwayLength(double months) {
        if (months < 1) {
            return "under a month";
        }
        long rounded = Math.round(months);
        return "about " + rounded + (rounded == 1 ? " month" : " months");
    }

    /**
     * The balance the summary period opens with, said beside a float.  The float is what the account has to hold
     * when the period opens and the deposit is the part of it not already there;  printed apart, "the float needed
     * is $488" and "you need to deposit $134" read as two answers to the same question.
     */
    static String periodOpensWith(double openingBalance) {
        return " (the period opens with " + Utility.formatRoundedDollarAmount(openingBalance) + ")";
    }

    /** The later of two dates, either of which may be null. */
    static Calendar latestOf(Calendar first, Calendar second) {
        if (first == null) {
            return copyCalendar(second);
        }
        if (second == null) {
            return copyCalendar(first);
        }
        return copyCalendar(dateOnlyCompare(first, second) >= 0 ? first : second);
    }

    /**
     * When the first negative balance falls, relative to today and to the summary period.  It decides how the report
     * may speak of it:  a register already overdrawn is not a projection, and an occurrence dated before today has
     * not happened -- it is overdue, still waiting to clear.  Both used to be reported as though they were history or
     * still to come:  on 09-11-2026 Bill Pay Dave, with $1.66 in the bank, was told "the account went negative on
     * 09-09-2026 -- this is in the past" about $42.65 of work expenses that had not cleared, and Bill Pay Danni,
     * already overdrawn at $-193.43, was told it "first goes negative on 09-11-2026".
     */
    enum NegativeBalanceTiming { NOW, OVERDUE, BEFORE_PERIOD, IN_PERIOD }

    static NegativeBalanceTiming negativeBalanceTiming(boolean openedNegative, Calendar date, Calendar today,
                                                      Calendar periodStart) {
        if (openedNegative) {
            return NegativeBalanceTiming.NOW;
        }
        if (dateOnlyCompare(date, today) < 0) {
            return NegativeBalanceTiming.OVERDUE;
        }
        if (dateOnlyCompare(date, periodStart) < 0) {
            return NegativeBalanceTiming.BEFORE_PERIOD;
        }
        return NegativeBalanceTiming.IN_PERIOD;
    }

    static String firstNegativeSummaryLine(NegativeBalanceTiming timing, double balance, Calendar date) {
        String amount = Utility.formatRoundedDollarAmount(balance);
        return switch (timing) {
            case NOW -> "The balance is already negative:  " + amount + " in the register today.";
            case OVERDUE -> "The first negative balance is: " + amount + ", once the overdue occurrences dated " +
                    Utility.calendarDateToStringDate(date) + " clear.";
            default -> "The first negative balance is: " + amount + " on " +
                    Utility.calendarDateToStringDate(date) + ".";
        };
    }

    static String firstNegativeRiskLine(NegativeBalanceTiming timing, double balance, Calendar date) {
        String amount = Utility.formatRoundedDollarAmount(balance);
        return switch (timing) {
            case NOW -> "  - Critical: The account is already overdrawn at " + amount + ".";
            case OVERDUE -> "  - Overdue occurrences dated " + Utility.calendarDateToStringDate(date) +
                    " have not cleared yet;  when they do, the balance goes to " + amount + ".";
            default -> "  - Critical: The account first goes negative on " +
                    Utility.calendarDateToStringDate(date) + " at " + amount + ".";
        };
    }

    static String firstNegativeTimelineLine(NegativeBalanceTiming timing, double balance, Calendar date) {
        String amount = Utility.formatRoundedDollarAmount(balance);
        return switch (timing) {
            case NOW -> "  - The account is already overdrawn (" + amount + ") before the summary period opens.";
            case OVERDUE -> "  - Overdue occurrences dated " + Utility.calendarDateToStringDate(date) +
                    " take the balance to " + amount + " before the summary period opens.";
            default -> "  - The balance goes negative on " + Utility.calendarDateToStringDate(date) + " (" + amount +
                    ") before the summary period opens.";
        };
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

        // A credit line is not an account holding money, and most of the analysis below assumes it
        // is.  Float, runway, "the account went negative", "deposit this much to stay solvent" --
        // all of it reads a negative balance as trouble, which on a credit card is simply the
        // balance owed.  Rendering it anyway produced a summary that argued with itself:  on
        // 09-04-2026 the Citi report said "You have sufficient float to ensure no negative
        // balances" directly beneath "Lowest projected balance is $-14,321".  Both were true to
        // their own arithmetic and neither meant anything.  The cash-flow figures, the monthly
        // table and the expense breakdown are sound either way, so only the asset-account sections
        // are withheld.
        boolean creditLine = forecastRegister.isCreditLine();
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
        Calendar today = Calendar.getInstance();
        Calendar firstFirstOfMonth = getNextFirstOfMonth(Calendar.getInstance());
        double firstFirstOfMonthBalance = 0.0;

        // The balance extremes are read at the end of each day rather than after each occurrence -- see
        // DayEndBalanceTracker for why:
        DayEndBalanceTracker dayEndBalances = new DayEndBalanceTracker(runningBalance, today, firstFirstOfMonth);

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

            }

            // The balance carried into the summary period is taken by dayEndBalances at the first
            // occurrence that falls inside it -- not at one dated exactly on the 1st.
            //
            // This used to require an occurrence dated *exactly* on the first of the month, and to
            // sit inside the month-change block above.  Both made it a coincidence.  The iterator
            // drops fully reconciled occurrences -- it takes remainingAmount <> 0, plus zero rows
            // only where the item amount is also zero -- so whether anything survives to land on
            // the 1st depends on what has already been reconciled.  On 09-06-2026 the Citi forecast
            // had one occurrence dated 10-01, a Boat Storage already reconciled to zero against a
            // -$170 item;  it was filtered out, the first October row the loop actually saw was
            // 10-04, the comparison failed, and this stayed at its initialised 0.
            //
            // Zero looks like a balance, so nothing flagged it.  It is subtracted from the closing
            // balance to get the net change and the out-of-balance amount, so the report concluded
            // the forecast was "out of balance by $4,242" and asked for $848/month of cuts on an
            // account whose true opening balance was -$13,444.57 and which clears $9,203 across the
            // period.  Register balance -11,986.25 plus -1,458.32 of pre-October occurrences is that
            // opening figure, and -13,444.57 + 649 is the -12,795 the monthly table reported for
            // October -- the number was right there in the same report.
            // Update the running balance, and hand it to the tracker, which reads the extremes once each day is
            // complete:
            double remainingAmount = roundCurrency(forecastTransaction.getRemainingAmount());
            runningBalance = roundCurrency(runningBalance + remainingAmount);
            dayEndBalances.record(forecastTransaction.getPlannedDate(), runningBalance);

            // If we are within the forecast summary period (from the firstFirstOfMonth data to the end of the forecast),
            // then update the summary:
            if (dateOnlyCompare(forecastTransaction.getPlannedDate(), firstFirstOfMonth) >= 0) {
                String periodKey = monthKey(forecastTransaction.getPlannedDate());
                String periodLabel = monthLabel(forecastTransaction.getPlannedDate());
                MonthlyCashFlow monthSummary = monthlyCashFlowMap.computeIfAbsent(periodKey,
                        ignored -> new MonthlyCashFlow(periodLabel));
                monthSummary.endingBalance = runningBalance;

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

            // Save off the updated running balance, without moving the occurrence's version:
            forecastTransaction.saveRunningBalance(runningBalance);

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

        // Close the last day.  When nothing at all falls inside the summary period this also opens it on
        // the closing balance -- nothing happens between them -- which makes the net change zero and the
        // forecast balanced, the truth about a period with no occurrences in it.  Leaving the initialised
        // 0.0 instead would report the whole closing balance as the period's net change.
        dayEndBalances.finish();
        firstFirstOfMonthBalance = dayEndBalances.getPeriodOpeningBalance();
        lowestBalance = dayEndBalances.getLowestBalance();
        dateOfLowestBalance = dayEndBalances.getDateOfLowestBalance();
        highestBalance = dayEndBalances.getHighestBalance();
        dateOfHighestBalance = dayEndBalances.getDateOfHighestBalance();
        firstNegativeBalance = dayEndBalances.getFirstNegativeBalance();
        dateOfFirstNegativBalance = dayEndBalances.getDateOfFirstNegativeBalance();
        firstPeriodNegativeBalance = dayEndBalances.getFirstPeriodNegativeBalance();
        dateOfFirstPeriodNegativeBalance = dayEndBalances.getDateOfFirstPeriodNegativeBalance();
        periodOpensInDeficit = dayEndBalances.isPeriodOpensInDeficit();
        double lowestBeforePeriod = dayEndBalances.getLowestBeforePeriod();
        Calendar dateOfLowestBeforePeriod = dayEndBalances.getDateOfLowestBeforePeriod();
        NegativeBalanceTiming firstNegativeTiming = dateOfFirstNegativBalance == null ? null :
                negativeBalanceTiming(dayEndBalances.isOpenedNegative(), dateOfFirstNegativBalance, today,
                        firstFirstOfMonth);

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

        // Display the average monthly change in balance.  On a credit line the balance is what is owed, so a
        // rising balance is the debt being paid down, not money accumulating:
        double rateOfChangeInBalance = roundCurrency(netChangeInBalance / numberOfMonthsInForecast);
        if (creditLine) {
            getView().say(new StringBuilder().append(netChangeInBalance > 0
                            ? "The balance owed falls by an average of " : "The balance owed grows by an average of ").
                    append(Utility.formatRoundedDollarAmount(Math.abs(rateOfChangeInBalance))).
                    append(" per month.").toString());
        } else if (netChangeInBalance > 0) {
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
        // occurred.  Not for a credit line, where going below zero is what the account is for:
        if (firstNegativeTiming != null && !creditLine) {
            getView().say(firstNegativeSummaryLine(firstNegativeTiming, firstNegativeBalance,
                    dateOfFirstNegativBalance));
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

        // The deficits the float analysis below answers for:  one inside the summary period, and one
        // before it opens -- the rest of this month, which the period does not cover but the account
        // still has to get through.  A credit line has neither;  below zero is simply what it owes:
        boolean periodDeficit = lowestBalance < 0 && !creditLine;
        boolean prePeriodDeficit = lowestBeforePeriod < 0 && !creditLine;

        // Whether the low before the period is a dip of its own, or simply the balance carried into the
        // period.  When it is the carry-in there is nothing separate to report:  the period's own low
        // point is that same balance, and saying it again under the previous day's date reads as two
        // different troughs.  Bill Pay Danni's $-248 was printed three times on 09-12-2026.
        boolean prePeriodLowBelowOpening =
                roundCurrency(lowestBeforePeriod) < roundCurrency(firstFirstOfMonthBalance);

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
        } else if (!periodDeficit && !prePeriodDeficit) {
            // "No action is required" is only true when nothing below asks for any.  It used to print
            // for every balanced forecast:  on 09-11-2026 Bill Pay Dave was told it directly above "you
            // need to deposit $134" and an Immediate Actions checklist, and Bill Pay Danni -- overdrawn
            // at $-193.43 -- with nothing after it at all.
            getView().say("The forecast is balanced.  No action is required.");
        } else {
            // Balanced, but with a deficit to cover, which the lines below set out.  The forecast still
            // has to say it is in balance:  every other sentence here is about the deficit, so without
            // this one a forecast that pays its own way reads as one that does not.
            getView().say("The forecast is balanced over the period.");
        }

        // Save the pre-adjustment low-balance event for risk and timeline reporting.
        double periodLowestBalance = lowestBalance;
        Calendar dateOfPeriodLowestBalance = copyCalendar(dateOfLowestBalance);

        double requiredFloat = 0.0;
        double requiredDeposit = 0.0;
        double excessFloat = 0.0;

        // What the opening balance leaves over at the period's trough:  negative when it falls short of the float:
        double periodFloatSurplus = 0.0;

        // If there are any negative balances, then the float is insufficient.  Calculate the required float and let
        // the user know how much they need to deposit to fix the float issue.  A credit line has no float to be
        // insufficient:
        if (periodDeficit) {

            // Only a forecast that is actually out of balance has a monthly shortfall to correct.
            // Everything below used to run unconditionally, so a balanced forecast with a temporary
            // dip was told "once the monthly shortfall is corrected" about a shortfall it did not
            // have -- and worse, outOfBalanceMonthlyAmount is positive when balanced, so the loop
            // subtracted a surplus each month, made the projection worse, and reported the result as
            // the corrected case.  On 09-09-2026 Bill Pay Dave was balanced (+$1,234 over the
            // period) and was asked to deposit $1,021 against a trough of $-434.
            if (outOfBalanceMonthlyAmount < 0) {

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
                // Deliberately not "the date of the lowest balance".  That phrase is already spoken for
                // by the trough reported above, and this is a different date:  the loop just above
                // recomputed the balances assuming the monthly shortfall is corrected, which moves the
                // low point.  Both were called the same thing on 09-07-2026 -- "$-3,746 on 08-11-2027"
                // and "the date of the lowest balance (05-12-2027)" -- and read as a contradiction.
                if (lowestBalance < 0) {
                    getView().say(new StringBuilder().append("Once the monthly shortfall is corrected the low point moves to ").
                            append(Utility.calendarDateToStringDate(dateOfLowestBalance)).
                            append(", and the float needed to cover it is ").
                            append(Utility.formatRoundedDollarAmount(-lowestBalance)).
                            append(periodOpensWith(firstFirstOfMonthBalance)).append(".").toString());
                } else {
                    // The correction cleared the deficit outright, so there is no low point to name --
                    // and dateOfLowestBalance still holds the uncorrected one, which naming would be
                    // worse than saying nothing.  This is the case that printed "the required float ...
                    // is $0" on 09-04-2026.
                    getView().say("Correcting the monthly shortfall removes the projected deficit, so no float is required.");
                }

            } else {

                // The forecast covers its own outgoings over the period, so the dip is a matter of
                // when money arrives relative to when it leaves rather than of how much there is.
                // There is nothing to recompute:  the trough the main pass already found is the real
                // one.
                //
                // It does have to be put on the same footing as the branch above, though.  The main
                // pass tracks lowestBalance as an ACTUAL balance -- it is seeded from the balance
                // carried into the period -- whereas the correction loop reseeds it to 0 and
                // accumulates, making it a drawdown from the opening balance.  Everything below here,
                // requiredDeposit and excessFloat included, reads it the second way, so the opening
                // balance comes off before it is handed on.  Leaving it absolute would understate the
                // deposit by exactly that opening balance.
                lowestBalance = roundCurrency(lowestBalance - firstFirstOfMonthBalance);

                // A low point that IS the balance carried in has no drawdown to cover:  the period never
                // falls below where it opened, and the deficit is the opening balance itself, which the
                // deposit line below states.  Printed regardless, this said "the float needed to cover it
                // is $0" directly above "you need to deposit $248" -- Bill Pay Danni, 09-12-2026.
                if (lowestBalance < 0) {
                    getView().say(new StringBuilder().append("The low point is a timing gap rather than a " +
                                    "shortfall:  it falls on ").
                            append(Utility.calendarDateToStringDate(dateOfLowestBalance)).
                            append(", and the float needed to cover it is ").
                            append(Utility.formatRoundedDollarAmount(-lowestBalance)).
                            append(periodOpensWith(firstFirstOfMonthBalance)).append(".").toString());
                }
            }
            requiredFloat = roundCurrency(-lowestBalance);
            periodFloatSurplus = roundCurrency(lowestBalance + firstFirstOfMonthBalance);
        }

        // A deficit before the period opens is not covered by the period's float, and it comes first --
        // unless it is the carry-in itself, which is reported as the period's low point:
        if (prePeriodDeficit && prePeriodLowBelowOpening) {
            getView().say(new StringBuilder().append("Before the summary period opens, the balance falls to ").
                    append(Utility.formatRoundedDollarAmount(lowestBeforePeriod)).append(" on ").
                    append(Utility.calendarDateToStringDate(dateOfLowestBeforePeriod)).append(".").toString());
        }

        // Tell the user how much they need to deposit.  A deposit made now lifts every balance after it, so
        // the one that clears both deficits is the larger of the two rather than their sum -- and it is due
        // before the first negative balance, or today when that is already here or overdue:
        double shortfall = Math.max(periodDeficit ? -periodFloatSurplus : 0.0,
                prePeriodDeficit ? -lowestBeforePeriod : 0.0);
        Calendar depositDeadline = latestOf(today, dateOfFirstNegativBalance);
        if (shortfall > 0) {
            requiredDeposit = roundCurrency(shortfall);
            getView().say(new StringBuilder().append("To ensure you have no negative balances, you need to deposit ").
                    append(Utility.formatRoundedDollarAmount(requiredDeposit)).
                    append(" to the ").append(forecastRegister.getName()).append(" account by ").
                    append(Utility.calendarDateToStringDate(depositDeadline)).append(".").toString());
        } else if (periodDeficit && periodFloatSurplus < 1) {
            getView().say(new StringBuilder().append("You have sufficient float in the ").
                    append(forecastRegister.getName()).append(" account to ensure no negative balances.").
                    toString());
        } else if (periodDeficit) {
            excessFloat = periodFloatSurplus;
            getView().say(new StringBuilder().append("You have excess float in the amount of ").
                    append(Utility.formatRoundedDollarAmount(excessFloat)).
                    append(" in the ").append(forecastRegister.getName()).
                    append(" account.").toString());
        }

        // Improvement 4: explicit risk warnings.
        getView().say("\nRisk Warnings:");
        if (creditLine) {

            // On a credit line the balance owed is the thing worth watching, and it is not a risk
            // in the sense the rest of this section means.  "The account went negative" is not news
            // about a card, and float is not a quantity it has, so the trough is reported as what
            // it is -- an amount owed -- and nothing else is claimed.
            if (dateOfPeriodLowestBalance != null) {
                getView().say(new StringBuilder().append("  - Largest projected balance owed is ").
                        append(Utility.formatRoundedDollarAmount(-periodLowestBalance)).append(" on ").
                        append(Utility.calendarDateToStringDate(dateOfPeriodLowestBalance)).append(".").toString());
            }

        } else {
            if (firstNegativeTiming != null) {
                getView().say(firstNegativeRiskLine(firstNegativeTiming, firstNegativeBalance,
                        dateOfFirstNegativBalance));

                // The first negative balance is not always the worst one before the period opens:  Bill Pay
                // Danni was overdrawn at $-193.43 on 09-11-2026 and went on to $-263.43 before the next paycheck.
                if (prePeriodLowBelowOpening
                        && roundCurrency(lowestBeforePeriod) < roundCurrency(firstNegativeBalance)) {
                    getView().say(new StringBuilder().append("  - Lowest balance before the summary period opens is ").
                            append(Utility.formatRoundedDollarAmount(lowestBeforePeriod)).append(" on ").
                            append(Utility.calendarDateToStringDate(dateOfLowestBeforePeriod)).append(".").toString());
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
                        append(Utility.formatRoundedDollarAmount(requiredFloat)).
                        append(periodOpensWith(firstFirstOfMonthBalance)).append(".").toString());
            }
            if (excessFloat > 0 && requiredFloat > excessFloat) {
                getView().say(new StringBuilder().append("  - Current excess float is ").
                        append(Utility.formatRoundedDollarAmount(excessFloat)).append(", which is insufficient for the worst-case month.").
                        toString());
            }
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
        // Runway asks how long the money lasts at the current burn.  A credit line has no money to
        // last:  the answer on 09-04-2026 was "If 'Credit card payment' stopped, runway drops to
        // about 0 months", which is arithmetic on a balance owed and says nothing about the card.
        if (!creditLine) {
            getView().say("\nFinancial Runway Analysis:");
            double monthlyIncomeAverage = totalIncome == 0 ? 0 : roundCurrency(totalIncome / numberOfMonthsInForecast);
            double monthlyExpenseAverage = totalExpense == 0 ? 0 : roundCurrency(-totalExpense / numberOfMonthsInForecast);
            double baselineMonthlyNet = roundCurrency(monthlyIncomeAverage - monthlyExpenseAverage);
            double baselineRunway = monthsOfRunway(firstFirstOfMonthBalance, baselineMonthlyNet);

            // The whole section asks how long a balance lasts.  With nothing left to last, every
            // sentence in it is arithmetic on a number that is already spent -- including the
            // per-source lines below, which on 09-07-2026 offered "if David's net pay 1 stopped,
            // runway drops to about 0 months" against a balance that was already $-181.  Say the
            // one true thing instead of four false ones.
            if (firstFirstOfMonthBalance <= 0) {
                getView().say(new StringBuilder().append("  - The balance is already in deficit (").
                        append(Utility.formatRoundedDollarAmount(firstFirstOfMonthBalance)).
                        append("), so there is no runway to measure.").toString());

            } else if (Double.isInfinite(baselineRunway)) {
                getView().say("  - Current monthly net is non-negative, so runway is not constrained by burn rate.");

            } else {
                getView().say(new StringBuilder().append("  - At the current net burn of ").
                        append(Utility.formatRoundedDollarAmount(baselineMonthlyNet)).append("/month, runway is ").
                        append(runwayLength(baselineRunway)).append(".").toString());
            }

            // Skipped in deficit for the reason given above:  "runway drops to about N months" is
            // a claim about a balance that is not there.
            List<Map.Entry<String, Double>> topIncomeSources = firstFirstOfMonthBalance <= 0
                    ? List.of()
                    : incomeBySource.entrySet().stream()
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
                            append(Utility.formatRoundedDollarAmount(sourceMonthly)).append("/month), runway drops to ").
                            append(runwayLength(runwayIfRemoved)).append(".").toString());
                }
            }
        }

        // Improvement 7: timeline visualization as key milestones.
        // Every line of the timeline is a deficit narrative -- historical deficit, positive balance
        // phase, first deficit, persistent deficit -- and on a credit line all of it describes the
        // balance owed rather than any event.  The trough is already reported as an amount owed
        // under Risk Warnings, so there is nothing left here worth saying about a card.
        if (!creditLine) {
            getView().say("\nForecast Timeline:");
            // A deficit before the summary period opens is reported as such -- as the overdraft it already is,
            // as overdue occurrences still to clear, or as a date still to come this month.  It used to be called
            // "historical" whatever it was:  on 09-11-2026 Bill Pay Danni's deficit that very day, still ahead of
            // the paycheck, was a "historical deficit".  It says nothing about whether the balance has recovered
            // by the time the period opens, so it must not be used to claim that the balances within the period
            // are sound — that claim is only true when no deficit occurs inside the period:
            boolean deficitBeforePeriod = firstNegativeTiming != null
                    && firstNegativeTiming != NegativeBalanceTiming.IN_PERIOD;
            if (deficitBeforePeriod) {
                getView().say(firstNegativeTimelineLine(firstNegativeTiming, firstNegativeBalance,
                        dateOfFirstNegativBalance));
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
                // leading up to the deficit whether or not an older deficit was reported as history above.
                //
                // That phase runs from the period's first day up to the day before the deficit, which
                // leaves no phase at all when the deficit falls on that first day:  the day before it
                // lies outside the period.  Printing it regardless produced a range that ran
                // backwards -- "Positive balance phase: 10-01-2026 through 09-30-2026" on 09-09-2026.
                Calendar preCrisisDate = copyCalendar(dateOfFirstPeriodNegativeBalance);
                preCrisisDate.add(Calendar.DAY_OF_MONTH, -1);
                if (dateOnlyCompare(preCrisisDate, firstFirstOfMonth) >= 0) {
                    getView().say(new StringBuilder().append("  - Positive balance phase: ").
                            append(Utility.calendarDateToStringDate(firstFirstOfMonth)).append(" through ").
                            append(Utility.calendarDateToStringDate(preCrisisDate)).append(".").toString());
                }

                getView().say(new StringBuilder().append("  - First deficit within the forecast summary period: ").
                        append(Utility.calendarDateToStringDate(dateOfFirstPeriodNegativeBalance)).append(" (").
                        append(Utility.formatRoundedDollarAmount(firstPeriodNegativeBalance)).append(").").toString());
            } else if (deficitBeforePeriod) {
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
        }

        // Improvement 8: immediate actions checklist.
        // Every line here is an instruction to fix something, so the section only belongs in a
        // forecast that has something to fix.  It used to print unconditionally:  a balanced
        // forecast with no deficit and no float requirement was told to "identify at least $0/month
        // in spending cuts" and to "maintain minimum float target of $0", and to hold contingency
        // float against a negative-balance risk it did not have.  Bill Pay Danni got all four on
        // 09-09-2026 against a trough of +$515.
        boolean hasMonthlyGapAction = monthlyGap > 0;
        boolean hasFloatAction = !creditLine && requiredFloat > 0;
        boolean hasDepositAction = requiredDeposit > 0;
        if (hasMonthlyGapAction || hasFloatAction || hasDepositAction) {
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

            // The first two actions are about closing a monthly gap, so they only apply when there is
            // one;  the second is the follow-through on the plan the first asks for, and says nothing on
            // its own.
            if (hasMonthlyGapAction) {
                getView().say(new StringBuilder().append("  [ ] By ").append(Utility.calendarDateToStringDate(actionDate1)).
                        append(": identify at least ").append(Utility.formatRoundedDollarAmount(monthlyGap)).
                        append("/month in spending cuts, new income, or a combination.").toString());
                getView().say(new StringBuilder().append("  [ ] By ").append(Utility.calendarDateToStringDate(actionDate2)).
                        append(": implement and verify the plan against actual account activity.").toString());
            }
            // The last two are about float, which a credit line does not have -- on 09-04-2026 they asked
            // the user to hold contingency float against a "negative-balance risk" that was just the
            // card's balance, and to maintain a float target of $0.
            //
            // A deposit already worked out is the contingency float, named:  "have contingency float ready" beside
            // "deposit $134" asked for the same money twice.  It is due by the deadline worked out with it, which
            // a deficit before the period opens brings forward to today -- Bill Pay Danni's could not wait for the
            // period's first day.
            if (hasDepositAction) {
                getView().say(new StringBuilder().append("  [ ] By ").
                        append(Utility.calendarDateToStringDate(depositDeadline)).append(": deposit ").
                        append(Utility.formatRoundedDollarAmount(requiredDeposit)).append(" to the ").
                        append(forecastRegister.getName()).append(" account.").toString());
            } else if (hasFloatAction) {
                getView().say(new StringBuilder().append("  [ ] By ").append(Utility.calendarDateToStringDate(actionDate3)).
                        append(": have contingency float ready before projected negative-balance risk.").toString());
            }
            if (hasFloatAction) {
                getView().say(new StringBuilder().append("  [ ] By ").append(Utility.calendarDateToStringDate(actionDate4)).
                        append(": maintain minimum float target of ").append(Utility.formatRoundedDollarAmount(requiredFloat)).
                        append(periodOpensWith(firstFirstOfMonthBalance)).
                        append(" to avoid trough-period shortfalls.").toString());
            }
        }

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
