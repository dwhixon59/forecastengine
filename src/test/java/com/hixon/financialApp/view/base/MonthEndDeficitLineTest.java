package com.hixon.financialApp.view.base;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AbstractForecastView#monthEndDeficitLine}:  a deficit is only called persistent
 * when it lasts to the end of the period.
 */
@DisplayName("Month-end deficit line Tests")
class MonthEndDeficitLineTest {

    private static final List<String> MONTHS = List.of("October 2026", "November 2026", "December 2026");

    @Test
    @DisplayName("The 09-17-2026 Bill Pay Danni render:  one negative month then recovery is not persistent")
    void recoveredDeficit_isNotPersistent() {
        String line = AbstractForecastView.monthEndDeficitLine(MONTHS, List.of(-107.0, 999.0, 2240.0));

        assertEquals("  - The balance is negative at the end of October 2026 and back in the black by the end " +
                "of November 2026.", line);
    }

    @Test
    @DisplayName("A deficit that lasts to the end of the period is persistent")
    void lastingDeficit_isPersistent() {
        String line = AbstractForecastView.monthEndDeficitLine(MONTHS, List.of(12.0, -233.0, -131.0));

        assertEquals("  - Persistent deficit period begins by month-end in November 2026.", line);
    }

    @Test
    @DisplayName("A deficit only in the last month is persistent")
    void deficitInLastMonth_isPersistent() {
        String line = AbstractForecastView.monthEndDeficitLine(MONTHS, List.of(12.0, 5.0, -1.0));

        assertEquals("  - Persistent deficit period begins by month-end in December 2026.", line);
    }

    @Test
    @DisplayName("A zero balance counts as recovered")
    void zeroIsRecovered() {
        String line = AbstractForecastView.monthEndDeficitLine(MONTHS, List.of(-1.0, 0.0, -5.0));

        assertTrue(line.contains("back in the black by the end of November 2026"), line);
    }

    @Test
    @DisplayName("No negative month-end, no line")
    void noDeficit_noLine() {
        assertNull(AbstractForecastView.monthEndDeficitLine(MONTHS, List.of(1.0, 2.0, 3.0)));
        assertNull(AbstractForecastView.monthEndDeficitLine(List.of(), List.of()));
    }
}
