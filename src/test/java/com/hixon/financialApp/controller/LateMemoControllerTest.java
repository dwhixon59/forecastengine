package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.MemoBudgetItemHistory;
import com.hixon.financialApp.model.budget.TransactionSplit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the late-arriving transfer memo.
 *
 * <p>A transfer categorized while pending was categorized without its memo, because the pending feed
 * truncates the description before the memo every time.  When the cleared copy arrives the memo is a
 * fact that did not exist when the question was answered, and this is the one moment it can be acted
 * on.
 *
 * <p>What these tests hold is the narrowness of the offer.  The memo is being weighed against an
 * answer the user gave deliberately, so the bar for interrupting them is high:  <b>disagreement with
 * a memo that has priors, on a single split, and nothing else</b>.  Agreement is the common case --
 * 80.8% of the time the memo names what the user already chose -- and a question there would be the
 * opposite of the point.
 */
@DisplayName("Late Memo Confirmation Tests")
public class LateMemoControllerTest {

    private static final UUID ROOM_RENTAL = UUID.randomUUID();
    private static final UUID GROCERIES = UUID.randomUUID();


    /*
     * Test doubles:
     */
    private static BudgetItem budgetItem(UUID id, String payee) {
        BudgetItem item = mock(BudgetItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getPayee()).thenReturn(payee);
        return item;
    }

    /** A split assigned to the given budget item, as one taken from a provisional transaction. */
    private static TransactionSplit splitFor(BudgetItem item) throws Exception {
        UUID idBudgetItem = item.getId();
        TransactionSplit split = mock(TransactionSplit.class);
        when(split.getIdBudgetItem()).thenReturn(idBudgetItem);
        when(split.getBudgetItem()).thenReturn(item);
        return split;
    }

    private static MemoBudgetItemHistory.Suggestion suggestion(BudgetItem item, int priors) {
        return new MemoBudgetItemHistory.Suggestion(item, "RENT", priors);
    }

    private static List<TransactionSplit> listOf(TransactionSplit... splits) {
        return new ArrayList<>(Arrays.asList(splits));
    }


    /*
     * When there is something to ask about:
     */
    @Test
    @DisplayName("A memo that names a different item than the one assigned is worth asking about")
    void testDisagreementIsWorthAsking() throws Exception {

        // The case section 6 was written for:  the transfer was categorized as Groceries while
        // pending, and cleared carrying the word RENT.
        BudgetItem groceries = budgetItem(GROCERIES, "Groceries");
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental");

        assertSame(groceries, LateMemoController.disagreeingItem(
                listOf(splitFor(groceries)), suggestion(roomRental, 42)));
    }

    @Test
    @DisplayName("A memo that agrees with what is already assigned asks nothing")
    void testAgreementAsksNothing() throws Exception {

        // The common case by a wide margin -- 80.8% of suggestions name what the user chose. A
        // question here would fire on almost every reconciled transfer that carries a memo.
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental");

        assertNull(LateMemoController.disagreeingItem(
                listOf(splitFor(roomRental)), suggestion(roomRental, 42)));
    }

    @Test
    @DisplayName("A memo resting on a single prior still asks, and says so")
    void testSinglePriorStillAsks() throws Exception {
        // One prior is weak evidence, but the user answers the question -- and the count is in the
        // prompt for them to weigh. Silence would throw away the case where they typed the memo
        // precisely because the pending categorization was wrong.
        BudgetItem groceries = budgetItem(GROCERIES, "Groceries");
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental");

        assertSame(groceries, LateMemoController.disagreeingItem(
                listOf(splitFor(groceries)), suggestion(roomRental, 1)));
    }


    /*
     * When there is not:
     */
    @Test
    @DisplayName("No memo, or a memo with no history, asks nothing")
    void testNoSuggestionAsksNothing() throws Exception {

        BudgetItem groceries = budgetItem(GROCERIES, "Groceries");

        assertNull(LateMemoController.disagreeingItem(listOf(splitFor(groceries)), null));
        assertNull(LateMemoController.disagreeingItem(listOf(splitFor(groceries)),
                new MemoBudgetItemHistory.Suggestion(null, "RENT", 42)));
    }

    @Test
    @DisplayName("A multi-split transfer asks nothing")
    void testMultiSplitAsksNothing() throws Exception {

        // FUND ENVELOPES is one transfer across ten envelopes. Such a transfer is excluded from the
        // memo history for the same reason it is excluded here: "it is currently split to X" is not
        // a true sentence about it, and neither is any single replacement.
        BudgetItem groceries = budgetItem(GROCERIES, "Groceries");
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental");

        assertNull(LateMemoController.disagreeingItem(
                listOf(splitFor(groceries), splitFor(roomRental)), suggestion(roomRental, 42)));
    }

    @Test
    @DisplayName("No splits at all asks nothing")
    void testNoSplitsAsksNothing() {

        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental");

        assertNull(LateMemoController.disagreeingItem(null, suggestion(roomRental, 42)));
        assertNull(LateMemoController.disagreeingItem(Collections.emptyList(), suggestion(roomRental, 42)));
    }

    @Test
    @DisplayName("A split whose budget item cannot be read asks nothing, rather than throwing")
    void testUnreadableAssignedItemAsksNothing() throws Exception {

        // The transaction is already categorized and the import has already done its work. A
        // question that cannot name what it proposes to replace is not worth asking, and an
        // exception here would cost the user an import over a hint.
        TransactionSplit split = mock(TransactionSplit.class);
        when(split.getIdBudgetItem()).thenReturn(GROCERIES);
        when(split.getBudgetItem()).thenThrow(new IllegalStateException("budget item deleted"));

        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental");

        assertNull(LateMemoController.disagreeingItem(listOf(split), suggestion(roomRental, 42)));
    }


    /*
     * The question itself:
     */
    @Test
    @DisplayName("The prompt names the memo, the item it points to, the count, and what is assigned")
    void testPromptNamesTheEvidence() {

        // Naming the evidence is what makes a suggestion that is wrong one time in five safe to put
        // in front of an answer the user gave on purpose.
        BudgetItem groceries = budgetItem(GROCERIES, "Groceries");
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental");

        assertEquals("This transfer cleared with the memo \"RENT\", which you have assigned to " +
                        "Room rental 42 times.  It is currently split to Groceries.",
                LateMemoController.describeDisagreement(suggestion(roomRental, 42), groceries));
    }

    @Test
    @DisplayName("The prompt reads correctly for a single prior")
    void testPromptIsSingularForOnePrior() {

        BudgetItem groceries = budgetItem(GROCERIES, "Groceries");
        BudgetItem roomRental = budgetItem(ROOM_RENTAL, "Room rental");

        assertEquals("This transfer cleared with the memo \"RENT\", which you have assigned to " +
                        "Room rental 1 time.  It is currently split to Groceries.",
                LateMemoController.describeDisagreement(suggestion(roomRental, 1), groceries));
    }
}
