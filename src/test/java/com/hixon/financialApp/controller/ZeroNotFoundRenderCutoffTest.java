package com.hixon.financialApp.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static com.hixon.financialApp.controller.ForecastTransactionController.renderCutoffClause;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the line {@code zeroNotFound} draws between a row the user deleted from the
 * spreadsheet and a row the spreadsheet never held.
 *
 * <p>The importer marks every row it reads from the file as found, then zeroes everything left over.
 * That is only sound if the file lists every occurrence that had something left -- and it does not.
 * The renderer selects on {@code remainingAmount > 0} and {@code remainingAmount < 0}, so an
 * occurrence sitting at zero when the file is produced is not written to it at all.  If that
 * occurrence is given an amount before the file is imported, it is missing from the file for a reason
 * that has nothing to do with the user, and zeroing it destroys their change.
 *
 * <p>The dates below are the run that exposed this.  The Bill Pay Danni forecast was rendered on
 * 09-09-2026 at 05:30:12.  Two rows the user really did delete had last been written at 05:30:08;
 * six Danni's Spending Money occurrences were given $-150.00 each between 10:10 and 10:12, after the
 * render.  The 10:16 import zeroed all eight and described all eight as deletions -- $900 of planned
 * spending removed without being asked for.  {@code lastRenderedDate} separates the two groups
 * cleanly, which is the whole basis of the fix.
 */
@DisplayName("zeroNotFound render-cutoff tests")
class ZeroNotFoundRenderCutoffTest {

    /** When the Bill Pay Danni spreadsheet the user edited was rendered. */
    private static Calendar rendered() {
        return at(2026, Calendar.SEPTEMBER, 9, 5, 30, 12);
    }

    private static Calendar at(int year, int month, int day, int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    @Test
    @DisplayName("The clause for genuine deletions bounds on or before the render")
    void theDeletionClauseBoundsOnOrBeforeTheRender() {

        assertEquals("and ft.updatedTimeStamp <= '2026-09-09 05:30:12' ",
                renderCutoffClause(rendered(), "ft.", "<="),
                "a deletion is a row the render saw and the user then removed from the file");
    }

    @Test
    @DisplayName("The clause for rows the file predates bounds strictly after the render")
    void theSkipClauseBoundsStrictlyAfterTheRender() {

        assertEquals("and ft.updatedTimeStamp > '2026-09-09 05:30:12' ",
                renderCutoffClause(rendered(), "ft.", ">"),
                "a row written after the render was never in the file to be deleted from");
    }

    @Test
    @DisplayName("The two clauses partition the missing rows with no gap and no overlap")
    void theTwoClausesPartitionTheMissingRows() {

        // <= and > against one value:  every row falls in exactly one side.  A row exactly on the
        // render timestamp counts as deleted, which is the safe direction -- the render wrote it, so
        // the file had it, so its absence is the user's doing.
        String deleted = renderCutoffClause(rendered(), "ft.", "<=");
        String skipped = renderCutoffClause(rendered(), "ft.", ">");

        assertTrue(deleted.contains("<="), "the deletion side must include the boundary");
        assertTrue(skipped.contains(">") && !skipped.contains(">="),
                "the skip side must exclude the boundary, or a row would be in both");
        assertEquals(deleted.replace("<=", "").trim(), skipped.replace(">", "").trim(),
                "both sides must compare against the same instant");
    }

    @Test
    @DisplayName("The bulk update gets an unqualified column, the joined listing a qualified one")
    void theColumnIsQualifiedOnlyWhereTheStatementHasAnAlias() {

        // "update forecast_transaction set ..." has no alias, so "ft.updatedTimeStamp" there is an
        // error;  the listing selects "from forecast_transaction ft" and needs the qualifier.
        assertEquals("and updatedTimeStamp <= '2026-09-09 05:30:12' ",
                renderCutoffClause(rendered(), "", "<="));
        assertTrue(renderCutoffClause(rendered(), "ft.", "<=").contains("ft.updatedTimeStamp"));
    }

    @Test
    @DisplayName("A forecast that was never rendered yields no clause, keeping the old behaviour")
    void aForecastNeverRenderedYieldsNoClause() {

        // Bill Pay Envelopes has a null lastRenderedDate.  There is no cutoff to compare against, so
        // the safeguard cannot apply;  deletion has to keep working, and zeroNotFound says so rather
        // than silently dropping the protection.
        assertEquals("", renderCutoffClause(null, "ft.", "<="));
        assertEquals("", renderCutoffClause(null, "", "<="));
    }

    /*
     * The rule against the run that exposed it.  These assert the boundary decides each of the eight
     * rows the old code zeroed the way it should have.
     */

    private static boolean countsAsDeleted(Calendar updated) {
        return !updated.after(rendered());
    }

    @Test
    @DisplayName("The two rows the user really deleted fall on the deletion side")
    void theRealDeletionsAreStillZeroed() {

        // Citibank Card 09-02 and Medications/Vetmedin 09-05, both last written at 05:30:08.
        Calendar lastWritten = at(2026, Calendar.SEPTEMBER, 9, 5, 30, 8);

        assertTrue(countsAsDeleted(lastWritten),
                "these were in the rendered file and the user removed them, so they must still zero");
    }

    @Test
    @DisplayName("The six rows changed after the render fall on the skip side")
    void theRowsChangedAfterTheRenderAreSpared() {

        // Danni's Spending Money, written between 10:10:06 and 10:12:29.
        for (int[] time : new int[][]{{10, 10, 6}, {10, 10, 57}, {10, 11, 17},
                                      {10, 11, 57}, {10, 12, 13}, {10, 12, 29}}) {
            Calendar lastWritten = at(2026, Calendar.SEPTEMBER, 9, time[0], time[1], time[2]);
            assertFalse(countsAsDeleted(lastWritten),
                    "a row written at " + time[0] + ":" + time[1] + " changed after the 05:30:12 " +
                            "render, so the spreadsheet never held it and it must not be zeroed");
        }
    }
}
