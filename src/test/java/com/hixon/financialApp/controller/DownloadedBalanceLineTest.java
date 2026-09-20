package com.hixon.financialApp.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the "Downloaded balance" line.
 *
 * <p>The figure the register is compared against is the bank's ledger balance plus the register's pending
 * transactions.  Quoting only the total left it unrecognisable:  on 09-20-2026 Bill Pay Danni's statement
 * said $122.33, the register held $10.05 of pending charges and refunds, the line read $132.38, and the
 * bank's website showed $97.62 -- its available balance, a third figure again.
 */
@DisplayName("The downloaded balance line")
class DownloadedBalanceLineTest {

    private static final Calendar SEPTEMBER_18 = new GregorianCalendar(2026, Calendar.SEPTEMBER, 18);

    @Test
    @DisplayName("Pending transactions are named as what was added to the bank's figure")
    void breaksDownAPendingAdjustedBalance() {
        assertEquals("Downloaded balance: $132.38  (the bank's ledger balance of $122.33 as of 09-18-2026," +
                        " plus $10.05 of pending transactions the bank has not counted yet)",
                RegisterController.downloadedBalanceLine(132.38, 122.33, 10.05, SEPTEMBER_18));
    }

    @Test
    @DisplayName("Pending charges that reduce the balance read as a negative adjustment")
    void breaksDownPendingCharges() {
        assertEquals("Downloaded balance: $60.69  (the bank's ledger balance of $85.67 as of 09-18-2026," +
                        " plus $-24.98 of pending transactions the bank has not counted yet)",
                RegisterController.downloadedBalanceLine(60.69, 85.67, -24.98, SEPTEMBER_18));
    }

    @Test
    @DisplayName("With nothing pending the bank's own figure is named, with its date")
    void namesTheBankFigureWhenNothingIsPending() {
        assertEquals("Downloaded balance: $-10,125.35  (the bank's ledger balance of $-10,125.35 as of " +
                        "09-18-2026)",
                RegisterController.downloadedBalanceLine(-10125.35, -10125.35, 0.0, SEPTEMBER_18));
    }

    @Test
    @DisplayName("An undated balance is still named, without inventing a date")
    void namesAnUndatedBankFigure() {
        assertEquals("Downloaded balance: $122.33  (the bank's ledger balance of $122.33)",
                RegisterController.downloadedBalanceLine(122.33, 122.33, 0.0, null));
    }

    @Test
    @DisplayName("A balance the bank did not state is quoted on its own")
    void noBankFigure() {
        assertEquals("Downloaded balance: $132.38",
                RegisterController.downloadedBalanceLine(132.38, null, 10.05, SEPTEMBER_18));
    }

    @Test
    @DisplayName("A breakdown that does not add up is not offered at all")
    void refusesABreakdownThatDoesNotAddUp() {
        // Re-importing a wider statement can leave the comparison figure and the balance last read from a
        // file belonging to different statements.
        assertEquals("Downloaded balance: $132.38",
                RegisterController.downloadedBalanceLine(132.38, 90.00, 10.05, SEPTEMBER_18));
    }
}
