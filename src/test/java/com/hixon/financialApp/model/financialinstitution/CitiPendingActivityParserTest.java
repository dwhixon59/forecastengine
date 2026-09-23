package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.model.financialinstitution.CitiPendingActivityParser.PendingRecord;
import com.hixon.financialApp.model.financialinstitution.CitiPendingActivityParser.Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CitiPendingActivityParser}, mostly on real pastes of the Citi portal's activity page.
 * See CITI_PENDING_TRANSACTIONS_DESIGN.md &sect;1 for the samples and &sect;5 for this list of cases.
 */
class CitiPendingActivityParserTest {

    /** The day the tests run "on", the end of a "Since ..." period. */
    private static final Calendar TODAY = day(2026, 9, 23);

    /**
     * The 09-15 sample (&sect;1.1), with the page text around the table that the design doc lists:
     * the statement and payment dates, the last sign-on and the credit score.
     */
    private static final String SAMPLE_0915 = """
            Statement closing Oct 12, 2026
            Payment due on Oct 08, 2026
            Last sign on: Sep. 15, 2026 (4:52 AM ET)
            Your FICO Score
            742
            as of 08/25/2026
            Current Balance
            $11,610.12
            Time Period
            Since Sep 11, 2026
            Transactions
                Date
                Description
                Name
                Amount
                Running Balance
            Pending Total
            Pending Purchases
            $80.23

            Sep 15, 2026
            LA FITNESS IRVINE USA
            DAVID W HIXON
            $80.23
            -----
            Posted Total
            Posted Total
            -$662.40

            Sep 13, 2026
            ADT SECURITY*320925392 BOCA RATON FL
            DAVID W HIXON
            $53.49
            $11,610.12

            Sep 12, 2026
            ONLINE PAYMENT, THANK YOU
            DAVID W HIXON
            -$750.00
            $11,556.63

            Sep 12, 2026
            VXNBILL.COM CAMDEN DE
            DAVID W HIXON
            $9.95
            $12,306.63

            Sep 11, 2026
            Spotify USA New York NY
            DAVID W HIXON
            $24.16
            $12,296.68
            """;

    /**
     * The earlier sample, filtered to Sep 14 - Sep 15 with the running balance hidden.  Rebuilt from
     * the design doc's description of it (the paste itself was not kept).
     */
    private static final String SAMPLE_0914_0915 = """
            Time Period
            Sep 14, 2026 - Sep 15, 2026
            Pending Total
            Pending Purchases
            $80.23
            Sep 15, 2026
            LA FITNESS IRVINE USA
            DAVID W HIXON
            $80.23
            """;

    @Test
    void the0915SampleGivesOnlyThePendingLaFitnessCharge() {
        Result result = parse(SAMPLE_0915);

        assertEquals(1, result.records().size());
        assertRecord(result.records().get(0), 2026, 9, 15, -80.23, "LA FITNESS IRVINE USA", "DAVID W HIXON");
        assertRange(result, day(2026, 9, 11), TODAY);
    }

    @Test
    void theFilteredSampleWithTheRunningBalanceHiddenGivesTheSameRecord() {
        Result result = parse(SAMPLE_0914_0915);

        assertEquals(1, result.records().size());
        assertRecord(result.records().get(0), 2026, 9, 15, -80.23, "LA FITNESS IRVINE USA", "DAVID W HIXON");
        assertRange(result, day(2026, 9, 14), day(2026, 9, 15));
    }

    @Test
    void the0923SampleGivesBothPendingChargesAndSkipsEveryPostedRow() throws IOException {
        // The posted Spectrum row is the same charge as the pending one; the parser still returns the
        // pending row, and the import's cleared-twin check is what skips it.  The N/A row is posted.
        Result result = parse(readFixture("citi/pending-activity-20260923.txt"));

        assertEquals(2, result.records().size());
        assertRecord(result.records().get(0), 2026, 9, 22, -405.01,
                "PEACE RIVER ELECTRIC WAUCHULA USA", "DAVID W HIXON");
        assertRecord(result.records().get(1), 2026, 9, 21, -79.99, "Spectrum SAINT LOUIS USA", "DAVID W HIXON");
        assertRange(result, day(2026, 9, 11), TODAY);
    }

