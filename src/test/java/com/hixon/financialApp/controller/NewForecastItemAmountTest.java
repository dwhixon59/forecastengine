package com.hixon.financialApp.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ForecastController#newForecastItemAmount}:  a forecast item created for an unmatched
 * split plans what its budget item plans.
 */
@DisplayName("New forecast item amount Tests")
class NewForecastItemAmountTest {

    @Test
    @DisplayName("The 09-17-2026 Sudafed charge:  OTC Medicine plans its budgeted $-20.00, not $-7.49")
    void budgetItemAmountWins() {
        assertEquals(-20.00, ForecastController.newForecastItemAmount(-20.00, -7.49));
    }

    @Test
    @DisplayName("A refund against an expense item still plans the expense")
    void refundKeepsTheBudgetedExpense() {
        assertEquals(-25.00, ForecastController.newForecastItemAmount(-25.00, 7.47));
    }

    @Test
    @DisplayName("A budget item that plans nothing takes the split's amount")
    void noBudgetedAmount_usesSplit() {
        assertEquals(-189.00, ForecastController.newForecastItemAmount(0.0, -189.00));
        assertEquals(-189.00, ForecastController.newForecastItemAmount(0.001, -189.00));
    }
}
