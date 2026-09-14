package com.hixon.financialApp.controller;

import com.hixon.financialApp.controller.ForecastChangeReasons.ItemState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ForecastChangeReasons Tests")
class ForecastChangeReasonsTest {

    private static Map<String, ItemState> budget(ItemState... items) {
        Map<String, ItemState> map = new LinkedHashMap<>();
        for (int i = 0; i < items.length; i++) {
            map.put("id-" + items[i].displayString(), items[i]);
        }
        return map;
    }

    private static ItemState item(String display, String signature) {
        return new ItemState(display, signature);
    }

    @Test
    @DisplayName("The 09-14-2026 run:  a copied budget item and a recategorization are both named")
    void copiedItemAndRecategorization_areBothNamed() {
        ItemState groceries = item("Groceries (Food and Beverage, $-150 Weekly)", "g1");
        ItemState airfare = item("Airfare (Travel, $-250 On-Demand)", "a1");

        List<String> reasons = ForecastChangeReasons.describe(
                budget(groceries), budget(groceries, airfare), true, false);

        assertEquals(List.of(
                "Budget item added:  Airfare (Travel, $-250 On-Demand)",
                "An imported transaction was recategorized."), reasons);
    }

    @Test
    @DisplayName("A changed and a removed budget item are named")
    void changedAndRemovedItems_areNamed() {
        Map<String, ItemState> before = new LinkedHashMap<>();
        before.put("1", item("Rent", "r1"));
        before.put("2", item("Gym", "g1"));
        Map<String, ItemState> after = new LinkedHashMap<>();
        after.put("1", item("Rent", "r2"));

        List<String> reasons = ForecastChangeReasons.describe(before, after, false, false);

        assertEquals(List.of("Budget item changed:  Rent", "Budget item removed:  Gym"), reasons);
    }

    @Test
    @DisplayName("An unchanged budget gives no budget reasons")
    void unchangedBudget_givesNoReasons() {
        Map<String, ItemState> before = budget(item("Rent", "r1"));
        Map<String, ItemState> after = budget(item("Rent", "r1"));

        assertTrue(ForecastChangeReasons.describe(before, after, false, false).isEmpty());
    }

    @Test
    @DisplayName("A forecast already out of date at the start says so")
    void staleAtStart_isReported() {
        List<String> reasons = ForecastChangeReasons.describe(budget(), budget(), false, true);

        assertEquals(List.of("The forecast was already out of date when this update started."), reasons);
    }

    @Test
    @DisplayName("A snapshot that could not be read does not make every item look added")
    void missingSnapshot_skipsBudgetComparison() {
        Map<String, ItemState> after = budget(item("Rent", "r1"), item("Gym", "g1"));

        assertTrue(ForecastChangeReasons.describe(null, after, false, false).isEmpty());
    }
}