    @Test
    void pageTextWithDatesInItNeverStartsARecord() {
        Result result = parse("""
                Statement closing Oct 12, 2026
                Payment due on Oct 08, 2026
                Last sign on: Sep. 15, 2026 (4:52 AM ET)
                as of 08/25/2026
                $500.00
                """);

        assertTrue(result.records().isEmpty());
    }

    @Test
    void totalsAndRunningBalancesAreNeverRecords() {
        Result result = parse("""
                Pending Total
                Pending Purchases
                $80.23
                -----
                Posted Total
                -$662.40
                Current Balance
                $11,610.12
                """);

        assertTrue(result.records().isEmpty());
    }

    @Test
    void aTabSeparatedCopyParsesLikeTheMultiLineCopy() {
        Result result = parse("Since Sep 11, 2026\n" +
                "Pending Total\n" +
                "Sep 15, 2026\tLA FITNESS IRVINE USA\tDAVID W HIXON\t$80.23\t-----\n" +
                "Posted Total\n" +
                "Sep 13, 2026\tADT SECURITY*320925392 BOCA RATON FL\tDAVID W HIXON\t$53.49\t$11,610.12\n");

        assertEquals(1, result.records().size());
        assertRecord(result.records().get(0), 2026, 9, 15, -80.23, "LA FITNESS IRVINE USA", "DAVID W HIXON");
    }

    @Test
    void twoIdenticalChargesOnOneDayAreBothReturned() {
        Result result = parse("""
                Pending Total
                Sep 20, 2026
                STARBUCKS BRADENTON USA
                DAVID W HIXON
                $6.45
                -----
                Sep 20, 2026
                STARBUCKS BRADENTON USA
                DAVID W HIXON
                $6.45
                -----
                Sep 19, 2026
                PUBLIX SARASOTA USA
                DANIELLE M HIXON
                $41.10
                -----
                """);

        assertEquals(3, result.records().size());
        assertRecord(result.records().get(0), 2026, 9, 20, -6.45, "STARBUCKS BRADENTON USA", "DAVID W HIXON");
        assertRecord(result.records().get(1), 2026, 9, 20, -6.45, "STARBUCKS BRADENTON USA", "DAVID W HIXON");
        assertRecord(result.records().get(2), 2026, 9, 19, -41.10, "PUBLIX SARASOTA USA", "DANIELLE M HIXON");
    }

    @Test
    void aPendingCreditBecomesAPositiveAmount() {
        Result result = parse("""
                Pending Total
                Sep 20, 2026
                AMAZON RETURN SEATTLE USA
                DAVID W HIXON
                -$25.00
                """);

        assertEquals(25.00, result.records().get(0).amount(), 0.001);
    }

    @Test
    void largeAmountsKeepTheirThousands() {
        Result result = parse("""
                Pending Total
                Sep 20, 2026
                ROOFING CO SARASOTA USA
                DAVID W HIXON
                $12,345.67
                """);

        assertEquals(-12345.67, result.records().get(0).amount(), 0.001);
    }

    @Test
    void aDateWithNoAmountWithinFourLinesIsDropped() {
        Result result = parse("""
                Pending Total
                Sep 20, 2026
                SOMETHING
                DAVID W HIXON
                more text
                and more
                and still more
                $10.00
                Sep 19, 2026
                NETFLIX LOS GATOS USA
                DAVID W HIXON
                $22.56
                """);

        assertEquals(1, result.records().size());
        assertEquals("NETFLIX LOS GATOS USA", result.records().get(0).description());
    }

    @Test
    void aRecordCutOffByTheNextDateIsDropped() {
        Result result = parse("""
                Pending Total
                Sep 20, 2026
                HALF A ROW
                Sep 19, 2026
                NETFLIX LOS GATOS USA
                DAVID W HIXON
                $22.56
                """);

        assertEquals(1, result.records().size());
        assertEquals("NETFLIX LOS GATOS USA", result.records().get(0).description());
    }

