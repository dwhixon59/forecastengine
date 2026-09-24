package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.register.Transaction;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ImportController#transactionsNotHeld}:  the check after a pending import that every
 * row of the file reached the register.
 */
class TransactionsNotHeldTest {

    @Test
    void aRowSkippedBecauseItHadClearedIsNotReportedMissing() {
        // 09-24-2026:  the Citi paste's pending Spectrum charge was correctly skipped, its cleared copy
        // being in the register under the posted wording, and was reported as $79.99 missing.
        Transaction pending = transaction("Spectrum SAINT LOUIS USA", -79.99);
        Transaction cleared = transaction("Spectrum SAINT LOUIS MO", -79.99);

        List<Transaction> missing = ImportController.transactionsNotHeld(
                List.of(pending), List.of(cleared), identitySetOf(pending));

        assertTrue(missing.isEmpty());
    }

    @Test
    void theSameRowIsStillReportedWhenItWasNotSkipped() {
        // Without the skip, a row the register does not hold is exactly what the check is for.
        Transaction pending = transaction("Spectrum SAINT LOUIS USA", -79.99);
        Transaction cleared = transaction("Spectrum SAINT LOUIS MO", -79.99);

        List<Transaction> missing = ImportController.transactionsNotHeld(
                List.of(pending), List.of(cleared), identitySetOf());

        assertEquals(List.of(pending), missing);
    }

    @Test
    void aSkipCoversOnlyTheRowThatWasSkipped() {
        // Two identical pending charges; only the first was skipped as cleared.  The second must still
        // be reported if the register does not hold it.
        Transaction first = transaction("PURCHASE PUBLIX #361 SARASOTA FL CARD0148", -51.49);
        Transaction second = transaction("PURCHASE PUBLIX #361 SARASOTA FL CARD0148", -51.49);

        List<Transaction> missing = ImportController.transactionsNotHeld(
                List.of(first, second), List.of(transaction("PUBLIX #361", -51.49)), identitySetOf(first));

        assertEquals(1, missing.size());
        assertSame(second, missing.get(0));
    }

    @Test
    void aRowTheRegisterHoldsIsNotReported() {
        Transaction pending = transaction("PURCHASE WALMART.COM BENTONVILLE AR CARD0148", -277.16);
        Transaction held = transaction("PURCHASE WALMART.COM BENTONVILLE AR CARD0148", -277.16);

        assertTrue(ImportController.transactionsNotHeld(List.of(pending), List.of(held), null).isEmpty());
    }

    @Test
    void theSamePayeeWithADifferentAmountIsNotAMatch() {
        Transaction pending = transaction("PURCHASE PUBLIX #1553 BRADENTON FL CARD0148", -36.05);
        Transaction held = transaction("PURCHASE PUBLIX #1553 BRADENTON FL CARD0148", -36.50);

        assertEquals(List.of(pending),
                ImportController.transactionsNotHeld(List.of(pending), List.of(held), identitySetOf()));
    }

    private static Transaction transaction(String payee, double amount) {
        Transaction t = mock(Transaction.class);
        when(t.getPayee()).thenReturn(payee);
        when(t.getAmount()).thenReturn(amount);
        return t;
    }

    private static Set<Transaction> identitySetOf(Transaction... transactions) {
        Set<Transaction> set = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(set, transactions);
        return set;
    }
}
