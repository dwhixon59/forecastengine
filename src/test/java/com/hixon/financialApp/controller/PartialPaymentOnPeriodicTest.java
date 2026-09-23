package com.hixon.financialApp.controller;

import org.junit.jupiter.api.Test;

import static com.hixon.financialApp.controller.ForecastController.looksLikePartialPaymentOnPeriodic;
import static com.hixon.financialApp.controller.ForecastController.partialPaymentOnPeriodicHint;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the nudge that fires when a periodic occurrence is closed out by a payment that
 * covered only a small part of it -- the signature of a collection item entered as a periodic one.
 *
 * <p>The behaviour itself does not change: a periodic occurrence is still zeroed no matter what
 * cleared.  This only decides when to point out that the item might be miscategorised.  The figures
 * are the two real cases from 09-23-2026: the $250.00 payment against the $1,242.00 "Danni's
 * contribution" occurrence (should nudge) and the electric bill that comes in a little under its
 * budget (must not).</p>
 */
class PartialPaymentOnPeriodicTest {

    @Test
    void aSmallPaymentAgainstALargeOccurrenceNudges() {
        // $250.00 cleared, $1,242.00 was on the occurrence -- $992.00 closed out.
        assertTrue(looksLikePartialPaymentOnPeriodic(-250.00, -1242.00));
    }

    @Test
    void anIncomeContributionNudgesTheSameWay() {
        // The other side of the same movement:  +$250.00 against a +$1,242.00 income occurrence.
        assertTrue(looksLikePartialPaymentOnPeriodic(250.00, 1242.00));
    }

    @Test
    void aBillThatCameInUnderBudgetDoesNotNudge() {
        // $350.00 electric bill against a $400.00 budget covers most of it -- the $50.00 is normal.
        assertFalse(looksLikePartialPaymentOnPeriodic(-350.00, -400.00));
    }

    @Test
    void anExactPaymentDoesNotNudge() {
        assertFalse(looksLikePartialPaymentOnPeriodic(-400.00, -400.00));
    }

    @Test
    void anOverPaymentDoesNotNudge() {
        // More cleared than was left is not a partial payment.
        assertFalse(looksLikePartialPaymentOnPeriodic(-450.00, -400.00));
    }

    @Test
    void aSmallShortfallBelowTheFloorDoesNotNudge() {
        // Covers under half, but only $40.00 is left behind -- not worth a nudge.
        assertFalse(looksLikePartialPaymentOnPeriodic(-30.00, -70.00));
    }

    @Test
    void theHintNamesTheAmountClosedOutAndThePayee() {
        String hint = partialPaymentOnPeriodicHint(-250.00, -1242.00, "Danni's contribution");
        assertTrue(hint.contains("$992.00"), hint);
        assertTrue(hint.contains("Danni's contribution"), hint);
        assertTrue(hint.contains("collection item"), hint);
    }
}

