package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for reading Wells Fargo pending transactions from the account activity CSV download, which
 * marks each row Pending or Posted.  The fixture is Bill Pay Danni's download of 09-24-2026
 * (Checking3.csv):  two pending rows and six posted ones.
 */
class WellsFargoPendingDownloadTest {

    private static final String FIXTURE = "wellsfargo/activity-download-20260924.csv";
    private static final Calendar TODAY = day(2026, 9, 24);

    @Test
    void readsOnlyThePendingRowsOfTheDownload() throws IOException {
        List<Transaction> pending = WellsFargoBank.readPendingRowsFromDownload(
                fixtureLines(), mock(Register.class), TODAY);

        assertNotNull(pending);
        assertEquals(2, pending.size());
        assertPending(pending.get(0), 2026, 9, 24, -277.16, "PURCHASE WALMART.COM BENTONVILLE AR CARD0148");
        assertPending(pending.get(1), 2026, 9, 24, -36.05, "PURCHASE PUBLIX #1553 BRADENTON FL CARD0148");
    }

    @Test
    void aPendingRowReadsLikeTheSameChargeCopiedFromThePage() {
        // The register's copy of yesterday's Publix charge came from the page as
        // "PURCHASE PUBLIX #361 SARASOTA FL CARD0148"; the download pads the same fields with spaces.
        // The pending import matches rows on this tidied text, so the two must agree.
        List<Transaction> pending = readDownload(
                "\"09/23/2026\",\"PURCHASE PUBLIX #361               SARASOTA      FL CARD0148\",\"-51.49\",\"\",\"Pending\"");

        assertEquals("PURCHASE PUBLIX #361 SARASOTA FL CARD0148", pending.get(0).getPayee());
        assertEquals(pending.get(0).getPayee(), pending.get(0).getMerchantPayee());
    }

    @Test
    void aPendingDepositIsPositive() {
        List<Transaction> pending = readDownload(
                "\"09/24/2026\",\"ONLINE TRANSFER FROM RYBICKI C REF #IB03C6LNSW EVERYDAY CHECKING\",\"75.00\",\"\",\"Pending\"");

        assertEquals(75.00, pending.get(0).getAmount(), 0.001);
    }

    @Test
    void theAuthorizationDateIsTheDayOfTheImportAsForACopiedLine() {
        List<Transaction> pending = readDownload(
                "\"09/22/2026\",\"PURCHASE WALMART.COM BENTONVILLE AR CARD0148\",\"-5.00\",\"\",\"Pending\"");

        assertSameDay(day(2026, 9, 22), pending.get(0).getPostDate());
        assertSameDay(TODAY, pending.get(0).getAuthorizationDate());
        assertFalse(pending.get(0).isCleared());
    }

    @Test
    void aDownloadWithNothingPendingGivesAnEmptyList() {
        List<Transaction> pending = readDownload(
                "\"09/23/2026\",\"PURCHASE PUBLIX #361 SARASOTA FL CARD0148\",\"-51.49\",\"\",\"Posted\"");

        assertNotNull(pending);
        assertTrue(pending.isEmpty());
    }

    @Test
    void unreadableRowsAreSkippedAndTheRestKept() {
        List<Transaction> pending = readDownload(
                "\"not a date\",\"PURCHASE A\",\"-1.00\",\"\",\"Pending\"",
                "\"09/24/2026\",\"PURCHASE B\",\"lots\",\"\",\"Pending\"",
                "\"09/24/2026\",\"\",\"-3.00\",\"\",\"Pending\"",
                "\"09/24/2026\",\"PURCHASE C\",\"0.00\",\"\",\"Pending\"",
                "\"09/24/2026\",\"PURCHASE D\",\"-4.00\"",
                "\"09/24/2026\",\"PURCHASE E\",\"-5.00\",\"\",\"Pending\"");

        assertEquals(1, pending.size());
        assertEquals("PURCHASE E", pending.get(0).getPayee());
    }

    @Test
    void aByteOrderMarkAndLeadingBlankLinesDoNotHideTheHeader() throws IOException {
        List<Transaction> pending = WellsFargoBank.readPendingRowsFromDownload(List.of(
                "",
                "﻿\"DATE\",\"DESCRIPTION\",\"AMOUNT\",\"CHECK #\",\"STATUS\"",
                "\"09/24/2026\",\"PURCHASE E\",\"-5.00\",\"\",\"Pending\""), mock(Register.class), TODAY);

        assertEquals(1, pending.size());
    }

    @Test
    void aCopyOfThePendingPageIsNotADownload() throws IOException {
        // The copied page (BillPayDanni-ProvTrx.tsv) is left to the per-line reader.
        List<Transaction> pending = WellsFargoBank.readPendingRowsFromDownload(List.of(
                "\t09/23/26\tPURCHASE PUBLIX #361 SARASOTA FL CARD0148\t\t$51.49"), mock(Register.class), TODAY);

        assertNull(pending);
    }

    @Test
    void anOlderHeaderlessDownloadIsNotMistakenForThisOne() throws IOException {
        // The layout the posted CSV import reads (Checking2.csv):  no header, amount second.
        List<Transaction> pending = WellsFargoBank.readPendingRowsFromDownload(List.of(
                "\"01/02/2026\",\"-54.01\",\"*\",\"\",\"RECURRING PAYMENT AUTHORIZED ON 01/01 PELOTON\""),
                mock(Register.class), TODAY);

        assertNull(pending);
    }

    @Test
    void theHookReadsTheDownloadAndStillReadsACopiedPage() throws Exception {
        WellsFargoBank bank = new WellsFargoBank(mock(SessionController.class));

        ProvisionalFileContents download = bank.loadProvisionalTransactions(fixtureLines(), mock(Register.class));
        assertEquals(2, download.transactions().size());
        assertTrue(download.coveredRange().isUnbounded());

        ProvisionalFileContents copied = bank.loadProvisionalTransactions(List.of(
                "\t09/23/26\tPURCHASE PUBLIX #361 SARASOTA FL CARD0148\t\t$51.49"), mock(Register.class));
        assertEquals(1, copied.transactions().size());
        assertEquals(-51.49, copied.transactions().get(0).getAmount(), 0.001);
    }

    // ---- helpers ----

    private static List<Transaction> readDownload(String... rows) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("\"DATE\",\"DESCRIPTION\",\"AMOUNT\",\"CHECK #\",\"STATUS\"");
        lines.addAll(List.of(rows));
        try {
            return WellsFargoBank.readPendingRowsFromDownload(lines, mock(Register.class), TODAY);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static List<String> fixtureLines() throws IOException {
        try (InputStream in = WellsFargoPendingDownloadTest.class.getClassLoader().getResourceAsStream(FIXTURE)) {
            assertNotNull(in, "missing test fixture " + FIXTURE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    private static Calendar day(int year, int month, int dayOfMonth) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, dayOfMonth);
        return c;
    }

    private static void assertPending(Transaction t, int year, int month, int dayOfMonth, double amount,
                                      String payee) {
        assertSameDay(day(year, month, dayOfMonth), t.getPostDate());
        assertEquals(amount, t.getAmount(), 0.001);
        assertEquals(payee, t.getPayee());
        assertFalse(t.isCleared());
    }

    private static void assertSameDay(Calendar expected, Calendar actual) {
        assertNotNull(actual);
        assertEquals(expected.get(Calendar.YEAR), actual.get(Calendar.YEAR));
        assertEquals(expected.get(Calendar.MONTH), actual.get(Calendar.MONTH));
        assertEquals(expected.get(Calendar.DAY_OF_MONTH), actual.get(Calendar.DAY_OF_MONTH));
    }
}
