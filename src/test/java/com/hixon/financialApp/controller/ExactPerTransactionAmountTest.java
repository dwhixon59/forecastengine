package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the rule that decides when the import may assign a split without asking.
 *
 * <p>It replaced earlier logic that acted on a <em>relevancy score</em>. That was removed after
 * real data showed it would have silently assigned a $150 rent transfer to Groceries — scoring 80.0
 * against a 25.0 runner-up, a wider lead than the case it was designed for. The lesson these tests
 * encode is that a wide lead in amount/date/importance space is not evidence of anything; only an
 * answer the user recorded in advance is.
 */
@DisplayName("Exact Per-Transaction Amount Tests")
public class ExactPerTransactionAmountTest {

    private static BudgetItemMerchant association(double perTransactionAmount) {
        BudgetItemMerchant association = mock(BudgetItemMerchant.class);
        when(association.getAmount()).thenReturn(perTransactionAmount);
        return association;
    }

    @Test
    @DisplayName("A configured per-transaction amount matching the transaction exactly is an answer")
    void testExactMatch() {
        // The real case: "Savings ($-1 On-Demand, $1 per trx)" against a $1.00 SAVE AS YOU GO transfer.
        assertTrue(TransactionSplitsController.hasExactPerTransactionAmount(association(1.00), -1.00));
        assertTrue(TransactionSplitsController.hasExactPerTransactionAmount(association(1.00), 1.00));
    }

    @Test
    @DisplayName("Matching is by currency comparison, not floating point equality")
    void testCurrencyComparison() {
        assertTrue(TransactionSplitsController.hasExactPerTransactionAmount(association(1.00), -1.0000001));
        assertFalse(TransactionSplitsController.hasExactPerTransactionAmount(association(1.00), -1.02));
    }

    @Test
    @DisplayName("A near miss is not a match - this rule never fires on proximity")
    void testNearMissIsNotAMatch() {
        // The whole point. $150 against a $200 weekly grocery budget scored 80.0 under the old rule
        // and would have been auto-assigned; here it is simply not an exact amount.
        assertFalse(TransactionSplitsController.hasExactPerTransactionAmount(association(200.00), -150.00));
        assertFalse(TransactionSplitsController.hasExactPerTransactionAmount(association(95.00), -86.95));
    }

    @Test
    @DisplayName("An association with no configured amount can never trigger the shortcut")
    void testNoConfiguredAmount() {
        // Most associations are like this, and they must always fall through to the question.
        assertFalse(TransactionSplitsController.hasExactPerTransactionAmount(association(0.0), -1.00));
        assertFalse(TransactionSplitsController.hasExactPerTransactionAmount(association(0.0), 0.0));
        assertFalse(TransactionSplitsController.hasExactPerTransactionAmount(null, -1.00));
    }

    @Test
    @DisplayName("A negative configured amount is not treated as a claim")
    void testNegativeConfiguredAmount() {
        assertFalse(TransactionSplitsController.hasExactPerTransactionAmount(association(-1.00), -1.00));
    }
}
