package com.hixon.financialApp.model.financialinstitution;

import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

class DateRangeTest {

    @Test
    void containsBothEndsAndIgnoresTheTimeOfDay() {
        Calendar end = day(2026, 9, 15);
        end.set(Calendar.HOUR_OF_DAY, 9);
        DateRange range = DateRange.of(day(2026, 9, 14), end);

        Calendar lateOnTheLastDay = day(2026, 9, 15);
        lateOnTheLastDay.set(Calendar.HOUR_OF_DAY, 23);

        assertTrue(range.contains(day(2026, 9, 14)));
        assertTrue(range.contains(lateOnTheLastDay));
        assertFalse(range.contains(day(2026, 9, 13)));
        assertFalse(range.contains(day(2026, 9, 16)));
        assertFalse(range.contains(null));
    }

    @Test
    void unboundedContainsEverythingAndEmptyContainsNothing() {
        assertTrue(DateRange.unbounded().contains(day(1999, 1, 1)));
        assertTrue(DateRange.unbounded().contains(null));
        assertFalse(DateRange.empty().contains(day(2026, 9, 15)));
    }

    @Test
    void onlyARangeOfSpecificDaysIsBounded() {
        assertTrue(DateRange.of(day(2026, 9, 14), day(2026, 9, 15)).isBounded());
        assertFalse(DateRange.unbounded().isBounded());
        assertFalse(DateRange.empty().isBounded());
    }

    @Test
    void rejectsARangeThatEndsBeforeItStarts() {
        assertThrows(IllegalArgumentException.class, () -> DateRange.of(day(2026, 9, 15), day(2026, 9, 14)));
        assertThrows(IllegalArgumentException.class, () -> DateRange.of(null, day(2026, 9, 14)));
    }

    @Test
    void isNotChangedByChangingTheCalendarsItWasBuiltFrom() {
        Calendar start = day(2026, 9, 14);
        DateRange range = DateRange.of(start, day(2026, 9, 15));
        start.add(Calendar.DAY_OF_MONTH, 5);

        assertTrue(range.contains(day(2026, 9, 14)));
    }

    private static Calendar day(int year, int month, int dayOfMonth) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, dayOfMonth);
        return c;
    }
}
