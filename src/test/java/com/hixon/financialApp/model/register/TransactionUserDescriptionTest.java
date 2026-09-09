package com.hixon.financialApp.model.register;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for the {@code user_description} field added to {@link Transaction}.
 *
 * <p>The column already existed, had a FULLTEXT index, and was read by
 * {@code TransactionUtilities.getByUserDescriptionFullText} -- but nothing in the application ever
 * wrote it, so every recent row was NULL.  These tests cover the setter's contract and the presence
 * of the column in the SQL the entity generates.  No database is needed.</p>
 */
@DisplayName("Transaction user description")
class TransactionUserDescriptionTest {

    /** A Transaction whose real methods run, without the constructors' register/history setup. */
    private final Transaction transaction =
            mock(Transaction.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

    @Test
    @DisplayName("defaults to null, because most transfers carry no memo")
    void defaultsToNull() {
        assertNull(transaction.getUserDescription());
    }

    @Test
    @DisplayName("stores a memo and marks the entity dirty so save() writes it")
    void storesMemoAndSetsDirty() {
        transaction.setUserDescription("JOINT SPENDING MONEY JSA");
        assertEquals("JOINT SPENDING MONEY JSA", transaction.getUserDescription());
        assertTrue(transaction.isDirty(), "the dirty flag gates save(), per the entity convention");
    }

    @Test
    @DisplayName("normalizes an absent memo to null rather than a blank string")
    void normalizesBlankToNull() {
        transaction.setUserDescription(null);
        assertNull(transaction.getUserDescription());

        transaction.setUserDescription("   ");
        assertNull(transaction.getUserDescription());
    }

    @Test
    @DisplayName("trims surrounding whitespace")
    void trimsWhitespace() {
        transaction.setUserDescription("  RENT  ");
        assertEquals("RENT", transaction.getUserDescription());
    }

    @Test
    @DisplayName("truncates to the width of the user_description column")
    void truncatesToColumnWidth() {
        String tooLong = "X".repeat(Transaction.USER_DESCRIPTION_MAX_LENGTH + 20);
        transaction.setUserDescription(tooLong);
        assertEquals(Transaction.USER_DESCRIPTION_MAX_LENGTH, transaction.getUserDescription().length(),
                "a memo longer than the column would fail the insert");
    }

    @Test
    @DisplayName("is selected back out of the database")
    void isIncludedInSelectColumns() {
        // loadFromResultSet reads this alias, so every query building a Transaction needs it.
        assertTrue(Transaction.getSelectColumns().contains("tr.user_description"),
                "select columns were: " + Transaction.getSelectColumns());
    }
}
