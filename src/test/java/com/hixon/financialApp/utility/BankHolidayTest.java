package com.hixon.financialApp.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the US bank holiday calendar.
 *
 * <p>This was a hardcoded array of the eleven 2025 dates. It went stale in January 2026 and nothing
 * reported it: by September 2026 every holiday in the year counted as an ordinary business day.
 *
 * <p>That is not cosmetic. {@code businessDaysBeteween} feeds the date-proximity half of the
 * forecast match score at eight points per day, so a missed holiday inside the gap moves the score
 * by eight. A Walmart+ membership charge on 09-08-2026 scored 68 against its occurrence and missed
 * the 70 threshold by two — with Labor Day 2026 counted the gap is three business days, not four,
 * and the score is 76.
 *
 * <p>The 2025 dates below are the exact contents of the list that was replaced, so they check the
 * computation against a set already known to be right.
 */
@DisplayName("Bank Holiday Tests")
class BankHolidayTest {

    @Test
    @DisplayName("Every 2025 date from the list this replaced is still a holiday")
    void testAgainstTheReplacedList() {

        // Verbatim from the old array, which was correct for its one year.
        String[] wasHardcoded = {"01-01-2025", "01-20-2025", "02-17-2025", "05-26-2025", "06-19-2025",
                "07-04-2025", "09-01-2025", "10-13-2025", "11-11-2025", "11-27-2025", "12-25-2025"};

        for (String holiday : wasHardcoded) {
            assertTrue(Utility.isaBankHoliday(holiday), holiday + " was a holiday before and still is");
        }
        assertEquals(11, Utility.bankHolidaysFor(2025).size(), "eleven federal holidays, no more");
    }

    @Test
    @DisplayName("2026 is a holiday year too, which is the whole point")
    void test2026() {

        // Labor Day 2026 is the one that cost the Walmart+ match two points.
        assertTrue(Utility.isaBankHoliday("09-07-2026"), "Labor Day 2026, first Monday of September");

        assertTrue(Utility.isaBankHoliday("01-01-2026"));  // New Year's Day, a Thursday
        assertTrue(Utility.isaBankHoliday("01-19-2026"));  // MLK Day, third Monday
        assertTrue(Utility.isaBankHoliday("02-16-2026"));  // Presidents Day, third Monday
        assertTrue(Utility.isaBankHoliday("05-25-2026"));  // Memorial Day, last Monday
        assertTrue(Utility.isaBankHoliday("06-19-2026"));  // Juneteenth, a Friday
        // Independence Day 2026 falls on a Saturday, so no weekday is affected at all -- see
        // testSaturdayIsNotMovedBack.  Asserting the Friday here would contradict that rule, which
        // is exactly the mistake this line originally made.
        assertFalse(Utility.isaBankHoliday("07-03-2026"), "Reserve Banks are open that Friday");
        assertFalse(Utility.isaBankHoliday("07-06-2026"), "and that Monday");
        assertTrue(Utility.isaBankHoliday("10-12-2026"));  // Columbus Day, second Monday
        assertTrue(Utility.isaBankHoliday("11-11-2026"));  // Veterans Day, a Wednesday
        assertTrue(Utility.isaBankHoliday("11-26-2026"));  // Thanksgiving, fourth Thursday
        assertTrue(Utility.isaBankHoliday("12-25-2026"));  // Christmas Day, a Friday
    }

    @Test
    @DisplayName("A Sunday holiday is observed on the Monday")
    void testSundayObservance() {

        // Independence Day 2027 is a Sunday, so banks close the Monday.
        assertTrue(Utility.isaBankHoliday("07-05-2027"), "observed Monday");
        assertFalse(Utility.isaBankHoliday("07-04-2027"), "the Sunday itself is not a business day anyway");
    }

    @Test
    @DisplayName("A Saturday holiday does not close the Friday")
    void testSaturdayIsNotMovedBack() {

        // Federal *employees* get the Friday; Reserve Banks do not, and "bank holiday" is what this
        // answers.  Christmas 2027 falls on a Saturday.
        assertFalse(Utility.isaBankHoliday("12-24-2027"),
                "Reserve Banks are open the Friday before a Saturday holiday");
    }

    @Test
    @DisplayName("Ordinary days are not holidays, in any year")
    void testOrdinaryDays() {

        assertFalse(Utility.isaBankHoliday("09-08-2026"), "the day the Walmart+ charge posted");
        assertFalse(Utility.isaBankHoliday("09-04-2026"));
        assertFalse(Utility.isaBankHoliday("03-17-2026"));
        assertFalse(Utility.isaBankHoliday("09-01-2026"),
                "Labor Day 2025 was the 1st; the same date in 2026 is an ordinary Tuesday");
    }

    @Test
    @DisplayName("It keeps working past the year anyone thought about")
    void testFutureYearsAreCovered() {

        // The defect was a table that covered one year.  Forecasts already run into 2027, and the
        // register holds transactions from 2020.
        assertTrue(Utility.isaBankHoliday("01-01-2030"));
        assertTrue(Utility.isaBankHoliday("11-28-2030"), "Thanksgiving 2030, fourth Thursday");
        assertEquals(11, Utility.bankHolidaysFor(2030).size());
        assertEquals(11, Utility.bankHolidaysFor(2019).size());
    }

    @Test
    @DisplayName("Malformed input is not a holiday and not an exception")
    void testMalformedInput() {

        // This is called inside a loop that counts business days during an import; a bad string must
        // not end the run.
        assertFalse(Utility.isaBankHoliday((String) null));
        assertFalse(Utility.isaBankHoliday(""));
        assertFalse(Utility.isaBankHoliday("not-a-date"));
        assertFalse(Utility.isaBankHoliday("2026-09-07"), "wrong order; the format is MM-dd-yyyy");
        assertFalse(Utility.isaBankHoliday((Calendar) null));
    }

    @Test
    @DisplayName("The business-day gap that decided the Walmart+ match")
    void testTheGapThatMattered() {

        Calendar sep08 = Calendar.getInstance();
        sep08.clear();
        sep08.set(2026, Calendar.SEPTEMBER, 8);
        Calendar sep02 = Calendar.getInstance();
        sep02.clear();
        sep02.set(2026, Calendar.SEPTEMBER, 2);

        // Thu 3rd, Fri 4th and Tue 8th are business days; Mon 7th is Labor Day and was being counted
        // as one.  Three rather than four is 16 date points rather than 8, which with an exact amount
        // and a matching merchant is 76 rather than 68 -- across the 70 threshold.
        assertEquals(3, Utility.businessDaysBeteween(sep08, sep02),
                "Labor Day 2026 must not count as a business day");
    }
}
