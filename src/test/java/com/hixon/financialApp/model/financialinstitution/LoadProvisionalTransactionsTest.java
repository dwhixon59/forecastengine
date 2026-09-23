package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.controller.CancelException;
import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.controller.SkipException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link FinancialInstitutionInt#loadProvisionalTransactions}:  the per-line default that
 * Wells Fargo uses, and Citi's override that reads a paste of the portal's activity page.
 */
class LoadProvisionalTransactionsTest {

    /** Lines shaped like a Wells Fargo pending file (BillPayDanni-ProvTrx.tsv), with a blank line and a header. */
    private static final List<String> WELLS_FARGO_LINES = List.of(
            "Pending transactions",
            "\t09/23/26\tPURCHASE EFORMS 844-5 +18445336767 FL CARD0148\t\t$49.00",
            "",
            "\t09/23/26\tPURCHASE PUBLIX #361 SARASOTA FL CARD0148\t\t$51.49",
            "\t09/22/26\tONLINE TRANSFER FROM SAVINGS\t$200.00\t",
            "\t09/22/26\tPURCHASE WITH NO AMOUNT CARD0148\t\t");

    @Test
    void theDefaultGivesWellsFargoTheSameTransactionsAsThePerLineLoop() throws Exception {
        WellsFargoBank bank = new WellsFargoBank(mock(SessionController.class));
        Register register = mock(Register.class);

        // What the import did before the hook existed:  one call per line, skipping unparseable lines.
        List<Transaction> perLine = new ArrayList<>();
        for (String line : WELLS_FARGO_LINES) {
            try {
                perLine.add(bank.loadProvisionalTransactionFromCSV(line, register));
            } catch (ParseException ignored) {
            }
        }

        ProvisionalFileContents contents = bank.loadProvisionalTransactions(WELLS_FARGO_LINES, register);

        assertEquals(3, perLine.size());
        assertEquals(perLine.size(), contents.transactions().size());
        for (int i = 0; i < perLine.size(); i++) {
            Transaction expected = perLine.get(i);
            Transaction actual = contents.transactions().get(i);
            assertEquals(expected.getPayee(), actual.getPayee());
            assertEquals(expected.getAmount(), actual.getAmount(), 0.001);
            assertEquals(expected.getPostDate(), actual.getPostDate());
            assertFalse(actual.isCleared());
        }
        assertTrue(contents.coveredRange().isUnbounded());
    }

    @Test
    void theDefaultSkipsLinesTheUserSkipsOrCancelsAndKeepsTheRest() throws Exception {
        FinancialInstitutionInt institution = mock(FinancialInstitutionInt.class);
        Register register = mock(Register.class);
        Transaction kept = mock(Transaction.class);
        when(institution.loadProvisionalTransactions(any(), any())).thenCallRealMethod();
        when(institution.loadProvisionalTransactionFromCSV(anyString(), any())).thenAnswer(call -> {
            switch ((String) call.getArgument(0)) {
                case "skip" -> throw new SkipException("skipped");
                case "cancel" -> throw new CancelException("cancelled");
                case "junk" -> throw new ParseException("junk", 0);
                default -> {
                    return kept;
                }
            }
        });

        ProvisionalFileContents contents =
                institution.loadProvisionalTransactions(List.of("skip", "keep", "cancel", "junk"), register);

        assertEquals(List.of(kept), contents.transactions());
    }

    @Test
    void theDefaultLetsAnyOtherFailureThrough() throws Exception {
        FinancialInstitutionInt institution = mock(FinancialInstitutionInt.class);
        when(institution.loadProvisionalTransactions(any(), any())).thenCallRealMethod();
        when(institution.loadProvisionalTransactionFromCSV(anyString(), any()))
                .thenThrow(new IllegalStateException("database down"));

        assertThrows(IllegalStateException.class,
                () -> institution.loadProvisionalTransactions(List.of("line"), mock(Register.class)));
    }

    @Test
    void citiTurnsThe0923PasteIntoTwoPendingTransactions() throws IOException {
        Register register = mock(Register.class);
        Calendar today = day(2026, 9, 23);
        CitiPendingActivityParser.Result parsed = CitiPendingActivityParser.parse(
                readFixture("citi/pending-activity-20260923.txt").lines().toList(), today);

        ProvisionalFileContents contents = CitiBank.toProvisionalFileContents(parsed, register);

        assertEquals(2, contents.transactions().size());
        Transaction electric = contents.transactions().get(0);
        assertEquals("PEACE RIVER ELECTRIC WAUCHULA USA", electric.getPayee());
        assertEquals(-405.01, electric.getAmount(), 0.001);
        assertFalse(electric.isCleared());
        assertSameDay(day(2026, 9, 22), electric.getPostDate());
        // A Citi pending row's one date is the day the charge was authorized.
        assertSameDay(day(2026, 9, 22), electric.getAuthorizationDate());

        Transaction spectrum = contents.transactions().get(1);
        assertEquals("Spectrum SAINT LOUIS USA", spectrum.getPayee());
        assertEquals(-79.99, spectrum.getAmount(), 0.001);
        assertSameDay(day(2026, 9, 21), spectrum.getPostDate());

        assertSame(parsed.coveredRange(), contents.coveredRange());
    }

    @Test
    void citiReadsThePasteThroughTheHook() throws IOException {
        CitiBank bank = new CitiBank(mock(SessionController.class));

        ProvisionalFileContents contents = bank.loadProvisionalTransactions(
                readFixture("citi/pending-activity-20260923.txt").lines().toList(), mock(Register.class));

        assertEquals(2, contents.transactions().size());
        assertFalse(contents.coveredRange().isUnbounded());
        assertTrue(contents.coveredRange().contains(day(2026, 9, 11)));
        assertFalse(contents.coveredRange().contains(day(2026, 9, 10)));
    }

    @Test
    void theAuthorizationDateIsNotTheSameCalendarAsThePostDate() {
        Calendar date = day(2026, 9, 22);
        Transaction transaction = new Transaction(mock(Register.class), date, "X", -1.00, "X");

        transaction.getPostDate().add(Calendar.DAY_OF_MONTH, 1);

        assertSameDay(day(2026, 9, 22), transaction.getAuthorizationDate());
    }

    // ---- helpers ----

    private static String readFixture(String path) throws IOException {
        try (InputStream in = LoadProvisionalTransactionsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing test fixture " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Calendar day(int year, int month, int dayOfMonth) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, dayOfMonth);
        return c;
    }

    private static void assertSameDay(Calendar expected, Calendar actual) {
        assertNotNull(actual);
        assertEquals(expected.get(Calendar.YEAR), actual.get(Calendar.YEAR));
        assertEquals(expected.get(Calendar.MONTH), actual.get(Calendar.MONTH));
        assertEquals(expected.get(Calendar.DAY_OF_MONTH), actual.get(Calendar.DAY_OF_MONTH));
    }
}
