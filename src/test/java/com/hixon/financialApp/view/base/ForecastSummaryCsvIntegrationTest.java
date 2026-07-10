package com.hixon.financialApp.view.base;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-style test that validates summary-critical metrics against the real long-term forecast CSV.
 */
@DisplayName("Forecast Summary CSV Integration Test")
class ForecastSummaryCsvIntegrationTest {

    private static final Pattern MONTH_HEADER_PATTERN = Pattern.compile(
            "^(January|February|March|April|May|June|July|August|September|October|November|December) - (\\d{4}).*$");
    private static final Pattern DAY_PATTERN = Pattern.compile("^(\\d+)");

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2027, 6, 30);

    @Test
    @DisplayName("Validates 12-month summary metrics against Bill Pay Dave CSV export")
    void validatesSummaryMetricsFromCsv() throws IOException {
        Path csvPath = Path.of("LongTermForecast-BillPayAccount-DaveForecast.csv");
        assertTrue(Files.exists(csvPath), "Expected CSV at project root: " + csvPath.toAbsolutePath());

        ForecastSnapshot snapshot = parseForecastCsv(csvPath);

        assertEquals(1048.0, snapshot.startingBalance, 0.001, "Starting balance should match July 2026 header");
        assertEquals(-1352.0, snapshot.endingBalance, 0.001, "Ending balance should match final June 2027 transaction");
        assertEquals(-2400.0, snapshot.endingBalance - snapshot.startingBalance, 0.001,
                "Net change should be exact ending-starting (fixes the old $1 discrepancy)");

        assertEquals(6354.0, snapshot.highestBalance, 0.001);
        assertEquals(LocalDate.of(2026, 7, 1), snapshot.highestBalanceDate);

        assertEquals(-1561.0, snapshot.lowestBalance, 0.001);
        assertEquals(LocalDate.of(2027, 6, 14), snapshot.lowestBalanceDate);

        assertEquals(-33.0, snapshot.firstNegativeBalance, 0.001);
        assertEquals(LocalDate.of(2026, 10, 9), snapshot.firstNegativeDate);

        assertEquals(12, snapshot.monthlyNetByMonth.size(), "Summary period should contain exactly 12 months");
        assertTrue(snapshot.monthlyNetByMonth.containsKey(YearMonth.of(2026, 10)),
                "Monthly breakdown should include October 2026");
        assertTrue(snapshot.monthlyNetByMonth.getOrDefault(YearMonth.of(2026, 10), 0.0) < 0,
                "October 2026 should show negative monthly net in breakdown");

        // Smoke checks for top-level breakdowns used by summary improvements.
        assertTrue(snapshot.expenseByCategory.containsKey("Household"));
        assertTrue(snapshot.expenseByCategory.containsKey("Spending Money"));
        assertTrue(snapshot.incomeBySource.containsKey("David's net pay 1"));
        assertTrue(snapshot.incomeBySource.containsKey("David's net pay 2"));
    }

    private static ForecastSnapshot parseForecastCsv(Path csvPath) throws IOException {
        List<String> lines = Files.readAllLines(csvPath);

        Integer currentYear = null;
        Integer currentMonth = null;
        Integer currentDay = null;

        Map<YearMonth, Double> monthHeaderBalance = new HashMap<>();
        Map<YearMonth, Double> incomeByMonth = new HashMap<>();
        Map<YearMonth, Double> expenseByMonth = new HashMap<>();
        Map<String, Double> expenseByCategory = new HashMap<>();
        Map<String, Double> incomeBySource = new HashMap<>();

        double highestBalance = Double.NEGATIVE_INFINITY;
        LocalDate highestBalanceDate = null;
        double lowestBalance = Double.POSITIVE_INFINITY;
        LocalDate lowestBalanceDate = null;
        double firstNegativeBalance = 0.0;
        LocalDate firstNegativeDate = null;
        boolean foundNegative = false;

        double endingBalance = 0.0;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher monthHeaderMatcher = MONTH_HEADER_PATTERN.matcher(line);
            if (monthHeaderMatcher.matches()) {
                String monthName = monthHeaderMatcher.group(1);
                currentYear = Integer.parseInt(monthHeaderMatcher.group(2));
                currentMonth = monthNameToInt(monthName);
                currentDay = null;

                String[] monthFields = splitCsv(rawLine);
                if (monthFields.length > 6) {
                    double monthBalance = parseAmount(monthFields[6]);
                    monthHeaderBalance.put(YearMonth.of(currentYear, currentMonth), monthBalance);
                }
                continue;
            }

            if (line.startsWith("Date,Category")) {
                continue;
            }

            if (currentYear == null || currentMonth == null) {
                continue;
            }

            String[] fields = splitCsv(rawLine);
            if (fields.length < 7) {
                continue;
            }

            String dayCell = stripQuotes(fields[0]);
            if (!dayCell.isBlank()) {
                Matcher dayMatcher = DAY_PATTERN.matcher(dayCell);
                if (dayMatcher.find()) {
                    currentDay = Integer.parseInt(dayMatcher.group(1));
                }
            }
            if (currentDay == null) {
                continue;
            }

            LocalDate date = LocalDate.of(currentYear, currentMonth, currentDay);
            if (date.isBefore(PERIOD_START) || date.isAfter(PERIOD_END)) {
                continue;
            }

            String category = stripQuotes(fields[1]).isBlank() ? "Uncategorized expense" : stripQuotes(fields[1]);
            String payee = stripQuotes(fields[2]).isBlank() ? "Unspecified income source" : stripQuotes(fields[2]);
            double credit = parseAmount(fields[4]);
            double debit = parseAmount(fields[5]);
            double balance = parseAmount(fields[6]);

            YearMonth ym = YearMonth.from(date);

            if (credit > 0) {
                incomeByMonth.merge(ym, credit, Double::sum);
                incomeBySource.merge(payee, credit, Double::sum);
            }
            if (debit > 0) {
                expenseByMonth.merge(ym, debit, Double::sum);
                expenseByCategory.merge(category, debit, Double::sum);
            }

            if (balance > highestBalance) {
                highestBalance = balance;
                highestBalanceDate = date;
            }
            if (balance < lowestBalance) {
                lowestBalance = balance;
                lowestBalanceDate = date;
            }
            if (!foundNegative && balance < 0) {
                foundNegative = true;
                firstNegativeBalance = balance;
                firstNegativeDate = date;
            }

            endingBalance = balance;
        }

        YearMonth july2026 = YearMonth.of(2026, 7);
        double startingBalance = monthHeaderBalance.getOrDefault(july2026, 0.0);

        Map<YearMonth, Double> monthlyNetByMonth = new TreeMap<>();
        YearMonth cursor = july2026;
        YearMonth end = YearMonth.of(2027, 6);
        while (!cursor.isAfter(end)) {
            double income = incomeByMonth.getOrDefault(cursor, 0.0);
            double expense = expenseByMonth.getOrDefault(cursor, 0.0);
            monthlyNetByMonth.put(cursor, AbstractForecastView.roundCurrency(income - expense));
            cursor = cursor.plusMonths(1);
        }

        return new ForecastSnapshot(
                startingBalance,
                endingBalance,
                highestBalance,
                highestBalanceDate,
                lowestBalance,
                lowestBalanceDate,
                firstNegativeBalance,
                firstNegativeDate,
                monthlyNetByMonth,
                expenseByCategory,
                incomeBySource
        );
    }

    private static String[] splitCsv(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace("\"", "");
    }

    private static double parseAmount(String amountCell) {
        String cleaned = stripQuotes(amountCell).replace("$", "").replace(",", "");
        if (cleaned.isBlank()) {
            return 0.0;
        }
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }
        return Double.parseDouble(cleaned);
    }

    private static int monthNameToInt(String monthName) {
        for (int month = 1; month <= 12; month++) {
            String name = java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            if (name.equalsIgnoreCase(monthName)) {
                return month;
            }
        }
        throw new IllegalArgumentException("Unknown month: " + monthName);
    }

    private static final class ForecastSnapshot {
        private final double startingBalance;
        private final double endingBalance;
        private final double highestBalance;
        private final LocalDate highestBalanceDate;
        private final double lowestBalance;
        private final LocalDate lowestBalanceDate;
        private final double firstNegativeBalance;
        private final LocalDate firstNegativeDate;
        private final Map<YearMonth, Double> monthlyNetByMonth;
        private final Map<String, Double> expenseByCategory;
        private final Map<String, Double> incomeBySource;

        private ForecastSnapshot(
                double startingBalance,
                double endingBalance,
                double highestBalance,
                LocalDate highestBalanceDate,
                double lowestBalance,
                LocalDate lowestBalanceDate,
                double firstNegativeBalance,
                LocalDate firstNegativeDate,
                Map<YearMonth, Double> monthlyNetByMonth,
                Map<String, Double> expenseByCategory,
                Map<String, Double> incomeBySource
        ) {
            this.startingBalance = AbstractForecastView.roundCurrency(startingBalance);
            this.endingBalance = AbstractForecastView.roundCurrency(endingBalance);
            this.highestBalance = AbstractForecastView.roundCurrency(highestBalance);
            this.highestBalanceDate = highestBalanceDate;
            this.lowestBalance = AbstractForecastView.roundCurrency(lowestBalance);
            this.lowestBalanceDate = lowestBalanceDate;
            this.firstNegativeBalance = AbstractForecastView.roundCurrency(firstNegativeBalance);
            this.firstNegativeDate = firstNegativeDate;
            this.monthlyNetByMonth = monthlyNetByMonth;
            this.expenseByCategory = expenseByCategory;
            this.incomeBySource = incomeBySource;
        }
    }
}



