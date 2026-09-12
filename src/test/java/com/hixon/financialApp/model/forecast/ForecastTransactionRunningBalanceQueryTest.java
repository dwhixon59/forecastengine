package com.hixon.financialApp.model.forecast;

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the running-balance update a rendering uses.  The occurrence's updatedTimeStamp is its version for
 * the spreadsheet round trip, and a rendering that moved it made every edit in the spreadsheet look stale:  all four
 * edits in the daily updates of 09-11-2026 were queried as conflicts with the database.
 */
class ForecastTransactionRunningBalanceQueryTest {

    @Test
    void theRunningBalanceUpdateLeavesTheVersionAlone() {
        ForecastTransaction occurrence = new ForecastTransaction();
        occurrence.setRunningBalance(1790.95);

        String query = occurrence.getUpdateRunningBalanceQuery();

        assertTrue(query.startsWith("update forecast_transaction set runningBalance = 1790.95 where "), query);
        assertTrue(query.contains("uuid_to_bin('" + occurrence.getId() + "')"), query);
        assertFalse(query.contains("updatedTimeStamp"), query);
    }

    @Test
    void aFullUpdateStillMovesTheVersion() {
        // The regression guard in the other direction:  a real change to an occurrence is a new version.
        ForecastTransaction occurrence = new ForecastTransaction();
        occurrence.setPlannedDate(new GregorianCalendar(2026, Calendar.SEPTEMBER, 15));

        assertTrue(occurrence.getUpdateByIdQuery().contains("updatedTimeStamp = current_timestamp()"));
    }
}
