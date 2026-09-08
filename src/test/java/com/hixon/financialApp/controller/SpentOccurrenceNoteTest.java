package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for telling the user that the occurrence a charge belongs to is already spent.
 *
 * <p>The case that prompted it: a $12.95 Walmart+ membership charge on 09-08-2026 was not
 * auto-matched, and the prompt offered
 *
 * <pre>
 *   1.  Groceries (Food and Beverage, $-13 Monthly, 09-04-2026, Walmart+ Membership), Relevancy Score: 79.4
 * </pre>
 *
 * with no indication of why the obvious item had not simply taken it. Every occurrence of that item
 * near the date was already at zero, so the matcher had nothing to match against — it filters
 * fully-reconciled occurrences out of its candidates. The relevancy score is a different scale from
 * the match threshold and says nothing about this, which is what made the list confusing.
 */
@DisplayName("Spent Occurrence Note Tests")
class SpentOccurrenceNoteTest {

    private static Calendar on(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    @Test
    @DisplayName("A spent occurrence is named, with its date")
    void testSpentOccurrenceIsReported() {

        // The 09-02 occurrence of the Walmart+ Membership item, at zero when the 09-08 charge arrived.
        assertEquals("  <- its 09-02-2026 occurrence is already fully spent",
                BudgetController.spentOccurrenceNote(on(2026, Calendar.SEPTEMBER, 2), 0.00));
    }

    @Test
    @DisplayName("An occurrence with money left says nothing")
    void testUnspentOccurrenceIsSilent() {

        // If there is budget left then the item is a live candidate and the failure to match had some
        // other cause;  claiming otherwise would send the user looking in the wrong place.
        assertEquals("", BudgetController.spentOccurrenceNote(on(2026, Calendar.OCTOBER, 27), -12.95));
    }

    @Test
    @DisplayName("No occurrence nearby means nothing to report")
    void testNoOccurrenceIsSilent() {

        assertEquals("", BudgetController.spentOccurrenceNote(null, 0.00));
    }

    @Test
    @DisplayName("A rounding-error remainder counts as spent")
    void testCurrencyComparisonNotExactZero() {

        // Currency is compared through the shared threshold, never with ==.  A tenth of a cent left
        // on an occurrence is spent for every purpose the user cares about.
        assertEquals("  <- its 09-02-2026 occurrence is already fully spent",
                BudgetController.spentOccurrenceNote(on(2026, Calendar.SEPTEMBER, 2), -0.0001));
    }

    @Test
    @DisplayName("Only periodic items report this")
    void testOnlyPeriodicItems() {

        // PERIODIC is the only kind with one planned occurrence per period to be spent.
        assertTrue(BudgetController.reportsSpentOccurrences(Item.HowOccurs.PERIODIC));

        // A COLLECTION item is meant to be drawn down over many charges and already says so in its
        // own words when it runs out -- the overage prompt asks what to do about it.  Repeating that
        // here would be a second voice saying the same thing.
        assertFalse(BudgetController.reportsSpentOccurrences(Item.HowOccurs.COLLECTION));

        // An UNPLANNED item generates no occurrences at all, so there is never one to have been spent.
        assertFalse(BudgetController.reportsSpentOccurrences(Item.HowOccurs.UNPLANNED));

        // ENVELOPE accumulates across periods rather than being consumed within one, so a zero
        // balance is not the same event and would not mean what the note says.
        assertFalse(BudgetController.reportsSpentOccurrences(Item.HowOccurs.ENVELOPE));

        assertFalse(BudgetController.reportsSpentOccurrences(null), "an unset kind must not throw");
    }
}
