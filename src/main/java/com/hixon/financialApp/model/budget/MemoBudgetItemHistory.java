package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * Answers "which budget item has this transfer memo meant before?" from the transaction history.
 *
 * <p>Wells Fargo appends the memo the user typed to the transfer description, and
 * {@code extractUserDescription} lifts it back out at import time into
 * {@code transaction.user_description}.  A memo is the one place the user says <em>why</em> they
 * moved the money, so what they chose the last time they typed it is a strong hint about what they
 * want this time:  measured over the 960 single-split transfers since 2024, the most recent earlier
 * transfer with the same memo named the same budget item <b>83.5%</b> of the time.
 *
 * <p>83.5% orders a list well and chooses silently badly, so callers must obey the rule the
 * measurement dictates:  <b>the memo prefers a budget item, it never selects one and never rules one
 * out.</b>  This class only reports what the history says; it decides nothing.
 *
 * <h2>Why this is derived and not learned</h2>
 *
 * <p>There is no memo-to-budget-item table, deliberately.  A mapping table would have to be
 * invalidated every time the user recategorizes a transaction, and nothing would do that.  Derived
 * history is correct by construction:  recategorize a transfer and the next suggestion changes, with
 * no learn() call, no staleness and no unlearn path.  {@link TransferBudgetItemPair} earns its table
 * because it records something no transaction shows; a memo association is fully visible in the
 * transactions themselves.
 *
 * <h2>The key</h2>
 *
 * <p><b>(normalized memo, register, direction)</b>.  Both scope terms are load-bearing rather than
 * incidental:  dropping either takes the backtest from 83.5% to 77.6%, because both sides of one
 * transfer carry the same memo and belong to different budget items -- RENT is Room rental in
 * Dave's budget and Danni's contribution in Danni's.  The TransferMemoMapping deleted in e6253c8
 * failed for the opposite reason:  its key, a bare payee string, carried less information than the
 * question needed.
 */
public class MemoBudgetItemHistory {

    /**
     * A memo seen exactly once is a much weaker claim than one seen forty times, and callers are
     * expected to weight it accordingly.
     */
    public static final int SINGLE_PRIOR = 1;

    /**
     * How far back the history is allowed to reach, in months, measured from the transaction being
     * categorized.
     *
     * <p>Bounding this is not a performance concern, it is a correctness one.  Consulting every
     * transfer the register has ever recorded is <b>strictly worse</b> than an 18-month window --
     * measured by {@code com.hixon.utilities.MemoRankingBacktest} over the 1,044 single-split
     * memo-bearing transfers since 2024:
     *
     * <pre>
     *   window        suggestions   correct   accuracy
     *    6 months           514       433      84.2%
     *   12 months           551       446      80.9%
     *   18 months           559       447      80.0%
     *   24 months           566       445      78.6%
     *   36 months           571       435      76.2%
     *   unbounded           574       432      75.3%
     * </pre>
     *
     * <p>Reaching further back does not merely dilute the average, it loses correct answers outright:
     * a memo that meant one budget item two years ago can outvote what it has meant ever since.  18
     * months is the maximum of that curve on both axes at once; 12 is within one suggestion of it and
     * half a point more accurate, so anything in 12-18 is defensible.
     */
    public static final int HISTORY_WINDOW_MONTHS = 18;

    /**
     * Restricts the history to transfers that posted before this date.
     *
     * <p>Null in the application, which asks the question as of now and should see every transfer
     * the user has ever categorized.  Only the backtest sets it, because measuring a suggestion
     * against history that includes the answer measures nothing.
     */
    private Calendar postedBefore = null;

    /**
     * Ask the history as it stood on a past date.
     *
     * @param postedBefore consider only transfers that posted before this date
     * @return this lookup, for chaining
     */
    public MemoBudgetItemHistory asOf(Calendar postedBefore) {
        this.postedBefore = postedBefore;
        return this;
    }


    /**
     * One budget item the memo has been assigned to before, as the history query reports it --
     * before the item has been resolved into the budget in play.
     *
     * @param idBudgetItem the budget item chosen, which may belong to a budget no longer current
     * @param payee        that item's name, which is what survives a budget being re-created
     * @param priors       how many earlier transfers with this memo chose it
     */
    public record Candidate(UUID idBudgetItem, String payee, int priors) {
    }


    /**
     * What the history suggests for one transaction:  a budget item in the current budget, the memo
     * that produced it, and how much evidence there is.
     *
     * @param budgetItem the suggested item, always in the budget the caller asked about
     * @param memo       the normalized memo, as shown to the user
     * @param priors     how many earlier transfers with this memo chose this item
     */
    public record Suggestion(BudgetItem budgetItem, String memo, int priors) {

