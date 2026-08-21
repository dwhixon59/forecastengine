package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static com.hixon.financialApp.model.budget.Item.MAXIMUM_PERIOD_DAYS;
import static com.hixon.financialApp.model.budget.Item.MINIMUM_PERIOD_DAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link Item.PeriodType#FIXED_DAYS} period, which recurs a set number of days after the previous
 * occurrence rather than on a calendar boundary.
 *
 * <p>The worked example throughout is a dog medication supplied 25 pills at a time, so the bottle is replaced every
 * 25 days.  A 25 day cycle drifts through the calendar — it never lands on the same day of the month or the same day
 * of the week twice — which is the reason none of the existing periods can express it.</p>
 */
@DisplayName("Fixed number of days period")
class FixedDaysPeriodTest {

    /** 01-01-2026, the day the first bottle is bought. */
    private static Calendar startDate() {
        return new GregorianCalendar(2026, Calendar.JANUARY, 1);
    }

    /** A budget item recurring every {@code days} days from 01-01-2026. */
    private static BudgetItem medication(int days) {
        BudgetItem item = new BudgetItem();
        item.setPayee("Pet Medications");
        item.setCategory("Pets");
        item.setPeriod(Item.PeriodType.FIXED_DAYS);
        item.setPeriodDays(days);
        item.setStartDate(startDate());
        item.setAmount(-75.00);
        item.setHowOccurs(Item.HowOccurs.PERIODIC);
        return item;
    }

    private static String asDate(Calendar calendar) {
        return Utility.calendarDateToStringDate(calendar);
    }

    /*
     * Storing and reading back the period.  The day count travels inside the period string, so that it reaches the
     * forecast_item table through the raw SQL that copies bi.period into fi.period.
     */

    @Test
    @DisplayName("stores the day count inside the period")
    void storesTheDayCountInsideThePeriod() throws BudgetException {

        assertEquals("Every-25-Days", Item.generatePeriodType(Item.PeriodType.FIXED_DAYS, 25));
    }

    @Test
    @DisplayName("reads the period and the day count back")
    void readsThePeriodAndDayCountBack() throws BudgetException {

        assertEquals(Item.PeriodType.FIXED_DAYS, Item.parsePeriodType("Every-25-Days"));
        assertEquals(25, Item.parsePeriodDays("Every-25-Days"));
    }

    @Test
    @DisplayName("leaves the existing periods untouched")
    void leavesTheExistingPeriodsUntouched() throws BudgetException {

        // The stored form of every other period is unchanged, and none of them carry a day count.
        assertEquals("Monthly", Item.generatePeriodType(Item.PeriodType.MONTHLY, 0));
        assertEquals("Four-Weeks", Item.generatePeriodType(Item.PeriodType.FOUR_WEEKS, 0));
        assertEquals(Item.PeriodType.MONTHLY, Item.parsePeriodType("Monthly"));
        assertEquals(0, Item.parsePeriodDays("Monthly"));
        assertEquals(0, Item.parsePeriodDays("Four-Weeks"));
    }

    @Test
    @DisplayName("rejects a stored period that is not a period at all")
    void rejectsAStoredPeriodThatIsNotAPeriod() {

        assertThrows(BudgetException.class, () -> Item.parsePeriodType("Every-25-Weeks"));
        assertThrows(BudgetException.class, () -> Item.parsePeriodType("Every--Days"));
        assertThrows(BudgetException.class, () -> Item.parsePeriodType("Fortnightly"));
    }

    @Test
    @DisplayName("rejects a day count outside the range a fixed-day period allows")
    void rejectsADayCountOutsideTheAllowedRange() {

        assertThrows(BudgetException.class,
                () -> Item.generatePeriodType(Item.PeriodType.FIXED_DAYS, MINIMUM_PERIOD_DAYS - 1));
        assertThrows(BudgetException.class,
                () -> Item.generatePeriodType(Item.PeriodType.FIXED_DAYS, MAXIMUM_PERIOD_DAYS + 1));

        // The single-argument form cannot know the day count, so it must refuse rather than store a period with none.
        assertThrows(BudgetException.class, () -> Item.generatePeriodType(Item.PeriodType.FIXED_DAYS));
    }

    /*
     * Recurrence.  A fixed-day item steps by its own number of days in both directions.
     */

    @Test
    @DisplayName("steps forward by its own number of days")
    void stepsForwardByItsOwnNumberOfDays() throws ForecastException {

        BudgetItem item = medication(25);

        Calendar occurrence = item.getNextDateOfOccurrence(startDate());
        assertEquals("01-26-2026", asDate(occurrence));

        // The cycle drifts through the calendar rather than landing on a fixed day of the month.
        occurrence = item.getNextDateOfOccurrence(occurrence);
        assertEquals("02-20-2026", asDate(occurrence));

        occurrence = item.getNextDateOfOccurrence(occurrence);
        assertEquals("03-17-2026", asDate(occurrence));
    }

    @Test
    @DisplayName("steps backward by its own number of days")
    void stepsBackwardByItsOwnNumberOfDays() throws ForecastException {

        BudgetItem item = medication(25);

        assertEquals("01-01-2026",
                asDate(item.getPreviousDateOfOccurrence(new GregorianCalendar(2026, Calendar.JANUARY, 26))));
        assertEquals("02-20-2026",
                asDate(item.getPreviousDateOfOccurrence(new GregorianCalendar(2026, Calendar.MARCH, 17))));
    }

