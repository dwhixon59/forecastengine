package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Item.PeriodType#SCHOOL_YEAR_SEMIMONTHLY} period:  its annual amount, and the school-year
 * boundary that every part of the class now agrees on.
 *
 * <p>The school year runs <strong>August through May</strong>.  June and July are the only months out of session.
 * That is what the data says:  Justin's school lunches were charged in August (5 charges), September (9), October
 * (12), November (12), December (10), January (11), February (17), March (16), April (9) and May (11), and never in
 * June or July.  Ten months, paid twice each, is twenty payments a year.
 *
 * <p>This is the high-school case.  The college meal plan is a different period -- {@link Item.PeriodType#SEMESTERS},
 * stored as {@code Semesters-Aug-Jan-4}, which bills August through November and January through April and skips
 * December.  Nothing here touches it;  see SemestersPeriodTest.
 *
 * <p>Two faults were fixed together, because the amount cannot be right unless the boundary is:
 * <ul>
 *   <li>The annual amount was {@code amount * 9.0} -- it counted months instead of payments, so every item with
 *       this period was budgeted at half of what it really costs, and so was every category and annual total.</li>
 *   <li>The boundary disagreed with itself.  Going forward, July and August were sent to September while June was
 *       left alone, so the forecast planned June occurrences (a month with no charges in it) and skipped August
 *       (one of the busiest).  Going backward, September stepped to May, skipping August too -- so the forward and
 *       backward walks followed different school years.</li>
 * </ul>
 */
@DisplayName("School-year semi-monthly period")
class SchoolYearSemiMonthlyAmountTest {

    /**
     * A school-year item paid twice a month.
     *
     * <p>Seeded a school year before the dates the tests ask about, because {@code getPreviousDateOfOccurrence}
     * returns null once the previous occurrence would fall before the item's start date (Item.java, "if the next
     * date is before the first date of this budget item, then return no previous date").  Starting it on the same
     * August the backward tests step back from would make every one of them null rather than May 15.
     */
    private static BudgetItem schoolYearItem(double amount) {
        BudgetItem item = new BudgetItem();
        item.setPayee("School lunches");
        item.setCategory("Children");
        item.setPeriod(Item.PeriodType.SCHOOL_YEAR_SEMIMONTHLY);
        item.setStartDate(new GregorianCalendar(2025, Calendar.AUGUST, 1));
        item.setAmount(amount);
        item.setHowOccurs(Item.HowOccurs.PERIODIC);
        return item;
    }

    private static Calendar on(int year, int month, int day) {
        return new GregorianCalendar(year, month, day);
    }

    private static String asDate(Calendar calendar) {
        return calendar == null ? null : Utility.calendarDateToStringDate(calendar);
    }

    /*
     * The annual amount.
     */

    @Test
    @DisplayName("is twenty payments a year:  ten school-year months, paid twice each")
    void testTwentyPaymentsAYear() {
        assertEquals(-2000.00, schoolYearItem(-100.00).getForecastAnnualAmount(), 0.005);
    }

    @Test
    @DisplayName("is the semi-monthly year less June and July")
    void testIsSemiMonthlyLessTheSummer() {

        // Semi-monthly is 24 payments across twelve months.  The school year drops June and July, which is two
        // months and so four payments, leaving twenty:
        BudgetItem schoolYear = schoolYearItem(-100.00);
        BudgetItem allYear = schoolYearItem(-100.00);
        allYear.setPeriod(Item.PeriodType.SEMIMONTHLY);

        assertEquals(-2400.00, allYear.getForecastAnnualAmount(), 0.005);
        assertEquals(allYear.getForecastAnnualAmount() * 20.0 / 24.0,
                schoolYear.getForecastAnnualAmount(), 0.005);
    }

    @Test
    @DisplayName("the monthly average spreads the school year across all twelve months")
    void testMonthlyAverage() {

        // getAverageAmountForAMonth divides the annual figure by twelve, so a school-year item shows less per month
        // than it is actually billed in the months it falls in.  That is the intended meaning of the average:
        assertEquals(-2000.00 / 12.0, schoolYearItem(-100.00).getAverageAmountForAMonth(), 0.005);
    }

    @Test
    @DisplayName("an income item of this period is not halved either")
    void testIncomeIsNotHalved() {

        // The period is used for money coming in as well -- a school-year paycheque -- and the same halving applied
        // to it, understating the income side of the budget:
        assertEquals(2000.00, schoolYearItem(100.00).getForecastAnnualAmount(), 0.005);
    }

    @Test
    @DisplayName("regression:  the annual amount is no longer half of what it should be")
    void testIsNoLongerHalved() {

        // Kept as a test because the original and the correct figure differ by more than a factor of two, and an
        // amount that is quietly too small is hard to notice in a budget total:
        double original = -100.00 * 9.0;
        assertNotEquals(original, schoolYearItem(-100.00).getForecastAnnualAmount(), 0.005);
        assertEquals(-2000.00, schoolYearItem(-100.00).getForecastAnnualAmount(), 0.005);
    }

    /*
     * The school-year boundary, walking forwards.
     */

    @Test
    @DisplayName("steps over the summer:  the occurrence after May 15 is August 1")
    void testNextStepsOverTheSummer() throws ForecastException {
        BudgetItem item = schoolYearItem(-100.00);
        assertEquals("08-01-2027", asDate(item.getNextDateOfOccurrence(on(2027, Calendar.MAY, 15))));
    }

    @Test
    @DisplayName("never plans an occurrence in June or July")
    void testNoSummerOccurrences() throws ForecastException {
        BudgetItem item = schoolYearItem(-100.00);

        // Walk two full years from the start of the school year;  a June or July occurrence used to appear because
        // the forward step left June alone:
        Calendar date = on(2026, Calendar.AUGUST, 1);
        for (int step = 0; step < 40; step++) {
            int month = date.get(Calendar.MONTH);
            assertNotEquals(Calendar.JUNE, month, "planned an occurrence in June:  " + asDate(date));
            assertNotEquals(Calendar.JULY, month, "planned an occurrence in July:  " + asDate(date));
            date = item.getNextDateOfOccurrence(date);
        }
    }

    @Test
    @DisplayName("walks the 1st and the 15th of every month in session")
    void testWalksBothHalvesOfEachMonth() throws ForecastException {
        BudgetItem item = schoolYearItem(-100.00);

        assertEquals("08-15-2026", asDate(item.getNextDateOfOccurrence(on(2026, Calendar.AUGUST, 1))));
        assertEquals("09-01-2026", asDate(item.getNextDateOfOccurrence(on(2026, Calendar.AUGUST, 15))));
        assertEquals("12-15-2026", asDate(item.getNextDateOfOccurrence(on(2026, Calendar.DECEMBER, 1))));
        assertEquals("01-01-2027", asDate(item.getNextDateOfOccurrence(on(2026, Calendar.DECEMBER, 15))));
    }

    /*
     * The school-year boundary, walking backwards.  It has to agree with the forward walk.
     */

    @Test
    @DisplayName("steps back over the summer:  the occurrence before August 1 is May 15")
    void testPreviousStepsBackOverTheSummer() throws ForecastException {
        BudgetItem item = schoolYearItem(-100.00);
        assertEquals("05-15-2026", asDate(item.getPreviousDateOfOccurrence(on(2026, Calendar.AUGUST, 1))));
    }

    @Test
    @DisplayName("forwards and backwards agree:  August is in the school year, June and July are not")
    void testForwardAndBackwardAgree() throws ForecastException {
        BudgetItem item = schoolYearItem(-100.00);

        // The disagreement this pins:  the forward walk sent July and August to September, so August was skipped
        // going forward, while the backward walk sent September to May, skipping August going back as well.
        Calendar afterMay = item.getNextDateOfOccurrence(on(2027, Calendar.MAY, 15));
        assertEquals("08-01-2027", asDate(afterMay));
        assertEquals("05-15-2027", asDate(item.getPreviousDateOfOccurrence(afterMay)),
                "stepping forward over the summer and back again must return to where it started");

        Calendar beforeAugust = item.getPreviousDateOfOccurrence(on(2026, Calendar.AUGUST, 1));
        assertEquals("05-15-2026", asDate(beforeAugust));
        assertEquals("08-01-2026", asDate(item.getNextDateOfOccurrence(beforeAugust)),
                "stepping back over the summer and forward again must return to where it started");
    }

    /*
     * The closest occurrence, which had its own, different boundary.
     */

    @Test
    @DisplayName("the closest occurrence to an in-session date is in that same month")
    void testClosestOccurrenceInSession() {
        BudgetItem item = schoolYearItem(-100.00);

        // A January date used to have its "last occurrence before" pushed forward to August 15 -- seven months
        // after the date being asked about -- because the clamp tested the wrong way round:
        assertEquals("01-15-2027", asDate(ItemUtilities.getClosestOccurrence(item, on(2027, Calendar.JANUARY, 13))));
        assertEquals("12-01-2026", asDate(ItemUtilities.getClosestOccurrence(item, on(2026, Calendar.DECEMBER, 2))));
    }

    @Test
    @DisplayName("the closest occurrence to a summer date is the edge of the school year")
    void testClosestOccurrenceInSummer() {
        BudgetItem item = schoolYearItem(-100.00);

        // Early summer is nearer the May that just ended;  late summer is nearer the August about to start:
        assertEquals("05-15-2027", asDate(ItemUtilities.getClosestOccurrence(item, on(2027, Calendar.JUNE, 2))));
        assertEquals("08-01-2027", asDate(ItemUtilities.getClosestOccurrence(item, on(2027, Calendar.JULY, 28))));
    }
}
