package com.hixon.utilities;

import com.hixon.financialApp.controller.TransactionSplitsController;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.budget.MemoBudgetItemHistory;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.utility.DatabaseConnectionManager;
import com.hixon.financialApp.utility.Utility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Measures what the transfer memo is worth, against the real register.
 *
 * <p>A ranking change is only honestly judged in bulk.  A unit test can show that 30 points moves
 * one item past another; only replaying the whole file shows whether that is an improvement, and
 * the number that decides it is not accuracy but <b>how far up the list the item the user actually
 * chose moved</b>.  A suggestion that is right 83.5% of the time is a good first row and a bad
 * silent choice, so the constants it feeds -- {@code MEMO_BONUS} and {@code MEMO_BONUS_SINGLE_PRIOR}
 * -- should be tuned against this report and not against intuition.
 *
 * <p>Not a unit test:  like the other tools in this package it needs the real database, so it has a
 * {@code main} instead.  Run it after {@link BackfillUserDescriptions}, since a memo the import
 * never recorded cannot be replayed.
 *
 * <p>The replay is honest about time.  For each transfer it asks the history <em>as of that
 * transfer's post date</em>, through {@link MemoBudgetItemHistory#asOf}, so no suggestion is ever
 * informed by the answer it is being scored against.
 *
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=com.hixon.utilities.MemoRankingBacktest -Dexec.args=2024
 * </pre>
 */
public class MemoRankingBacktest {

    /** Transfers before this year are ignored unless a year is given on the command line. */
    private static final int DEFAULT_FROM_YEAR = 2024;

    /**
     * What the replay found for one transfer.
     *
     * @param rankBefore  the chosen item's 1-based position without the memo, or 0 if it was not in
     *                    the merchant's list at all
     * @param rankAfter   its position with the memo, or 0 as above
     * @param suggested   whether the memo suggested anything
     * @param correct     whether what the memo suggested is what the user chose
     * @param appended    whether the memo put the chosen item in a list that did not contain it
     * @param singlePrior whether the suggestion rested on exactly one earlier transfer
     */
    private record Outcome(int rankBefore, int rankAfter, boolean suggested, boolean correct,
                           boolean appended, boolean singlePrior) {
    }

    public static void main(String[] args) throws Exception {

        int fromYear = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_FROM_YEAR;

        // Credentials come from db.properties (excluded from version control) - never hardcode them.
        DatabaseConnectionManager mgr = DatabaseConnectionManager.fromProperties();
        Utility.setConnectionManager(mgr);

        try {
            List<UUID> transfers = loadTransfersToReplay(mgr, fromYear);
            System.out.println("Replaying " + transfers.size() + " single-split transfers with a memo, from "
                    + fromYear + " onwards.\n");

            List<Outcome> outcomes = new ArrayList<>();
            int unreplayable = 0;

            for (UUID idTransaction : transfers) {
                Outcome outcome = replay(idTransaction);
                if (outcome == null) {
                    unreplayable++;
                    continue;
                }
                outcomes.add(outcome);
            }

            report(outcomes, unreplayable);

        } finally {
            mgr.close();
        }
    }

