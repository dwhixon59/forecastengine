package com.hixon.financialApp.model.register;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for recognising a pending transaction that has already cleared into the register.
 *
 * <p>On 09-17-2026 a Bill Pay Danni pending file from the day before was imported after that day's
 * cleared file.  Its two charges had already posted and taken over their pending rows, so the file
 * put them into the register a second time:
 *
 * <pre>
 *   cleared  09-16  -184.00  Platinum Healthcar     pending  09-16  -184.00  PURCHASE Platinum Hea 194-1927112 FL CARD0148
 *   cleared  09-16   -14.65  SLIM CHICKENS 2080     pending  09-16   -14.65  PURCHASE SLIM CHICKEN LAKEWOOD RCH FL CARD0148
 * </pre>
 */
@DisplayName("Cleared Twin Lookup Tests")
public class ClearedTwinLookupTest {

    private static final UUID REGISTER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static Calendar dateOf(int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, month, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    @Test
    @DisplayName("The 09-17-2026 duplicates are recognised as already cleared")
    void testRealDuplicates() {
        assertTrue(TransactionUtilities.isClearedTwin(dateOf(Calendar.SEPTEMBER, 16),
                "PURCHASE Platinum Hea 194-1927112 FL CARD0148",
                dateOf(Calendar.SEPTEMBER, 16), "Platinum Healthcar", "Platinum Healthcare SRQ"));
        assertTrue(TransactionUtilities.isClearedTwin(dateOf(Calendar.SEPTEMBER, 16),
                "PURCHASE SLIM CHICKEN LAKEWOOD RCH FL CARD0148",
                dateOf(Calendar.SEPTEMBER, 16), "SLIM CHICKENS 2080", "Slim Chickens"));
    }

    @Test
    @DisplayName("The merchant name alone is enough when the bank's descriptions share nothing")
    void testMerchantNameMatches() {
        assertTrue(TransactionUtilities.isClearedTwin(dateOf(Calendar.SEPTEMBER, 15),
                "PURCHASE AMAZON MKTPL Amzn.com/bil WA CARD0148",
                dateOf(Calendar.SEPTEMBER, 17), "5L2FE", "Amazon"));
    }

    @Test
    @DisplayName("A charge cannot clear before the day it was pending")
    void testClearedBeforePendingIsNotATwin() {
        // Two $150 transfers on consecutive days read alike, but the one that cleared first is not the
        // one still pending.
        assertFalse(TransactionUtilities.isClearedTwin(dateOf(Calendar.SEPTEMBER, 12),
                "ONLINE TRANSFER TO HIXON D EVERYDAY CHECKING SPENDING",
                dateOf(Calendar.SEPTEMBER, 11), "ONLINE TRANSFER TO HIXON D EVERYDAY CHECKING SPENDING", null));
    }

    @Test
    @DisplayName("A cleared charge more than the window later is a different charge")
    void testOutsideWindow() {
        Calendar pending = dateOf(Calendar.SEPTEMBER, 1);
        Calendar lastDay = dateOf(Calendar.SEPTEMBER, 1 + TransactionUtilities.CLEARED_TWIN_DAY_WINDOW);
        Calendar dayAfter = dateOf(Calendar.SEPTEMBER, 2 + TransactionUtilities.CLEARED_TWIN_DAY_WINDOW);
        assertTrue(TransactionUtilities.isClearedTwin(pending, "SLIM CHICKEN", lastDay, "SLIM CHICKENS", null));
        assertFalse(TransactionUtilities.isClearedTwin(pending, "SLIM CHICKEN", dayAfter, "SLIM CHICKENS", null));
    }

    @Test
    @DisplayName("Same amount and date with a different payee is a different charge")
    void testDifferentPayee() {
        assertFalse(TransactionUtilities.isClearedTwin(dateOf(Calendar.SEPTEMBER, 16),
                "PURCHASE PUBLIX SUPER MARKET FL CARD0148",
                dateOf(Calendar.SEPTEMBER, 16), "SLIM CHICKENS 2080", "Slim Chickens"));
    }

    @Test
    @DisplayName("Missing dates never match")
    void testNullDates() {
        assertFalse(TransactionUtilities.isClearedTwin(null, "SLIM", dateOf(Calendar.SEPTEMBER, 1), "SLIM", null));
        assertFalse(TransactionUtilities.isClearedTwin(dateOf(Calendar.SEPTEMBER, 1), "SLIM", null, "SLIM", null));
    }

    @Test
    @DisplayName("The candidate query looks only at cleared rows of this register, on or after the pending date")
    void testCandidateQuery() {
        String query = TransactionUtilities.clearedTwinCandidateQuery(REGISTER, -14.65,
                dateOf(Calendar.SEPTEMBER, 16));
        assertTrue(query.contains("tr.Register_idRegister = uuid_to_bin('" + REGISTER + "')"), query);
        assertTrue(query.contains("tr.cleared = true"), query);
        assertTrue(query.contains("abs(tr.amount - -14.65) < 0.005"), query);
        assertTrue(query.contains("tr.postDate >= '2026-09-16'"), query);
        assertTrue(query.contains("tr.postDate <= '2026-09-21'"), query);
    }

    @Test
    @DisplayName("Nothing is looked up without a register and a pending transaction")
    void testNullPending() throws Exception {
        assertNull(TransactionUtilities.findClearedTwinOfProvisional(null, null, null));
        assertNull(TransactionUtilities.findClearedTwinOfProvisional(REGISTER, null, null));
    }
}
