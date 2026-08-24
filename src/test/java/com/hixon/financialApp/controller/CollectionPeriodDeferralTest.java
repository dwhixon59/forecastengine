package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ForecastTransactionController#mustDeferToPeriodForCollection}.
 *
 * <p>A COLLECTION item accumulates many charges against one period's budget. Once that budget is
 * exhausted its forecast transaction is zero, and zero transactions are excluded from scoring — so
 * the only candidate left is a <em>later</em> period. Assigning to it silently spends next month's
 * grocery money on this month's groceries, which is what was observed: every August grocery charge
 * landing on the September 4 forecast transaction.
 */
@DisplayName("Collection Period Deferral Tests")
public class CollectionPeriodDeferralTest {

    @Test
    @DisplayName("A COLLECTION charge before its best candidate's window defers to its own period")
    void testCollectionPriorToDefers() {
        // Once August's grocery budget is exhausted its forecast transaction is zero and therefore
        // unscored, leaving only September. Assigning to September would spend next month's money.
        assertTrue(ForecastTransactionController.mustDeferToPeriodForCollection(
                Item.HowOccurs.COLLECTION, ForecastTransaction.Timing.PRIOR_TO));
    }

    @Test
    @DisplayName("A COLLECTION charge AFTER the candidate's window is the ordinary roll-forward")
    void testCollectionAfterDoesNotDefer() {
        // Unspent money legitimately carries forward; that path already works and must not change.
        assertFalse(ForecastTransactionController.mustDeferToPeriodForCollection(
                Item.HowOccurs.COLLECTION, ForecastTransaction.Timing.AFTER));
    }

    @Test
    @DisplayName("Non-collection items are unaffected in either direction")
    void testOtherOccurrenceTypesUnaffected() {
        for (Item.HowOccurs howOccurs : Item.HowOccurs.values()) {
            if (howOccurs == Item.HowOccurs.COLLECTION) {
                continue;
            }
            assertFalse(ForecastTransactionController.mustDeferToPeriodForCollection(
                    howOccurs, ForecastTransaction.Timing.PRIOR_TO), howOccurs.name());
        }
    }

    @Test
    @DisplayName("A WITHIN match is never deferred - it is already the right period")
    void testWithinIsNeverDeferred() {
        assertFalse(ForecastTransactionController.mustDeferToPeriodForCollection(
                Item.HowOccurs.COLLECTION, ForecastTransaction.Timing.WITHIN));
    }}
