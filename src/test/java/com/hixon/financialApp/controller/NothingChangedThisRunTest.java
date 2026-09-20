package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.register.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the rule that decides whether a daily update changed anything, and so whether the forecast
 * is worth rendering again.
 *
 * <p>On 09-20-2026 the Citi statement qdl20260911.QFX was imported a second time -- a bank names a download
 * after its start date, so a re-download arrives under the name the last one had.  All seven charges were
 * recognised, and the update still re-rendered the forecast and opened it in Excel with nothing different
 * in it.
 */
@DisplayName("Did this daily update change anything?")
class NothingChangedThisRunTest {

    /** A run that imported nothing and changed nothing:  the re-imported statement. */
    private static boolean unchangedRun() {
        return DailyUpdateController.nothingChangedThisRun(0, false, false, false, false, false, true);
    }

    @Test
    @DisplayName("A statement whose every charge is already held changed nothing")
    void reimportedStatementChangedNothing() {
        assertTrue(unchangedRun());
    }

    @Test
    @DisplayName("A single new transaction is a change")
    void newTransactionIsAChange() {
        assertFalse(DailyUpdateController.nothingChangedThisRun(1, false, false, false, false, false, true));
    }

    @Test
    @DisplayName("Each of the things the user can do in a run counts as a change")
    void everyUserActionIsAChange() {
        // recategorized, balance updated, skipped transactions reprocessed, spreadsheet changes read in,
        // forecast regenerated.
        assertFalse(DailyUpdateController.nothingChangedThisRun(0, true, false, false, false, false, true));
        assertFalse(DailyUpdateController.nothingChangedThisRun(0, false, true, false, false, false, true));
        assertFalse(DailyUpdateController.nothingChangedThisRun(0, false, false, true, false, false, true));
        assertFalse(DailyUpdateController.nothingChangedThisRun(0, false, false, false, true, false, true));
        assertFalse(DailyUpdateController.nothingChangedThisRun(0, false, false, false, false, true, true));
    }

    @Test
    @DisplayName("A forecast out of step with the register is a change, whatever was imported")
    void outOfSyncForecastIsAChange() {
        // A pending charge that fell off the bank's list changes the register without importing anything.
        assertFalse(DailyUpdateController.nothingChangedThisRun(0, false, false, false, false, false, false));
    }

    @Test
    @DisplayName("The import log counts only what was taken into the register")
    void countsOnlyNewlyImported() {
        ImportLog log = new ImportLog();
        log.recordImportEvent(mock(Transaction.class), ImportLog.ImportRecord.Status.ALREADY_IMPORTED);
        log.recordImportEvent(mock(Transaction.class), ImportLog.ImportRecord.Status.SKIPPED_BY_USER);

        assertEquals(0, log.countNewlyImported());

        log.recordImportEvent(mock(Transaction.class), ImportLog.ImportRecord.Status.NEWLY_IMPORTED);
        assertEquals(1, log.countNewlyImported());
    }
}
