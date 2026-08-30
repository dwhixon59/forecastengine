package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.utility.Utility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the memo history lookup.
 *
 * <p>The measurement this feature rests on -- the memo names the budget item it has named most often
 * 84.4% of the time -- was taken with the history scoped to <b>(memo, register, direction)</b> and
 * bounded to the trailing 18 months.  Dropping the register and direction takes it to 74.4%, and
 * removing the window takes it to 75.3%; both make <em>more</em> suggestions while getting
 * <em>fewer</em> of them right.  So the scoping is not a detail of the query, it is the result, and
 * these tests treat it that way:  {@code HistoryTable} reproduces the SQL's filtering clause for
 * clause over in-memory rows, and a separate assertion holds the real SQL to the same clauses.
 */
@DisplayName("Memo Budget Item History Tests")
public class MemoBudgetItemHistoryTest {

    private static final UUID DAVES_CHECKING = UUID.randomUUID();
    private static final UUID DANNIS_CHECKING = UUID.randomUUID();
    private static final UUID CURRENT_BUDGET = UUID.randomUUID();
    private static final UUID LAST_YEARS_BUDGET = UUID.randomUUID();

    private static final UUID ROOM_RENTAL = UUID.randomUUID();
    private static final UUID SECOND_ROOM_RENTAL = UUID.randomUUID();
    private static final UUID JOINT_SPENDING = UUID.randomUUID();
    private static final UUID GROCERIES = UUID.randomUUID();
    private static final UUID STALE_ROOM_RENTAL = UUID.randomUUID();


    /*
     * Test doubles:
     */
    /**
     * One historical transaction, as the history query sees it.
     *
     * @param memo         the memo on the transaction
     * @param idRegister   the register it belongs to
     * @param sign         the sign of its amount
     * @param splitCount   how many splits it has
     * @param idBudgetItem the budget item its split names
     * @param payee        that item's name
     * @param postDate     when it posted, which decides whether it is inside the history window
     */
    private record Row(String memo, UUID idRegister, int sign, int splitCount, UUID idBudgetItem, String payee,
                       Calendar postDate) {

        /**
         * A prior from a month ago -- comfortably inside the window, so that the scoping tests are
         * about scoping and nothing else.
         */
        Row(String memo, UUID idRegister, int sign, int splitCount, UUID idBudgetItem, String payee) {
            this(memo, idRegister, sign, splitCount, idBudgetItem, payee, monthsAgo(1));
        }
    }

    private static Calendar monthsAgo(int months) {
        Calendar when = Calendar.getInstance();
        when.add(Calendar.MONTH, -months);
        return when;
    }

    /**
     * The in-memory stand-in for the transaction history.  Its filtering deliberately mirrors the
     * SQL clause for clause, so that a test asserting "the opposite direction is not consulted" is
     * asserting something about the lookup rather than about the fake.
     */
    private static class HistoryTable extends MemoBudgetItemHistory {

        private final List<Row> rows = new ArrayList<>();
        private final List<BudgetItem> itemsById = new ArrayList<>();
        private final List<BudgetItem> itemsByName = new ArrayList<>();
        private boolean itemLookupFails = false;
        private Calendar windowStartAsked = null;

        HistoryTable with(Row row) {
            rows.add(row);
            return this;
        }

        HistoryTable knowing(BudgetItem item) {
            itemsById.add(item);
            return this;
        }

        HistoryTable named(BudgetItem item) {
            itemsByName.add(item);
            return this;
        }

        HistoryTable withTheItemDeleted() {
            itemLookupFails = true;
            return this;
        }

        @Override
        protected List<Candidate> fetchCandidates(String memo, UUID idRegister, int sign, UUID idTransaction,
                Calendar notBefore) {

            windowStartAsked = notBefore;

            List<Row> matching = new ArrayList<>();
            for (Row row : rows) {
                if (row.memo().equals(memo)
                        && row.idRegister().equals(idRegister)
                        && row.sign() == sign
                        && row.splitCount() == 1
                        && (notBefore == null || !row.postDate().before(notBefore))) {
                    matching.add(row);
                }
            }

            List<Candidate> candidates = new ArrayList<>();
            List<UUID> counted = new ArrayList<>();
            for (Row row : matching) {
                if (counted.contains(row.idBudgetItem())) {
                    continue;
                }
                counted.add(row.idBudgetItem());
                int priors = 0;
                for (Row other : matching) {
                    if (other.idBudgetItem().equals(row.idBudgetItem())) {
                        priors++;
                    }
                }
                candidates.add(new Candidate(row.idBudgetItem(), row.payee(), priors));
            }

            candidates.sort((a, b) -> Integer.compare(b.priors(), a.priors()));
            return candidates;
        }

