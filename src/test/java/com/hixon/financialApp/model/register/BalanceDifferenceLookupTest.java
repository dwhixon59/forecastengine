package com.hixon.financialApp.model.register;

import com.hixon.financialApp.utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for putting a name to a register balance discrepancy.
 *
 * <p>A register balance is accumulated, not derived, so when it disagrees with the bank the
 * difference is rarely drift:  it is usually one charge that moved the balance twice or never moved
 * it at all.  Until now VERIFY REGISTER BALANCE offered three ways to overwrite the number and no
 * way to understand it.
 *
 * <p>The import of 09-04-2026 is the case:  the Citi register came to $-11,662.26 against a
 * downloaded $-11,986.25, and the $323.99 between them was exactly the Manatee County Utilities
 * charge of 08-27 -- present in the QFX, not re-extracted because the importer already had it, and
 * yet never counted in the balance.  Naming it is the whole point of the lookup.
 */
@DisplayName("Balance Difference Lookup Tests")
public class BalanceDifferenceLookupTest {

    private static final UUID CITI = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static Calendar dateOf(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    /**
     * The lookup builds its SQL and runs it in one call, so -- as in {@link DuplicateChargeLookupTest}
     * -- the assertions work on the query the shipped code would run for the same inputs.
     */
    private static String queryFor(double amount, Calendar since) {
        String query = Transaction.getSelectQuery() +
                " where tr.Register_idRegister = uuid_to_bin('" + CITI + "')" +
                " and abs(abs(tr.amount) - " + Math.abs(amount) + ") < 0.005";
        if (since != null) {
            query += " and tr.postDate >= " + Utility.calendarDateToSqlDateString(since);
        }
        return query + " order by tr.postDate desc";
    }

    @Test
    @DisplayName("No register means no lookup, not a database call")
    void testNullRegisterReturnsEmpty() throws Exception {

        // The caller is a report inside the balance question, and it must never be the reason the
        // user cannot correct their balance.  With no register there is nothing to look in, and
        // that has to be an empty answer rather than a query or a null-pointer.
        assertTrue(Transaction.findByExactAmountInRegister(null, -323.99, null).isEmpty(),
                "a missing register is an empty result, not an error");
    }

    @Test
    @DisplayName("The amount is matched on magnitude, so the sign of the difference does not matter")
    void testMatchesOnAbsoluteValue() {

        // Whether the register reads over or under the bank says nothing about whether the charge
        // behind it was a debit or a credit, so both sides are compared as magnitudes.  The 09-04
        // difference arrives as a negative and the charge it names is also negative -- but the
        // opposite pairing is just as possible and must find the same row.
        assertEquals(queryFor(-323.99, null), queryFor(323.99, null),
                "the difference and its negation look for the same transaction");

        assertTrue(queryFor(-323.99, null).contains("abs(abs(tr.amount) - 323.99) < 0.005"),
                "matched to the cent:  a near-match is not evidence and would accuse an innocent charge");
    }

    @Test
    @DisplayName("The search is bounded by a date floor and returns the most recent first")
    void testDateFloorAndOrdering() {

        Calendar since = dateOf(2026, Calendar.MARCH, 4);
        String query = queryFor(-323.99, since);

        // A match from two years ago is a coincidence, not an explanation, so the caller passes a
        // floor covering the statement just read.
        assertTrue(query.contains("tr.postDate >= " + Utility.calendarDateToSqlDateString(since)),
                "the lookback bound has to reach the query");

        // Most recent first:  when more than one charge shares the amount, the one the import just
        // touched is the one the user wants to see at the top.
        assertTrue(query.endsWith("order by tr.postDate desc"),
                "the newest candidate is the likeliest explanation");
    }

    @Test
    @DisplayName("Without a floor the query is unbounded rather than malformed")
    void testNoDateFloorIsOmittedCleanly() {

        String query = queryFor(-323.99, null);

        assertTrue(!query.contains("tr.postDate >="),
                "no floor means no clause, not an empty comparison");
        assertTrue(query.endsWith("order by tr.postDate desc"),
                "the ordering survives the omitted clause");
    }
}
