package com.hixon.financialApp.controller;

import com.hixon.financialApp.controller.TransactionController.ForecastRelease;
import com.hixon.financialApp.model.budget.Item.HowOccurs;
import com.hixon.financialApp.model.budget.Item.PeriodType;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the rules {@link TransactionController#recategorizeTransaction} uses to undo the old
 * categorization's effect on the forecast, and to keep its memo.
 */
@DisplayName("Recategorize forecast release Tests")
class RecategorizeForecastReleaseTest {

    @Test
    @DisplayName("The 09-14-2026 orphan:  an on-demand occurrence created for the split is removed")
    void onDemandOccurrence_isDeleted() {
        assertEquals(ForecastRelease.DELETE_OCCURRENCE, TransactionController.releaseActionFor(
                HowOccurs.UNPLANNED, PeriodType.ON_DEMAND, null, 1, true));
    }

    @Test
    @DisplayName("An on-demand period is treated as unplanned whatever its howOccurs")
    void onDemandPeriod_isDeletedRegardlessOfHowOccurs() {
        assertEquals(ForecastRelease.DELETE_OCCURRENCE, TransactionController.releaseActionFor(
                HowOccurs.COLLECTION, PeriodType.ON_DEMAND, null, 1, false));
    }

    @Test
    @DisplayName("A plain collection deduction is added back")
    void collection_isAddedBack() {
        assertEquals(ForecastRelease.ADD_BACK_SPLIT, TransactionController.releaseActionFor(
                HowOccurs.COLLECTION, PeriodType.WEEKLY, null, 3, false));
        assertEquals(ForecastRelease.ADD_BACK_SPLIT, TransactionController.releaseActionFor(
                HowOccurs.COLLECTION, PeriodType.MONTHLY, SplitDisposition.ASSIGN, 1, false));
    }

    @Test
    @DisplayName("Money in to an expense collection is reported, since crediting it may have been declined")
    void creditToExpenseCollection_isReported() {
        assertEquals(ForecastRelease.REPORT_ONLY, TransactionController.releaseActionFor(
                HowOccurs.COLLECTION, PeriodType.WEEKLY, null, 1, true));
    }

    @Test
    @DisplayName("A periodic occurrence used up by this split alone gets its budgeted amount back")
    void periodicSoleSplit_isRestored() {
        assertEquals(ForecastRelease.RESTORE_BUDGETED, TransactionController.releaseActionFor(
                HowOccurs.PERIODIC, PeriodType.SEMIMONTHLY, null, 1, false));
    }

    @Test
    @DisplayName("A periodic occurrence shared with other splits is reported")
    void periodicSharedOccurrence_isReported() {
        assertEquals(ForecastRelease.REPORT_ONLY, TransactionController.releaseActionFor(
                HowOccurs.PERIODIC, PeriodType.SEMIMONTHLY, null, 2, false));
    }

    @Test
    @DisplayName("Overages that were adjusted, ignored, disputed or rolled forward are reported")
    void overageDispositions_areReported() {
        for (SplitDisposition disposition : List.of(SplitDisposition.ADJUST, SplitDisposition.IGNORE,
                SplitDisposition.DISPUTE, SplitDisposition.ROLL_FORWARD)) {
            assertEquals(ForecastRelease.REPORT_ONLY, TransactionController.releaseActionFor(
                    HowOccurs.COLLECTION, PeriodType.WEEKLY, disposition, 1, false), disposition.name());
        }
    }

    @Test
    @DisplayName("Envelope occurrences are reported")
    void envelope_isReported() {
        assertEquals(ForecastRelease.REPORT_ONLY, TransactionController.releaseActionFor(
                HowOccurs.ENVELOPE, PeriodType.MONTHLY, null, 1, false));
    }

    private static TransactionSplit splitWithMemo(String memo) {
        TransactionSplit split = mock(TransactionSplit.class);
        when(split.getMemo()).thenReturn(memo);
        return split;
    }

    @Test
    @DisplayName("The memo the splits share is kept")
    void sharedMemo_isReturned() {
        assertEquals("White water rafting", TransactionController.sharedMemo(
                List.of(splitWithMemo("White water rafting"), splitWithMemo(null), splitWithMemo("  "))));
    }

    @Test
    @DisplayName("Splits that disagree on memo keep none")
    void conflictingMemos_returnNull() {
        assertNull(TransactionController.sharedMemo(List.of(splitWithMemo("Hot dog"), splitWithMemo("Soda"))));
    }

    @Test
    @DisplayName("Splits with no memo keep none")
    void noMemo_returnsNull() {
        assertNull(TransactionController.sharedMemo(List.of(splitWithMemo(null))));
    }
}
