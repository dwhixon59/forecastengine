package com.hixon.financialApp.controller;

import org.junit.jupiter.api.Test;

import static com.hixon.financialApp.controller.ForecastController.calculateOverage;
import static com.hixon.financialApp.controller.ForecastController.exceedsRemainingAmount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the overage decision that {@code ForecastController.deductSplitAmount} makes on collection type
 * budget items.
 *
 * <p>Getting this comparison wrong is not a rounding nuisance: answering the resulting prompt with IGNORE zeroes the
 * remaining amount for the period, and a zeroed forecast transaction on an item whose budgeted amount is non-zero
 * matches none of the queries in {@code ForecastTransactionAndItemDatabaseIterator}, so the item silently vanishes
 * from the rendered forecast for that month.</p>
 *
 * <p>The income figures below are the real "Room rental and utilities" item from the Bill Pay Danni budget: $1,242.00
 * a month collected from a tenant in instalments.</p>
 */
class ForecastControllerOverageTest {

    private static final boolean INCOME = true;
    private static final boolean EXPENSE = false;

    /** Tolerance for comparing the returned dollar amounts. */
    private static final double TOLERANCE = 0.0001;

    /*
     * Income items.  The amounts are positive, so a split overshoots when it is MORE positive than what is left.
     */

    @Test
    void partialIncomeCollectionIsNotAnOverage() {

        // $400.00 received against $1,242.00 still to collect is a partial payment, not an overage.
        assertFalse(exceedsRemainingAmount(INCOME, 1242.00, 400.00));
        assertEquals(-842.00, calculateOverage(INCOME, 1242.00, 400.00), TOLERANCE);
    }

    @Test
    void smallPartialIncomeCollectionIsNotAnOverage() {

        // The $50.00 instalment that triggered the original defect.
        assertFalse(exceedsRemainingAmount(INCOME, 1242.00, 50.00));
    }

    @Test
    void incomeCollectionForTheExactAmountIsNotAnOverage() {

        assertFalse(exceedsRemainingAmount(INCOME, 1242.00, 1242.00));
        assertEquals(0.00, calculateOverage(INCOME, 1242.00, 1242.00), TOLERANCE);
    }

    @Test
    void overCollectedIncomeIsAnOverage() {

        // The tenant paid $1,245.00 against $1,242.00 owed, so $3.00 more arrived than was budgeted.
        assertTrue(exceedsRemainingAmount(INCOME, 1242.00, 1245.00));
        assertEquals(3.00, calculateOverage(INCOME, 1242.00, 1245.00), TOLERANCE);
    }

    @Test
    void incomeCollectedAfterThePeriodIsExhaustedIsAnOverage() {

        // Nothing is left to collect, so any further receipt is over the budgeted amount.
        assertTrue(exceedsRemainingAmount(INCOME, 0.00, 245.00));
        assertEquals(245.00, calculateOverage(INCOME, 0.00, 245.00), TOLERANCE);
    }

    /*
     * Expense items.  The amounts are negative, so a split overshoots when it is MORE negative than what is left.
     * These cases must behave exactly as they did before income was handled.
     */

    @Test
    void overspentExpenseIsAnOverage() {

        assertTrue(exceedsRemainingAmount(EXPENSE, -1242.00, -1300.00));
        assertEquals(58.00, calculateOverage(EXPENSE, -1242.00, -1300.00), TOLERANCE);
    }

    @Test
    void partialExpenseSpendIsNotAnOverage() {

        assertFalse(exceedsRemainingAmount(EXPENSE, -1242.00, -400.00));
        assertEquals(-842.00, calculateOverage(EXPENSE, -1242.00, -400.00), TOLERANCE);
    }

    @Test
    void expenseForTheExactAmountIsNotAnOverage() {

        assertFalse(exceedsRemainingAmount(EXPENSE, -1242.00, -1242.00));
        assertEquals(0.00, calculateOverage(EXPENSE, -1242.00, -1242.00), TOLERANCE);
    }

    @Test
    void expenseSpentAfterThePeriodIsExhaustedIsAnOverage() {

        assertTrue(exceedsRemainingAmount(EXPENSE, 0.00, -85.00));
        assertEquals(85.00, calculateOverage(EXPENSE, 0.00, -85.00), TOLERANCE);
    }

    @Test
    void refundToAnExpenseIsNotAnOverage() {

        // A positive split against an expense item is a refund or reimbursement.  It moves the remaining amount back
        // towards zero, so it can never overshoot.
        assertFalse(exceedsRemainingAmount(EXPENSE, -1242.00, 75.00));
    }

    /*
     * Currency comparison.  A split is only an overage once it clears the currency comparison threshold, so amounts
     * that agree to the cent are treated as an exact match rather than as a fractional overspend.
     */

    @Test
    void subCentOvershootIsNotAnOverage() {

        assertFalse(exceedsRemainingAmount(EXPENSE, -1242.00, -1242.001));
        assertFalse(exceedsRemainingAmount(INCOME, 1242.00, 1242.001));
    }

    @Test
    void oneCentOvershootIsAnOverage() {

        assertTrue(exceedsRemainingAmount(EXPENSE, -1242.00, -1242.01));
        assertTrue(exceedsRemainingAmount(INCOME, 1242.00, 1242.01));
    }
}
