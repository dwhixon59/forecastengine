package com.hixon.financialApp.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for comparing a register balance against the bank's, over the same transactions.
 *
 * <p>The register counts a provisional transaction the moment it is saved. The bank's downloaded
 * balance does not — it is a balance over settled transactions, and a pending charge is not in it.
 * Comparing the two directly compares different sets, so while anything is pending they differ by
 * exactly the pending total, correctly and every time.
 *
 * <p>The prompt compared them anyway and offered to overwrite. Taking that offer on Bill Pay Danni
 * on 09-08-2026 set the balance to the bank's figure and dropped $255.91 of pending charges out of
 * it — four charges that had rows in the register and, from that moment, no effect on its balance.
 * Nothing recomputes an accumulated balance, so that is permanent.
 *
 * <p>None of this is put to the user. They see one balance at their bank and one here.
 */
@DisplayName("Pending Balance Comparison Tests")
class PendingBalanceComparisonTest {

    @Test
    @DisplayName("The register's settled position excludes what is still pending")
    void testSettledBalanceRemovesPending() {

        // Bill Pay Danni as the run left it: the register carried -69.26 including everything
        // pending, and -547.10 of that was not yet counted by the bank.
        assertEquals(477.84, RegisterController.settledBalance(-69.26, -547.10));
    }

    @Test
    @DisplayName("Going with the bank keeps the pending money counted")
    void testRegisterBalanceAddsPendingBack() {

        // The defect, in one line.  The bank said 186.65 and the old code wrote exactly that,
        // discarding the pending charges from the balance.  The register has to keep counting them.
        assertEquals(-360.45, RegisterController.registerBalanceFor(186.65, -547.10));
    }

    @Test
    @DisplayName("The four charges that were dropped, as the arithmetic that dropped them")
    void testTheChargesThatWereLost() {

        // The register held 186.65, the four recovered charges took it to -69.26, and accepting the
        // bank's 186.65 put it straight back -- undoing them in the balance while their rows stayed.
        assertEquals(-69.26, RegisterController.registerBalanceFor(186.65, -255.91),
                "with those four still pending, going with the bank has to land back on -69.26");
    }

    @Test
    @DisplayName("With nothing pending the two are the same question")
    void testNoPendingIsUnchangedBehaviour() {

        // Most registers, most days.  The comparison and the write must both behave exactly as they
        // did before pending was accounted for.
        assertEquals(500.00, RegisterController.settledBalance(500.00, 0.0));
        assertEquals(500.00, RegisterController.registerBalanceFor(500.00, 0.0));
    }

    @Test
    @DisplayName("The two are inverses, so going with the bank settles the question")
    void testRoundTrip() {

        // If they were not inverses, accepting the bank's figure would leave the balances still
        // differing and the prompt would have to be answered again.
        double pending = -547.10;
        double bank = 186.65;

        double shouldHold = RegisterController.registerBalanceFor(bank, pending);
        assertEquals(bank, RegisterController.settledBalance(shouldHold, pending));
    }

    @Test
    @DisplayName("Pending credits work the same way as pending charges")
    void testPendingCredits() {

        // A pending deposit is money the register counts and the bank has not.  The sign is the only
        // difference and nothing in the arithmetic treats it specially.
        assertEquals(100.00, RegisterController.settledBalance(150.00, 50.00));
        assertEquals(150.00, RegisterController.registerBalanceFor(100.00, 50.00));
    }

    @Test
    @DisplayName("Rounded to cents, so a comparison is never decided by a binary tail")
    void testRounding() {

        // 0.1 + 0.2 arithmetic must not make two equal balances look different and raise a prompt.
        assertEquals(0.30, RegisterController.registerBalanceFor(0.10, 0.20));
        assertEquals(0.10, RegisterController.settledBalance(0.30, 0.20));
    }
}
