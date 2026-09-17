package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ImportLog#displayDate}.
 */
@DisplayName("Import display date Tests")
class ImportDisplayDateTest {

    private static Calendar dateOf(int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.SEPTEMBER, day, 0, 0, 0);
        return calendar;
    }

    private static Transaction transaction(boolean cleared, int postDay, Integer authorizationDay) {
        Register register = mock(Register.class);
        when(register.getId()).thenReturn(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        Transaction transaction = new Transaction(register, dateOf(postDay), "SLIM CHICKENS", -14.65, cleared, 0, "X");
        transaction.setAuthorizationDate(authorizationDay == null ? null : dateOf(authorizationDay));
        return transaction;
    }

    @Test
    @DisplayName("The 09-17-2026 pending charge shows the bank's 09-16 date, not the day it was imported")
    void pendingShowsPostDate() {
        assertEquals("09-16-2026",
                Utility.calendarDateToStringDate(ImportLog.displayDate(transaction(false, 16, 17))));
    }

    @Test
    @DisplayName("A cleared charge shows the day the purchase was made")
    void clearedShowsAuthorizationDate() {
        assertEquals("09-14-2026",
                Utility.calendarDateToStringDate(ImportLog.displayDate(transaction(true, 16, 14))));
    }

    @Test
    @DisplayName("Without an authorization date the post date is shown")
    void noAuthorizationDate() {
        assertEquals("09-16-2026",
                Utility.calendarDateToStringDate(ImportLog.displayDate(transaction(true, 16, null))));
    }
}
