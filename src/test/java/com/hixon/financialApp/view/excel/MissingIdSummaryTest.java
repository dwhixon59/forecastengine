package com.hixon.financialApp.view.excel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ExcelForecastView#missingIdSummary}:  one warning for every stale row, not one each.
 */
@DisplayName("Missing id summary Tests")
class MissingIdSummaryTest {

    @Test
    @DisplayName("Consecutive rows are given as ranges")
    void ranges() {
        String summary = ExcelForecastView.missingIdSummary(List.of(15, 16, 17, 20, 22, 23), 284);

        assertEquals("WARNING: 6 of 284 spreadsheet rows refer to forecast transactions that are no longer in the " +
                "database, and were skipped (rows 15-17, 20, 22-23).", summary);
    }

    @Test
    @DisplayName("A single row is named as a row")
    void singleRow() {
        String summary = ExcelForecastView.missingIdSummary(List.of(40), 10);

        assertTrue(summary.contains("1 of 10"), summary);
        assertTrue(summary.endsWith("(row 40)."), summary);
    }
}
