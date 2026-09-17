package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Forecast#endDateFor}:  a forecast of n months ends the day before the same date n
 * months later.
 */
@DisplayName("Forecast end date Tests")
class ForecastEndDateTest {

    private static Calendar dateOf(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    @Test
    @DisplayName("The 09-17-2026 update:  twelve months from 10-01-2026 end on 09-30-2027, not 10-01-2027")
    void twelveMonthsFromTheFirst() {
        assertEquals("09-30-2027", Utility.calendarDateToStringDate(
                Forecast.endDateFor(dateOf(2026, Calendar.OCTOBER, 1), 12)));
    }

    @Test
    @DisplayName("The window really is twelve months:  the summary counts twelve, not thirteen")
    void summaryCountsTwelveMonths() {
        Calendar start = dateOf(2026, Calendar.OCTOBER, 1);
        assertEquals(12, Utility.monthsBetweenDatesInclusive(start, Forecast.endDateFor(start, 12)));
    }

    @Test
    @DisplayName("A mid-month start ends the day before the same date")
    void midMonthStart() {
        assertEquals("09-10-2027", Utility.calendarDateToStringDate(
                Forecast.endDateFor(dateOf(2026, Calendar.SEPTEMBER, 11), 12)));
    }

    @Test
    @DisplayName("The start date is not changed")
    void startDateUntouched() {
        Calendar start = dateOf(2026, Calendar.OCTOBER, 1);
        Forecast.endDateFor(start, 12);
        assertEquals("10-01-2026", Utility.calendarDateToStringDate(start));
    }
}
