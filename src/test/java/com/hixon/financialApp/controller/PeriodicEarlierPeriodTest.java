package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ForecastTransactionController#belongsToAnEarlierPeriod}.
 *
 * <p>Only non-zero occurrences are scored, and a merchant match alone scores enough to auto-assign.  So once one
 * period's occurrence of a subscription was spent, the next charge was assigned to the occurrence after it, however
 * far away, and every charge after that drifted one further:  by 09-11-2026 the 09-09 AppleTV+ charge sat on the
 * 11-07 occurrence and the 09-04 Disney charge on 10-24.
 */
@DisplayName("Periodic Earlier-Period Tests")
class PeriodicEarlierPeriodTest {

    private static Calendar on(int year, int month, int day) {
        return new GregorianCalendar(year, month, day);
    }

    @Test
    @DisplayName("A charge before the candidate's previous occurrence belongs to that earlier period")
    void testChargeBeforePreviousOccurrenceBelongsEarlier() {
        // Disney:  charged 09-04, candidate 10-24, whose previous occurrence is 09-24.
        assertTrue(ForecastTransactionController.belongsToAnEarlierPeriod(Item.HowOccurs.PERIODIC,
                on(2026, Calendar.SEPTEMBER, 4), on(2026, Calendar.SEPTEMBER, 24)));

        // AppleTV+:  charged 09-09, candidate 11-07, whose previous occurrence is 10-07.
        assertTrue(ForecastTransactionController.belongsToAnEarlierPeriod(Item.HowOccurs.VARIABLE_PERIODIC,
                on(2026, Calendar.SEPTEMBER, 9), on(2026, Calendar.OCTOBER, 7)));
    }

    @Test
    @DisplayName("A charge on the previous occurrence's date is that occurrence's charge")
    void testChargeOnPreviousOccurrenceDateBelongsEarlier() {
        assertTrue(ForecastTransactionController.belongsToAnEarlierPeriod(Item.HowOccurs.PERIODIC,
                on(2026, Calendar.SEPTEMBER, 24), on(2026, Calendar.SEPTEMBER, 24)));
    }

    @Test
    @DisplayName("A charge a few days early for its own occurrence is still that occurrence's")
    void testEarlyPaymentOfOwnOccurrenceIsUnaffected() {
        // Charged 09-20 for the 09-24 occurrence, whose previous occurrence is 08-24.
        assertFalse(ForecastTransactionController.belongsToAnEarlierPeriod(Item.HowOccurs.PERIODIC,
                on(2026, Calendar.SEPTEMBER, 20), on(2026, Calendar.AUGUST, 24)));
    }

    @Test
    @DisplayName("Without a previous occurrence there is no earlier period to belong to")
    void testNoPreviousOccurrence() {
        assertFalse(ForecastTransactionController.belongsToAnEarlierPeriod(Item.HowOccurs.PERIODIC,
                on(2026, Calendar.SEPTEMBER, 4), null));
    }

    @Test
    @DisplayName("Only periodic items are affected;  collections and envelopes have their own rules")
    void testOtherOccurrenceTypesUnaffected() {
        for (Item.HowOccurs howOccurs : Item.HowOccurs.values()) {
            if (howOccurs == Item.HowOccurs.PERIODIC || howOccurs == Item.HowOccurs.VARIABLE_PERIODIC) {
                continue;
            }
            assertFalse(ForecastTransactionController.belongsToAnEarlierPeriod(howOccurs,
                    on(2026, Calendar.SEPTEMBER, 4), on(2026, Calendar.SEPTEMBER, 24)), howOccurs.name());
        }
    }
}
