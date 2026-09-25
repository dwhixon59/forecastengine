package com.hixon.financialApp.model.forecast;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ForecastTransaction#getApplicableZeroOccurrence(Forecast, UUID, Calendar)}, which finds the
 * occurrence a charge's own period belongs to when every occurrence with money left is in a later period.
 *
 * <p>On 09-25-2026 a $25 Visible charge for Justin's calling plan was offered Danni's 09-15 occurrence:  the lookup
 * chose by category and payee (Utilities / Smart Phones), which all the family's calling plans share.</p>
 */
class ApplicableZeroOccurrenceTest {

    private static final UUID JUSTINS_FORECAST_ITEM = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void theLastOccurrenceIsLookedUpByTheForecastItemItself() {
        String query = ForecastTransaction.lastOccurrenceOnOrBeforeQuery(JUSTINS_FORECAST_ITEM, day(2026, 9, 24));

        assertTrue(query.contains("ft.ForecastItem_idForecastItem = uuid_to_bin('" + JUSTINS_FORECAST_ITEM + "')"),
                query);
        assertTrue(query.contains("ft.plannedDate <= "), query);
        assertTrue(query.contains("order by ft.plannedDate desc"), query);
        assertTrue(query.contains("limit 1"), query);
    }

    @Test
    void theLastOccurrenceIsNotLookedUpByCategoryAndPayee() {
        // Category and payee are shared by several budget items (and by other registers' forecasts), so a
        // filter on them can return another item's occurrence.
        String query = ForecastTransaction.lastOccurrenceOnOrBeforeQuery(JUSTINS_FORECAST_ITEM, day(2026, 9, 24));

        assertFalse(query.contains("fi.category ="), query);
        assertFalse(query.contains("fi.payee ="), query);
    }

    @Test
    void theBudgetItemIsLookedUpInTheGivenForecast() throws Exception {
        Forecast citiForecast = mock(Forecast.class);
        UUID justinsBudgetItem = UUID.randomUUID();

        try (MockedStatic<ForecastItem> forecastItems = mockStatic(ForecastItem.class)) {
            forecastItems.when(() -> ForecastItem.getByBudgetItemId(any(Forecast.class), any(UUID.class)))
                    .thenReturn(null);

            ForecastTransaction.getApplicableZeroOccurrence(citiForecast, justinsBudgetItem, day(2026, 9, 24));

            forecastItems.verify(() -> ForecastItem.getByBudgetItemId(eq(citiForecast), eq(justinsBudgetItem)));
            forecastItems.verify(() -> ForecastItem.getByBudgetItemId(any(UUID.class)), never());
        }
    }

    @Test
    void aBudgetItemWithNoForecastItemInTheForecastHasNoOccurrence() throws Exception {
        try (MockedStatic<ForecastItem> forecastItems = mockStatic(ForecastItem.class)) {
            forecastItems.when(() -> ForecastItem.getByBudgetItemId(any(Forecast.class), any(UUID.class)))
                    .thenReturn(null);

            assertNull(ForecastTransaction.getApplicableZeroOccurrence(mock(Forecast.class), UUID.randomUUID(),
                    day(2026, 9, 24)));
        }
    }

    private static Calendar day(int year, int month, int dayOfMonth) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, dayOfMonth);
        return c;
    }
}
