package com.hixon.financialApp.utility;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.MemoBudgetItemHistory;
import com.hixon.financialApp.model.forecast.ForecastItem;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.utility.ForecastTransactionMatcher.ReferenceVerdict;
import com.hixon.financialApp.utility.ForecastTransactionMatcher.ScoredCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the transfer memo's effect on forecast transaction matching.
 *
 * <p>This is the one place the memo is consulted where nobody is going to read the answer:  a
 * candidate at or above the threshold is assigned <em>silently</em>.  So the memo gets a much
 * narrower licence here than it does in the ranked budget item list, and it is a single property:
 *
 * <blockquote><b>The threshold is evaluated on the memo-free score.  The memo can change which
 * candidate wins; it can never change whether one wins.</b></blockquote>
 *
 * <p>Nearly every test below is that sentence from a different angle, because a memo that could
 * make a match would be the dominant-relevancy-score shortcut all over again -- the one that would
 * have silently assigned a $150 rent transfer to Groceries.
 */
@DisplayName("Forecast Transaction Matcher - Transfer Memo Tie-Break Tests")
public class ForecastTransactionMatcherMemoTest {

    // Two real items that collide:  both planned, both in Bill Pay Dave, and both have taken a
    // $-30.00 transfer in the same week.  The memo is the only thing that separates them.
    private static final UUID ALLOWANCES = UUID.randomUUID();
    private static final UUID WEEKLY_EXPENSES = UUID.randomUUID();
    private static final UUID ROOM_RENTAL = UUID.randomUUID();


    /*
     * Test doubles:
     */
    /** A forecast transaction whose forecast item is the given budget item. */
    private static ForecastTransaction candidateFor(UUID idBudgetItem) {
        ForecastItem forecastItem = mock(ForecastItem.class);
        when(forecastItem.getIdBudgetItem()).thenReturn(idBudgetItem);

        ForecastTransaction candidate = mock(ForecastTransaction.class);
        try {
            when(candidate.getForecastItem()).thenReturn(forecastItem);
        } catch (Exception e) {
            throw new AssertionError("stubbing does not throw", e);
        }
        return candidate;
    }

    private static MemoBudgetItemHistory.Suggestion suggestionFor(UUID idBudgetItem, int priors) {
        BudgetItem budgetItem = mock(BudgetItem.class);
        when(budgetItem.getId()).thenReturn(idBudgetItem);
        return new MemoBudgetItemHistory.Suggestion(budgetItem, "GAS FOR JDH", priors);
    }

    /** A candidate scored the way the matcher scores one, with the memo's opinion applied. */
    private static ScoredCandidate scored(ForecastTransaction candidate, double score,
            MemoBudgetItemHistory.Suggestion suggestion) {
        return new ScoredCandidate(candidate, score,
                ForecastTransactionMatcher.memoTieBreak(candidate, suggestion));
    }

    private static List<ScoredCandidate> listOf(ScoredCandidate... candidates) {
        return new ArrayList<>(Arrays.asList(candidates));
    }


    /*
     * The tie-break itself:
     */
    @Test
    @DisplayName("The memo's budget item is the only candidate that gets the tie-break")
    void testTieBreakGoesOnlyToTheMemosItem() {

        MemoBudgetItemHistory.Suggestion suggestion = suggestionFor(WEEKLY_EXPENSES, 9);

        assertEquals(ForecastTransactionMatcher.MEMO_TIE_BREAK,
                ForecastTransactionMatcher.memoTieBreak(candidateFor(WEEKLY_EXPENSES), suggestion));
        assertEquals(0.0, ForecastTransactionMatcher.memoTieBreak(candidateFor(ALLOWANCES), suggestion));
    }

    @Test
    @DisplayName("The tie-break is worth less than the smallest factual input")
    void testTieBreakIsWorthLessThanMerchantAgreement() {
        // 20 points is the merchant bonus, the weakest of the three factual signals. A memo that
        // outweighed it would have stopped breaking ties and started making matches.
        assertTrue(ForecastTransactionMatcher.MEMO_TIE_BREAK < 20.0);
    }

