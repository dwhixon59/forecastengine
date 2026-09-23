package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.financialinstitution.DateRange;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ImportController#mayHaveFallenOff}:  which pending register rows missing from the
 * pending file may be offered for deletion.  See CITI_PENDING_TRANSACTIONS_DESIGN.md &sect;3.6.
 */
class MayHaveFallenOffTest {

    /** A Wednesday. */
    private static final Calendar TODAY = day(2026, 9, 23);

    @Test
    void wellsFargoCoversEveryDateSoOnlyAgeMatters() {
        DateRange everything = DateRange.unbounded();

        assertTrue(ImportController.mayHaveFallenOff(day(2026, 9, 10), TODAY, everything));
        assertTrue(ImportController.mayHaveFallenOff(day(2025, 1, 2), TODAY, everything));
    }

    @Test
    void aRowOutsideTheDatesThePasteCoversIsNeverACandidate() {
        // The paste was filtered to Sep 14 - Sep 15.  A pending Sep 10 charge is simply not in it.
        DateRange filtered = DateRange.of(day(2026, 9, 14), day(2026, 9, 15));

        assertFalse(ImportController.mayHaveFallenOff(day(2026, 9, 10), TODAY, filtered));
        assertFalse(ImportController.mayHaveFallenOff(day(2026, 9, 13), TODAY, filtered));
        assertFalse(ImportController.mayHaveFallenOff(day(2026, 9, 16), TODAY, filtered));
    }

    @Test
    void aRowInsideTheDatesThePasteCoversIsACandidate() {
        DateRange filtered = DateRange.of(day(2026, 9, 14), day(2026, 9, 15));

        assertTrue(ImportController.mayHaveFallenOff(day(2026, 9, 14), TODAY, filtered));
        assertTrue(ImportController.mayHaveFallenOff(day(2026, 9, 15), TODAY, filtered));
    }

    @Test
    void aRecentRowIsNotACandidateEvenInsideTheRange() {
        // "Since Sep 11" runs through today; yesterday's charge may just not be listed yet.
        DateRange sinceSep11 = DateRange.of(day(2026, 9, 11), TODAY);

        assertFalse(ImportController.mayHaveFallenOff(day(2026, 9, 22), TODAY, sinceSep11));
        assertFalse(ImportController.mayHaveFallenOff(TODAY, TODAY, sinceSep11));
        assertTrue(ImportController.mayHaveFallenOff(day(2026, 9, 18), TODAY, sinceSep11));
    }

    @Test
    void aFileThatCoversNoDatesLetsNothingFallOff() {
        // A paste of the wrong page:  no rows and no time period.
        assertFalse(ImportController.mayHaveFallenOff(day(2026, 9, 10), TODAY, DateRange.empty()));
    }

    private static Calendar day(int year, int month, int dayOfMonth) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, dayOfMonth);
        return c;
    }
}
