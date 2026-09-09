package com.hixon.financialApp.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for choosing between two downloaded balances when one arrives from a re-import.
 *
 * <p>The trap this exists for:  a statement pulled over a <em>wider</em> date range is not a
 * <em>newer</em> statement, and the option that fetches one exists precisely to reach further back.
 * The wider Citi download that would recover the missing 08-27 charge carries
 * {@code DTASOF 20260901} and {@code BALAMT -11886.30} -- three days older than the -11,986.25
 * already imported, and $99.95 short of it, because it predates the 08-30 Echst charge.
 *
 * <p>Offering that as "the downloaded balance" would invite the user to move the register backwards
 * by $99.95, and nothing in the number itself would show them why.
 */
@DisplayName("Ledger Balance Recency Tests")
class LedgerBalanceRecencyTest {

    private static Calendar on(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private static final Calendar SEP_01 = on(2026, Calendar.SEPTEMBER, 1);
    private static final Calendar SEP_04 = on(2026, Calendar.SEPTEMBER, 4);

    @Test
    @DisplayName("An older wider statement does not displace the newer balance")
    void testOlderReimportIsRejected() {

        // The real numbers from the two downloads.
        Double kept = RegisterController.keepLaterLedgerBalance(-11986.25, SEP_04, -11886.30, SEP_01);

        assertEquals(-11986.25, kept,
                "the 09-04 balance stands; taking the 09-01 one would lose the Echst charge");
    }

    @Test
    @DisplayName("A genuinely newer statement is taken")
    void testNewerReimportWins() {

        // The ordinary case the option is for:  a statement that reaches further back AND is more
        // recent.  Rejecting this would make the re-import pointless.
        assertEquals(-12000.00,
                RegisterController.keepLaterLedgerBalance(-11986.25, SEP_01, -12000.00, SEP_04));
    }

    @Test
    @DisplayName("Only a date can demote the newly imported balance")
    void testUndatedReimportIsStillTaken() {

        // DTASOF is optional, and a bank that omits it must not have its statement quietly ignored.
        // With nothing to prove staleness, the file the user just chose is the better guess -- which
        // is also the behaviour before this change.
        assertEquals(-11886.30,
                RegisterController.keepLaterLedgerBalance(-11986.25, SEP_04, -11886.30, null));
        assertEquals(-11886.30,
                RegisterController.keepLaterLedgerBalance(-11986.25, null, -11886.30, SEP_01));
        assertEquals(-11886.30,
                RegisterController.keepLaterLedgerBalance(-11986.25, null, -11886.30, null));
    }

    @Test
    @DisplayName("A statement with no balance leaves the question as it was")
    void testMissingBalancesAreTolerated() {

        // A CSV or a QFX without a LEDGERBAL yields no balance at all;  the user must be left with
        // whatever they had rather than a null comparison.
        assertEquals(-11986.25,
                RegisterController.keepLaterLedgerBalance(-11986.25, SEP_04, null, SEP_04));
        assertEquals(-11886.30,
                RegisterController.keepLaterLedgerBalance(null, null, -11886.30, SEP_01));
        assertNull(RegisterController.keepLaterLedgerBalance(null, null, null, null));
    }

    @Test
    @DisplayName("An undated balance never displaces a dated one as the comparison date")
    void testIsLater() {

        assertTrue(RegisterController.isLater(SEP_04, SEP_01));
        assertFalse(RegisterController.isLater(SEP_01, SEP_04));
        assertFalse(RegisterController.isLater(SEP_04, SEP_04), "the same date is not later");

        // Losing a known date to an unknown one would disarm the check for every later re-import.
        assertFalse(RegisterController.isLater(null, SEP_04));
        assertTrue(RegisterController.isLater(SEP_04, null), "any date beats no date at all");
    }
}