        /**
         * A single prior is one keystroke of evidence.  Callers scale their weighting down for it so
         * that a memo typed once cannot displace an item that already matches on amount.
         *
         * @return true if this suggestion rests on exactly one earlier transfer
         */
        public boolean isSinglePrior() {
            return priors <= SINGLE_PRIOR;
        }

        /**
         * The evidence, phrased for the user.  Naming it is what makes a suggestion that is wrong
         * one time in six safe to show first:  the user can see <em>why</em> it is first.
         *
         * @return for example:  memo "RENT" (42 priors)
         */
        public String describe() {
            return "memo \"" + memo + "\" (" + priors + (priors == 1 ? " prior)" : " priors)");
        }
    }


    /*
     * Lookup:
     */
    /**
     * The budget item this transaction's memo has meant before, in this register and this
     * direction.
     *
     * @param transaction   the transaction being categorized
     * @param currentBudget the budget its splits will be assigned within
     * @return the suggestion, or null if the transaction carries no memo, the memo has no history,
     *         or the item it names cannot be resolved into the current budget
     * @throws EntityException if the history query fails
     * @throws SQLException    if the history query fails
     */
    public Suggestion lookup(Transaction transaction, Budget currentBudget) throws EntityException, SQLException {

        if (transaction == null || currentBudget == null) {
            return null;
        }

        String memo = normalize(transaction.getUserDescription());
        UUID idRegister = transaction.getIdRegister();
        if (memo == null || idRegister == null) {
            return null;
        }

        // Direction scopes the question:  the far side of this same transfer carries this same memo
        // and belongs to a different budget item.
        int sign = (int) Math.signum(transaction.getAmount());

        List<Candidate> candidates = fetchCandidates(memo, idRegister, sign, transaction.getId(),
                historyWindowStart(transaction.getPostDate()));
        if (candidates.isEmpty()) {
            return null;
        }

        // The plurality wins.  Where the user is not consistent with themselves -- COVER OVERDRAFTS
        // is Short term borrowing 19x, Joint Spending Money 13x and Other 7x -- surfacing the
        // plurality and its count is as well as anyone can do.
        Candidate winner = candidates.get(0);

        BudgetItem budgetItem = resolveIntoBudget(winner, currentBudget);
        if (budgetItem == null) {
            return null;
        }

        return new Suggestion(budgetItem, memo, winner.priors());
    }

    /**
     * Run the history query.  Split out from lookup so that the ranking above it can be exercised
     * without a database.
     *
     * @param memo          the normalized memo to look up
     * @param idRegister    the register the transaction belongs to
     * @param sign          the sign of the transaction amount
     * @param idTransaction the transaction being categorized, excluded from its own history
     * @param notBefore     the start of the history window -- see {@link #HISTORY_WINDOW_MONTHS}
     * @return the budget items this memo has named before, most-chosen first
     * @throws EntityException if the query fails
     * @throws SQLException    if the query fails
     */
    protected List<Candidate> fetchCandidates(String memo, UUID idRegister, int sign, UUID idTransaction,
            Calendar notBefore) throws EntityException, SQLException {

        String query = buildCandidateQuery(memo, idRegister, sign, idTransaction, postedBefore, notBefore);
        List<Candidate> candidates = new ArrayList<>();

        ResultSet rs = EntityInt.getRS(query, "trying to find the budget items the memo " + memo +
                " has been assigned to before.");
        while (rs.next()) {
            candidates.add(new Candidate(UUID.fromString(rs.getString("mh.idBudgetItem")),
                    rs.getString("mh.payee"), rs.getInt("mh.priors")));
        }
        return candidates;
    }

