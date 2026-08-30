package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.budget.MemoBudgetItemHistory;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the transfer memo's effect on the ranked budget item list.
 *
 * <p>The rule the whole feature obeys:  <b>the memo prefers a budget item, it never selects one and
 * never rules one out.</b>  It is right about five times in six, which orders a list well and
 * chooses silently badly -- a wrong split is worse than a question, because nobody looks at it
 * again.  These tests hold the ranking to that:  the memo moves an item up, and every path that
 * assigns without asking is left exactly as it was.
 */
@DisplayName("Transaction Splits Memo Ranking Tests")
public class TransactionSplitsControllerMemoRankingTest {

    private static final UUID ROOM_RENTAL = UUID.randomUUID();
    private static final UUID GROCERIES = UUID.randomUUID();
    private static final UUID MISCELLANEOUS = UUID.randomUUID();


    /*
     * Test doubles:
     */
    private static BudgetItem budgetItem(UUID id, String payee, double amount) {
        BudgetItem item = mock(BudgetItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getPayee()).thenReturn(payee);
        when(item.getAmount()).thenReturn(amount);
        // No period and no start date:  an on-demand item, which is what 231 of 2026's 334
        // transfers were assigned to.
        when(item.getPeriod()).thenReturn(null);
        when(item.getStartDate()).thenReturn(null);
        when(item.getHowImportant()).thenReturn(null);
        return item;
    }

    private static BudgetItemMerchant association(BudgetItem item, double perTransactionAmount) {
        // Read the id before opening a stubbing:  calling a mock inside when(...) is an
        // unfinished stubbing, not an argument.
        UUID idBudgetItem = item.getId();
        BudgetItemMerchant association = mock(BudgetItemMerchant.class);
        when(association.getIdBudgetItem()).thenReturn(idBudgetItem);
        when(association.getBudgetItem()).thenReturn(item);
        when(association.getAmount()).thenReturn(perTransactionAmount);
        when(association.getPercentage()).thenReturn(0);
        return association;
    }

