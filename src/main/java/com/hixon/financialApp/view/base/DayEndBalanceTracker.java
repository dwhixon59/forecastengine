package com.hixon.financialApp.view.base;

import java.util.Calendar;

import static com.hixon.financialApp.utility.Utility.dateOnlyCompare;

/**
 * The balance extremes of a long term forecast, read at the end of each day rather than after each occurrence.
 *
 * <p>The rendering walks a day's credits first, largest first, and only then its debits (see
 * {@code ForecastTransactionAndItemDatabaseIterator}), so the balance between two occurrences on the same day is a
 * point the account never actually sits at.  Reading the extremes there reported Bill Pay Dave's highest balance as
 * $6,375 on 09-01-2027 -- the paycheck and Danni's contribution counted, the mortgage not yet -- on a day that closed
 * at $907, and its first deficit as $-53 on 10-01-2026 on a day that closed at $-91.  Only the balance a day closes
 * on is one the account holds.
 *
 * <p>The opening balance is the register's balance now, and counts as a balance held today:  an account that is
 * already overdrawn has its first negative balance before any occurrence is applied.  On 09-11-2026 Bill Pay Danni
 * stood at $-193.43 and the report named 09-11-2026 at $-238 as the first negative balance.
 *
 * <p>Days before the summary period opens -- the rest of the current month, and any overdue occurrences dated before
 * today -- are tracked separately from the period itself, because the account has to get through them too.
 */
final class DayEndBalanceTracker {

    private final Calendar today;
    private final Calendar periodStart;

    // The day still being accumulated, and the balance it has reached so far:
    private Calendar pendingDate;
    private double pendingBalance;

    // The balance the last completed day closed on;  the opening balance until a day completes:
    private double lastDayEndBalance;

    private final boolean openedNegative;
    private double firstNegativeBalance;
    private Calendar dateOfFirstNegativeBalance;
    private double lowestBeforePeriod;
    private Calendar dateOfLowestBeforePeriod;

    private boolean periodOpened;
    private double periodOpeningBalance;
    private boolean periodOpensInDeficit;
    private double lowestBalance;
    private Calendar dateOfLowestBalance;
    private double highestBalance;
    private Calendar dateOfHighestBalance;
    private double firstPeriodNegativeBalance;
    private Calendar dateOfFirstPeriodNegativeBalance;

    /**
     * @param openingBalance the register's balance now
     * @param today          the date the opening balance is held on
     * @param periodStart    the first day of the summary period
     */
    DayEndBalanceTracker(double openingBalance, Calendar today, Calendar periodStart) {
        this.today = copy(today);
        this.periodStart = copy(periodStart);
        lastDayEndBalance = openingBalance;
        lowestBeforePeriod = openingBalance;
        dateOfLowestBeforePeriod = copy(today);
        openedNegative = openingBalance < 0;
        if (openedNegative) {
            firstNegativeBalance = openingBalance;
            dateOfFirstNegativeBalance = copy(today);
        }
    }

    /**
     * Record the balance reached after one occurrence.  Occurrences must arrive in rendering order, so that a change
     * of date means the previous day is complete.
     */
    void record(Calendar date, double balance) {
        if (pendingDate != null && dateOnlyCompare(date, pendingDate) != 0) {
            closeDay();
        }
        if (!periodOpened && dateOnlyCompare(date, periodStart) >= 0) {
            openPeriod();
        }
        pendingDate = copy(date);
        pendingBalance = balance;
    }

    /**
     * Close the last day.  Call once, after the last occurrence.  A rendering with nothing inside the summary period
     * opens it on the closing balance, which is the balance the period would begin with.
     */
    void finish() {
        if (pendingDate != null) {
            closeDay();
        }
        if (!periodOpened) {
            openPeriod();
        }
    }

    private void closeDay() {
        double balance = pendingBalance;
        Calendar date = pendingDate;

        if (balance < 0 && dateOfFirstNegativeBalance == null) {
            firstNegativeBalance = balance;
            dateOfFirstNegativeBalance = date;
        }

        if (dateOnlyCompare(date, periodStart) < 0) {
            if (balance < lowestBeforePeriod) {
                lowestBeforePeriod = balance;
                dateOfLowestBeforePeriod = date;
            }
        } else {
            if (balance < 0 && dateOfFirstPeriodNegativeBalance == null) {
                firstPeriodNegativeBalance = balance;
                dateOfFirstPeriodNegativeBalance = date;
            }
            if (balance < lowestBalance) {
                lowestBalance = balance;
                dateOfLowestBalance = date;
            }
            if (balance > highestBalance) {
                highestBalance = balance;
                dateOfHighestBalance = date;
            }
        }

        lastDayEndBalance = balance;
        pendingDate = null;
    }

    // The balance carried into the period is the one the day before it closed on.  It seeds the period's low and high
    // points, and a period that opens in the red has its first deficit on its first day:
    private void openPeriod() {
        periodOpened = true;
        periodOpeningBalance = lastDayEndBalance;
        lowestBalance = periodOpeningBalance;
        highestBalance = periodOpeningBalance;
        dateOfLowestBalance = copy(periodStart);
        dateOfHighestBalance = copy(periodStart);
        if (periodOpeningBalance < 0) {
            periodOpensInDeficit = true;
            firstPeriodNegativeBalance = periodOpeningBalance;
            dateOfFirstPeriodNegativeBalance = copy(periodStart);
        }
    }

    private static Calendar copy(Calendar date) {
        return date == null ? null : (Calendar) date.clone();
    }

    /*
     * Results.  Read them after finish().
     */

    /** True when the register balance itself was already below zero. */
    boolean isOpenedNegative() { return openedNegative; }

    /** The first negative balance anywhere in the rendering, or 0 when there is none. */
    double getFirstNegativeBalance() { return firstNegativeBalance; }

    /** The date of {@link #getFirstNegativeBalance()}, or null when there is none. */
    Calendar getDateOfFirstNegativeBalance() { return copy(dateOfFirstNegativeBalance); }

    /** The lowest balance held before the summary period opens, the opening balance included. */
    double getLowestBeforePeriod() { return lowestBeforePeriod; }

    Calendar getDateOfLowestBeforePeriod() { return copy(dateOfLowestBeforePeriod); }

    /** The balance carried into the summary period. */
    double getPeriodOpeningBalance() { return periodOpeningBalance; }

    boolean isPeriodOpensInDeficit() { return periodOpensInDeficit; }

    double getLowestBalance() { return lowestBalance; }

    Calendar getDateOfLowestBalance() { return copy(dateOfLowestBalance); }

    double getHighestBalance() { return highestBalance; }

    Calendar getDateOfHighestBalance() { return copy(dateOfHighestBalance); }

    /** The first negative balance inside the summary period, or 0 when there is none. */
    double getFirstPeriodNegativeBalance() { return firstPeriodNegativeBalance; }

    Calendar getDateOfFirstPeriodNegativeBalance() { return copy(dateOfFirstPeriodNegativeBalance); }

    Calendar getToday() { return copy(today); }
}
