package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.forecast.Forecast.AfterEndDateCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the "forecast transaction planned after the budget item end date" detection logic
 * ({@link Forecast#findTransactionsAfterEndDate(java.util.List)}).
 * <p>
 * A candidate is flagged when its planned date is strictly after its budget item's end date AND it is
 * not linked to any forecast transaction split.  Such rows are stale projections left behind when an
 * item is given an end date, and they keep expired items showing a future planned date.
 */
@DisplayName("Forecast Transaction After End Date Detection Tests")
public class ForecastAfterEndDateTransactionDetectionTest {

    private AfterEndDateCandidate candidate(LocalDate plannedDate, LocalDate endDate, boolean hasSplit,
                                            String transactionId) {
        return new AfterEndDateCandidate("Household", "Furniture", plannedDate, endDate, hasSplit,
                transactionId);
    }

    @Test
    @DisplayName("Planned after end date, no split → flagged")
    void testPlannedAfterEndDate_NoSplit_IsFlagged() {
        AfterEndDateCandidate c = candidate(LocalDate.of(2026, 1, 20), LocalDate.of(2025, 6, 27),
                false, "ft-1");

        List<AfterEndDateCandidate> flagged = Forecast.findTransactionsAfterEndDate(List.of(c));

        assertEquals(1, flagged.size());
        assertEquals("ft-1", flagged.get(0).transactionId);
    }

    @Test
    @DisplayName("Planned on the end date (not after) → NOT flagged")
    void testPlannedOnEndDate_NotFlagged() {
        AfterEndDateCandidate c = candidate(LocalDate.of(2025, 6, 27), LocalDate.of(2025, 6, 27),
                false, "ft-2");

        assertTrue(Forecast.findTransactionsAfterEndDate(List.of(c)).isEmpty(),
                "A transaction planned exactly on the end date is still within the item's life");
    }

    @Test
    @DisplayName("Planned before the end date → NOT flagged")
    void testPlannedBeforeEndDate_NotFlagged() {
        AfterEndDateCandidate c = candidate(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 6, 27),
                false, "ft-3");

        assertTrue(Forecast.findTransactionsAfterEndDate(List.of(c)).isEmpty());
    }

    @Test
    @DisplayName("Planned after end date but HAS a linked split → NOT flagged")
    void testPlannedAfterEndDate_WithSplit_NotFlagged() {
        AfterEndDateCandidate c = candidate(LocalDate.of(2026, 1, 20), LocalDate.of(2025, 6, 27),
                true, "ft-4");

        assertTrue(Forecast.findTransactionsAfterEndDate(List.of(c)).isEmpty(),
                "A reconciled (linked) transaction that posted after the end date must be kept");
    }

    @Test
    @DisplayName("Null end date → NOT flagged (item never expires)")
    void testNullEndDate_NotFlagged() {
        AfterEndDateCandidate c = candidate(LocalDate.of(2026, 1, 20), null, false, "ft-5");

        assertTrue(Forecast.findTransactionsAfterEndDate(List.of(c)).isEmpty());
    }

    @Test
    @DisplayName("Mixed batch: only the stale after-end-date transactions are returned, in input order")
    void testMixedBatch() {
        AfterEndDateCandidate after1 = candidate(LocalDate.of(2026, 1, 20), LocalDate.of(2025, 6, 27),
                false, "ft-1");
        AfterEndDateCandidate before = candidate(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 6, 27),
                false, "ft-2");
        AfterEndDateCandidate withSplit = candidate(LocalDate.of(2026, 1, 20), LocalDate.of(2025, 6, 27),
                true, "ft-3");
        AfterEndDateCandidate nullEnd = candidate(LocalDate.of(2026, 1, 20), null, false, "ft-4");
        AfterEndDateCandidate after2 = candidate(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 15),
                false, "ft-5");

        List<AfterEndDateCandidate> flagged = Forecast.findTransactionsAfterEndDate(
                List.of(after1, before, withSplit, nullEnd, after2));

        assertEquals(2, flagged.size());
        assertEquals("ft-1", flagged.get(0).transactionId);
        assertEquals("ft-5", flagged.get(1).transactionId);
    }

    @Test
    @DisplayName("An empty candidate list yields nothing")
    void testEmptyInput() {
        assertTrue(Forecast.findTransactionsAfterEndDate(List.of()).isEmpty());
    }
}