    @Test
    @DisplayName("No memo, a memo with no budget item, and no candidate are all worth nothing")
    void testTieBreakIsZeroWithoutASuggestion() {

        assertEquals(0.0, ForecastTransactionMatcher.memoTieBreak(candidateFor(ALLOWANCES), null));
        assertEquals(0.0, ForecastTransactionMatcher.memoTieBreak(null, suggestionFor(ALLOWANCES, 4)));
        assertEquals(0.0, ForecastTransactionMatcher.memoTieBreak(candidateFor(ALLOWANCES),
                new MemoBudgetItemHistory.Suggestion(null, "GAS FOR JDH", 4)));
    }

    @Test
    @DisplayName("A candidate whose budget item cannot be read is worth nothing, not an exception")
    void testUnreadableCandidateDegradesToNoOpinion() throws Exception {
        // The memo is a tie-break; losing it costs the user nothing, and throwing here would cost
        // them the import.
        ForecastTransaction broken = mock(ForecastTransaction.class);
        when(broken.getForecastItem()).thenThrow(new IllegalStateException("no forecast item"));

        assertEquals(0.0, ForecastTransactionMatcher.memoTieBreak(broken, suggestionFor(ALLOWANCES, 4)));
    }


    /*
     * The invariant:
     */
    @Test
    @DisplayName("The memo reorders two candidates that both clear the threshold")
    void testMemoReordersTwoQualifyingCandidates() {

        // A $-30.00 transfer in a week when both items expect one. Date and amount cannot separate
        // them; the memo can, and this is the case it exists for.
        MemoBudgetItemHistory.Suggestion suggestion = suggestionFor(WEEKLY_EXPENSES, 9);
        ForecastTransaction allowances = candidateFor(ALLOWANCES);
        ForecastTransaction weeklyExpenses = candidateFor(WEEKLY_EXPENSES);

        assertSame(weeklyExpenses, ForecastTransactionMatcher.selectMatch(listOf(
                scored(allowances, 80.0, suggestion),
                scored(weeklyExpenses, 78.0, suggestion))));

        // ... and without the memo the higher score still wins, unchanged.
        assertSame(allowances, ForecastTransactionMatcher.selectMatch(listOf(
                scored(allowances, 80.0, null),
                scored(weeklyExpenses, 78.0, null))));
    }

    @Test
    @DisplayName("A candidate whose memo-free score is 69 is not matched, whatever the memo says")
    void testMemoCannotLiftAMarginalCandidateOverTheThreshold() {

        // THE test. 69 + 15 is 84, comfortably "confident" on any reading of the number -- and it
        // is not allowed to matter, because the threshold never sees the memo.
        MemoBudgetItemHistory.Suggestion suggestion = suggestionFor(ROOM_RENTAL, 42);
        ForecastTransaction marginal = candidateFor(ROOM_RENTAL);

        assertNull(ForecastTransactionMatcher.selectMatch(listOf(scored(marginal, 69.0, suggestion))));
    }

    @Test
    @DisplayName("The memo cannot pull a below-threshold candidate past one that qualifies")
    void testMemoCannotDisplaceAQualifyingCandidateWithADisqualifiedOne() {

        // 69 + 15 = 84 beats 72 on the total, and still loses: it was never eligible to be ranked.
        MemoBudgetItemHistory.Suggestion suggestion = suggestionFor(WEEKLY_EXPENSES, 9);
        ForecastTransaction qualifying = candidateFor(ALLOWANCES);
        ForecastTransaction marginal = candidateFor(WEEKLY_EXPENSES);

        assertSame(qualifying, ForecastTransactionMatcher.selectMatch(listOf(
                scored(marginal, 69.0, suggestion),
                scored(qualifying, 72.0, suggestion))));
    }

    @Test
    @DisplayName("A candidate exactly at the threshold qualifies, and the memo may then rank it")
    void testThresholdIsInclusive() {

        MemoBudgetItemHistory.Suggestion suggestion = suggestionFor(WEEKLY_EXPENSES, 9);
        ForecastTransaction atThreshold = candidateFor(WEEKLY_EXPENSES);

        assertSame(atThreshold, ForecastTransactionMatcher.selectMatch(listOf(
                scored(atThreshold, ForecastTransactionMatcher.AUTO_MATCH_THRESHOLD, suggestion))));
    }

