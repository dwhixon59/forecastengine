package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for a cleared import whose record id already belongs to a different charge.
 *
 * <p>Citi's import id is the date plus the charge's position in that download.  On 09-17-2026 the
 * register held LA Fitness as {@code 20260915090001}, while that day's download numbered it
 * {@code 20260915090006}:  a new 09-15 charge listed first would have arrived as
 * {@code 20260915090001} and been taken for LA Fitness.
 */
@DisplayName("Import record id reuse Tests")
class ImportRecordIdReuseTest {

    private static Transaction charge(int day, double amount, String payee, String importRecordId) {
        Register register = mock(Register.class);
        when(register.getId()).thenReturn(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        Calendar date = Calendar.getInstance();
        date.set(2026, Calendar.SEPTEMBER, day, 0, 0, 0);
        return new Transaction(register, date, payee, amount, true, 0, importRecordId);
    }

    @Test
    @DisplayName("A different amount under the same id is a different charge")
    void differentAmount_isDifferentCharge() {
        Transaction laFitness = charge(15, -80.23, "LA FITNESS IRVINE CA", "20260915090001");
        Transaction visible = charge(15, -35.00, "VISIBLE 8663313527 CO", "20260915090001");

        assertFalse(ImportController.isSameCharge(laFitness, visible));
    }

    @Test
    @DisplayName("A different date under the same id is a different charge")
    void differentDate_isDifferentCharge() {
        assertFalse(ImportController.isSameCharge(charge(14, -9.95, "VXNBILL.COM", "X"),
                charge(15, -9.95, "VXNBILL.COM", "X")));
    }

    @Test
    @DisplayName("A renamed re-download of the same charge is still the same charge")
    void renamedCharge_isSameCharge() {
        assertTrue(ImportController.isSameCharge(charge(12, 750.00, "PAYMENT THANK YOU", "20260912090005"),
                charge(12, 750.00, "ONLINE PAYMENT, THANK YOU", "20260912090005")));
    }

    @Test
    @DisplayName("The new charge gets a suffixed id, not the next Citi position")
    void suffixedId() throws Exception {
        assertEquals("20260915090001-2",
                ImportController.firstFreeSuffixedImportRecordId("20260915090001", id -> false));

        Set<String> taken = Set.of("20260915090001-2", "20260915090001-3");
        assertEquals("20260915090001-4",
                ImportController.firstFreeSuffixedImportRecordId("20260915090001", taken::contains));
    }

    @Test
    @DisplayName("The message names both charges and the id they share")
    void message() {
        String message = ImportController.importIdReusedMessage(
                charge(15, -80.23, "LA FITNESS IRVINE CA", "20260915090001"),
                charge(15, -35.00, "VISIBLE 8663313527 CO", "20260915090001"));

        assertTrue(message.contains("20260915090001"), message);
        assertTrue(message.contains("LA FITNESS IRVINE CA"), message);
        assertTrue(message.contains("VISIBLE 8663313527 CO"), message);
        assertTrue(message.contains("not treated as already imported"), message);
    }
}
