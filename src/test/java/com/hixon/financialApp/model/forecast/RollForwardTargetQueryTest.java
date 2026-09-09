package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the query that decides which occurrences a roll-forward may move an overage into.
 *
 * <p>The overage prompt offers "roll" as one of four answers, and the occurrence it names is the one
 * the roll will deduct from.  It used to be sourced from
 * {@code getNonZeroForecastTransactionsForBudgetItem}, which filters on {@code remainingAmount <> 0}
 * and orders from the start of the forecast.  That is the right list for matching a split to an
 * occurrence, and the wrong one for rolling out of an occurrence:  when the overdrawn occurrence is
 * only <em>partly</em> overdrawn -- the split is bigger than what is left, but something is left --
 * it satisfies {@code <> 0} itself and is the first row returned.
 *
 * <p>On 09-08-2026 a $-4.99 charge at 7-Eleven was applied to a Danni's Work Expenses occurrence
 * planned 09-04 holding $-3.18.  The prompt printed the same occurrence twice:
 *
 * <pre>
 * Overdrawn:      ... Planned Date = 09-04-2026 ... Remaining Amount = $-3.18
 * Roll would use: ... Planned Date = 09-04-2026 ... Remaining Amount = $-3.18
 * </pre>
 *
 * <p>Answering "r" there would have deducted the overage from the very occurrence it was rolling out
 * of, counting the split against that one period twice.
 */
@DisplayName("Roll-forward target query")
class RollForwardTargetQueryTest {

    private static final UUID BUDGET_ITEM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FORECAST = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OVERDRAWN = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** The 09-04-2026 Danni's Work Expenses occurrence from the run that exposed this. */
    private static ForecastTransaction overdrawnOccurrence() {
        Calendar plannedDate = Calendar.getInstance();
        plannedDate.set(2026, Calendar.SEPTEMBER, 4, 0, 0, 0);
        plannedDate.set(Calendar.MILLISECOND, 0);

        ForecastTransaction overdrawn = mock(ForecastTransaction.class);
        when(overdrawn.getPlannedDate()).thenReturn(plannedDate);
        when(overdrawn.getId()).thenReturn(OVERDRAWN);
        return overdrawn;
    }

    @Test
    @DisplayName("The occurrence being overdrawn is excluded by id")
    void theOverdrawnOccurrenceIsExcludedById() {

        String query = ForecastTransaction.rollForwardTargetQuery(BUDGET_ITEM, FORECAST, overdrawnOccurrence());

        // Without this clause a partly-overdrawn occurrence is returned as its own roll target.
        assertTrue(query.contains("ft.idForecastTransaction <> uuid_to_bin('" + OVERDRAWN + "')"),
                "the overdrawn occurrence must be excluded from its own roll-forward targets");
    }

    @Test
    @DisplayName("Occurrences before the one being overdrawn are excluded")
    void earlierOccurrencesAreExcluded() {

        ForecastTransaction overdrawn = overdrawnOccurrence();
        String query = ForecastTransaction.rollForwardTargetQuery(BUDGET_ITEM, FORECAST, overdrawn);

        // Excluding by id alone is not enough:  the ordering starts at the beginning of the forecast,
        // so the next row would be an earlier occurrence, and rolling into it moves the overage
        // backwards into a period that has already been reported and closed.
        assertTrue(query.contains("ft.plannedDate >= " +
                        Utility.calendarDateToSqlDateString(overdrawn.getPlannedDate())),
                "a roll-forward must not move an overage into an earlier period");
    }

    @Test
    @DisplayName("Only occurrences with something left are offered")
    void onlyOccurrencesWithSomethingLeftAreOffered() {

        String query = ForecastTransaction.rollForwardTargetQuery(BUDGET_ITEM, FORECAST, overdrawnOccurrence());

        // A fully spent occurrence has nothing to absorb the overage.
        assertTrue(query.contains("ft.remainingAmount <> 0"),
                "an exhausted occurrence cannot absorb a roll-forward");
    }

    @Test
    @DisplayName("The query stays scoped to one budget item in one forecast, earliest first")
    void theQueryStaysScopedAndOrdered() {

        String query = ForecastTransaction.rollForwardTargetQuery(BUDGET_ITEM, FORECAST, overdrawnOccurrence());

        assertTrue(query.contains("fi.BudgetItem_idBudgetItem = uuid_to_bin('" + BUDGET_ITEM + "')"),
                "an overage rolls forward within its own budget item");
        assertTrue(query.contains("fi.Forecast_idForecast = uuid_to_bin('" + FORECAST + "')"),
                "an overage rolls forward within its own forecast");
        assertTrue(query.contains("order by ft.plannedDate asc"),
                "the nearest later occurrence should be filled first");
    }
}
