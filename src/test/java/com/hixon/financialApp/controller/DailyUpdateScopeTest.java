package com.hixon.financialApp.controller;

import com.hixon.financialApp.controller.ForecastChangeReasons.ItemState;
import com.hixon.financialApp.model.budget.TransactionSplit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for which budget items the daily update's forecast update regenerates.
 */
@DisplayName("Daily update scope Tests")
class DailyUpdateScopeTest {

    private static final UUID RENT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID GYM = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID DOG_FOOD = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
    private static final UUID GROCERIES = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004");

    private static Map<String, ItemState> budget(Object... idsAndStates) {
        Map<String, ItemState> map = new LinkedHashMap<>();
        for (int i = 0; i < idsAndStates.length; i += 2) {
            map.put(idsAndStates[i].toString(), (ItemState) idsAndStates[i + 1]);
        }
        return map;
    }

    private static TransactionSplit split(UUID budgetItem) {
        TransactionSplit split = mock(TransactionSplit.class);
        when(split.getIdBudgetItem()).thenReturn(budgetItem);
        return split;
    }

    @Test
    @DisplayName("Changed, added and removed forecast items are regenerated;  unchanged ones are not")
    void changedItemIds() {
        Map<String, ItemState> before = budget(
                RENT, new ItemState("Rent", "r1"),
                GYM, new ItemState("Gym", "g1"),
                GROCERIES, new ItemState("Groceries", "c1"));
        Map<String, ItemState> after = budget(
                RENT, new ItemState("Rent", "r2"),
                GROCERIES, new ItemState("Groceries", "c1"),
                DOG_FOOD, new ItemState("Dog Food", "d1", true));

        assertEquals(Set.of(RENT, GYM, DOG_FOOD), ForecastChangeReasons.changedItemIds(before, after));
    }

    @Test
    @DisplayName("On-demand items that change are left out, as they are from the reasons")
    void onDemandLeftOut() {
        Map<String, ItemState> before = budget(DOG_FOOD, new ItemState("Dog Food", "d1", false));
        Map<String, ItemState> after = budget(DOG_FOOD, new ItemState("Dog Food", "d2", false),
                GYM, new ItemState("Gym", "g1", false));

        assertEquals(Set.of(), ForecastChangeReasons.changedItemIds(before, after));
    }

    @Test
    @DisplayName("An item that stops generating occurrences is regenerated")
    void stopsGenerating() {
        Map<String, ItemState> before = budget(RENT, new ItemState("Rent", "r1", true));
        Map<String, ItemState> after = budget(RENT, new ItemState("Rent", "r2", false));

        assertEquals(Set.of(RENT), ForecastChangeReasons.changedItemIds(before, after));
    }

    @Test
    @DisplayName("A missing snapshot rules nothing out")
    void missingSnapshot() {
        assertNull(ForecastChangeReasons.changedItemIds(null, budget()));
        assertNull(ForecastChangeReasons.changedItemIds(budget(), null));
    }

    @Test
    @DisplayName("The scope is the changed items plus the recategorized ones")
    void scopeIsTheUnion() {
        assertEquals(Set.of(RENT, GROCERIES),
                ForecastChangeReasons.updateScope(false, Set.of(RENT), true, Set.of(GROCERIES)));
        assertEquals(Set.of(RENT),
                ForecastChangeReasons.updateScope(false, Set.of(RENT), false, null));
    }

    @Test
    @DisplayName("Anything unknown makes it a full update")
    void unknownMeansFull() {
        // Already out of date when the run started:  what made it so is not known.
        assertNull(ForecastChangeReasons.updateScope(true, Set.of(RENT), false, Set.of()));
        // The budget could not be compared.
        assertNull(ForecastChangeReasons.updateScope(false, null, false, Set.of()));
        // A recategorization whose budget items could not be read.
        assertNull(ForecastChangeReasons.updateScope(false, Set.of(RENT), true, null));
    }

    @Test
    @DisplayName("A recategorization contributes the budget items of its splits")
    void splitBudgetItems() {
        Set<UUID> ids = new HashSet<>();

        assertTrue(ImportSummaryController.addBudgetItemIds(ids, List.of(split(GROCERIES), split(DOG_FOOD))));
        assertEquals(Set.of(GROCERIES, DOG_FOOD), ids);
    }

    @Test
    @DisplayName("Splits that cannot be read, or a split without a budget item, are reported as unknown")
    void unreadableSplits() {
        assertFalse(ImportSummaryController.addBudgetItemIds(new HashSet<>(), null));

        List<TransactionSplit> splits = new ArrayList<>();
        splits.add(split(GROCERIES));
        splits.add(split(null));
        assertFalse(ImportSummaryController.addBudgetItemIds(new HashSet<>(), splits));
    }
}
