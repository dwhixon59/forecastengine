package com.hixon.financialApp.model.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the query behind {@link ForecastTransaction#getLiveSameMonthSiblings}.
 */
@DisplayName("Live same-month sibling query Tests")
class LiveSameMonthSiblingQueryTest {

    private static final UUID CALLING_PLAN = UUID.fromString("16fd4451-3df5-42cc-94b4-835623445198");
    private static final UUID SEPT_15 = UUID.fromString("c2de8f2f-4cd2-4d44-9df8-8748f2310b51");

    private static Calendar dateOf(int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, month, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    @Test
    @DisplayName("The 09-17-2026 calling plan:  the 09-15 occurrence looks for live ones across September")
    void searchesTheWholeMonth() {
        String query = ForecastTransaction.liveSameMonthSiblingQuery(CALLING_PLAN, SEPT_15,
                dateOf(Calendar.SEPTEMBER, 15));

        assertTrue(query.contains("ft.plannedDate >= '2026-09-01'"), query);
        assertTrue(query.contains("ft.plannedDate <= '2026-09-30'"), query);
    }

    @Test
    @DisplayName("Only the same forecast item's other occurrences with something left")
    void scoping() {
        String query = ForecastTransaction.liveSameMonthSiblingQuery(CALLING_PLAN, SEPT_15,
                dateOf(Calendar.SEPTEMBER, 15));

        assertTrue(query.contains("ft.ForecastItem_idForecastItem = uuid_to_bin('" + CALLING_PLAN + "')"), query);
        assertTrue(query.contains("ft.idForecastTransaction <> uuid_to_bin('" + SEPT_15 + "')"), query);
        assertTrue(query.contains("ft.remainingAmount <> 0"), query);
    }

    @Test
    @DisplayName("February ends on its own last day")
    void shortMonth() {
        String query = ForecastTransaction.liveSameMonthSiblingQuery(CALLING_PLAN, SEPT_15,
                dateOf(Calendar.FEBRUARY, 15));

        assertTrue(query.contains("ft.plannedDate >= '2026-02-01'"), query);
        assertTrue(query.contains("ft.plannedDate <= '2026-02-28'"), query);
    }

    @Test
    @DisplayName("The planned date passed in is not changed")
    void plannedDateUntouched() {
        Calendar planned = dateOf(Calendar.SEPTEMBER, 15);
        ForecastTransaction.liveSameMonthSiblingQuery(CALLING_PLAN, SEPT_15, planned);
        assertEquals(15, planned.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    @DisplayName("No occurrence, no siblings")
    void nullOccurrence() throws Exception {
        assertTrue(ForecastTransaction.getLiveSameMonthSiblings(null).isEmpty());
    }
}
