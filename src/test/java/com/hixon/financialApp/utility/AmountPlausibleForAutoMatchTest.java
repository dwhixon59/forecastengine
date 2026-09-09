package com.hixon.financialApp.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the amount safeguard that decides whether an import may assign a split to a
 * forecast occurrence without asking.
 *
 * <p>The safeguard used to be judged on the occurrence's <em>remaining</em> amount alone, which is
 * right for an occurrence that has been partly consumed but wrong the moment remaining and budgeted
 * drift apart.  Seen in the import of 09-04-2026:  a $309.23 State Farm charge against an item
 * budgeting $309.00 was declared to "differ significantly" because that occurrence was carrying a
 * remaining amount of $563.72.  The user answered "adjust" and re-budgeted the item by 23 cents to
 * make a question go away that should never have been asked.
 *
 * <p>Matching either amount is now enough.  These tests hold both halves of that:  the false alarm
 * is gone, and nothing the safeguard exists to catch got through with it.
 */
class AmountPlausibleForAutoMatchTest {

    @Test
    @DisplayName("A charge matching the budgeted amount is plausible even when remaining has drifted")
    void testMatchesBudgetedWhenRemainingHasDrifted() {

        // The State Farm case, to the cent:  transaction $-309.23, budgeted $-309.00,
        // remaining $-563.72.
        assertFalse(ForecastTransactionMatcher.isAmountWithinAutoMatchTolerance(-309.23, -563.72),
                "against remaining alone this is what raised the false question");

        assertTrue(ForecastTransactionMatcher.isAmountPlausibleForAutoMatch(-309.23, -563.72, -309.00),
                "$309.23 against a budgeted $309.00 is a match, whatever remaining says");
    }

    @Test
    @DisplayName("A charge matching the remaining amount is still plausible")
    void testMatchesRemainingOnPartlyConsumedOccurrence() {

        // The case the remaining-amount test was written for:  an occurrence budgeting $200 that
        // has $50 left, and a $50 charge arrives.  Reading only the budgeted amount would break
        // this, so both are consulted.
        assertTrue(ForecastTransactionMatcher.isAmountPlausibleForAutoMatch(-50.00, -50.00, -200.00),
                "the remaining amount is the right comparison for a partly consumed occurrence");
    }

    @Test
    @DisplayName("A wildly wrong amount is still caught - it is wrong against both figures")
    void testWildlyWrongAmountIsStillRejected() {

        // The reason the safeguard exists:  a $1,200 charge must not be assigned to a $50 planned
        // expense because the merchant and date happened to line up.  Widening the test to two
        // amounts must not let that through.
        assertFalse(ForecastTransactionMatcher.isAmountPlausibleForAutoMatch(-1200.00, -50.00, -50.00),
                "$1,200 is not a $50 expense under either reading");

        // And the transfer case from the 09-03 log:  $2.00 against a $2,000 plan.
        assertFalse(ForecastTransactionMatcher.isAmountPlausibleForAutoMatch(-2.00, -2000.00, -2000.00),
                "a thousand-fold difference is not a match");
    }

    @Test
    @DisplayName("Sign is ignored, as it is for the single-amount test")
    void testSignIsIgnored() {

        // Deposits are positive and expenses negative depending on the register;  the comparison
        // has always been on magnitude, and widening it must not quietly change that.
        assertTrue(ForecastTransactionMatcher.isAmountPlausibleForAutoMatch(309.23, -563.72, -309.00),
                "a positive transaction still matches a negative budgeted amount of the same size");
    }

    @Test
    @DisplayName("Neither amount matching means the user is still asked")
    void testNeitherAmountMatchingStillAsks() {

        // The regression guard in the other direction:  consulting a second amount must not turn
        // the safeguard off.  $22.56 against a $19.99 plan is the Netflix price rise, and it is
        // still outside tolerance against both figures.
        assertFalse(ForecastTransactionMatcher.isAmountPlausibleForAutoMatch(-22.56, -19.99, -19.99),
                "a real price change is still worth asking about");
    }
}
