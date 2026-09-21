package com.hixon.financialApp.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ImportController#firstFreeImportRecordId}, which keeps a new provisional transaction from
 * being saved over a different transaction that already holds its import record id.
 */
@DisplayName("Import record id collision Tests")
class ImportRecordIdCollisionTest {

    @Test
    @DisplayName("A free id is kept as it is")
    void freeId_isKept() throws Exception {
        assertEquals("P20260914001", ImportController.firstFreeImportRecordId("P20260914001", id -> false));
    }

    @Test
    @DisplayName("The 09-14-2026 collision:  Amazon Prime does not take the payroll deposit's P20260914001")
    void takenId_movesToNextFreeCounter() throws Exception {
        // P20260914001 held the 09-12 deposit;  P20260914002 belonged to the $42.00 transfer in the same file.
        Set<String> taken = Set.of("P20260914001", "P20260914002");

        String id = ImportController.firstFreeImportRecordId("P20260914001", taken::contains);

        assertEquals("P20260914003", id);
    }

    @Test
    @DisplayName("A two-digit counter continues from where it is")
    void twoDigitCounter_continues() throws Exception {
        Set<String> taken = Set.of("P20260914011", "P20260914012");

        assertEquals("P20260914013", ImportController.firstFreeImportRecordId("P20260914011", taken::contains));
    }

    @Test
    @DisplayName("An id without the date-and-counter shape gets a numbered suffix")
    void unrecognisedShape_getsSuffix() throws Exception {
        Set<String> taken = Set.of("ABC");

        assertEquals("ABC-2", ImportController.firstFreeImportRecordId("ABC", taken::contains));
    }

    @Test
    @DisplayName("A null id is returned unchanged")
    void nullId_isReturned() throws Exception {
        assertNull(ImportController.firstFreeImportRecordId(null, id -> true));
    }
}
