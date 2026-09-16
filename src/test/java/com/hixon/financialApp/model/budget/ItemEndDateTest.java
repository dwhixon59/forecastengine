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
 * Tests that an item occurs on its end date.
 *
 * <p>The end date is the last date an item may occur on, but it is stored at midnight while the dates a forecast
 * generates carry the time of day the generation ran ({@link Utility#copyDate} sets only the year, month and day).
 * Comparing the two as instants made an item look expired for the whole of its final day.
 *
 * <p>Justin's meal plan, a monthly item from 08-15-2026 to 04-15-2027, was generated through 03-15-2027 on 09-16-2026:
 * the April payment, which is the one the end date exists to include, was missing.</p>
 */
@DisplayName("An item occurs on its end date")
class ItemEndDateTest {

    /** Midnight, as dates are stored. */
    private static Calendar storedDate(int year, int month, int day) {
        return new GregorianCalendar(year, month, day);
    }

    /** A date carrying a time of day, as a generated occurrence does. */
    private static Calendar generatedDate(int year, int month, int day) {
        return new GregorianCalendar(year, month, day, 6, 7, 8);
    }

    /** Justin's meal plan:  $700 a month from 08-15-2026 through 04-15-2027. */
    private static BudgetItem mealPlan() {
        BudgetItem item = new BudgetItem();
        item.setPayee("Justin's Meal Plan 2026-2027");
        item.setCategory("Children");
        item.setPeriod(Item.PeriodType.MONTHLY);
        item.setStartDate(storedDate(2026, Calendar.AUGUST, 15));
        item.setEndDate(storedDate(2027, Calendar.APRIL, 15));
        item.setAmount(-700.00);
        item.setHowOccurs(Item.HowOccurs.PERIODIC);
        return item;
    }

    @Test
    @DisplayName("an item is not expired on its end date, whatever the time of day")
    void notExpiredOnTheEndDate() {
        BudgetItem item = mealPlan();

        assertFalse(item.isExpired(generatedDate(2027, Calendar.APRIL, 15)), "the end date is still due");
        assertFalse(item.isExpired(storedDate(2027, Calendar.APRIL, 15)));
    }

    @Test
    @DisplayName("an item is expired the day after its end date")
    void expiredTheDayAfter() {
        assertTrue(mealPlan().isExpired(generatedDate(2027, Calendar.APRIL, 16)));
    }

    @Test
    @DisplayName("an item with no end date never expires")
    void noEndDateNeverExpires() {
        BudgetItem item = mealPlan();
        item.setEndDate(null);

        assertFalse(item.isExpired(generatedDate(2030, Calendar.JANUARY, 1)));
    }

    @Test
    @DisplayName("the occurrence on the end date is generated")
    void occurrenceOnTheEndDateIsGenerated() throws ForecastException {
        BudgetItem item = mealPlan();

        assertEquals("04-15-2027",
                Utility.calendarDateToStringDate(item.getNextDateOfOccurrence(generatedDate(2027, Calendar.MARCH, 15))),
                "April is the last payment, and the end date is the day it falls on");
    }

    @Test
    @DisplayName("nothing is generated after the end date")
    void nothingAfterTheEndDate() throws ForecastException {
        assertNull(mealPlan().getNextDateOfOccurrence(generatedDate(2027, Calendar.APRIL, 15)));
    }

    /**
     * The whole run, the way the forecast engine walks it:  from the first occurrence on or after a date, to the next,
     * while the item has not expired.
     */
    @Test
    @DisplayName("the meal plan runs October through April")
    void theRunEndsOnTheEndDate() throws ForecastException {
        BudgetItem item = mealPlan();
        List<String> payments = new ArrayList<>();

        for (Calendar next = item.getFirstDateOnOrAfter(generatedDate(2026, Calendar.OCTOBER, 1));
             next != null && !item.isExpired(next); next = item.getNextDateOfOccurrence(next)) {
            payments.add(Utility.calendarDateToStringDate(next));
        }

        assertEquals(List.of("10-15-2026", "11-15-2026", "12-15-2026", "01-15-2027",
                "02-15-2027", "03-15-2027", "04-15-2027"), payments);
    }
}
