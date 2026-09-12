package com.hixon.financialApp.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the messages the import prints about what it did to an occurrence.  Each one was misleading in the
 * daily updates of 09-11-2026.
 */
class ImportMessagesTest {

    @Test
    void ignoredOverageSaysWhatWasUsedUpAndWhatWasIgnored() {
        // $132.87 of HelloFresh against the $4.92 left in Groceries:  the $4.92 is used up, $127.95 is ignored.
        String message = ForecastController.ignoredOverageMessage(-132.87, -4.92, 127.95, "Groceries 09-11");

        assertTrue(message.startsWith("$-132.87 assigned to Groceries 09-11."), message);
        assertTrue(message.contains("used up the $4.92 left"), message);
        assertTrue(message.contains("the $127.95 over that is ignored"), message);
        assertFalse(message.contains("not deducted"), "part of it was deducted: " + message);
    }

    @Test
    void moneyInIsReceivedNotSpent() {
        assertEquals("received", ForecastController.alreadyRecordedAs(20.00));
        assertEquals("spent", ForecastController.alreadyRecordedAs(-10.99));
    }

    @Test
    void adjustSaysTheForecastKeepsItsAmountUntilRegenerated() {
        String message = ForecastTransactionController.adjustedBudgetMessage("Credit card interest", -264.18, -237.23);

        assertTrue(message.startsWith("Budgeted amount for Credit card interest changed to $-264.18."), message);
        assertTrue(message.contains("keeps planning $-237.23"), message);
        assertTrue(message.contains("until it is regenerated"), message);
    }

    @Test
    void importSummaryCountsASingleTransactionInTheSingular() {
        assertEquals("1 transaction", ImportSummaryController.transactionCount(1));
        assertEquals("2 transactions", ImportSummaryController.transactionCount(2));
        assertEquals("0 transactions", ImportSummaryController.transactionCount(0));
    }
}