    @Test
    @DisplayName("stepping forward then back returns to the same date")
    void steppingForwardThenBackReturnsToTheSameDate() throws ForecastException {

        BudgetItem item = medication(25);
        Calendar occurrence = startDate();

        for (int step = 0; step < 20; step++) {
            occurrence = item.getNextDateOfOccurrence(occurrence);
        }
        for (int step = 0; step < 20; step++) {
            occurrence = item.getPreviousDateOfOccurrence(occurrence);
        }

        assertEquals("01-01-2026", asDate(occurrence));
    }

    /*
     * Landing in a forecast window.  This is what decides where the item first shows up in a forecast.
     */

    @Test
    @DisplayName("finds the first occurrence on or after a date part way through a cycle")
    void findsTheFirstOccurrencePartWayThroughACycle() throws ForecastException {

        BudgetItem item = medication(25);

        // 02-01-2026 is 31 days after the start, so it is 6 days into the second cycle and the next bottle is due on
        // 02-20-2026.
        assertEquals("02-20-2026",
                asDate(item.getFirstDateOnOrAfter(new GregorianCalendar(2026, Calendar.FEBRUARY, 1))));
    }

    @Test
    @DisplayName("returns the date itself when it is an occurrence")
    void returnsTheDateItselfWhenItIsAnOccurrence() throws ForecastException {

        BudgetItem item = medication(25);

        assertEquals("01-26-2026",
                asDate(item.getFirstDateOnOrAfter(new GregorianCalendar(2026, Calendar.JANUARY, 26))));
        assertEquals("01-01-2026", asDate(item.getFirstDateOnOrAfter(startDate())));
    }

    @Test
    @DisplayName("every occurrence found in a window is a whole number of cycles from the start")
    void everyOccurrenceIsAWholeNumberOfCyclesFromTheStart() throws ForecastException {

        BudgetItem item = medication(25);

        // Walk a year of query dates and check the answer is always a real occurrence of the item on or after the
        // date asked about.
        Calendar queryDate = startDate();
        for (int day = 0; day < 365; day++) {
            Calendar occurrence = item.getFirstDateOnOrAfter(queryDate);

            int daysFromStart = Utility.daysBetween(startDate(), occurrence);
            assertEquals(0, daysFromStart % 25,
                    "the occurrence on " + asDate(occurrence) + " is not a whole number of 25 day cycles from " +
                            "the start date");
            assertTrue(Utility.daysBetween(queryDate, occurrence) >= 0,
                    "the occurrence on " + asDate(occurrence) + " is before the date asked about, " +
                            asDate(queryDate));
            assertTrue(Utility.daysBetween(queryDate, occurrence) < 25,
                    "the occurrence on " + asDate(occurrence) + " skipped a cycle after " + asDate(queryDate));

            queryDate.add(Calendar.DATE, 1);
        }
    }

    @Test
    @DisplayName("refuses to compute dates without a usable day count")
    void refusesToComputeDatesWithoutAUsableDayCount() {

        // A zero day count would make the recurrence never advance, so it is refused rather than looping.
        BudgetItem item = medication(0);

        assertThrows(ForecastException.class, () -> item.getNextDateOfOccurrence(startDate()));
        assertThrows(ForecastException.class, () -> item.getFirstDateOnOrAfter(startDate()));
        assertThrows(ForecastException.class, () -> item.getPreviousDateOfOccurrence(startDate()));
    }

    /*
     * Amounts and validation.
     */

    @Test
    @DisplayName("annualizes on its own cycle length")
    void annualizesOnItsOwnCycleLength() {

        // 365 / 25 is 14.6 bottles a year at $75 each.
        assertEquals(-75.0 / 25.0 * 365.0, medication(25).getForecastAnnualAmount(), 0.0001);

        // A 7 day cycle should annualize the same as the weekly period does.
        BudgetItem weekly = medication(7);
        assertEquals(-75.0 / 7.0 * 365.0, weekly.getForecastAnnualAmount(), 0.0001);
    }

    @Test
    @DisplayName("validates that only a fixed-day item carries a day count")
    void validatesThatOnlyAFixedDayItemCarriesADayCount() {

        BudgetItem missingDayCount = medication(0);
        assertThrows(BudgetException.class, missingDayCount::validatePeriodHowOccursConsistency);

        BudgetItem monthlyWithDayCount = medication(25);
        monthlyWithDayCount.setPeriod(Item.PeriodType.MONTHLY);
        assertThrows(BudgetException.class, monthlyWithDayCount::validatePeriodHowOccursConsistency);
    }

    @Test
    @DisplayName("accepts a valid fixed-day item")
    void acceptsAValidFixedDayItem() throws BudgetException {

        medication(25).validatePeriodHowOccursConsistency();
    }

    @Test
    @DisplayName("allows a date variance in proportion to the cycle length")
    void allowsADateVarianceInProportionToTheCycleLength() throws BudgetException {

        // A short cycle is held to a tight tolerance, a long one to a loose one, matching the calendar periods of
        // comparable length.
        assertTrue(medication(7).isWithinNormalDateVariance(2));
        assertTrue(!medication(7).isWithinNormalDateVariance(5));

        assertTrue(medication(25).isWithinNormalDateVariance(3));
        assertTrue(!medication(25).isWithinNormalDateVariance(6));

        assertTrue(medication(180).isWithinNormalDateVariance(6));
        assertTrue(!medication(180).isWithinNormalDateVariance(10));
    }
}
