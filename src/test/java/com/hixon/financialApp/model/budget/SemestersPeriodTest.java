package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link Item.PeriodType#SEMESTERS} period, which recurs monthly on the start date's day but only
 * during the fall and spring semesters.
 *
 * <p>The worked example is Justin's UF meal plan:  $700 billed on the 15th of August, September, October and November,
 * and of January, February, March and April.  Nothing is billed in December or over the summer.</p>
 */
@DisplayName("Semesters period")
class SemestersPeriodTest {

    /** Fall starts in August, spring in January, four payments each. */
    private static final int UF_SCHEDULE = Item.packSemesterSchedule(8, 1, 4);

    private static Calendar date(int year, int month, int day) {
        return new GregorianCalendar(year, month, day);
    }

    private static String asDate(Calendar calendar) {
        return calendar == null ? null : Utility.calendarDateToStringDate(calendar);
    }

    /** The meal plan, paid on the 15th from 08-15-2026. */
    private static BudgetItem mealPlan() {
        return mealPlan(date(2026, Calendar.AUGUST, 15), UF_SCHEDULE);
    }

    private static BudgetItem mealPlan(Calendar startDate, int schedule) {
        BudgetItem item = new BudgetItem();
        item.setPayee("Justin's Meal Plan");
        item.setCategory("Children");
        item.setPeriod(Item.PeriodType.SEMESTERS);
        item.setPeriodDays(schedule);
        item.setStartDate(startDate);
        item.setAmount(-700.00);
        item.setHowOccurs(Item.HowOccurs.PERIODIC);
        return item;
    }

    /*
     * Storing and reading the period back.
     */

    @Test
    @DisplayName("stores the schedule inside the period")
    void storesTheScheduleInsideThePeriod() throws BudgetException {
        assertEquals("Semesters-Aug-Jan-4", Item.generatePeriodType(Item.PeriodType.SEMESTERS, UF_SCHEDULE));
    }

    @Test
    @DisplayName("reads the period and the schedule back")
    void readsThePeriodAndScheduleBack() throws BudgetException {
        assertEquals(Item.PeriodType.SEMESTERS, Item.parsePeriodType("Semesters-Aug-Jan-4"));
        int schedule = Item.parsePeriodDays("Semesters-Aug-Jan-4");
        assertEquals(8, Item.fallStartMonthOf(schedule));
        assertEquals(1, Item.springStartMonthOf(schedule));
        assertEquals(4, Item.paymentsPerSemesterOf(schedule));
    }

    @Test
    @DisplayName("rejects a stored schedule that is not valid")
    void rejectsAnInvalidStoredSchedule() {
        assertThrows(BudgetException.class, () -> Item.parsePeriodDays("Semesters-Xyz-Jan-4"));
        assertThrows(BudgetException.class, () -> Item.parsePeriodDays("Semesters-Aug-Jan-0"));
        assertThrows(BudgetException.class, () -> Item.parsePeriodDays("Semesters-Aug-Jan-7"));
        assertThrows(BudgetException.class, () -> Item.parsePeriodDays("Semesters-Aug-Oct-4"));
        assertThrows(BudgetException.class,
                () -> Item.generatePeriodType(Item.PeriodType.SEMESTERS, Item.packSemesterSchedule(8, 1, 0)));
    }

    @Test
    @DisplayName("describes the schedule the way a person would say it")
    void describesTheSchedule() {
        assertEquals("Aug-Nov & Jan-Apr", Item.describeSemesterSchedule(UF_SCHEDULE));
        assertEquals("Aug & Jan", Item.describeSemesterSchedule(Item.packSemesterSchedule(8, 1, 1)));
    }

    @Test
    @DisplayName("reads months typed as numbers, abbreviations or names")
    void readsMonths() {
        assertEquals(8, Item.parseMonth("8"));
        assertEquals(8, Item.parseMonth("aug"));
        assertEquals(8, Item.parseMonth("August"));
        assertEquals(0, Item.parseMonth("13"));
        assertEquals(0, Item.parseMonth("Au"));
        assertEquals(0, Item.parseMonth("Summer"));
    }

    /*
     * Validation.
     */

    @Test
    @DisplayName("finds what is wrong with a schedule")
    void findsScheduleProblems() {
        assertNull(Item.semesterScheduleProblem(UF_SCHEDULE));
        assertNotNull(Item.semesterScheduleProblem(Item.packSemesterSchedule(8, 10, 4)), "fall and spring overlap");
        assertNotNull(Item.semesterScheduleProblem(Item.packSemesterSchedule(13, 1, 4)), "no such month");
        assertNotNull(Item.semesterScheduleProblem(Item.packSemesterSchedule(8, 1, 7)), "too many payments");
    }

    @Test
    @DisplayName("is a scheduled period, so it cannot be unplanned")
    void cannotBeUnplanned() {
        BudgetItem item = mealPlan();
        item.setHowOccurs(Item.HowOccurs.UNPLANNED);
        assertThrows(BudgetException.class, item::validatePeriodHowOccursConsistency);
    }

    @Test
    @DisplayName("accepts a valid schedule and rejects an invalid one")
    void validatesTheSchedule() throws BudgetException {
        mealPlan().validatePeriodHowOccursConsistency();

        BudgetItem overlapping = mealPlan(date(2026, Calendar.AUGUST, 15), Item.packSemesterSchedule(8, 10, 4));
        assertThrows(BudgetException.class, overlapping::validatePeriodHowOccursConsistency);
    }