    @Test
    void aDateFollowedDirectlyByAnAmountIsNotARecord() {
        Result result = parse("""
                Pending Total
                Sep 20, 2026
                $10.00
                """);

        assertTrue(result.records().isEmpty());
    }

    @Test
    void theCardholderIsOptionalAndNaMeansNone() {
        Result result = parse("""
                Pending Total
                Sep 15, 2026
                VISIBLE 8663313527 USA
                N/A
                $35.00
                Sep 14, 2026
                NO NAME COLUMN USA
                $5.00
                """);

        assertEquals(2, result.records().size());
        assertNull(result.records().get(0).cardholder());
        assertNull(result.records().get(1).cardholder());
    }

    @Test
    void aPasteWithNoHeadingsIsTreatedAsPending() {
        Result result = parse("""
                Sep 15, 2026
                LA FITNESS IRVINE USA
                DAVID W HIXON
                $80.23
                """);

        assertEquals(1, result.records().size());
    }

    @Test
    void withoutATimePeriodTheRangeSpansEveryRowIncludingPostedOnes() {
        Result result = parse("""
                Pending Total
                Sep 22, 2026
                PEACE RIVER ELECTRIC WAUCHULA USA
                DAVID W HIXON
                $405.01
                Posted Total
                Sep 12, 2026
                VXNBILL.COM CAMDEN DE
                DAVID W HIXON
                $9.95
                """);

        assertRange(result, day(2026, 9, 12), day(2026, 9, 22));
    }

    @Test
    void aPasteWithNothingInItCoversNoDates() {
        // A paste of the wrong page must not let every pending register row "fall off".
        Result result = parse("Hello\nnothing here\n");

        assertTrue(result.records().isEmpty());
        assertTrue(result.coveredRange().isEmpty());
        assertFalse(result.coveredRange().contains(day(2026, 9, 20)));
    }

    @Test
    void aSincePeriodAfterTodayCoversJustThatDay() {
        Result result = parse("Since Sep 25, 2026\n");

        assertRange(result, day(2026, 9, 25), day(2026, 9, 25));
    }

    @Test
    void aRangeWithAnEnDashIsRead() {
        Result result = parse("Sep 14, 2026 – Sep 15, 2026\n");

        assertRange(result, day(2026, 9, 14), day(2026, 9, 15));
    }

    @Test
    void anImpossibleDateDoesNotStartARecord() {
        Result result = parse("""
                Pending Total
                Sep 31, 2026
                NOT A DAY USA
                DAVID W HIXON
                $1.00
                """);

        assertTrue(result.records().isEmpty());
    }

    // ---- helpers ----

    private static Result parse(String paste) {
        return CitiPendingActivityParser.parse(paste.lines().toList(), TODAY);
    }

    private static String readFixture(String path) throws IOException {
        try (InputStream in = CitiPendingActivityParserTest.class.getClassLoader().getResourceAsStream(path)) {
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

    private static void assertRecord(PendingRecord record, int year, int month, int dayOfMonth, double amount,
                                     String description, String cardholder) {
        assertEquals(year, record.date().get(Calendar.YEAR));
        assertEquals(month - 1, record.date().get(Calendar.MONTH));
        assertEquals(dayOfMonth, record.date().get(Calendar.DAY_OF_MONTH));
        assertEquals(amount, record.amount(), 0.001);
        assertEquals(description, record.description());
        assertEquals(cardholder, record.cardholder());
    }

    private static void assertRange(Result result, Calendar start, Calendar end) {
        DateRange range = result.coveredRange();
        assertFalse(range.isEmpty());
        assertFalse(range.isUnbounded());
        assertEquals(String.format("%1$tY-%1$tm-%1$td .. %2$tY-%2$tm-%2$td", start, end), range.toString());
    }
}
