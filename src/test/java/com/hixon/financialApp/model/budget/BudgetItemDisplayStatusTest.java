package com.hixon.financialApp.model.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Budget item display status tests")
class BudgetItemDisplayStatusTest {

    @Test
    @DisplayName("Expired by end date shows expired")
    void expiredByEndDateShowsExpired() {
        assertEquals("Expired.", BudgetItem.formatScheduleStatus(true, null));
    }

    @Test
    @DisplayName("Active item with planned date shows that date")
    void activeItemWithPlannedDateShowsDate() {
        Calendar plannedDate = new GregorianCalendar(2026, Calendar.DECEMBER, 15);

        assertEquals("12-15-2026", BudgetItem.formatScheduleStatus(false, plannedDate));
    }

    @Test
    @DisplayName("Active item without a current forecast transaction is not marked expired")
    void activeItemWithoutForecastShowsNotInCurrentForecast() {
        assertEquals("Not in current forecast.", BudgetItem.formatScheduleStatus(false, null));
    }
}

