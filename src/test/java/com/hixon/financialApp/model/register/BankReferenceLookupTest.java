package com.hixon.financialApp.model.register;

import com.hixon.financialApp.utility.BankReferenceNumber;
import com.hixon.financialApp.utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for finding the other side of a transfer by the bank's own reference number.
 *
 * <p>Wells Fargo writes the same reference into both legs, so where one exists it is an identity
 * rather than a guess. Both legs of the 09-07-2026 transfer are in the database:
 *
 * <pre>
 * Bill Pay Dave    2026-09-03   261.00  ONLINE TRANSFER FROM HIXON D REF #IB0338WKXT ...
 * Bill Pay Danni   2026-09-03  -261.00  ONLINE TRANSFER TO   HIXON D REF #IB0338WKXT ...
 * </pre>
 *
 * <p>The import guessed Dave's Spending Account anyway, the user answered "n", and picked Bill Pay
 * Danni from a list — a question the reference could have answered outright.
 *
 * <p>The query is asserted as well as the guards. The case-variant check shipped with correct Java
 * sitting on top of a query that could never feed it anything, and that is the mistake this file is
 * shaped to avoid repeating.
 */
@DisplayName("Bank Reference Lookup Tests")
public class BankReferenceLookupTest {

    private static final UUID BILL_PAY_DAVE = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static Calendar dateOf(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private static String queryFor(String reference, double amount) {
        return Transaction.bankReferenceQuery(reference, BILL_PAY_DAVE,
                dateOf(2026, Calendar.AUGUST, 29), dateOf(2026, Calendar.SEPTEMBER, 8), amount);
    }

    @Test
    @DisplayName("The reference is matched on the payee, since no column holds it")
    void testMatchesTheReferenceInThePayee() {

        // The bank writes the reference into the description and nothing parses it on the way in,
        // so a substring match on the payee is the only way to find it.
        assertTrue(queryFor("IB0338WKXT", 261.00).contains("tr.payee like '%IB0338WKXT%'"));
    }

    @Test
    @DisplayName("Only the opposite amount is a candidate")
    void testSearchesForTheNegatedAmount() {

        // A payee substring is loose on its own.  Requiring this side's exact negation is most of
        // what makes the match safe:  the far leg of +$261.00 is -$261.00.
        assertTrue(queryFor("IB0338WKXT", 261.00).contains("abs(tr.amount - -261.0) < 0.005"),
                "the search is for the negation, to the cent");
        assertTrue(queryFor("IB0338WKXT", -261.00).contains("abs(tr.amount - 261.0) < 0.005"),
                "and symmetrically from the other side");
    }

    @Test
    @DisplayName("The register being imported cannot match its own transaction")
    void testExcludesTheCurrentRegister() {

        // Without this the transfer finds itself:  its own row carries the reference and, from the
        // far side's point of view, the opposite amount.
        assertTrue(queryFor("IB0338WKXT", 261.00)
                        .contains("tr.Register_idRegister <> uuid_to_bin('" + BILL_PAY_DAVE + "')"),
                "a transfer must not be identified as coming from the account it is in");
    }

    @Test
    @DisplayName("The search is bounded by a date window")
    void testDateWindow() {

        String query = queryFor("IB0338WKXT", 261.00);

        assertTrue(query.contains("tr.postDate between " +
                        Utility.calendarDateToSqlDateString(dateOf(2026, Calendar.AUGUST, 29)) +
                        " and " + Utility.calendarDateToSqlDateString(dateOf(2026, Calendar.SEPTEMBER, 8))),
                "both legs of a transfer post within days of each other");
    }

    @Test
    @DisplayName("A reference from a payee is quoted safely")
    void testReferenceIsEscaped() {

        // Queries here are concatenated, not prepared, and this value comes out of bank-supplied
        // text.  The extractor only yields alphanumerics, but the query must not depend on that
        // holding for every institution added later.
        String query = Transaction.bankReferenceQuery("AB'12", BILL_PAY_DAVE,
                dateOf(2026, Calendar.AUGUST, 29), dateOf(2026, Calendar.SEPTEMBER, 8), 1.00);

        assertTrue(query.contains(Utility.escapeSqlString("AB'12")));
        assertFalse(query.contains("'%AB'12%'"), "an unescaped quote would break the statement");
    }

    @Test
    @DisplayName("No reference means no lookup, not a database call")
    void testAbsentReferenceReturnsEmpty() throws Exception {

        // The rule the whole feature is bound by:  the reference confirms a match, it never gates
        // one.  About a fifth of transfers carry none and must reach exactly the questions they
        // reach today, so every missing input is an empty answer rather than an error.
        Calendar date = dateOf(2026, Calendar.SEPTEMBER, 3);

        assertTrue(Transaction.findByBankReference(null, BILL_PAY_DAVE, date, 261.00, 5).isEmpty());
        assertTrue(Transaction.findByBankReference("   ", BILL_PAY_DAVE, date, 261.00, 5).isEmpty());
        assertTrue(Transaction.findByBankReference("IB0338WKXT", null, date, 261.00, 5).isEmpty());
        assertTrue(Transaction.findByBankReference("IB0338WKXT", BILL_PAY_DAVE, null, 261.00, 5).isEmpty());
    }

    @Test
    @DisplayName("The reference the extractor yields is the one the query looks for")
    void testExtractorAndQueryAgree() {

        // The two halves have to line up on the same string:  the extractor upper-cases what it
        // finds, so a query built from a raw payee substring instead would miss.
        String payee = "ONLINE TRANSFER FROM HIXON D REF #IB0338WKXT EVERYDAY CHECKING OVERDRAFTS";
        String reference = BankReferenceNumber.extract(payee);

        assertEquals("IB0338WKXT", reference);
        assertTrue(queryFor(reference, 261.00).contains("'%IB0338WKXT%'"));
    }
}
