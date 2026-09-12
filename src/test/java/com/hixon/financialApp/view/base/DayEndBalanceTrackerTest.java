package com.hixon.financialApp.view.base;

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DayEndBalanceTracker}.  The figures are the Bill Pay Dave and Bill Pay Danni forecasts as
 * rendered on 09-11-2026, in the order the rendering applies them:  a day's credits first, then its debits.
 */
class DayEndBalanceTrackerTest {

    private static final double TOLERANCE = 0.001;

    private static final Calendar TODAY = on(2026, Calendar.SEPTEMBER, 11);
    private static final Calendar PERIOD_START = on(2026, Calendar.OCTOBER, 1);

    private static Calendar on(int year, int month, int day) {
        return new GregorianCalendar(year, month, day);
    }

    private static void day(DayEndBalanceTracker tracker, Calendar date, double... balances) {
        for (double balance : balances) {
            tracker.record(date, balance);
        }
    }

    @Test
    void highestBalanceIsADayEndNotAMomentBetweenCreditsAndDebits() {
        DayEndBalanceTracker tracker = new DayEndBalanceTracker(1.66, TODAY, PERIOD_START);
        day(tracker, on(2027, Calendar.AUGUST, 31), 1068.83);

        // 09-01-2027:  both paychecks land before the mortgage, touching $6,374.83 on a day that closes at $907.37.
        day(tracker, on(2027, Calendar.SEPTEMBER, 1), 5132.83, 6374.83, 2855.98, 1230.98, 962.37, 907.37);
        tracker.finish();

        assertEquals(1068.83, tracker.getHighestBalance(), TOLERANCE);
        assertEquals(0, on(2027, Calendar.AUGUST, 31).compareTo(tracker.getDateOfHighestBalance()));
    }

    @Test
    void firstDeficitIsTheBalanceTheDayClosesOn() {
        DayEndBalanceTracker tracker = new DayEndBalanceTracker(1.66, TODAY, PERIOD_START);
        day(tracker, on(2026, Calendar.SEPTEMBER, 30), 53.55);

        // 10-01-2026:  the HOA fee takes it to $-52.91, then Justin's registration to $-91.46.
        day(tracker, PERIOD_START, 4117.55, 5359.55, 1840.70, 215.70, -52.91, -91.46);
        tracker.finish();

        assertEquals(-91.46, tracker.getFirstPeriodNegativeBalance(), TOLERANCE);
        assertEquals(0, PERIOD_START.compareTo(tracker.getDateOfFirstPeriodNegativeBalance()));
        assertEquals(-91.46, tracker.getFirstNegativeBalance(), TOLERANCE);
        assertEquals(-91.46, tracker.getLowestBalance(), TOLERANCE);
    }

    @Test
    void aRegisterAlreadyOverdrawnIsTheFirstNegativeBalance() {
        // Bill Pay Danni:  the register stood at $-193.43, and the two 09-11 work expenses took it to $-263.43.
        DayEndBalanceTracker tracker = new DayEndBalanceTracker(-193.43, TODAY, PERIOD_START);
        day(tracker, TODAY, -238.43, -263.43);
        day(tracker, on(2026, Calendar.SEPTEMBER, 15), 3193.07, 1738.07);
        day(tracker, on(2026, Calendar.SEPTEMBER, 30), 607.54);
        day(tracker, PERIOD_START, 700.00);
        tracker.finish();

        assertTrue(tracker.isOpenedNegative());
        assertEquals(-193.43, tracker.getFirstNegativeBalance(), TOLERANCE);
        assertEquals(0, TODAY.compareTo(tracker.getDateOfFirstNegativeBalance()));

        assertEquals(-263.43, tracker.getLowestBeforePeriod(), TOLERANCE);
        assertEquals(0, TODAY.compareTo(tracker.getDateOfLowestBeforePeriod()));

        // and none of that is inside the period, which opens on the 09-30 close:
        assertEquals(607.54, tracker.getPeriodOpeningBalance(), TOLERANCE);
        assertFalse(tracker.isPeriodOpensInDeficit());
        assertNull(tracker.getDateOfFirstPeriodNegativeBalance());
        assertEquals(607.54, tracker.getLowestBalance(), TOLERANCE);
    }

    @Test
    void anOverdueOccurrenceKeepsItsOwnDate() {
        // Bill Pay Dave:  $1.66 in the register, and $42.65 of work expenses dated 09-09 still to clear.
        DayEndBalanceTracker tracker = new DayEndBalanceTracker(1.66, TODAY, PERIOD_START);
        day(tracker, on(2026, Calendar.SEPTEMBER, 9), -40.99);
        day(tracker, on(2026, Calendar.SEPTEMBER, 15), 4023.01, 2023.01);
        tracker.finish();

        assertFalse(tracker.isOpenedNegative());
        assertEquals(-40.99, tracker.getFirstNegativeBalance(), TOLERANCE);
        assertEquals(0, on(2026, Calendar.SEPTEMBER, 9).compareTo(tracker.getDateOfFirstNegativeBalance()));
        assertEquals(-40.99, tracker.getLowestBeforePeriod(), TOLERANCE);
    }

    @Test
    void thePeriodOpensOnTheBalanceThePreviousDayClosedOn() {
        DayEndBalanceTracker tracker = new DayEndBalanceTracker(100.00, TODAY, PERIOD_START);
        day(tracker, on(2026, Calendar.SEPTEMBER, 30), 400.00, -50.00);
        day(tracker, on(2026, Calendar.OCTOBER, 2), 25.00);
        tracker.finish();

        assertEquals(-50.00, tracker.getPeriodOpeningBalance(), TOLERANCE);
        assertTrue(tracker.isPeriodOpensInDeficit());
        assertEquals(-50.00, tracker.getFirstPeriodNegativeBalance(), TOLERANCE);
        assertEquals(0, PERIOD_START.compareTo(tracker.getDateOfFirstPeriodNegativeBalance()));
        assertEquals(-50.00, tracker.getLowestBalance(), TOLERANCE);
        assertEquals(25.00, tracker.getHighestBalance(), TOLERANCE);
    }

    @Test
    void aRenderingWithNothingInThePeriodOpensItOnTheClosingBalance() {
        DayEndBalanceTracker tracker = new DayEndBalanceTracker(100.00, TODAY, PERIOD_START);
        day(tracker, on(2026, Calendar.SEPTEMBER, 20), 80.00);
        tracker.finish();

        assertEquals(80.00, tracker.getPeriodOpeningBalance(), TOLERANCE);
        assertEquals(80.00, tracker.getLowestBalance(), TOLERANCE);
        assertEquals(80.00, tracker.getHighestBalance(), TOLERANCE);
        assertNotNull(tracker.getDateOfHighestBalance(), "a high point always has a date to print");
    }

    @Test
    void aSolventRenderingHasNoNegativeBalances() {
        DayEndBalanceTracker tracker = new DayEndBalanceTracker(500.00, TODAY, PERIOD_START);
        day(tracker, PERIOD_START, 900.00, 300.00);
        tracker.finish();

        assertNull(tracker.getDateOfFirstNegativeBalance());
        assertEquals(0.0, tracker.getFirstNegativeBalance(), TOLERANCE);
        assertEquals(300.00, tracker.getLowestBalance(), TOLERANCE);
        assertEquals(500.00, tracker.getHighestBalance(), TOLERANCE, "the opening balance seeds the high point");
    }
}
