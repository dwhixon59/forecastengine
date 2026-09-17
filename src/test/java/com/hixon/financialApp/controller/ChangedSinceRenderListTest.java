package com.hixon.financialApp.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ForecastTransactionController#firstFewChanged}.
 */
@DisplayName("Changed since render list Tests")
class ChangedSinceRenderListTest {

    private static List<String> lines(int count) {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            lines.add("occurrence " + i);
        }
        return lines;
    }

    @Test
    @DisplayName("The 230 occurrences of 09-17-2026 are cut to five and a count")
    void longListIsCut() {
        List<String> shown = ForecastTransactionController.firstFewChanged(lines(230), 5);

        assertEquals(List.of("occurrence 1", "occurrence 2", "occurrence 3", "occurrence 4", "occurrence 5",
                "... and 225 more."), shown);
    }

    @Test
    @DisplayName("A short list is shown whole")
    void shortListIsWhole() {
        assertEquals(lines(5), ForecastTransactionController.firstFewChanged(lines(5), 5));
        assertEquals(lines(2), ForecastTransactionController.firstFewChanged(lines(2), 5));
    }
}
