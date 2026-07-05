package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.forecast.Forecast.UnplannedOrphanCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the orphan unplanned/on-demand forecast-transaction detection logic
 * ({@link Forecast#findUnplannedOrphanTransactions(java.util.List)}).
 * <p>
 * An orphan is a forecast transaction whose forecast item is unplanned ({@code howOccurs == "U"}) or
 * on-demand ({@code period == "On-Demand"}), that has a zero remaining amount AND is not linked to
 * any forecast transaction split.  Such rows serve no purpose in the forecast.
 */
@DisplayName("Orphan Unplanned Forecast Transaction Detection Tests")
public class ForecastOrphanTransactionDetectionTest {

    private final LocalDate date = LocalDate.of(2026, 7, 15);

    private UnplannedOrphanCandidate candidate(String period, String howOccurs, double remainingAmount,
                                               boolean hasSplit, String transactionId) {
        return new UnplannedOrphanCandidate("Miscellaneous", "Other", date, period, howOccurs,
                remainingAmount, hasSplit, transactionId);
    }

    @Test
    @DisplayName("Unplanned item, zero remaining, no split → orphan")
    void testUnplannedZeroNoSplit_IsOrphan() {
        UnplannedOrphanCandidate c = candidate("On-Demand", "U", 0.0, false, "ft-1");

        List<UnplannedOrphanCandidate> orphans = Forecast.findUnplannedOrphanTransactions(List.of(c));

        assertEquals(1, orphans.size());
        assertEquals("ft-1", orphans.get(0).transactionId);
    }

    @Test
    @DisplayName("On-demand period (but not UNPLANNED howOccurs), zero remaining, no split → orphan")
    void testOnDemandPeriodZeroNoSplit_IsOrphan() {
        // Period is On-Demand even though howOccurs is ENVELOPE ("E"): still an orphan.
        UnplannedOrphanCandidate c = candidate("On-Demand", "E", 0.0, false, "ft-2");

        List<UnplannedOrphanCandidate> orphans = Forecast.findUnplannedOrphanTransactions(List.of(c));

        assertEquals(1, orphans.size());
        assertEquals("ft-2", orphans.get(0).transactionId);
    }

    @Test
    @DisplayName("Unplanned item with a NON-zero remaining amount → NOT an orphan")
    void testUnplannedNonZeroRemaining_NotOrphan() {
        UnplannedOrphanCandidate c = candidate("On-Demand", "U", -25.0, false, "ft-3");

        assertTrue(Forecast.findUnplannedOrphanTransactions(List.of(c)).isEmpty(),
                "A non-zero remaining amount means the transaction is still meaningful");
    }

    @Test
    @DisplayName("Unplanned item, zero remaining, but HAS a linked split → NOT an orphan")
    void testUnplannedWithSplit_NotOrphan() {
        UnplannedOrphanCandidate c = candidate("On-Demand", "U", 0.0, true, "ft-4");

        assertTrue(Forecast.findUnplannedOrphanTransactions(List.of(c)).isEmpty(),
                "A linked split means the transaction is reconciling a real split and should be kept");
    }

    @Test
    @DisplayName("Planned/periodic item with zero remaining and no split → NOT an orphan")
    void testPlannedItem_NotOrphan() {
        // Monthly, periodic item: not unplanned and not on-demand, so never an orphan here.
        UnplannedOrphanCandidate c = candidate("Monthly", "P", 0.0, false, "ft-5");

        assertTrue(Forecast.findUnplannedOrphanTransactions(List.of(c)).isEmpty(),
                "Planned periodic items are outside the scope of this check");
    }

    @Test
    @DisplayName("Zero remaining recognized to the cent despite floating-point noise")
    void testFloatingPointZero_IsOrphan() {
        UnplannedOrphanCandidate c = candidate("On-Demand", "U", 0.0009, false, "ft-6");

        assertEquals(1, Forecast.findUnplannedOrphanTransactions(List.of(c)).size(),
                "Amounts that round to $0.00 should be treated as zero");
    }

    @Test
    @DisplayName("Mixed batch: only the true orphans are returned, in input order")
    void testMixedBatch() {
        UnplannedOrphanCandidate orphan1 = candidate("On-Demand", "U", 0.0, false, "ft-1");
        UnplannedOrphanCandidate nonZero = candidate("On-Demand", "U", -10.0, false, "ft-2");
        UnplannedOrphanCandidate withSplit = candidate("On-Demand", "U", 0.0, true, "ft-3");
        UnplannedOrphanCandidate planned = candidate("Monthly", "P", 0.0, false, "ft-4");
        UnplannedOrphanCandidate orphan2 = candidate("On-Demand", "E", 0.0, false, "ft-5");

        List<UnplannedOrphanCandidate> orphans = Forecast.findUnplannedOrphanTransactions(
                List.of(orphan1, nonZero, withSplit, planned, orphan2));

        assertEquals(2, orphans.size());
        assertEquals("ft-1", orphans.get(0).transactionId);
        assertEquals("ft-5", orphans.get(1).transactionId);
    }

    @Test
    @DisplayName("An empty candidate list yields no orphans")
    void testEmptyInput() {
        assertTrue(Forecast.findUnplannedOrphanTransactions(List.of()).isEmpty());
    }
}

