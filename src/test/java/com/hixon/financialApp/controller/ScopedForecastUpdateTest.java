package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for updating a forecast for the budget items that changed only.
 *
 * <p>On 09-17-2026 moving the start date of Dave's gas and food for work regenerated every item in Bill Pay
 * Dave's forecast while it was open in Excel, and 274 of the spreadsheet's 284 rows could not be read back.
 */
@DisplayName("Scoped forecast update Tests")
class ScopedForecastUpdateTest {

    private static final UUID FORECAST = UUID.fromString("bff7a19e-9077-418f-a6f7-6fe384a7205b");
    private static final UUID GAS_AND_FOOD = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTC_MEDICINE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

    private static Calendar firstOfOctober() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.OCTOBER, 1, 0, 0, 0);
        return calendar;
    }

    private static BudgetItem item(UUID id) {
        BudgetItem item = mock(BudgetItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    @Test
    @DisplayName("A full update deletes every item's regenerable occurrences")
    void fullUpdate() {
        String query = ForecastController.regenerationDeleteQuery(FORECAST, firstOfOctober(), null);

        assertTrue(query.contains("Forecast_idForecast = uuid_to_bin('" + FORECAST + "')"), query);
        assertFalse(query.contains("BudgetItem_idBudgetItem"), query);
        assertTrue(query.contains("plannedDate >= '2026-10-01'"), query);
        assertTrue(query.contains("not overridden"), query);
        assertTrue(query.contains("forecast_transaction_split"), query);
    }

    @Test
    @DisplayName("A scoped update deletes only the changed items' occurrences")
    void scopedUpdate() {
        Set<UUID> scope = new LinkedHashSet<>(List.of(GAS_AND_FOOD, OTC_MEDICINE));
        String query = ForecastController.regenerationDeleteQuery(FORECAST, firstOfOctober(), scope);

        assertTrue(query.contains("Forecast_idForecast = uuid_to_bin('" + FORECAST + "') and BudgetItem_idBudgetItem in " +
                "(uuid_to_bin('" + GAS_AND_FOOD + "'), uuid_to_bin('" + OTC_MEDICINE + "'))"), query);
        assertTrue(query.contains("not overridden"), query);
        assertTrue(query.contains("plannedDate >= '2026-10-01'"), query);
    }

    @Test
    @DisplayName("An empty scope deletes nothing rather than everything")
    void emptyScope() {
        String query = ForecastController.regenerationDeleteQuery(FORECAST, firstOfOctober(), Set.of());

        assertTrue(query.contains("BudgetItem_idBudgetItem in (null)"), query);
    }

    @Test
    @DisplayName("The scope is the saved items that changed")
    void scopeFromItems() {
        assertEquals(Set.of(GAS_AND_FOOD), BudgetController.onlyTheseBudgetItems(item(GAS_AND_FOOD)));
        assertEquals(Set.of(GAS_AND_FOOD, OTC_MEDICINE),
                BudgetController.onlyTheseBudgetItems(item(GAS_AND_FOOD), null, item(OTC_MEDICINE)));
    }

    @Test
    @DisplayName("With no saved item known, the update is a full one")
    void noItemsMeansFullUpdate() {
        assertNull(BudgetController.onlyTheseBudgetItems());
        assertNull(BudgetController.onlyTheseBudgetItems((BudgetItem) null));
        assertNull(BudgetController.onlyTheseBudgetItems(item(null)));
    }
}
