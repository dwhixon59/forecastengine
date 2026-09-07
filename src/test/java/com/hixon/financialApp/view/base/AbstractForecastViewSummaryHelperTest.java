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
    void copyCalendarCreatesDefensiveClone() {
        Calendar original = new GregorianCalendar(2026, Calendar.JUNE, 1);
        Calendar copy = AbstractForecastView.copyCalendar(original);

        assertNotSame(original, copy);
        assertEquals(original.getTimeInMillis(), copy.getTimeInMillis());

        copy.add(Calendar.DAY_OF_MONTH, 10);
        assertNotEquals(original.getTimeInMillis(), copy.getTimeInMillis());
    }
}

