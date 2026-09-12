package com.hixon.financialApp.view.base;

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

class AbstractForecastViewSummaryHelperTest {

    @Test
    void roundCurrencyRoundsToCents() {
        assertEquals(10.01, AbstractForecastView.roundCurrency(10.005));
        assertEquals(-2.4, AbstractForecastView.roundCurrency(-2.3999));
    }

    @Test
    void monthsOfRunwayReturnsInfinityForNonNegativeNet() {
        assertTrue(Double.isInfinite(AbstractForecastView.monthsOfRunway(1000, 0)));
        assertTrue(Double.isInfinite(AbstractForecastView.monthsOfRunway(1000, 25)));
    }

    @Test
    void monthsOfRunwayCalculatesExpectedBurnDuration() {
        assertEquals(5.0, AbstractForecastView.monthsOfRunway(1000, -200));
        assertEquals(2.5, AbstractForecastView.monthsOfRunway(500, -200));
    }

    @Test
    void monthsOfRunwayIsZeroWhenTheBalanceIsAlreadySpent() {
        // A balance at or below zero has no runway to run down.  Dividing anyway produced a negative
        // duration and the report printed it as one:  on 09-07-2026 a starting balance of -$181
        // against a burn of -$309 gave -0.59, shown as "runway is about -1 months".
        assertEquals(0.0, AbstractForecastView.monthsOfRunway(-181, -309));
        assertEquals(0.0, AbstractForecastView.monthsOfRunway(0, -309));

        // And never negative, whatever the inputs -- the guard is what stops the sentence existing.
        assertTrue(AbstractForecastView.monthsOfRunway(-10000, -1) >= 0.0);
    }

    @Test
    void monthsOfRunwayStillMeasuresAPositiveBalance() {
        // The regression guard in the other direction:  adding the deficit case must not change the
        // answer for an account that does have money to run down.
        assertEquals(5.0, AbstractForecastView.monthsOfRunway(1000, -200));
        assertTrue(Double.isInfinite(AbstractForecastView.monthsOfRunway(-181, 25)),
                "a non-negative net is unconstrained by burn even from a deficit, as before");
    }

    @Test
    void labelHelpersNormalizeAndFormatMonthData() {
        assertEquals("fallback", AbstractForecastView.normalizedLabel("   ", "fallback"));
        assertEquals("Paycheck", AbstractForecastView.normalizedLabel(" Paycheck ", "fallback"));

        Calendar july = new GregorianCalendar(2026, Calendar.JULY, 1);
        assertEquals("2026-07", AbstractForecastView.monthKey(july));
        assertEquals("July 2026", AbstractForecastView.monthLabel(july));
    }

    @Test
    void runwayUnderAMonthIsSaidInWords() {
        // $54 against a $3,962 monthly shortfall was printed "runway drops to about 0 months".
        assertEquals("under a month", AbstractForecastView.runwayLength(0.01));
        assertEquals("under a month", AbstractForecastView.runwayLength(0.99));
        assertEquals("about 1 month", AbstractForecastView.runwayLength(1.2));
        assertEquals("about 2 months", AbstractForecastView.runwayLength(1.8));
    }

    @Test
    void theFloatIsSaidBesideTheBalanceThePeriodOpensWith() {
        assertEquals(" (the period opens with $354)", AbstractForecastView.periodOpensWith(354.17));
    }

    @Test
    void latestOfPicksTheLaterDateAndToleratesNull() {
        Calendar today = new GregorianCalendar(2026, Calendar.SEPTEMBER, 11);
        Calendar overdue = new GregorianCalendar(2026, Calendar.SEPTEMBER, 9);
        Calendar later = new GregorianCalendar(2026, Calendar.OCTOBER, 14);

        assertEquals(0, today.compareTo(AbstractForecastView.latestOf(today, overdue)),
                "an overdue deficit is due today, not in the past");
        assertEquals(0, later.compareTo(AbstractForecastView.latestOf(today, later)));
        assertEquals(0, today.compareTo(AbstractForecastView.latestOf(today, null)));
        assertNull(AbstractForecastView.latestOf(null, null));
    }

