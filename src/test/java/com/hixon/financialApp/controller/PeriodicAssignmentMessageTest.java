package com.hixon.financialApp.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ForecastController#periodicAssignmentMessage}.
 */
@DisplayName("Periodic assignment message Tests")
class PeriodicAssignmentMessageTest {

    private static final String OCCURRENCE = "Forecast Transaction: Planned date = 09-15, Budgeted amount = $-35.00, " +
            "Remaining amount = $0.00";

    @Test
    @DisplayName("The 09-17-2026 Visible charge:  an occurrence already at zero is not said to be deducted from")
    void alreadyZero() {
        String message = ForecastController.periodicAssignmentMessage(-35.00, true, OCCURRENCE);

        assertFalse(message.contains("deducted from"), message);
        assertTrue(message.contains("already had nothing left"), message);
        assertTrue(message.startsWith("$-35.00 assigned to "), message);
    }

    @Test
    @DisplayName("An occurrence with money left is still deducted from")
    void deducted() {
        assertEquals("$-35.00 deducted from " + OCCURRENCE,
                ForecastController.periodicAssignmentMessage(-35.00, false, OCCURRENCE));
    }

    @Test
    @DisplayName("Income is added to")
    void income() {
        assertEquals("$4,064.00 added to " + OCCURRENCE,
                ForecastController.periodicAssignmentMessage(4064.00, false, OCCURRENCE));
    }
}
