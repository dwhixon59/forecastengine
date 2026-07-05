package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.forecast.Forecast.DuplicateCandidate;
import com.hixon.financialApp.model.forecast.Forecast.DuplicateGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the duplicate forecast-transaction detection logic
 * ({@link Forecast#findDuplicateGroups(java.util.List)}).
 * <p>
 * The key behavior under test: two forecast transactions are only considered duplicates when they
 * share the same forecast item, the same planned date, AND the exact same set of linked transaction
 * splits (their "split signature").  Forecast transactions linked to <em>different</em> splits - for
 * example, two separate purchases from the same merchant on the same day for the same amount - must
 * NOT be flagged as duplicates.
 */
@DisplayName("Forecast Duplicate Detection Tests")
public class ForecastDuplicateDetectionTest {

    private final LocalDate date = LocalDate.of(2026, 7, 5);
    private final Timestamp ts = Timestamp.valueOf("2026-07-05 08:00:00");

    private DuplicateCandidate candidate(String forecastItemId, String splitSignature,
                                         String transactionId, Timestamp updatedTimeStamp) {
        return candidate(forecastItemId, -25.00, splitSignature, transactionId, updatedTimeStamp);
    }

    private DuplicateCandidate candidate(String forecastItemId, double remainingAmount, String splitSignature,
                                         String transactionId, Timestamp updatedTimeStamp) {
        return new DuplicateCandidate(forecastItemId, "Spending Money", "Starbucks", date,
                remainingAmount, splitSignature, transactionId, updatedTimeStamp);
    }

    @Test
    @DisplayName("Same merchant twice in one day for the same amount (different splits) is NOT a duplicate")
    void testTwoStarbucksSameDaySameAmount_DifferentSplits_NotDuplicate() {
        // Two separate purchases → two different transaction splits → different split signatures,
        // even though the forecast item, planned date, and amount are identical.
        String forecastItemId = "item-starbucks";
        DuplicateCandidate purchase1 = candidate(forecastItemId, "txnA:budgetItemX", "ft-1", ts);
        DuplicateCandidate purchase2 = candidate(forecastItemId, "txnB:budgetItemX", "ft-2", ts);

        List<DuplicateGroup> groups = Forecast.findDuplicateGroups(List.of(purchase1, purchase2));

        assertTrue(groups.isEmpty(),
                "Two purchases linked to different splits must not be reported as duplicates");
    }

    @Test
    @DisplayName("Two forecast transactions linked to the SAME split ARE duplicates")
    void testSameSplit_IsDuplicate() {
        String forecastItemId = "item-starbucks";
        DuplicateCandidate a = candidate(forecastItemId, "txnA:budgetItemX", "ft-1",
                Timestamp.valueOf("2026-07-05 08:00:00"));
        DuplicateCandidate b = candidate(forecastItemId, "txnA:budgetItemX", "ft-2",
                Timestamp.valueOf("2026-07-05 09:00:00"));

        List<DuplicateGroup> groups = Forecast.findDuplicateGroups(List.of(a, b));

        assertEquals(1, groups.size(), "Two transactions on the same split should be one duplicate group");
        assertEquals(2, groups.get(0).transactionIds.size());
        // Most-recently-updated first:
        assertEquals(List.of("ft-2", "ft-1"), groups.get(0).transactionIds,
                "Transaction ids should be ordered most-recently-updated first");
    }

    @Test
    @DisplayName("Two unlinked forecast transactions (null signature) on same item/date ARE duplicates")
    void testBothUnlinked_IsDuplicate() {
        String forecastItemId = "item-starbucks";
        DuplicateCandidate a = candidate(forecastItemId, null, "ft-1", ts);
        DuplicateCandidate b = candidate(forecastItemId, null, "ft-2", ts);

        List<DuplicateGroup> groups = Forecast.findDuplicateGroups(List.of(a, b));

        assertEquals(1, groups.size(),
                "Two auto-generated rows with no linked split are redundant duplicates");
        assertEquals(2, groups.get(0).transactionIds.size());
    }

    @Test
    @DisplayName("An unlinked transaction and a split-linked transaction are NOT duplicates")
    void testUnlinkedVsLinked_NotDuplicate() {
        String forecastItemId = "item-starbucks";
        DuplicateCandidate unlinked = candidate(forecastItemId, null, "ft-1", ts);
        DuplicateCandidate linked = candidate(forecastItemId, "txnA:budgetItemX", "ft-2", ts);

        List<DuplicateGroup> groups = Forecast.findDuplicateGroups(List.of(unlinked, linked));

        assertTrue(groups.isEmpty(),
                "A null signature must be distinct from a real split signature");
    }

    @Test
    @DisplayName("Same date but different forecast items are NOT duplicates")
    void testDifferentForecastItems_NotDuplicate() {
        DuplicateCandidate a = candidate("item-starbucks", null, "ft-1", ts);
        DuplicateCandidate b = candidate("item-groceries", null, "ft-2", ts);

        List<DuplicateGroup> groups = Forecast.findDuplicateGroups(List.of(a, b));

        assertTrue(groups.isEmpty(),
                "Different forecast items must never be grouped together");
    }

    @Test
    @DisplayName("Same forecast item and split but different planned dates are NOT duplicates")
    void testDifferentDates_NotDuplicate() {
        String forecastItemId = "item-starbucks";
        DuplicateCandidate a = new DuplicateCandidate(forecastItemId, "Spending Money", "Starbucks",
                LocalDate.of(2026, 7, 5), -25.00, "txnA:budgetItemX", "ft-1", ts);
        DuplicateCandidate b = new DuplicateCandidate(forecastItemId, "Spending Money", "Starbucks",
                LocalDate.of(2026, 7, 6), -25.00, "txnA:budgetItemX", "ft-2", ts);

        List<DuplicateGroup> groups = Forecast.findDuplicateGroups(List.of(a, b));

        assertTrue(groups.isEmpty(), "Different planned dates must not be grouped together");
    }

    @Test
    @DisplayName("A single transaction is never a duplicate")
    void testSingleTransaction_NotDuplicate() {
        List<DuplicateGroup> groups = Forecast.findDuplicateGroups(
                List.of(candidate("item-starbucks", "txnA:budgetItemX", "ft-1", ts)));

        assertTrue(groups.isEmpty());
    }

    @Test
    @DisplayName("Same item, date and split but DIFFERENT remaining amounts are NOT duplicates")
    void testDifferentAmounts_NotDuplicate() {
        // Reproduces the reported case: same forecast item (AAdvantage Card - Danni) and planned
        // date, but different remaining amounts ($-283 vs $-566).  These are distinct entries.
        String forecastItemId = "item-aadvantage-danni";
        DuplicateCandidate a = new DuplicateCandidate(forecastItemId, "Household", "AAdvantage Card - Danni",
                LocalDate.of(2026, 7, 15), -283.00, null, "ft-12", ts);
        DuplicateCandidate b = new DuplicateCandidate(forecastItemId, "Household", "AAdvantage Card - Danni",
                LocalDate.of(2026, 7, 15), -566.00, null, "ft-13", ts);

        List<DuplicateGroup> groups = Forecast.findDuplicateGroups(List.of(a, b));

        assertTrue(groups.isEmpty(),
                "Transactions with different remaining amounts must not be flagged as duplicates");
    }

    @Test
    @DisplayName("Same amount that differs only by floating-point noise is still treated as equal")
    void testAmountFloatingPointTolerance_IsDuplicate() {
        String forecastItemId = "item-x";
        DuplicateCandidate a = new DuplicateCandidate(forecastItemId, "Household", "Vendor",
                LocalDate.of(2026, 7, 15), -283.00, null, "ft-1", Timestamp.valueOf("2026-07-05 08:00:00"));
        DuplicateCandidate b = new DuplicateCandidate(forecastItemId, "Household", "Vendor",
                LocalDate.of(2026, 7, 15), -283.004, null, "ft-2", Timestamp.valueOf("2026-07-05 09:00:00"));

        List<DuplicateGroup> groups = Forecast.findDuplicateGroups(List.of(a, b));

        assertEquals(1, groups.size(),
                "Amounts equal to the cent should group together despite tiny floating-point differences");
    }

    @Test
    @DisplayName("An empty candidate list yields no duplicate groups")
    void testEmptyInput() {
        assertTrue(Forecast.findDuplicateGroups(List.of()).isEmpty());
    }

    @Test
    @DisplayName("Mixed scenario: real duplicates are detected while legitimate multi-splits are ignored")
    void testMixedScenario() {
        // Two Starbucks purchases (different splits) → not duplicates.
        DuplicateCandidate sbux1 = candidate("item-starbucks", "txnA:bi", "ft-1", ts);
        DuplicateCandidate sbux2 = candidate("item-starbucks", "txnB:bi", "ft-2", ts);
        // Two genuinely duplicated rows for a different item (same split, same amount) → one group.
        DuplicateCandidate dupA = new DuplicateCandidate("item-rent", "Household", "Landlord", date,
                -1500.00, "txnC:bi", "ft-3", Timestamp.valueOf("2026-07-05 07:00:00"));
        DuplicateCandidate dupB = new DuplicateCandidate("item-rent", "Household", "Landlord", date,
                -1500.00, "txnC:bi", "ft-4", Timestamp.valueOf("2026-07-05 10:00:00"));

        List<DuplicateGroup> groups = Forecast.findDuplicateGroups(List.of(sbux1, sbux2, dupA, dupB));

        assertEquals(1, groups.size(), "Only the genuinely duplicated rows should be reported");
        DuplicateGroup group = groups.get(0);
        assertEquals("Household", group.category);
        assertEquals("Landlord", group.payee);
        assertEquals(List.of("ft-4", "ft-3"), group.transactionIds,
                "Ids should be ordered most-recently-updated first");
    }
}