    private static Transaction transfer(double amount) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getAmount()).thenReturn(amount);
        when(transaction.getDate()).thenReturn(Calendar.getInstance());
        return transaction;
    }

    private static MemoBudgetItemHistory.Suggestion suggestion(BudgetItem item, int priors) {
        return new MemoBudgetItemHistory.Suggestion(item, "RENT", priors);
    }

    private static List<BudgetItemMerchant> listOf(BudgetItemMerchant... associations) {
        return new ArrayList<>(Arrays.asList(associations));
    }


    /*
     * The bonus itself:
     */
    @Test
    @DisplayName("The memo's item gets the bonus and every other item gets nothing")
    void testBonusGoesOnlyToTheMemosItem() {

        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental", 750.00);
        BudgetItemMerchant rent = association(roomRental, 0);
        BudgetItemMerchant groceries = association(budgetItem(GROCERIES, "Groceries", 200.00), 0);

        MemoBudgetItemHistory.Suggestion memo = suggestion(roomRental, 42);

        assertEquals(TransactionSplitsController.MEMO_BONUS,
                TransactionSplitsController.memoBonus(rent, memo));
        assertEquals(0.0, TransactionSplitsController.memoBonus(groceries, memo));
    }

    @Test
    @DisplayName("A memo seen once is worth half as much as one seen forty times")
    void testSinglePriorIsWorthLess() {

        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental", 750.00);
        BudgetItemMerchant rent = association(roomRental, 0);

        assertEquals(TransactionSplitsController.MEMO_BONUS_SINGLE_PRIOR,
                TransactionSplitsController.memoBonus(rent, suggestion(roomRental, 1)));
        assertEquals(TransactionSplitsController.MEMO_BONUS,
                TransactionSplitsController.memoBonus(rent, suggestion(roomRental, 2)));
    }

    @Test
    @DisplayName("The bonus lands after the clamp, so an item already near the ceiling still gets it")
    void testBonusIsAppliedAfterTheClamp() {

        // calculateRelevancyScores clamps to 0-100.  Adding the bonus before the clamp would
        // swallow it for exactly the items that are already plausible, which is the whole point.
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental", 750.00);
        List<BudgetItemMerchant> items = listOf(association(roomRental, 0));

        List<Double> scores = TransactionSplitsController.calculateRelevancyScores(items, transfer(-750.00));
        double clamped = scores.get(0);
        assertTrue(clamped <= 100.0, "the base score must still be clamped: " + clamped);

        TransactionSplitsController.applyMemoBonus(items, scores, suggestion(roomRental, 42));

        assertEquals(clamped + TransactionSplitsController.MEMO_BONUS, scores.get(0), 0.0001);
        assertTrue(scores.get(0) > 100.0, "the bonus must survive the clamp: " + scores.get(0));
    }


    /*
     * The ordering the user actually sees:
     */
    @Test
    @DisplayName("The memo's item sorts to the top of the list")
    void testMemoItemSortsFirst() {

        // The case the design was written for:  a $150 transfer that the $200 grocery budget leads
        // on amount, where the word RENT is the only thing that says otherwise.  Room rental is
        // on-demand, as 231 of 2026's 334 transfers were, so it has no amount to compete with.
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental", 0.00);
        BudgetItem groceries = budgetItem(GROCERIES, "Groceries", 200.00);
        Transaction transaction = transfer(-150.00);

        List<BudgetItemMerchant> withoutMemo = listOf(association(groceries, 0), association(roomRental, 0));
        List<Double> baseScores = TransactionSplitsController.calculateRelevancyScores(withoutMemo, transaction);
        TransactionSplitsController.sortByRelevancyScore(withoutMemo, baseScores);
        assertEquals(GROCERIES, withoutMemo.get(0).getIdBudgetItem(), "without the memo, Groceries leads on amount");

        List<BudgetItemMerchant> items = listOf(association(groceries, 0), association(roomRental, 0));
        List<Double> scores = TransactionSplitsController.calculateRelevancyScores(items, transaction);
        TransactionSplitsController.applyMemoBonus(items, scores, suggestion(roomRental, 42));
        TransactionSplitsController.sortByRelevancyScore(items, scores);

        assertEquals(ROOM_RENTAL, items.get(0).getIdBudgetItem());
        assertEquals(GROCERIES, items.get(1).getIdBudgetItem());
    }

    @Test
    @DisplayName("The memo cannot lift an item the transaction amount rules out")
    void testMemoDoesNotOverturnAnExactAmountMatch() {

        // 30 points is calibrated to reorder plausible items, not to overrule the evidence.  A $150
        // transfer against an exact $150 budget item stays first however emphatic the memo:  the
        // memo prefers a budget item, it never selects one.
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental", 750.00);
        BudgetItem groceries = budgetItem(GROCERIES, "Groceries", 150.00);
        List<BudgetItemMerchant> items = listOf(association(groceries, 0), association(roomRental, 0));

        List<Double> scores = TransactionSplitsController.calculateRelevancyScores(items, transfer(-150.00));
        TransactionSplitsController.applyMemoBonus(items, scores, suggestion(roomRental, 42));
        TransactionSplitsController.sortByRelevancyScore(items, scores);

        assertEquals(GROCERIES, items.get(0).getIdBudgetItem());
    }

    @Test
    @DisplayName("A single-prior memo does not displace an item that matches on amount")
    void testSinglePriorDoesNotDisplaceAStrongAmountMatch() {

        // One keystroke of evidence against an exact amount match.  15 points is deliberately not
        // enough to overturn it; 30 would be.
        BudgetItem groceries = budgetItem(GROCERIES, "Groceries", 150.00);
        BudgetItem miscellaneous = budgetItem(MISCELLANEOUS, "Other", 0.00);
        List<BudgetItemMerchant> items = listOf(association(groceries, 0), association(miscellaneous, 0));

        List<Double> scores = TransactionSplitsController.calculateRelevancyScores(items, transfer(-150.00));
        TransactionSplitsController.applyMemoBonus(items, scores, suggestion(miscellaneous, 1));
        TransactionSplitsController.sortByRelevancyScore(items, scores);

        assertEquals(GROCERIES, items.get(0).getIdBudgetItem(),
                "an exact amount match should survive a memo typed once");
    }

    @Test
    @DisplayName("With no memo the scores are identical to what they were before this feature")
    void testNoMemoLeavesScoresUntouched() {

        // Just over half of transfers carry no memo, and coverage is falling.  This is the
        // regression guard for all of them.
        List<BudgetItemMerchant> items = listOf(
                association(budgetItem(GROCERIES, "Groceries", 150.00), 0),
                association(budgetItem(ROOM_RENTAL, "Room rental", 750.00), 0),
                association(budgetItem(MISCELLANEOUS, "Other", 0.00), 0));
        Transaction transaction = transfer(-150.00);

        List<Double> before = TransactionSplitsController.calculateRelevancyScores(items, transaction);
        List<Double> after = TransactionSplitsController.calculateRelevancyScores(items, transaction);
        TransactionSplitsController.applyMemoBonus(items, after, null);

        assertEquals(before, after);
    }

    @Test
    @DisplayName("A suggestion for an item that is not in the list changes nothing about the list")
    void testSuggestionForAnAbsentItemDoesNotDisturbTheScores() {

        BudgetItem groceries = budgetItem(GROCERIES, "Groceries", 150.00);
        List<BudgetItemMerchant> items = listOf(association(groceries, 0));
        Transaction transaction = transfer(-150.00);

        List<Double> before = TransactionSplitsController.calculateRelevancyScores(items, transaction);
        List<Double> after = TransactionSplitsController.calculateRelevancyScores(items, transaction);
        TransactionSplitsController.applyMemoBonus(items, after,
                suggestion(budgetItem(ROOM_RENTAL, "Room rental", 750.00), 42));

        assertEquals(before, after);
    }


    /*
     * The suggested item that the merchant has never been associated with:
     */
    @Test
    @DisplayName("An item the merchant has never been used with is appended to the list")
    void testAbsentSuggestionIsAppended() {

        // budgetItemsForMerchant holds only items already associated with this merchant, so the
        // memo's item is frequently not in it.  Dropping it would throw away the case that turns a
        // five-prompt interaction into one keystroke.
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental", 750.00);
        List<BudgetItemMerchant> items = listOf(association(budgetItem(GROCERIES, "Groceries", 150.00), 0));

        BudgetItemMerchant appended = TransactionSplitsController.appendMemoSuggestedItem(
                items, mock(Merchant.class), suggestion(roomRental, 42));

        assertNotNull(appended);
        assertEquals(2, items.size());
        assertSame(appended, items.get(1));
        assertEquals(ROOM_RENTAL, appended.getIdBudgetItem());
        assertSame(roomRental, appended.getBudgetItem());
    }

    @Test
    @DisplayName("An item already in the list is not appended twice")
    void testPresentSuggestionIsNotAppended() {

        // This also keeps the single-item askAlways shortcut intact:  a merchant with one budget
        // item that the memo agrees with still has a list of one.
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental", 750.00);
        List<BudgetItemMerchant> items = listOf(association(roomRental, 0));

        assertNull(TransactionSplitsController.appendMemoSuggestedItem(
                items, mock(Merchant.class), suggestion(roomRental, 42)));
        assertEquals(1, items.size());
    }

    @Test
    @DisplayName("With no memo nothing is appended")
    void testNoSuggestionAppendsNothing() {
        List<BudgetItemMerchant> items = listOf(association(budgetItem(GROCERIES, "Groceries", 150.00), 0));
        assertNull(TransactionSplitsController.appendMemoSuggestedItem(items, mock(Merchant.class), null));
        assertEquals(1, items.size());
    }

    @Test
    @DisplayName("An appended row can never trigger the exact per-transaction amount shortcut")
    void testAppendedRowIsNeverAutoAssigned() {

        // The shortcut reads a decision the user recorded in advance.  A memo is a guess, and has
        // no business in it -- so the row the memo adds carries no configured amount and cannot be
        // an auto-assign candidate even though it is in the list.
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental", 750.00);
        List<BudgetItemMerchant> items = listOf(association(budgetItem(GROCERIES, "Groceries", 150.00), 0));

        BudgetItemMerchant appended = TransactionSplitsController.appendMemoSuggestedItem(
                items, mock(Merchant.class), suggestion(roomRental, 42));

        assertEquals(0.0, appended.getAmount());
        assertEquals(0, appended.getPercentage());
        assertFalse(TransactionSplitsController.hasExactPerTransactionAmount(appended, -750.00));
        assertFalse(TransactionSplitsController.hasExactPerTransactionAmount(appended, 0.0));
    }


    /*
     * The label that makes a one-in-six-wrong suggestion safe to show first:
     */
    @Test
    @DisplayName("The memo's item is labelled with the evidence, and nothing else is labelled")
    void testAnnotation() {

        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental", 750.00);
        BudgetItemMerchant rent = association(roomRental, 0);
        BudgetItemMerchant groceries = association(budgetItem(GROCERIES, "Groceries", 150.00), 0);
        MemoBudgetItemHistory.Suggestion memo = suggestion(roomRental, 42);

        String annotation = BudgetController.memoAnnotation(rent, memo, false);
        assertTrue(annotation.contains("memo \"RENT\""), annotation);
        assertTrue(annotation.contains("42 priors"), annotation);
        assertFalse(annotation.contains("not yet assigned"), annotation);

        assertEquals("", BudgetController.memoAnnotation(groceries, memo, false));
        assertEquals("", BudgetController.memoAnnotation(rent, null, false));
    }

    @Test
    @DisplayName("A row the memo added says so, since selecting it also creates the association")
    void testAnnotationForAnUnassociatedItem() {

        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental", 750.00);
        String annotation = BudgetController.memoAnnotation(
                association(roomRental, 0), suggestion(roomRental, 42), true);

        assertTrue(annotation.contains("not yet assigned to this merchant"), annotation);
    }
}
