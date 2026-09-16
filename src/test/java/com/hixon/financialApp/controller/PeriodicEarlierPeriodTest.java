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

            // and the variance test must not drag them in either:
            assertFalse(ForecastTransactionController.belongsToAnEarlierPeriod(howOccurs,
                    on(2026, Calendar.SEPTEMBER, 12), on(2026, Calendar.SEPTEMBER, 4), false), howOccurs.name());
        }
    }

    @Test
    @DisplayName("A charge too early for its candidate belongs to the period it fell in")
    void testChargeTooEarlyForItsCandidateBelongsEarlier() {

        // Deeper.com, 09-16-2026.  The 09-12 charge was assigned to the 10-04 occurrence:  22 days early for a
        // monthly subscription.  The previous-occurrence test alone did not catch it -- 09-12 falls after 09-04 --
        // because September's occurrence had already been consumed by a charge that drifted into it, which took it
        // out of the scored candidates.  A merchant match alone scores 100, and the score test is an OR, so the date
        // never got a vote.  Outside the item's normal variance, the charge is its own period's:
        assertTrue(ForecastTransactionController.belongsToAnEarlierPeriod(Item.HowOccurs.PERIODIC,
                on(2026, Calendar.SEPTEMBER, 12), on(2026, Calendar.SEPTEMBER, 4), false));

        // The same charge judged within the item's normal variance stays with its candidate, so the rule turns on
        // the variance rather than on the distance being non-zero:
        assertFalse(ForecastTransactionController.belongsToAnEarlierPeriod(Item.HowOccurs.PERIODIC,
                on(2026, Calendar.SEPTEMBER, 12), on(2026, Calendar.SEPTEMBER, 4), true));
    }

    @Test
    @DisplayName("An early payment within the item's normal variance is still its own occurrence's")
    void testEarlyPaymentWithinVarianceIsUnaffected() {

        // Charged 09-20 for the 09-24 occurrence, four days early, previous occurrence 08-24.  A PERIODIC window
        // starts at the planned date, so every early payment is PRIOR_TO its own occurrence and sits inside the
        // previous one's window;  only the variance separates this from the Deeper.com case above:
        assertFalse(ForecastTransactionController.belongsToAnEarlierPeriod(Item.HowOccurs.PERIODIC,
                on(2026, Calendar.SEPTEMBER, 20), on(2026, Calendar.AUGUST, 24), true));
    }

    @Test
    @DisplayName("A charge past its candidate's previous occurrence belongs earlier whatever the variance says")
    void testFullPeriodDriftIsCaughtRegardlessOfVariance() {

        // The drift the previous-occurrence test was added for is still caught when the caller reports the charge as
        // within normal variance, which is what the three-argument form passes:
        assertTrue(ForecastTransactionController.belongsToAnEarlierPeriod(Item.HowOccurs.PERIODIC,
                on(2026, Calendar.SEPTEMBER, 4), on(2026, Calendar.SEPTEMBER, 24), true));
        assertTrue(ForecastTransactionController.belongsToAnEarlierPeriod(Item.HowOccurs.PERIODIC,
                on(2026, Calendar.SEPTEMBER, 4), on(2026, Calendar.SEPTEMBER, 24)));
    }

    @Test
    @DisplayName("Deeper.com's whole cascade is rejected one charge at a time")
    void testTheObservedCascade() {

        // Each charge against the occurrence it had actually been assigned to, as found in the database on
        // 09-16-2026.  Every one is a month or more early for its candidate, so none of them survives the guard and
        // the cascade cannot rebuild itself:  the April charge reached August by taking one month at a time.
        int[][] chargeAndItsCandidatesPreviousOccurrence = {
                {Calendar.APRIL, 2, Calendar.JULY, 4},
                {Calendar.MAY, 2, Calendar.AUGUST, 4},
                {Calendar.JUNE, 2, Calendar.SEPTEMBER, 4},
                {Calendar.SEPTEMBER, 12, Calendar.SEPTEMBER, 4},
        };

        for (int[] c : chargeAndItsCandidatesPreviousOccurrence) {
            assertTrue(ForecastTransactionController.belongsToAnEarlierPeriod(Item.HowOccurs.PERIODIC,
                            on(2026, c[0], c[1]), on(2026, c[2], c[3]), false),
                    "charge on " + (c[0] + 1) + "-" + c[1]);
        }
    }
}
