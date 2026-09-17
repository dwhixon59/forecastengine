package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for which forecasts are offered, or updated, after a budget item is copied or moved.
 */
@DisplayName("Budget forecast offer Tests")
class BudgetForecastOfferTest {

    private static final UUID BILL_PAY_DANNI = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID BILL_PAY_DAVE = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

    private static Budget budget(UUID id) {
        Budget budget = mock(Budget.class);
        when(budget.getId()).thenReturn(id);
        return budget;
    }

    @Test
    @DisplayName("The 09-17-2026 copy:  OTC Medicine copied into Bill Pay Dave offers Dave's forecasts")
    void copyIntoAnotherBudget_offersThatBudget() {
        assertEquals(BILL_PAY_DAVE, BudgetController.budgetOfferedForecastsFor(BILL_PAY_DAVE, BILL_PAY_DANNI));
    }

    @Test
    @DisplayName("A copy within the same budget offers that budget")
    void copyWithinBudget_offersThatBudget() {
        assertEquals(BILL_PAY_DANNI, BudgetController.budgetOfferedForecastsFor(BILL_PAY_DANNI, BILL_PAY_DANNI));
    }

    @Test
    @DisplayName("An item that does not say which budget it is in falls back to the one being browsed")
    void noBudgetOnItem_fallsBack() {
        assertEquals(BILL_PAY_DANNI, BudgetController.budgetOfferedForecastsFor(null, BILL_PAY_DANNI));
    }

    @Test
    @DisplayName("A move updates the forecasts of both budgets, the old one first")
    void moveUpdatesBoth() {
        Budget danni = budget(BILL_PAY_DANNI);
        Budget dave = budget(BILL_PAY_DAVE);

        assertEquals(List.of(danni, dave), BudgetController.budgetsToUpdate(danni, dave));
    }

    @Test
    @DisplayName("The same budget on both sides is updated once")
    void sameBudgetOnce() {
        Budget danni = budget(BILL_PAY_DANNI);
        Budget danniAgain = budget(BILL_PAY_DANNI);

        assertEquals(List.of(danni), BudgetController.budgetsToUpdate(danni, danniAgain));
    }

    @Test
    @DisplayName("A missing budget is left out")
    void missingBudgetLeftOut() {
        Budget dave = budget(BILL_PAY_DAVE);

        assertEquals(List.of(dave), BudgetController.budgetsToUpdate(null, dave));
        assertEquals(List.of(dave), BudgetController.budgetsToUpdate(dave, null));
        assertTrue(BudgetController.budgetsToUpdate(null, null).isEmpty());
    }
}