        @Override
        protected BudgetItem loadById(UUID idBudgetItem) throws EntityException {
            if (itemLookupFails) {
                throw new EntityException("the budget item has been deleted");
            }
            for (BudgetItem item : itemsById) {
                if (idBudgetItem.equals(item.getId())) {
                    return item;
                }
            }
            return null;
        }

        @Override
        protected List<BudgetItem> loadUnexpiredByPayee(Budget budget, String payee) {
            List<BudgetItem> found = new ArrayList<>();
            for (BudgetItem item : itemsByName) {
                if (payee.equals(item.getPayee()) && budget.getId().equals(item.getIdBudget())) {
                    found.add(item);
                }
            }
            return found;
        }
    }

    private static BudgetItem item(UUID id, String payee, UUID idBudget) {
        BudgetItem budgetItem = mock(BudgetItem.class);
        when(budgetItem.getId()).thenReturn(id);
        when(budgetItem.getPayee()).thenReturn(payee);
        when(budgetItem.getIdBudget()).thenReturn(idBudget);
        return budgetItem;
    }

    private static Budget budget(UUID id) {
        Budget budget = mock(Budget.class);
        when(budget.getId()).thenReturn(id);
        return budget;
    }

    private static Transaction transfer(String memo, UUID idRegister, double amount) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getUserDescription()).thenReturn(memo);
        when(transaction.getIdRegister()).thenReturn(idRegister);
        when(transaction.getAmount()).thenReturn(amount);
        when(transaction.getId()).thenReturn(UUID.randomUUID());
        when(transaction.getPostDate()).thenReturn(Calendar.getInstance());
        return transaction;
    }

    private static Row rentInto(UUID register, UUID idBudgetItem, String payee) {
        return new Row("RENT", register, 1, 1, idBudgetItem, payee);
    }


    /*
     * Tests:
     */
    @Test
    @DisplayName("The memo returns the item it named before, with the weight of the evidence")
    void testSameMemoSameRegisterSameDirection() throws Exception {

        BudgetItem roomRental = item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET);
        MemoBudgetItemHistory history = new HistoryTable()
                .with(rentInto(DAVES_CHECKING, ROOM_RENTAL, "Room rental"))
                .with(rentInto(DAVES_CHECKING, ROOM_RENTAL, "Room rental"))
                .with(rentInto(DAVES_CHECKING, ROOM_RENTAL, "Room rental"))
                .knowing(roomRental);

        MemoBudgetItemHistory.Suggestion suggestion =
                history.lookup(transfer("RENT", DAVES_CHECKING, 750.00), budget(CURRENT_BUDGET));

        assertNotNull(suggestion);
        assertSame(roomRental, suggestion.budgetItem());
        assertEquals("RENT", suggestion.memo());
        assertEquals(3, suggestion.priors());
        assertFalse(suggestion.isSinglePrior());
    }

    @Test
    @DisplayName("The opposite direction is a different question - it is the far side of the same transfer")
    void testOppositeDirectionIsNotConsulted() throws Exception {

        // RENT arriving in this register is Room rental; RENT leaving it is Danni's contribution.
        // One transfer writes both rows, so ignoring direction has them vote against each other.
        MemoBudgetItemHistory history = new HistoryTable()
                .with(rentInto(DAVES_CHECKING, ROOM_RENTAL, "Room rental"))
                .knowing(item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET));

        assertNull(history.lookup(transfer("RENT", DAVES_CHECKING, -750.00), budget(CURRENT_BUDGET)));
    }

    @Test
    @DisplayName("A different register is a different question")
    void testDifferentRegisterIsNotConsulted() throws Exception {

        MemoBudgetItemHistory history = new HistoryTable()
                .with(rentInto(DAVES_CHECKING, ROOM_RENTAL, "Room rental"))
                .knowing(item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET));

        assertNull(history.lookup(transfer("RENT", DANNIS_CHECKING, 750.00), budget(CURRENT_BUDGET)));
    }

    @Test
    @DisplayName("A transfer split across many items casts no vote - one transfer, one vote")
    void testMultiSplitTransfersAreExcluded() throws Exception {

        // FUND ENVELOPES is a bulk envelope-funding transfer split across ten budget items.  Each
        // split would otherwise be a vote, drowning out the memos that mean exactly one thing.
        MemoBudgetItemHistory history = new HistoryTable()
                .with(new Row("FUND ENVELOPES", DAVES_CHECKING, 1, 10, GROCERIES, "Groceries"))
                .with(new Row("FUND ENVELOPES", DAVES_CHECKING, 1, 10, ROOM_RENTAL, "Room rental"))
                .knowing(item(GROCERIES, "Groceries", CURRENT_BUDGET))
                .knowing(item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET));

        assertNull(history.lookup(transfer("FUND ENVELOPES", DAVES_CHECKING, 500.00), budget(CURRENT_BUDGET)));
    }

    @Test
    @DisplayName("The plurality wins when the user has not been consistent with themselves")
    void testPluralityWins() throws Exception {

        // COVER OVERDRAFTS really is Short term borrowing 19x, Joint Spending Money 13x and Other
        // 7x in the data.  Nothing can do better than reporting the plurality and its count.
        BudgetItem shortTermBorrowing = item(ROOM_RENTAL, "Short term borrowing", CURRENT_BUDGET);
        MemoBudgetItemHistory history = new HistoryTable()
                .with(new Row("COVER OVERDRAFTS", DAVES_CHECKING, 1, 1, JOINT_SPENDING, "Joint Spending Money"))
                .with(new Row("COVER OVERDRAFTS", DAVES_CHECKING, 1, 1, ROOM_RENTAL, "Short term borrowing"))
                .with(new Row("COVER OVERDRAFTS", DAVES_CHECKING, 1, 1, ROOM_RENTAL, "Short term borrowing"))
                .knowing(shortTermBorrowing)
                .knowing(item(JOINT_SPENDING, "Joint Spending Money", CURRENT_BUDGET));

        MemoBudgetItemHistory.Suggestion suggestion =
                history.lookup(transfer("cover overdrafts", DAVES_CHECKING, 200.00), budget(CURRENT_BUDGET));

        assertNotNull(suggestion);
        assertSame(shortTermBorrowing, suggestion.budgetItem());
        assertEquals(2, suggestion.priors());
    }

    @Test
    @DisplayName("A winner in a stale budget falls back to the same-named item in the current one")
    void testStaleBudgetFallsBackToTheSameName() throws Exception {

        // Budget items are per-budget and get re-created.  Most of the gap between the 2025
        // backtest (85.8%) and the 2026 one (73.3%) is the winner wearing last year's id.
        BudgetItem thisYearsRoomRental = item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET);
        MemoBudgetItemHistory history = new HistoryTable()
                .with(rentInto(DAVES_CHECKING, STALE_ROOM_RENTAL, "Room rental"))
                .knowing(item(STALE_ROOM_RENTAL, "Room rental", LAST_YEARS_BUDGET))
                .named(thisYearsRoomRental);

        MemoBudgetItemHistory.Suggestion suggestion =
                history.lookup(transfer("RENT", DAVES_CHECKING, 750.00), budget(CURRENT_BUDGET));

        assertNotNull(suggestion);
        assertSame(thisYearsRoomRental, suggestion.budgetItem());
        assertEquals(1, suggestion.priors());
        assertTrue(suggestion.isSinglePrior());
    }

    @Test
    @DisplayName("A budget item deleted outright still resolves through its name")
    void testDeletedItemFallsBackToTheSameName() throws Exception {

        BudgetItem thisYearsRoomRental = item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET);
        MemoBudgetItemHistory history = new HistoryTable()
                .with(rentInto(DAVES_CHECKING, STALE_ROOM_RENTAL, "Room rental"))
                .withTheItemDeleted()
                .named(thisYearsRoomRental);

        MemoBudgetItemHistory.Suggestion suggestion =
                history.lookup(transfer("RENT", DAVES_CHECKING, 750.00), budget(CURRENT_BUDGET));

        assertNotNull(suggestion);
        assertSame(thisYearsRoomRental, suggestion.budgetItem());
    }

    @Test
    @DisplayName("An ambiguous name is not resolved - the memo suggests nothing rather than guessing")
    void testAmbiguousNameSuggestsNothing() throws Exception {

        MemoBudgetItemHistory history = new HistoryTable()
                .with(rentInto(DAVES_CHECKING, STALE_ROOM_RENTAL, "Room rental"))
                .knowing(item(STALE_ROOM_RENTAL, "Room rental", LAST_YEARS_BUDGET))
                .named(item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET))
                .named(item(SECOND_ROOM_RENTAL, "Room rental", CURRENT_BUDGET));

        assertNull(history.lookup(transfer("RENT", DAVES_CHECKING, 750.00), budget(CURRENT_BUDGET)));
    }

    @Test
    @DisplayName("A prior older than the history window casts no vote")
    void testStalePriorsAreOutsideTheWindow() throws Exception {

        // Unbounded history is not merely diluted, it is worse:  432 correct against an 18-month
        // window's 447.  A memo that meant one item two years ago must not still be voting.
        MemoBudgetItemHistory history = new HistoryTable()
                .with(new Row("RENT", DAVES_CHECKING, 1, 1, ROOM_RENTAL, "Room rental", monthsAgo(30)))
                .knowing(item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET));

        assertNull(history.lookup(transfer("RENT", DAVES_CHECKING, 750.00), budget(CURRENT_BUDGET)));
    }

    @Test
    @DisplayName("Recent priors outvote stale ones, because the stale ones are not counted at all")
    void testTheWindowKeepsStaleVotesFromWinning() throws Exception {

        // The failure the window fixes:  three votes for last year's answer against two for this
        // year's.  Unbounded, the plurality picks the wrong one.
        BudgetItem thisYear = item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET);
        MemoBudgetItemHistory history = new HistoryTable()
                .with(new Row("RENT", DAVES_CHECKING, 1, 1, GROCERIES, "Groceries", monthsAgo(26)))
                .with(new Row("RENT", DAVES_CHECKING, 1, 1, GROCERIES, "Groceries", monthsAgo(25)))
                .with(new Row("RENT", DAVES_CHECKING, 1, 1, GROCERIES, "Groceries", monthsAgo(24)))
                .with(new Row("RENT", DAVES_CHECKING, 1, 1, ROOM_RENTAL, "Room rental", monthsAgo(3)))
                .with(new Row("RENT", DAVES_CHECKING, 1, 1, ROOM_RENTAL, "Room rental", monthsAgo(1)))
                .knowing(thisYear)
                .knowing(item(GROCERIES, "Groceries", CURRENT_BUDGET));

        MemoBudgetItemHistory.Suggestion suggestion =
                history.lookup(transfer("RENT", DAVES_CHECKING, 750.00), budget(CURRENT_BUDGET));

        assertNotNull(suggestion);
        assertSame(thisYear, suggestion.budgetItem());
        assertEquals(2, suggestion.priors(), "the stale votes must not be counted, even as priors");
    }

    @Test
    @DisplayName("The window is anchored on the transaction, not on today")
    void testWindowIsAnchoredOnTheTransaction() throws Exception {

        // Categorizing an old transaction asks the question the way it would have been asked then.
        Calendar postedTwoYearsAgo = monthsAgo(24);
        Transaction transaction = transfer("RENT", DAVES_CHECKING, 750.00);
        when(transaction.getPostDate()).thenReturn(postedTwoYearsAgo);

        HistoryTable history = new HistoryTable()
                .with(new Row("RENT", DAVES_CHECKING, 1, 1, ROOM_RENTAL, "Room rental", monthsAgo(30)))
                .knowing(item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET));

        MemoBudgetItemHistory.Suggestion suggestion = history.lookup(transaction, budget(CURRENT_BUDGET));

        // 30 months ago is stale today, but only 6 months before a transaction that posted 24
        // months ago -- so it counts.
        assertNotNull(suggestion);
        assertEquals(MemoBudgetItemHistory.historyWindowStart(postedTwoYearsAgo).getTimeInMillis(),
                history.windowStartAsked.getTimeInMillis());
    }

    @Test
    @DisplayName("Computing the window start does not disturb the transaction's own date")
    void testWindowStartDoesNotMutateThePostDate() {

        Calendar postDate = Calendar.getInstance();
        long before = postDate.getTimeInMillis();

        Calendar windowStart = MemoBudgetItemHistory.historyWindowStart(postDate);

        assertEquals(before, postDate.getTimeInMillis(), "the transaction's date must not be mutated");
        assertTrue(windowStart.before(postDate));
    }

    @Test
    @DisplayName("A memo with no history suggests nothing, and does not throw")
    void testMemoWithNoPriors() {
        MemoBudgetItemHistory history = new HistoryTable();
        assertDoesNotThrow(() ->
                assertNull(history.lookup(transfer("PATIO", DAVES_CHECKING, 200.00), budget(CURRENT_BUDGET))));
    }

    @Test
    @DisplayName("A transaction with no memo is never looked up")
    void testNoMemo() throws Exception {

        // Just over half of transfers carry no memo, and every one of them has to behave exactly as
        // it did before this feature existed.
        MemoBudgetItemHistory history = new HistoryTable()
                .with(rentInto(DAVES_CHECKING, ROOM_RENTAL, "Room rental"))
                .knowing(item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET));

        assertNull(history.lookup(transfer(null, DAVES_CHECKING, 750.00), budget(CURRENT_BUDGET)));
        assertNull(history.lookup(transfer("   ", DAVES_CHECKING, 750.00), budget(CURRENT_BUDGET)));
        assertNull(history.lookup(null, budget(CURRENT_BUDGET)));
        assertNull(history.lookup(transfer("RENT", DAVES_CHECKING, 750.00), null));
    }

    @Test
    @DisplayName("Memos are normalized to the form the history is keyed on")
    void testNormalize() {
        assertEquals("RENT", MemoBudgetItemHistory.normalize("  rent "));
        assertEquals("JOINT SPENDING MONEY JSA", MemoBudgetItemHistory.normalize("Joint  Spending\tMoney JSA"));
        assertNull(MemoBudgetItemHistory.normalize(null));
        assertNull(MemoBudgetItemHistory.normalize("   "));
    }

    @Test
    @DisplayName("The query scopes by register, direction, single-split and window, and excludes itself")
    void testQueryScoping() {

        UUID idTransaction = UUID.randomUUID();
        Calendar windowStart = MemoBudgetItemHistory.historyWindowStart(Calendar.getInstance());
        String query = MemoBudgetItemHistory.buildCandidateQuery(
                "RENT", DAVES_CHECKING, -1, idTransaction, null, windowStart);

        // Every one of these clauses is worth measured accuracy, not a stylistic preference.
        assertTrue(query.contains("t.user_description = 'RENT'"), query);
        assertTrue(query.contains("t.Register_idRegister = uuid_to_bin('" + DAVES_CHECKING + "')"), query);
        assertTrue(query.contains("sign(t.amount) = -1"), query);
        assertTrue(query.contains("where s.Transaction_idTransaction = t.idTransaction) = 1"), query);
        assertTrue(query.contains("t.idTransaction <> uuid_to_bin('" + idTransaction + "')"), query);
        assertTrue(query.contains("t.postDate >= " + Utility.calendarDateToSqlDateString(windowStart)), query);
        assertTrue(query.contains("order by count(*) desc, max(t.postDate) desc"), query);
    }

    @Test
    @DisplayName("The backtest cutoff and the history window are separate bounds")
    void testQueryCarriesBothDateBounds() {

        // asOf is the backtest's upper bound and is null in the application; the window is the lower
        // bound and is always set.  They must not be confused for one another.
        Calendar asOf = Calendar.getInstance();
        Calendar windowStart = MemoBudgetItemHistory.historyWindowStart(asOf);
        String query = MemoBudgetItemHistory.buildCandidateQuery(
                "RENT", DAVES_CHECKING, 1, null, asOf, windowStart);

        assertTrue(query.contains("t.postDate < " + Utility.calendarDateToSqlDateString(asOf)), query);
        assertTrue(query.contains("t.postDate >= " + Utility.calendarDateToSqlDateString(windowStart)), query);

        String unbounded = MemoBudgetItemHistory.buildCandidateQuery("RENT", DAVES_CHECKING, 1, null, null, null);
        assertFalse(unbounded.contains("t.postDate <"), unbounded);
        assertFalse(unbounded.contains("t.postDate >="), unbounded);
    }

    @Test
    @DisplayName("A memo carrying a quote cannot break the query")
    void testQueryEscapesTheMemo() {

        // DANNI'S HAIR and DAVE'S SPENDING MONEY are real memos in the file, and the queries here
        // are string-concatenated rather than prepared.
        String query = MemoBudgetItemHistory.buildCandidateQuery("DANNI'S HAIR", DAVES_CHECKING, 1, null, null, null);
        assertFalse(query.contains("'DANNI'S HAIR'"), query);
        assertTrue(query.contains("HAIR"), query);
    }

    @Test
    @DisplayName("The evidence is phrased for the user, singular and plural")
    void testDescribe() {
        BudgetItem roomRental = item(ROOM_RENTAL, "Room rental", CURRENT_BUDGET);
        assertEquals("memo \"RENT\" (42 priors)",
                new MemoBudgetItemHistory.Suggestion(roomRental, "RENT", 42).describe());
        assertEquals("memo \"PATIO\" (1 prior)",
                new MemoBudgetItemHistory.Suggestion(roomRental, "PATIO", 1).describe());
    }
}