    @Test
    @DisplayName("A single-prior memo breaks a tie exactly as any other memo does")
    void testSinglePriorIsNotWeightedDown() {
        // Unlike the ranked list, there is nothing here for half weight to protect: every candidate
        // this chooses between has already been judged confident on the facts alone.
        MemoBudgetItemHistory.Suggestion suggestion = suggestionFor(WEEKLY_EXPENSES, 1);

        assertEquals(ForecastTransactionMatcher.MEMO_TIE_BREAK,
                ForecastTransactionMatcher.memoTieBreak(candidateFor(WEEKLY_EXPENSES), suggestion));
    }


    /*
     * Regression guards -- what must not change for the transactions with no memo:
     */
    @Test
    @DisplayName("With no memo the highest score wins, exactly as before")
    void testNoMemoSelectsTheHighestScore() {

        ForecastTransaction low = candidateFor(ALLOWANCES);
        ForecastTransaction high = candidateFor(WEEKLY_EXPENSES);

        assertSame(high, ForecastTransactionMatcher.selectMatch(listOf(
                scored(low, 71.0, null),
                scored(high, 95.0, null))));
    }

    @Test
    @DisplayName("With no memo, equal scores leave the earliest candidate winning")
    void testNoMemoKeepsTheEarliestOfEqualCandidates() {
        // The scoring loop used a strict greater-than, so the first of two equal candidates won.
        // Nothing about that may shift for the roughly half of transfers that carry no memo.
        ForecastTransaction first = candidateFor(ALLOWANCES);
        ForecastTransaction second = candidateFor(WEEKLY_EXPENSES);

        assertSame(first, ForecastTransactionMatcher.selectMatch(listOf(
                scored(first, 88.0, null),
                scored(second, 88.0, null))));
    }

    @Test
    @DisplayName("Nothing reaching the threshold is still no match")
    void testNothingQualifiesIsNoMatch() {

        assertNull(ForecastTransactionMatcher.selectMatch(listOf(
                scored(candidateFor(ALLOWANCES), 69.99, null),
                scored(candidateFor(WEEKLY_EXPENSES), 40.0, null))));
        assertNull(ForecastTransactionMatcher.selectMatch(Collections.emptyList()));
    }


    /*
     * The bank reference stays the stronger fact:
     */
    @Test
    @DisplayName("A certain bank reference is settled before the memo is ever consulted")
    void testCertainReferenceIsDecidedWithoutTheMemo() throws Exception {

        // The matching loop returns on CERTAIN, so a candidate the reference identifies is never
        // scored and never becomes a ScoredCandidate. Asserted from the memo's side: nothing asked
        // that candidate for its budget item, which is the only question the memo can ask.
        assertEquals(ReferenceVerdict.CERTAIN,
                ForecastTransactionMatcher.compareReferences("IB0ZBFJRYR", "IB0ZBFJRYR"));

        ForecastTransaction certain = candidateFor(WEEKLY_EXPENSES);
        assertNull(ForecastTransactionMatcher.selectMatch(Collections.emptyList()));
        verify(certain, never()).getForecastItem();
    }

    @Test
    @DisplayName("A ruled-out candidate is never scored, so the memo cannot revive it")
    void testRuledOutCandidateIsNeverRanked() {

        // Two different bank references cannot be the same movement of money. That candidate is
        // skipped before scoring, so it never reaches the ranking for the memo to lift.
        assertEquals(ReferenceVerdict.RULED_OUT,
                ForecastTransactionMatcher.compareReferences("IB0ZBFJRYR", "IB09X44BJ8"));

        MemoBudgetItemHistory.Suggestion suggestion = suggestionFor(WEEKLY_EXPENSES, 9);
        ForecastTransaction survivor = candidateFor(ALLOWANCES);

        assertSame(survivor, ForecastTransactionMatcher.selectMatch(
                listOf(scored(survivor, 75.0, suggestion))));
    }
}
