package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for how the pending import recognises transactions it has already seen.
 */
@DisplayName("Provisional import merge Tests")
class ProvisionalImportMergeTest {

    private static Transaction transaction(String payee, double amount, boolean cleared, String importRecordId) {
        Register register = mock(Register.class);
        when(register.getId()).thenReturn(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        Calendar date = Calendar.getInstance();
        date.set(2026, Calendar.SEPTEMBER, 16, 0, 0, 0);
        return new Transaction(register, date, payee, amount, cleared, 0, importRecordId);
    }

    @Test
    @DisplayName("A row saved with the page text still matches the same charge read without it")
    void pageTextDoesNotChangeTheKey() {
        Transaction saved = transaction(
                "PURCH RTN AMAZON MKTPL Amzn.com/bil WA CARD0148Learn MoreOpens a dialog", 7.47, false, "P202609171");
        Transaction fromFile = transaction("PURCH RTN AMAZON MKTPL Amzn.com/bil WA CARD0148", 7.47, false, null);

        assertEquals(ImportController.provisionalMergeKey(saved), ImportController.provisionalMergeKey(fromFile));
    }

    @Test
    @DisplayName("A different amount is a different key")
    void amountIsPartOfTheKey() {
        assertNotEquals(ImportController.provisionalMergeKey(transaction("SLIM CHICKEN", -14.65, false, null)),
                ImportController.provisionalMergeKey(transaction("SLIM CHICKEN", -27.48, false, null)));
    }

    @Test
    @DisplayName("The already-cleared message shows both copies and the cleared one's import id")
    void alreadyClearedMessage() {
        String message = ImportController.alreadyClearedMessage(
                transaction("PURCHASE Platinum Hea 194-1927112 FL CARD0148", -184.00, false, null),
                transaction("Platinum Healthcar", -184.00, true, "202609162"));

        assertTrue(message.contains("already cleared"), message);
        assertTrue(message.contains("PURCHASE Platinum Hea 194-1927112 FL CARD0148"), message);
        assertTrue(message.contains("Platinum Healthcar"), message);
        assertTrue(message.contains("202609162"), message);
        assertTrue(message.contains("09-16-2026"), message);
    }
}