    /**
     * The transfers worth replaying:  single-split, memo-bearing, in post date order.
     *
     * <p>Multi-split transfers are excluded for the same reason the history query excludes them --
     * FUND ENVELOPES names ten budget items and none of them is "the" answer, so there is no rank
     * to measure.
     *
     * @param mgr      the connection manager
     * @param fromYear the earliest post year to include
     * @return the transaction ids to replay, oldest first
     * @throws Exception if the query fails
     */
    private static List<UUID> loadTransfersToReplay(DatabaseConnectionManager mgr, int fromYear) throws Exception {

        String sql = "select bin_to_uuid(t.idTransaction) as idTransaction from transaction t "
                + "where t.user_description is not null and t.user_description <> '' "
                + "and year(t.postDate) >= ? "
                + "and (select count(*) from transaction_split s "
                + "where s.Transaction_idTransaction = t.idTransaction) = 1 "
                + "order by t.postDate asc";

        List<UUID> transfers = new ArrayList<>();
        try (Connection conn = mgr.getConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, fromYear);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    transfers.add(UUID.fromString(rs.getString("idTransaction")));
                }
            }
        }
        return transfers;
    }

    /**
     * Replay one transfer:  rebuild the list the import would have shown, and find where the item
     * the user chose sat in it, with and without the memo.
     *
     * @param idTransaction the transfer to replay
     * @return what happened, or null if this transfer cannot be replayed (no merchant, no split, or
     *         a budget item that no longer loads)
     */
    private static Outcome replay(UUID idTransaction) {

        try {
            Transaction transaction = Transaction.getById(idTransaction);
            if (transaction == null || transaction.getIdMerchant() == null) {
                return null;
            }

            BudgetItem chosen = chosenBudgetItem(idTransaction);
            if (chosen == null) {
                return null;
            }

            // The budget in play is the one the item the user chose lives in.
            Budget budget = Budget.getById(chosen.getIdBudget());
            Merchant merchant = Merchant.getById(transaction.getIdMerchant());
            if (budget == null || merchant == null) {
                return null;
            }

            List<BudgetItemMerchant> baseline = BudgetItemMerchant.getAssignedBudgetItems(budget, merchant);
            if (baseline.isEmpty()) {
                return null;
            }

            // Without the memo:  exactly what the import ranked before this feature.
            List<Double> baseScores = TransactionSplitsController.calculateRelevancyScores(baseline, transaction);
            TransactionSplitsController.sortByRelevancyScore(baseline, baseScores);
            int rankBefore = rankOf(baseline, chosen.getId());

            // With it.  asOf keeps the replay from seeing its own answer.
            MemoBudgetItemHistory.Suggestion suggestion = new MemoBudgetItemHistory()
                    .asOf(transaction.getPostDate())
                    .lookup(transaction, budget);

            List<BudgetItemMerchant> ranked = BudgetItemMerchant.getAssignedBudgetItems(budget, merchant);
            BudgetItemMerchant appended =
                    TransactionSplitsController.appendMemoSuggestedItem(ranked, merchant, suggestion);
            List<Double> scores = TransactionSplitsController.calculateRelevancyScores(ranked, transaction);
            TransactionSplitsController.applyMemoBonus(ranked, scores, suggestion);
            TransactionSplitsController.sortByRelevancyScore(ranked, scores);
            int rankAfter = rankOf(ranked, chosen.getId());

            boolean correct = suggestion != null && chosen.getId().equals(suggestion.budgetItem().getId());

            return new Outcome(rankBefore, rankAfter, suggestion != null, correct,
                    appended != null && correct, suggestion != null && suggestion.isSinglePrior());

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The budget item of a transfer's one split.
     *
     * @param idTransaction the transfer
     * @return the item the user chose, or null if it no longer loads
     * @throws Exception if the query fails
     */
    private static BudgetItem chosenBudgetItem(UUID idTransaction) throws Exception {

        String sql = "select bin_to_uuid(BudgetItem_idBudgetItem) as idBudgetItem from transaction_split "
                + "where Transaction_idTransaction = uuid_to_bin('" + idTransaction + "')";

        try (java.sql.Statement statement = Utility.getDbConnection().createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            return BudgetItem.getById(UUID.fromString(rs.getString("idBudgetItem")));
        }
    }

    /**
     * The 1-based position of a budget item in a ranked list.
     *
     * @param ranked       the sorted list
     * @param idBudgetItem the item to find
     * @return its position, or 0 if the list does not contain it
     */
    private static int rankOf(List<BudgetItemMerchant> ranked, UUID idBudgetItem) {
        for (int i = 0; i < ranked.size(); i++) {
            if (idBudgetItem.equals(ranked.get(i).getIdBudgetItem())) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Print the report.  Coverage and accuracy set the context; the movement table is the number the
     * constants should be tuned against.
     *
     * @param outcomes     one entry per replayed transfer
     * @param unreplayable how many transfers could not be rebuilt at all
     */
    private static void report(List<Outcome> outcomes, int unreplayable) {

        int replayed = outcomes.size();
        if (replayed == 0) {
            System.out.println("Nothing could be replayed.  Has BackfillUserDescriptions been run?");
            return;
        }

        int suggested = 0, correct = 0, singlePrior = 0, appended = 0;
        int movedUp = 0, movedDown = 0, unchanged = 0;
        int reachedFirst = 0, wasAlreadyFirst = 0, missingBefore = 0;
        int placesGained = 0;

        for (Outcome outcome : outcomes) {
            if (outcome.suggested()) {
                suggested++;
            }
            if (outcome.correct()) {
                correct++;
            }
            if (outcome.singlePrior()) {
                singlePrior++;
            }
            if (outcome.appended()) {
                appended++;
            }

            if (outcome.rankBefore() == 0) {
                missingBefore++;
            }
            if (outcome.rankBefore() == 1) {
                wasAlreadyFirst++;
            }
            if (outcome.rankAfter() == 1 && outcome.rankBefore() != 1) {
                reachedFirst++;
            }

            // A rank of 0 means "not in the list"; it is worse than any real position, so treat it
            // as one place beyond the end for the purpose of measuring movement.
            int before = (outcome.rankBefore() == 0) ? Integer.MAX_VALUE : outcome.rankBefore();
            int after = (outcome.rankAfter() == 0) ? Integer.MAX_VALUE : outcome.rankAfter();
            if (after < before) {
                movedUp++;
                if (before != Integer.MAX_VALUE) {
                    placesGained += before - after;
                }
            } else if (after > before) {
                movedDown++;
            } else {
                unchanged++;
            }
        }

        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("Transfers replayed", replayed + "  (" + unreplayable + " could not be rebuilt)");
        lines.put("Memo produced a suggestion", percentage(suggested, replayed));
        lines.put("  ... and it was what the user chose", percentage(correct, suggested));
        lines.put("  ... resting on a single prior", percentage(singlePrior, suggested));
        lines.put("Chosen item was absent from the list", percentage(missingBefore, replayed));
        lines.put("  ... and the memo put it there", percentage(appended, replayed));

        System.out.println("Coverage and accuracy");
        System.out.println("---------------------");
        print(lines);

        lines.clear();
        lines.put("Chosen item moved up", percentage(movedUp, replayed));
        lines.put("Chosen item moved down", percentage(movedDown, replayed));
        lines.put("Chosen item did not move", percentage(unchanged, replayed));
        lines.put("Reached the top of the list", percentage(reachedFirst, replayed));
        lines.put("Was already at the top", percentage(wasAlreadyFirst, replayed));
        lines.put("Places gained, in total", String.valueOf(placesGained));

        System.out.println("\nWhere the item the user chose ended up");
        System.out.println("--------------------------------------");
        print(lines);

        System.out.println("\nTuned constants:  MEMO_BONUS = " + TransactionSplitsController.MEMO_BONUS
                + ", MEMO_BONUS_SINGLE_PRIOR = " + TransactionSplitsController.MEMO_BONUS_SINGLE_PRIOR);
        System.out.println("Moved down is the number to watch:  the memo is allowed to be wrong, but not");
        System.out.println("to bury the right answer for the transfers it says nothing useful about.");
    }

    private static String percentage(int count, int outOf) {
        if (outOf == 0) {
            return count + "";
        }
        return String.format("%d  (%.1f%%)", count, 100.0 * count / outOf);
    }

    private static void print(Map<String, String> lines) {
        int width = 0;
        for (String label : lines.keySet()) {
            width = Math.max(width, label.length());
        }
        for (Map.Entry<String, String> line : lines.entrySet()) {
            System.out.printf("%-" + width + "s  %s%n", line.getKey(), line.getValue());
        }
    }
}
