package com.hixon.financialApp.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BankReferenceNumber}.
 *
 * <p>The governing rule for the whole class is that <b>the reference confirms a match and never
 * gates one</b>.  Roughly 57% of transfers carry no reference at all, so the behaviour that must not
 * regress is the "absent" case:  every comparison involving a missing reference has to come back as
 * "no information", never as evidence either way.
 */
@DisplayName("Bank Reference Number Tests")
public class BankReferenceNumberTest {

    /*
     * Extraction.
     */

    @Test
    @DisplayName("Reads the reference out of a Wells Fargo transfer payee")
    void testExtractsFromWellsFargoTransferPayee() {
        assertEquals("IB0ZBFJRYR", BankReferenceNumber.extract(
                "ONLINE TRANSFER TO HIXON D EVERYDAY CHECKING XXXXXX7018 REF #IB0ZBFJRYR ON 08/11/26"));
    }

    @Test
    @DisplayName("Reads the reference when it appears mid-payee rather than before the date")
    void testExtractsWhenReferenceIsMidPayee() {
        assertEquals("IB09X44BJ8", BankReferenceNumber.extract(
                "ONLINE TRANSFER TO RYBICKI C REF #IB09X44BJ8 EVERYDAY CHECKING BOYS HAIR CUTS"));
    }

    @Test
    @DisplayName("Tolerates spacing variations around REF and the hash")
    void testTolerantOfSpacing() {
        assertEquals("IB0QMFTTTQ", BankReferenceNumber.extract("ONLINE TRANSFER REF# IB0QMFTTTQ CHECKING"));
        assertEquals("IB0QMFTTTQ", BankReferenceNumber.extract("ONLINE TRANSFER REF  #  IB0QMFTTTQ CHECKING"));
    }

    @Test
    @DisplayName("Normalizes the reference to upper case so comparisons are stable")
    void testNormalizesToUpperCase() {
        assertEquals("IB0ZBFJRYR", BankReferenceNumber.extract("online transfer ref #ib0zbfjryr on 08/11/26"));
    }

    @Test
    @DisplayName("A payee with no reference yields null, which is the majority case and entirely normal")
    void testNoReferenceYieldsNull() {
        assertNull(BankReferenceNumber.extract(
                "ONLINE TRANSFER TO HIXON D EVERYDAY CHECKING XXXXXX7018 ON 08/11/26"));
        assertNull(BankReferenceNumber.extract("PURCHASE AUTHORIZED ON 08/10 TRADER JOE'S #123 SAN DIEGO CA"));
    }

    @Test
    @DisplayName("Null and empty payees yield null rather than throwing")
    void testNullAndEmptyPayee() {
        assertNull(BankReferenceNumber.extract(null));
        assertNull(BankReferenceNumber.extract(""));
    }

    @Test
    @DisplayName("A bare REF with no value is not a reference")
    void testBareRefIsNotAReference() {
        assertNull(BankReferenceNumber.extract("ONLINE TRANSFER REF # ON 08/11/26"));
    }

    /*
     * Comparison.
     */

    @Test
    @DisplayName("The same reference on both sides is the same movement of money")
    void testSameReferenceIsSameMovement() {
        assertTrue(BankReferenceNumber.areSameMovement("IB0ZBFJRYR", "IB0ZBFJRYR"));
        assertFalse(BankReferenceNumber.areDifferentMovements("IB0ZBFJRYR", "IB0ZBFJRYR"));
    }

    @Test
    @DisplayName("Comparison ignores case")
    void testComparisonIgnoresCase() {
        assertTrue(BankReferenceNumber.areSameMovement("IB0ZBFJRYR", "ib0zbfjryr"));
    }

    @Test
    @DisplayName("Two different references are known to be different movements")
    void testDifferentReferencesAreDifferentMovements() {
        assertTrue(BankReferenceNumber.areDifferentMovements("IB0ZBFJRYR", "IB09X44BJ8"));
        assertFalse(BankReferenceNumber.areSameMovement("IB0ZBFJRYR", "IB09X44BJ8"));
    }

    @Test
    @DisplayName("A missing reference on either side is evidence of nothing at all")
    void testMissingReferenceIsNoEvidence() {
        // This is the case that must never regress: 57% of transfers land here, and both questions
        // have to answer "no" so that the existing score is left to decide on its own.
        assertFalse(BankReferenceNumber.areSameMovement("IB0ZBFJRYR", null));
        assertFalse(BankReferenceNumber.areDifferentMovements("IB0ZBFJRYR", null));

        assertFalse(BankReferenceNumber.areSameMovement(null, "IB0ZBFJRYR"));
        assertFalse(BankReferenceNumber.areDifferentMovements(null, "IB0ZBFJRYR"));

        assertFalse(BankReferenceNumber.areSameMovement(null, null));
        assertFalse(BankReferenceNumber.areDifferentMovements(null, null));
    }
}
