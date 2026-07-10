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

