package com.hixon.financialApp.model.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the shape of the SQL {@link BudgetItem} generates.
 *
 * <p>These queries are string-concatenated rather than prepared, so a column added to the shared
 * column list has to be added by hand to every values clause that uses it.  Commit {@code 0ba6770}
 * added {@code minimumBalance} to the column list, supplied it in {@link BudgetItem#getInsertQuery()}
 * and in both update clauses -- and missed the values clause of
 * {@link BudgetItem#getInsertOnDuplicateUpdateQuery()}.  That left a query with sixteen columns and
 * fifteen values, which MySQL rejects with "Column count doesn't match value count at row 1".
 *
 * <p>It stayed hidden for about eighteen months because almost nothing saves a budget item through
 * the insert-on-duplicate path.  The one thing that does is answering <em>a - adjust</em> when an
 * imported split exceeds a budget item's remaining amount, which reaches
 * {@code ForecastController.deductSplitAmount}; the import then aborted with a stack trace.
 *
 * <p>So these tests count, rather than checking for one column by name:  the next column added to
 * the list is covered without anybody remembering to come back here.
 */
@DisplayName("Budget Item Insert Query Tests")
public class BudgetItemInsertQueryTest {

    /**
     * A budget item with every persisted field set, so that no value is rendered as the empty string
     * and miscounted.  Modelled on the real row the failing import was saving.
     */
    private static BudgetItem populatedBudgetItem() {
        BudgetItem item = new BudgetItem();
        item.setId(UUID.randomUUID());
        item.setIdBudget(UUID.randomUUID());
        item.setCategory("Household");
        item.setPayee("Room rental and utilities");
        item.setMemo("Christine");
        item.setPeriod(Item.PeriodType.MONTHLY);
        item.setPeriodDays(0);
        item.setAmount(1245.0);
        item.setRunningBalance(0.0);
        item.setMinimumBalance(0.0);
        item.setStartDate(startOf(2026, Calendar.JANUARY, 24));
        item.setNumberOfPayments(0);
        item.setEndDate(null);
        item.setItemType(Item.ItemType.INCOME);
        item.setHowImportant(Item.HowImportant.VARIABLE_NONESSENTIAL);
        item.setHowOccurs(Item.HowOccurs.COLLECTION);
        item.setHowPaid(Item.HowPaid.CHECK);
        return item;
    }

    private static Calendar startOf(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }


    /*
     * Counting the two halves of an INSERT:
     */
    /**
     * The number of comma-separated items in the parenthesised list that starts at {@code from}.
     *
     * <p>Counts only commas at the top level of that list, so {@code uuid_to_bin('...')} counts as
     * one value, and ignores commas inside quoted strings so that a payee containing one does not
     * inflate the count.
     *
     * @param sql  the query to read
     * @param from the index of the opening parenthesis
     * @return how many items the list holds
     */
    private static int countListItems(String sql, int from) {

        assertEquals('(', sql.charAt(from), "expected a parenthesised list at index " + from);

        int items = 1;
        int depth = 0;
        char quote = 0;

        for (int i = from; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }

            switch (c) {
                case '\'', '"' -> quote = c;
                case '(' -> depth++;
                case ')' -> {
                    depth--;
                    if (depth == 0) {
                        return items;
                    }
                }
                case ',' -> {
                    if (depth == 1) {
                        items++;
                    }
                }
                default -> { }
            }
        }

        return fail("unterminated list in: " + sql);
    }

    /** The column list and the values list of an INSERT, as counts. */
    private static int[] columnsAndValues(String sql) {
        int columnsAt = sql.indexOf('(');
        int valuesAt = sql.indexOf("values (") + "values ".length();
        assertTrue(columnsAt >= 0 && valuesAt > "values ".length(), "not an INSERT: " + sql);
        return new int[] { countListItems(sql, columnsAt), countListItems(sql, valuesAt) };
    }


    /*
     * Tests:
     */
    @Test
    @DisplayName("The plain insert supplies one value per column")
    void testInsertQueryIsBalanced() throws Exception {
        int[] counts = columnsAndValues(populatedBudgetItem().getInsertQuery());
        assertEquals(counts[0], counts[1],
                "insert has " + counts[0] + " columns but " + counts[1] + " values");
    }

    @Test
    @DisplayName("The insert-on-duplicate-update supplies one value per column")
    void testInsertOnDuplicateUpdateQueryIsBalanced() throws Exception {

        // The regression.  Before the fix this was 16 columns against 15 values, and every import
        // that adjusted a budget item's amount died on it.
        int[] counts = columnsAndValues(populatedBudgetItem().getInsertOnDuplicateUpdateQuery());
        assertEquals(counts[0], counts[1],
                "insert-on-duplicate has " + counts[0] + " columns but " + counts[1] + " values");
    }

    @Test
    @DisplayName("Both inserts agree with each other, since they share one column list")
    void testBothInsertsAgree() throws Exception {

        // The invariant that makes the next added column safe:  the two queries are built from the
        // same column list, so their values clauses have to be the same length as each other.
        BudgetItem item = populatedBudgetItem();
        assertArrayEquals(columnsAndValues(item.getInsertQuery()),
                columnsAndValues(item.getInsertOnDuplicateUpdateQuery()));
    }

    @Test
    @DisplayName("minimumBalance is written, not just named in the update clause")
    void testMinimumBalanceIsSupplied() throws Exception {

        // Named explicitly because it is the column that was missing, and because a balanced count
        // alone would not notice a value being dropped and a different one duplicated.
        BudgetItem item = populatedBudgetItem();
        item.setRunningBalance(11.0);
        item.setMinimumBalance(22.0);

        String sql = item.getInsertOnDuplicateUpdateQuery();
        String values = sql.substring(sql.indexOf("values ("), sql.indexOf(") on duplicate key update"));

        assertTrue(values.contains("11.0, 22.0"),
                "runningBalance then minimumBalance should both appear in the values clause: " + values);
        assertTrue(sql.contains("minimumBalance = 22.0"), sql);
    }

    @Test
    @DisplayName("The counter is not fooled by commas inside values")
    void testCounterIgnoresCommasInsideValues() {

        // Guarding the test's own instrument:  uuid_to_bin('..') is one value, and a payee with a
        // comma in it is one value.
        assertEquals(3, countListItems("(a, b, c)", 0));
        assertEquals(2, countListItems("(uuid_to_bin('x'), \"Smith, John\")", 0));
        assertEquals(1, countListItems("(uuid_to_bin('x'))", 0));
    }
}
