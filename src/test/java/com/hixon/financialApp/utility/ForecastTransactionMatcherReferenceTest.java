package com.hixon.financialApp.utility;

import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.utility.ForecastTransactionMatcher.ReferenceVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    /*
     * The unpaired-counterpart rule:  an identity, never proximity.
     */

    /** A counterpart still waiting for its budget item pairing, carrying the given amount. */
    private static ForecastTransaction unpairedCounterpart(double remainingAmount) {
        ForecastTransaction counterpart = mock(ForecastTransaction.class);
        when(counterpart.isTransferPairingUnknown()).thenReturn(true);
        when(counterpart.getRemainingAmount()).thenReturn(remainingAmount);
        return counterpart;
    }

    @Test
    @DisplayName("An unpaired counterpart of the same size is admitted - it is the same movement")
    void testUnpairedCounterpartOfTheSameAmountIsAdmitted() {
        assertTrue(ForecastTransactionMatcher.admitsUnpairedCounterpart(-1.00, unpairedCounterpart(-1.00)));
    }

    @Test
    @DisplayName("An unpaired counterpart of a different size is refused, however close the dates")
    void testUnpairedCounterpartOfADifferentAmountIsRefused() {
        // Both observed in one real import: a $1.00 placeholder was scoring 40 against a $2.00
        // savings transfer and 32 against a $35.00 credit card payment, purely on date proximity.
        // It can never be assigned, so scoring it at all is noise -- and it is exempt from the
        // merchant filter, which is what let it reach an unrelated credit card payment.
        assertFalse(ForecastTransactionMatcher.admitsUnpairedCounterpart(-2.00, unpairedCounterpart(-1.00)));
        assertFalse(ForecastTransactionMatcher.admitsUnpairedCounterpart(-35.00, unpairedCounterpart(-1.00)));
    }

    @Test
    @DisplayName("A counterpart of the same size but the opposite sign is refused")
    void testOppositeSignIsNotTheSameMovement() {
        // The counterpart is created already negated, so it should carry this side's sign. One that
        // does not is the register's own copy of the far side, not an expectation for this one.
        assertFalse(ForecastTransactionMatcher.admitsUnpairedCounterpart(1.00, unpairedCounterpart(-1.00)));
    }

    @Test
    @DisplayName("A counterpart whose pairing is known is admitted on its amount as before")
    void testKnownPairingIsAdmittedRegardlessOfAmount() {
        // It carries a real budget item, so it can actually be assigned and the ordinary score is
        // allowed to decide - including for a partially reconciled remaining amount.
        ForecastTransaction paired = mock(ForecastTransaction.class);
        when(paired.isTransferPairingUnknown()).thenReturn(false);
        assertTrue(ForecastTransactionMatcher.admitsUnpairedCounterpart(-35.00, paired));
    }

    @Test
    @DisplayName("An ordinary forecast transaction is untouched by the rule")
    void testOrdinaryForecastTransactionIsAlwaysAdmitted() {
        // The regression guard: nothing about normal forecast matching may shift because of this.
        ForecastTransaction ordinary = mock(ForecastTransaction.class);
        when(ordinary.isTransferPairingUnknown()).thenReturn(false);
        assertTrue(ForecastTransactionMatcher.admitsUnpairedCounterpart(-1234.56, ordinary));
        assertTrue(ForecastTransactionMatcher.admitsUnpairedCounterpart(0.0, ordinary));
    }
}
