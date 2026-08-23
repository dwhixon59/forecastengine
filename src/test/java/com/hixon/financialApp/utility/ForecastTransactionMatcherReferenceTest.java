package com.hixon.financialApp.utility;

import com.hixon.financialApp.utility.ForecastTransactionMatcher.ReferenceVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the bank-reference rule in {@link ForecastTransactionMatcher}.
 *
 * <p>These cover the rule table directly:
 *
 * <table>
 *   <tr><th>Situation</th><th>Behaviour</th></tr>
 *   <tr><td>Both sides carry the same reference</td><td>Certain: skip the score threshold.</td></tr>
 *   <tr><td>Both carry references, but different ones</td><td>Ruled out regardless of score.</td></tr>
 *   <tr><td>Either side carries none</td><td>Unchanged: the score decides.</td></tr>
 * </table>
 */
@DisplayName("Forecast Transaction Matcher - Bank Reference Rule Tests")
public class ForecastTransactionMatcherReferenceTest {

    @Test
    @DisplayName("Both sides carrying the same reference makes the match certain")
    void testSameReferenceIsCertain() {
        assertEquals(ReferenceVerdict.CERTAIN,
                ForecastTransactionMatcher.compareReferences("IB0ZBFJRYR", "IB0ZBFJRYR"));
    }

    @Test
    @DisplayName("Case differences do not stop a reference from confirming a match")
    void testReferenceComparisonIgnoresCase() {
        assertEquals(ReferenceVerdict.CERTAIN,
                ForecastTransactionMatcher.compareReferences("ib0zbfjryr", "IB0ZBFJRYR"));
    }

    @Test
    @DisplayName("Two different references rule the candidate out, however well it would score")
    void testDifferentReferencesRuleTheCandidateOut() {
        // This is the row that earns its keep: without it the matcher can have two plausible
        // candidates for the same money and strand the right one.
        assertEquals(ReferenceVerdict.RULED_OUT,
                ForecastTransactionMatcher.compareReferences("IB0ZBFJRYR", "IB09X44BJ8"));
    }

    @Test
    @DisplayName("A transaction with no reference leaves the score to decide")
    void testTransactionWithoutReferenceIsUndecided() {
        assertEquals(ReferenceVerdict.UNDECIDED,
                ForecastTransactionMatcher.compareReferences(null, "IB0ZBFJRYR"));
    }

    @Test
    @DisplayName("A candidate with no reference leaves the score to decide")
    void testCandidateWithoutReferenceIsUndecided() {
        // Every ordinary forecast transaction is in this state - it was never created from a
        // transfer and so carries no reference. Nothing about existing matching may change for it.
        assertEquals(ReferenceVerdict.UNDECIDED,
                ForecastTransactionMatcher.compareReferences("IB0ZBFJRYR", null));
    }

    @Test
    @DisplayName("Neither side carrying a reference leaves the score to decide")
    void testNeitherSideHasAReferenceIsUndecided() {
        // 57% of transfers land here, and this is the behaviour that must not regress.
        assertEquals(ReferenceVerdict.UNDECIDED,
                ForecastTransactionMatcher.compareReferences(null, null));
    }
}