    /**
     * Builds the history query.  Kept separate from execution so that the scoping the accuracy
     * depends on is assertable in a unit test.
     *
     * @param memo          the normalized memo to look up
     * @param idRegister    the register the transaction belongs to
     * @param sign          the sign of the transaction amount
     * @param idTransaction the transaction being categorized, or null when there is nothing to
     *                      exclude
     * @param postedBefore  restrict the history to transfers posted before this date, or null for
     *                      all of it -- see {@link #asOf}
     * @param notBefore     the start of the history window, or null for no lower bound -- see
     *                      {@link #HISTORY_WINDOW_MONTHS}
     * @return the SQL to run
     */
    static String buildCandidateQuery(String memo, UUID idRegister, int sign, UUID idTransaction,
            Calendar postedBefore, Calendar notBefore) {

        StringBuilder query = new StringBuilder()
                .append("select bin_to_uuid(bi.idBudgetItem) as 'mh.idBudgetItem', bi.payee as 'mh.payee', ")
                .append("count(*) as 'mh.priors' ")
                .append("from transaction t ")
                .append("join transaction_split ts on ts.Transaction_idTransaction = t.idTransaction ")
                .append("join budget_item bi on bi.idBudgetItem = ts.BudgetItem_idBudgetItem ")
                .append("where t.user_description = '").append(Utility.escapeSqlString(memo)).append("' ")
                .append("and t.Register_idRegister = uuid_to_bin('").append(idRegister).append("') ")
                .append("and sign(t.amount) = ").append(sign).append(" ")

                // One transfer, one vote.  FUND ENVELOPES is a bulk transfer split across ten
                // envelopes; counting each split as a vote for the memo is noise, not evidence.
                .append("and (select count(*) from transaction_split s ")
                .append("where s.Transaction_idTransaction = t.idTransaction) = 1 ");

        if (idTransaction != null) {
            query.append("and t.idTransaction <> uuid_to_bin('").append(idTransaction).append("') ");
        }

        if (postedBefore != null) {
            query.append("and t.postDate < ").append(Utility.calendarDateToSqlDateString(postedBefore)).append(" ");
        }

        // Stale votes are worse than no votes:  a memo that meant one item two years ago can outvote
        // what it has meant ever since.
        if (notBefore != null) {
            query.append("and t.postDate >= ").append(Utility.calendarDateToSqlDateString(notBefore)).append(" ");
        }

        return query.append("group by bi.idBudgetItem, bi.payee ")
                .append("order by count(*) desc, max(t.postDate) desc")
                .toString();
    }

    /**
     * Resolve a historical budget item into the budget currently in play.
     *
     * <p>Budget items are per-budget and get re-created, so the winning item is often a
     * still-correct answer wearing a stale id -- most of the gap between the 2025 backtest (85.8%)
     * and the 2026 one (73.3%) is this.  Fall back to the item of the same name in the current
     * budget, and give up rather than guess when that name is ambiguous.
     *
     * @param candidate     the winning history row
     * @param currentBudget the budget the suggestion has to live in
     * @return the budget item to suggest, or null if it cannot be resolved unambiguously
     */
    private BudgetItem resolveIntoBudget(Candidate candidate, Budget currentBudget) {

        try {
            BudgetItem budgetItem = loadById(candidate.idBudgetItem());
            if (budgetItem != null && currentBudget.getId().equals(budgetItem.getIdBudget())) {
                return budgetItem;
            }
        } catch (EntityException | BudgetException e) {
            // The item has been deleted outright.  The same-name fallback below is the whole point.
        }

        try {
            List<BudgetItem> sameName = loadUnexpiredByPayee(currentBudget, candidate.payee());
            return (sameName.size() == 1) ? sameName.get(0) : null;
        } catch (BudgetException e) {
            return null;
        }
    }

    /**
     * Loads one budget item by id.  A seam, so that the resolution logic above can be exercised
     * without a database.
     *
     * @param idBudgetItem the item to load
     * @return the budget item
     * @throws EntityException if the item cannot be loaded
     * @throws BudgetException if the item cannot be loaded
     */
    protected BudgetItem loadById(UUID idBudgetItem) throws EntityException, BudgetException {
        return BudgetItem.getById(idBudgetItem);
    }

    /**
     * Loads the unexpired budget items of a given name within one budget.  A seam, for the same
     * reason as {@link #loadById}.
     *
     * @param budget the budget to look in
     * @param payee  the item name to look for
     * @return the matching items, which the caller treats as a usable answer only when there is one
     * @throws BudgetException if the lookup fails
     */
    protected List<BudgetItem> loadUnexpiredByPayee(Budget budget, String payee) throws BudgetException {
        return BudgetItem.getUnexpiredByPayee(budget, payee);
    }

    /**
     * The start of the history window for a transaction, {@link #HISTORY_WINDOW_MONTHS} before it
     * posted.
     *
     * <p>Anchored on the transaction rather than on today, so that categorizing an old transaction
     * -- or replaying one in the backtest -- asks the question the way it would have been asked at
     * the time.  A transaction with no post date is treated as posting now, which is what an import
     * in progress amounts to.
     *
     * @param postDate when the transaction posted, or null
     * @return the earliest post date a prior may have
     */
    static Calendar historyWindowStart(Calendar postDate) {
        Calendar windowStart = (Calendar) ((postDate != null) ? postDate : Calendar.getInstance()).clone();
        windowStart.add(Calendar.MONTH, -HISTORY_WINDOW_MONTHS);
        return windowStart;
    }

    /**
     * Reduce a memo to the form the history is keyed on.  Comparison is case-insensitive in the
     * database collation anyway; upper case is what the bank sends and what the user sees.
     *
     * @param memo the memo as stored on the transaction
     * @return the normalized memo, or null if there is nothing to look up
     */
    static String normalize(String memo) {
        if (memo == null) {
            return null;
        }
        String normalized = memo.trim().replaceAll("\\s+", " ").toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }
}
