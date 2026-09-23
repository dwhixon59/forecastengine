package com.hixon.financialApp.controller;

import com.hixon.financialApp.controller.ForecastController.RollStep;
import org.junit.jupiter.api.Test;

import static com.hixon.financialApp.controller.ForecastController.rollOntoOccurrence;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ForecastController#rollOntoOccurrence(double, double)}, the sign-aware step
 * that distributes a rolled-forward amount across occurrences.
 *
 * <p>These lock in the fix for the runaway roll seen on 09-23-2026: a $1,200.00 instalment rolled
 * onto the "Room rental and utilities" income item, whose occurrences are positive, zeroed the
 * first occurrence and carried -$42.00 forward, then repeated all the way to $-14,946.00.  The
 * income cases below are that item; the expense cases are the Publix grocery roll from the same
 * run, which must keep behaving as it did.</p>
 */
class RollOntoOccurrenceTest {

    private static final double TOLERANCE = 0.0001;

    /*
     * Income occurrences are positive.  An instalment that fits leaves the difference; one that is
     * larger fills the occurrence to zero and carries the excess.
     */

    @Test
    void incomeInstalmentThatFitsLeavesTheDifferenceAndCarriesNothing() {
        // $1,200.00 collected against a $1,242.00 occurrence leaves $42.00 -- and does not cascade.
        RollStep step = rollOntoOccurrence(1242.00, 1200.00);
        assertEquals(1200.00, step.applied(), TOLERANCE);
        assertEquals(42.00, step.newRemaining(), TOLERANCE);
        assertEquals(0.00, step.carriedOver(), TOLERANCE);
    }

    @Test
    void incomeOccurrenceAlreadyEmptyCarriesTheWholeAmount() {
        // The overdrawn occurrence had nothing left, so it applies nothing and rolls all of it.
        RollStep step = rollOntoOccurrence(0.00, 1200.00);
        assertEquals(0.00, step.applied(), TOLERANCE);
        assertEquals(0.00, step.newRemaining(), TOLERANCE);
        assertEquals(1200.00, step.carriedOver(), TOLERANCE);
    }

    @Test
    void incomeAmountLargerThanTheOccurrenceFillsItAndCarriesTheExcess() {
        RollStep step = rollOntoOccurrence(1242.00, 2000.00);
        assertEquals(1242.00, step.applied(), TOLERANCE);
        assertEquals(0.00, step.newRemaining(), TOLERANCE);
        assertEquals(758.00, step.carriedOver(), TOLERANCE);
    }

    @Test
    void incomeExactMatchSettlesToZero() {
        RollStep step = rollOntoOccurrence(1242.00, 1242.00);
        assertEquals(1242.00, step.applied(), TOLERANCE);
        assertEquals(0.00, step.newRemaining(), TOLERANCE);
        assertEquals(0.00, step.carriedOver(), TOLERANCE);
    }

    /*
     * Expense occurrences are negative.  These must behave exactly as the roll did before income was
     * handled.
     */

    @Test
    void expenseThatFitsLeavesTheDifference() {
        // $30.99 of grocery overage rolled onto a $75.00 occurrence leaves $44.01.
        RollStep step = rollOntoOccurrence(-75.00, -30.99);
        assertEquals(-30.99, step.applied(), TOLERANCE);
        assertEquals(-44.01, step.newRemaining(), TOLERANCE);
        assertEquals(0.00, step.carriedOver(), TOLERANCE);
    }

    @Test
    void expensePartlyRemainingOccurrenceFillsToZeroAndCarriesTheExcess() {
        // The 09-18 grocery occurrence had $20.50 left when a $51.49 Publix charge arrived.
        RollStep step = rollOntoOccurrence(-20.50, -51.49);
        assertEquals(-20.50, step.applied(), TOLERANCE);
        assertEquals(0.00, step.newRemaining(), TOLERANCE);
        assertEquals(-30.99, step.carriedOver(), TOLERANCE);
    }

    @Test
    void expenseExactMatchSettlesToZero() {
        RollStep step = rollOntoOccurrence(-150.00, -150.00);
        assertEquals(-150.00, step.applied(), TOLERANCE);
        assertEquals(0.00, step.newRemaining(), TOLERANCE);
        assertEquals(0.00, step.carriedOver(), TOLERANCE);
    }

    /**
     * The whole roll, iterated the way {@code deductSplitAmount} iterates it, must terminate and
     * conserve the amount -- the runaway did neither.
     */
    @Test
    void incomeRollTerminatesAndConservesAcrossOccurrences() {
        double[] occurrences = {0.00, 1242.00, 1242.00, 1242.00}; // overdrawn, then three targets
        double carry = 1200.00;
        double applied = 0.00;
        int steps = 0;

        for (int i = 0; i < occurrences.length && Math.abs(carry) > TOLERANCE; i++) {
            RollStep step = rollOntoOccurrence(occurrences[i], carry);
            occurrences[i] = step.newRemaining();
            applied += step.applied();
            carry = step.carriedOver();
            steps++;
        }

        assertEquals(1200.00, applied, TOLERANCE, "every dollar rolled must land somewhere");
        assertEquals(0.00, carry, TOLERANCE, "nothing should be left uncarried");
        assertEquals(2, steps, "it should settle on the first target, not cascade");
        assertEquals(42.00, occurrences[1], TOLERANCE, "the first target keeps $1,242 - $1,200");
        assertEquals(1242.00, occurrences[2], TOLERANCE, "later occurrences are untouched");
    }
}