    /*
     * Dates.
     */

    @Test
    @DisplayName("first payment on or after a date inside a semester")
    void firstPaymentInsideASemester() throws ForecastException {
        BudgetItem item = mealPlan();
        assertEquals("09-15-2026", asDate(item.getFirstDateOnOrAfter(date(2026, Calendar.SEPTEMBER, 15))));
        assertEquals("10-15-2026", asDate(item.getFirstDateOnOrAfter(date(2026, Calendar.SEPTEMBER, 20))));
    }

    @Test
    @DisplayName("first payment skips winter break and summer")
    void firstPaymentSkipsBreaks() throws ForecastException {
        BudgetItem item = mealPlan();
        assertEquals("01-15-2027", asDate(item.getFirstDateOnOrAfter(date(2026, Calendar.NOVEMBER, 16))));
        assertEquals("08-15-2027", asDate(item.getFirstDateOnOrAfter(date(2027, Calendar.MAY, 1))));
    }

    @Test
    @DisplayName("first payment is never before the item's start date")
    void firstPaymentNotBeforeStart() throws ForecastException {
        assertEquals("08-15-2026", asDate(mealPlan().getFirstDateOnOrAfter(date(2026, Calendar.JANUARY, 1))));
    }

    @Test
    @DisplayName("next payment crosses November to January and April to August")
    void nextPaymentCrossesBreaks() throws ForecastException {
        BudgetItem item = mealPlan();
        assertEquals("09-15-2026", asDate(item.getNextDateOfOccurrence(date(2026, Calendar.AUGUST, 15))));
        assertEquals("01-15-2027", asDate(item.getNextDateOfOccurrence(date(2026, Calendar.NOVEMBER, 15))));
        assertEquals("08-15-2027", asDate(item.getNextDateOfOccurrence(date(2027, Calendar.APRIL, 15))));
    }

    @Test
    @DisplayName("previous payment crosses the breaks backwards")
    void previousPaymentCrossesBreaks() throws ForecastException {
        BudgetItem item = mealPlan(date(2025, Calendar.AUGUST, 15), UF_SCHEDULE);
        assertEquals("11-15-2026", asDate(item.getPreviousDateOfOccurrence(date(2027, Calendar.JANUARY, 15))));
        assertEquals("04-15-2026", asDate(item.getPreviousDateOfOccurrence(date(2026, Calendar.AUGUST, 15))));
    }

    @Test
    @DisplayName("a pay day of the 31st is clamped to shorter months and restored in longer ones")
    void payDayIsClamped() throws ForecastException {
        BudgetItem item = mealPlan(date(2026, Calendar.AUGUST, 31), UF_SCHEDULE);
        assertEquals("09-30-2026", asDate(item.getNextDateOfOccurrence(date(2026, Calendar.AUGUST, 31))));
        assertEquals("10-31-2026", asDate(item.getNextDateOfOccurrence(date(2026, Calendar.SEPTEMBER, 30))));
    }

    @Test
    @DisplayName("one payment per semester recurs August and January")
    void onePaymentPerSemester() throws ForecastException {
        BudgetItem item = mealPlan(date(2026, Calendar.AUGUST, 15), Item.packSemesterSchedule(8, 1, 1));
        assertEquals("01-15-2027", asDate(item.getNextDateOfOccurrence(date(2026, Calendar.AUGUST, 15))));
        assertEquals("08-15-2027", asDate(item.getNextDateOfOccurrence(date(2027, Calendar.JANUARY, 15))));
    }

    @Test
    @DisplayName("a school year holds exactly the eight UF payments")
    void aSchoolYearHoldsEightPayments() throws ForecastException {
        BudgetItem item = mealPlan();
        Calendar end = date(2027, Calendar.JULY, 31);
        List<String> payments = new ArrayList<>();
        for (Calendar next = item.getFirstDateOnOrAfter(date(2026, Calendar.AUGUST, 1));
             next != null && next.compareTo(end) <= 0; next = item.getNextDateOfOccurrence(next)) {
            payments.add(asDate(next));
        }
        assertEquals(List.of("08-15-2026", "09-15-2026", "10-15-2026", "11-15-2026",
                "01-15-2027", "02-15-2027", "03-15-2027", "04-15-2027"), payments);
    }

    @Test
    @DisplayName("the end date stops the payments")
    void endDateStopsPayments() throws ForecastException {
        BudgetItem item = mealPlan();
        item.setEndDate(date(2027, Calendar.APRIL, 15));
        assertNull(item.getNextDateOfOccurrence(date(2027, Calendar.APRIL, 15)));
    }

    /*
     * Amounts and tolerances.
     */

    @Test
    @DisplayName("a year is two semesters of payments")
    void annualAmount() {
        assertEquals(-5600.00, mealPlan().getForecastAnnualAmount(), 0.001);
    }

    @Test
    @DisplayName("each payment has the monthly date tolerance")
    void dateTolerance() throws BudgetException {
        assertTrue(Item.isWithinNormalDateVariance(3, Item.PeriodType.SEMESTERS, Item.HowOccurs.PERIODIC));
        assertFalse(Item.isWithinNormalDateVariance(5, Item.PeriodType.SEMESTERS, Item.HowOccurs.PERIODIC));
    }
}
