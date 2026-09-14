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
        assertEquals("P202609141", ImportController.firstFreeImportRecordId("P202609141", id -> false));
    }

    @Test
    @DisplayName("The 09-14-2026 collision:  Amazon Prime does not take the payroll deposit's P202609141")
    void takenId_movesToNextFreeCounter() throws Exception {
        // P202609141 held the 09-12 deposit;  P202609142 belonged to the $42.00 transfer in the same file.
        Set<String> taken = Set.of("P202609141", "P202609142");

        String id = ImportController.firstFreeImportRecordId("P202609141", taken::contains);

        assertEquals("P202609143", id);
    }

    @Test
    @DisplayName("A two-digit counter continues from where it is")
    void twoDigitCounter_continues() throws Exception {
        Set<String> taken = Set.of("P2026091411", "P2026091412");

        assertEquals("P2026091413", ImportController.firstFreeImportRecordId("P2026091411", taken::contains));
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
