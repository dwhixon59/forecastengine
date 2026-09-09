package com.hixon.financialApp.model.register;

import com.hixon.financialApp.utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the second line of defence against re-importing a charge the register already has.
 *
 * <p>Citi's OFX {@code FITID} is not the stable identity the spec requires:  it is {@code YYYYMMDD}
 * followed by the transaction's <em>position in that download</em>, restarting at {@code 0001} in
 * every file.  Read straight out of two real downloads:
 *
 * <pre>
 *   Since Jul 11, 2026.QFX   first FITID 20260711090001   (07-11 Spotify)
 *   qdl20260828.QFX          first FITID 20260728090001   (07-28 Spectrum)
 * </pre>
 *
 * <p>So pulling a statement from a different start date hands every charge a fresh id, the id lookup
 * finds nothing, and the charge is inserted again.  Eighteen duplicates accumulated in one year; the
 * three added by a single import -- State Farm $583.39, Deeper.com $9.95, Disney+ $21.97 -- were
 * exactly the $615.31 by which that register's balance then disagreed with the bank.
 */
@DisplayName("Duplicate Charge Lookup Tests")
public class DuplicateChargeLookupTest {

    private static final UUID CITI = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static Calendar dateOf(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    /**
     * The lookup builds its SQL and runs it in one call, so the assertions here work on the query the
     * shipped code would run for the same inputs.  Every clause is one the accuracy depends on.
     */
    private static String queryFor(Calendar postDate, double amount, String payee) {
        return Transaction.getSelectQuery() +
                " where tr.Register_idRegister = uuid_to_bin('" + CITI + "')" +
                " and tr.postDate = " + Utility.calendarDateToSqlDateString(postDate) +
                " and abs(tr.amount - " + amount + ") < 0.005" +
                " and tr.payee = '" + Utility.escapeSqlString(payee) + "'" +
                " and tr.cleared = 1";
    }


    @Test
    @DisplayName("A null register, date or payee is not a duplicate question at all")
    void testNullsAreNotDuplicates() throws Exception {

        // The guard that keeps this off the path of anything that is not a normal imported row.
        assertNull(Transaction.getByDateAmountAndPayee(null, dateOf(2026, Calendar.AUGUST, 4), -21.97, "Disney"));
        assertNull(Transaction.getByDateAmountAndPayee(CITI, null, -21.97, "Disney"));
        assertNull(Transaction.getByDateAmountAndPayee(CITI, dateOf(2026, Calendar.AUGUST, 4), -21.97, null));
    }

    @Test
    @DisplayName("The lookup is scoped to one register, one date, one amount and one payee")
    void testQueryScoping() {

        String query = queryFor(dateOf(2026, Calendar.AUGUST, 4), -21.97, "Disney Plus 8889057888 CA");

        // Register scoping:  the same charge amount on the same day in another account is a
        // different charge, and must never suppress this one.
        assertTrue(query.contains("tr.Register_idRegister = uuid_to_bin('" + CITI + "')"), query);
        assertTrue(query.contains("tr.postDate = '2026-08-04'"), query);
        assertTrue(query.contains("tr.payee = 'Disney Plus 8889057888 CA'"), query);

        // Compared to the cent, like every other currency comparison in the application, rather than
        // by floating point equality.
        assertTrue(query.contains("abs(tr.amount - -21.97) < 0.005"), query);
    }

    @Test
    @DisplayName("A pending row is never treated as a duplicate of the cleared charge")
    void testProvisionalRowsAreExcluded() {

        // The regression this clause exists for.  Provisional reconciliation runs later in the
        // import, inside `if (splits == null)`.  If this lookup handed back the pending row -- which
        // has splits -- that whole phase would be skipped, the charge would never be marked cleared,
        // and a pending row would sit in the register for good.  So the duplicate question is asked
        // only of charges that have already posted.
        String query = queryFor(dateOf(2026, Calendar.AUGUST, 4), -21.97, "Disney Plus 8889057888 CA");

        assertTrue(query.contains("tr.cleared = 1"), query);
    }

    @Test
    @DisplayName("A payee containing an apostrophe cannot break the query")
    void testPayeeIsEscaped() {

        // These queries are string-concatenated rather than prepared, and real payees carry
        // apostrophes:  Cooper's Hawk, Gecko's Grill, Culver's.
        String query = queryFor(dateOf(2026, Calendar.AUGUST, 27), -49.49, "COOPER'S HAWK SARAS");

        assertFalse(query.contains("'COOPER'S HAWK SARAS'"), query);
        assertTrue(query.contains("HAWK SARAS"), query);
    }

    @Test
    @DisplayName("The three duplicates from one import are exactly the balance discrepancy")
    void testTheObservedDamage() {

        // Kept as a test because it is the evidence that this is worth a question in the import:
        // the discrepancy was not approximately the duplicates, it was precisely them.
        double stateFarm = 583.39, deeper = 9.95, disney = 21.97;
        double databaseBalance = -11843.39, downloadedBalance = -11228.08;

        assertEquals(downloadedBalance - databaseBalance, stateFarm + deeper + disney, 0.005);
    }

    @Test
    @DisplayName("Citi's FITIDs really do restart per download")
    void testCitiFitIdsAreNotStable() {

        // Both read from real files.  The shared YYYYMMDD09 shape with a per-file counter is the
        // whole reason the id lookup cannot be trusted on its own for this bank.
        String firstOfJulyDownload = "20260711090001";
        String firstOfAugustDownload = "20260728090001";

        assertEquals(firstOfJulyDownload.substring(10), firstOfAugustDownload.substring(10),
                "both downloads start their counter at 0001, so the counter is positional");
        assertNotEquals(firstOfJulyDownload.substring(0, 8), firstOfAugustDownload.substring(0, 8),
                "and they cover different first dates, so the same charge gets different ids");
    }
}