    @Test
    void negativeBalanceTimingDistinguishesNowOverdueBeforeAndInThePeriod() {
        Calendar today = new GregorianCalendar(2026, Calendar.SEPTEMBER, 11);
        Calendar periodStart = new GregorianCalendar(2026, Calendar.OCTOBER, 1);

        assertEquals(AbstractForecastView.NegativeBalanceTiming.NOW,
                AbstractForecastView.negativeBalanceTiming(true, today, today, periodStart));
        assertEquals(AbstractForecastView.NegativeBalanceTiming.OVERDUE, AbstractForecastView.negativeBalanceTiming(
                false, new GregorianCalendar(2026, Calendar.SEPTEMBER, 9), today, periodStart));
        assertEquals(AbstractForecastView.NegativeBalanceTiming.BEFORE_PERIOD, AbstractForecastView.negativeBalanceTiming(
                false, today, today, periodStart), "a deficit today, from occurrences due today, is still to come");
        assertEquals(AbstractForecastView.NegativeBalanceTiming.IN_PERIOD, AbstractForecastView.negativeBalanceTiming(
                false, new GregorianCalendar(2026, Calendar.OCTOBER, 6), today, periodStart));
    }

    @Test
    void anOverdueDeficitIsNotReportedAsHistory() {
        Calendar overdue = new GregorianCalendar(2026, Calendar.SEPTEMBER, 9);
        AbstractForecastView.NegativeBalanceTiming timing = AbstractForecastView.NegativeBalanceTiming.OVERDUE;

        String summary = AbstractForecastView.firstNegativeSummaryLine(timing, -40.99, overdue);
        String risk = AbstractForecastView.firstNegativeRiskLine(timing, -40.99, overdue);
        String timeline = AbstractForecastView.firstNegativeTimelineLine(timing, -40.99, overdue);

        for (String line : new String[]{summary, risk, timeline}) {
            assertTrue(line.contains("overdue") || line.contains("Overdue"), line);
            assertTrue(line.contains("09-09-2026"), line);
            assertTrue(line.contains("$-41"), line);
            assertFalse(line.contains("in the past") || line.contains("already occurred")
                    || line.contains("Historical"), line);
        }
    }

    @Test
    void anOverdrawnRegisterIsReportedAsOverdrawnNow() {
        Calendar today = new GregorianCalendar(2026, Calendar.SEPTEMBER, 11);
        AbstractForecastView.NegativeBalanceTiming timing = AbstractForecastView.NegativeBalanceTiming.NOW;

        assertEquals("The balance is already negative:  $-193 in the register today.",
                AbstractForecastView.firstNegativeSummaryLine(timing, -193.43, today));
        assertEquals("  - Critical: The account is already overdrawn at $-193.",
                AbstractForecastView.firstNegativeRiskLine(timing, -193.43, today));
        assertTrue(AbstractForecastView.firstNegativeTimelineLine(timing, -193.43, today)
                .contains("already overdrawn ($-193)"));
    }

    @Test
    void aFutureDeficitKeepsItsDate() {
        Calendar date = new GregorianCalendar(2026, Calendar.OCTOBER, 6);
        AbstractForecastView.NegativeBalanceTiming timing = AbstractForecastView.NegativeBalanceTiming.IN_PERIOD;

        assertEquals("The first negative balance is: $-2 on 10-06-2026.",
                AbstractForecastView.firstNegativeSummaryLine(timing, -2.25, date));
        assertEquals("  - Critical: The account first goes negative on 10-06-2026 at $-2.",
                AbstractForecastView.firstNegativeRiskLine(timing, -2.25, date));
    }

    @Test
    void copyCalendarCreatesDefensiveClone() {
        Calendar original = new GregorianCalendar(2026, Calendar.JUNE, 1);
        Calendar copy = AbstractForecastView.copyCalendar(original);

        assertNotSame(original, copy);
        assertEquals(original.getTimeInMillis(), copy.getTimeInMillis());

        copy.add(Calendar.DAY_OF_MONTH, 10);
        assertNotEquals(original.getTimeInMillis(), copy.getTimeInMillis());
    }
}

