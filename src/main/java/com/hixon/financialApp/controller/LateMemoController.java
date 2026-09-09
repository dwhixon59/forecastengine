package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.MemoBudgetItemHistory;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.view.base.ViewInt;

import java.util.List;
import java.util.UUID;

/**
 * Offers to re-categorize a transfer whose memo arrived after the answer did.
 *
 * <p>The memo is the one place the user says <em>why</em> they moved the money, and a transfer that
 * was categorized while it was still pending was categorized without it.  That is not a timing
 * accident that better code could avoid:  the pending feed truncates the bank's description, and the
 * truncation lands before the memo <b>every time</b> -- 47 characters, cut mid-word through
 * {@code WAY2SAVE}.  A memo can never be read from a provisional transaction.
 *
 * <p>So when the cleared copy arrives it carries a fact that did not exist when the question was
 * answered.  {@code ImportController} takes the splits from the provisional and skips Phases 2.5, 3
 * and 4 entirely -- correctly, since the transaction is already categorized -- and the memo is
 * stored and never read.  This controller is the one moment it can still be acted on.
 *
 * <h2>When it asks</h2>
 *
 * <p>Only on <b>disagreement with a memo that has priors</b>:
 *
 * <ul>
 *   <li>the transaction was reconciled with a provisional one, so the answer really was given
 *       without the memo;</li>
 *   <li>the cleared copy yields a memo, and that memo has named a budget item before;</li>
 *   <li>there is exactly one split -- a multi-split transfer is excluded from the memo history
 *       anyway ({@code FUND ENVELOPES} is one transfer across ten envelopes), and "it is currently
 *       split to X" is not a true sentence about one;</li>
 *   <li>and the item the memo names is not the item already assigned.</li>
 * </ul>
 *
 * <p>Anything else is silent.  A question on every reconciled transfer would be the opposite of the
 * point, and agreement is the common case:  measured over 1,044 replayed transfers, the memo names
 * what the user chose 80.8% of the time.
 *
 * <h2>Why a question and not a correction</h2>
 *
 * <p>The rule the whole feature obeys -- <b>the memo prefers a budget item, it never selects
 * one</b> -- applies here as everywhere.  One suggestion in five is wrong, and this one is being
 * weighed against an answer the user gave deliberately.  What tips it toward being worth asking is
 * that the user gave that answer <em>without</em> the fact now on the table, which is exactly the
 * situation a question is for.  The prompt names the evidence and the count so the user can judge it
 * at a glance.
 *
 * <p>It also protects the history the rest of the feature reads.  Without this, a provisional-first
 * transfer permanently records a categorization made in ignorance of its own memo, and that row then
 * becomes a prior that teaches the next lookup the wrong answer.
 *
 * @see MemoBudgetItemHistory
 * @see TransactionController#recategorizeTransaction(Transaction)
 */
public class LateMemoController {

    /*
     * Fields:
     */
    private final SessionController sessionController;
    private final ViewInt view;
    private final Budget budget;


    /**
     * Constructor.
     *
     * @param sessionController the session controller, for the budget, the view and the
     *                          recategorization flow this delegates to
     */
    public LateMemoController(SessionController sessionController) {
        this.sessionController = sessionController;
        this.view = sessionController.getView();
        this.budget = sessionController.getBudget();
    }


    /*
     * The offer:
     */
    /**
     * Ask about a memo that arrived after the transaction was categorized, and re-categorize it if
     * the user wants that.
     *
     * <p>Silent unless the memo actually disagrees with what is already assigned -- see the class
     * comment for the full list of conditions.  The user's answer is the decision:  "no" leaves the
     * splits exactly as they were found, and so does cancelling or skipping out of the
     * recategorization itself.
     *
     * @param clearedTransaction the cleared transaction, which is the copy that carries the memo
     * @param splits             the splits it already has, taken from the provisional transaction
     * @return true if the transaction was re-categorized, so the caller can reload its splits
     * @throws Exception if the recategorization fails for a reason other than the user declining
     */
    public boolean confirmLateMemo(Transaction clearedTransaction, List<TransactionSplit> splits) throws Exception {

        MemoBudgetItemHistory.Suggestion suggestion = lookUpMemoSuggestion(clearedTransaction);
        BudgetItem assigned = disagreeingItem(splits, suggestion);
        if (assigned == null) {
            return false;
        }

        view.sayH3(describeDisagreement(suggestion, assigned));
        if (!view.getYesOrNo("Change it?")) {
            return false;
        }

        try {
            new TransactionController(sessionController).recategorizeTransaction(clearedTransaction);
            return true;

        } catch (CancelException | SkipException e) {
            // Backing out of the recategorization is an answer too, and it is the same answer as
            // "no".  recategorizeTransaction has already rolled its own changes back.
            view.say("Left as it was:  " + assigned.getDisplayString() + ".");
            return false;
        }
    }

    /**
     * The budget item currently assigned, when the memo disagrees with it and the disagreement is
     * worth a question.
     *
     * <p>Kept separate from the asking so that the conditions in the class comment can be exercised
     * without a view or a database.
     *
     * @param splits     the splits the transaction already has
     * @param suggestion what the memo history suggests, or null when there is no memo or no history
     * @return the item the transaction is assigned to, or null when there is nothing to ask about
     */
    static BudgetItem disagreeingItem(List<TransactionSplit> splits, MemoBudgetItemHistory.Suggestion suggestion) {

        if (suggestion == null || suggestion.budgetItem() == null || splits == null || splits.size() != 1) {
            return null;
        }

        TransactionSplit split = splits.get(0);
        if (split == null) {
            return null;
        }

        UUID suggested = suggestion.budgetItem().getId();
        if (suggested == null || suggested.equals(split.getIdBudgetItem())) {
            return null;
        }

        try {
            // A question that cannot say what it is proposing to replace is not worth asking.
            return split.getBudgetItem();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The question, phrased for the user.
     *
     * <p>Naming the evidence and the count is what makes a suggestion that is wrong one time in five
     * safe to put in front of an answer the user gave on purpose:  they can see what it rests on.
     *
     * @param suggestion what the memo history suggests
     * @param assigned   the budget item the transaction is currently split to
     * @return for example:  This transfer cleared with the memo "RENT", which you have assigned to
     *         Room rental 42 times.  It is currently split to Groceries.
     */
    static String describeDisagreement(MemoBudgetItemHistory.Suggestion suggestion, BudgetItem assigned) {

        return "This transfer cleared with the memo \"" + suggestion.memo() + "\", which you have assigned to " +
                suggestion.budgetItem().getPayee() + " " + suggestion.priors() +
                (suggestion.priors() == 1 ? " time." : " times.") +
                "  It is currently split to " + assigned.getPayee() + ".";
    }

    /**
     * Ask the history what this transaction's memo has meant before.
     *
     * <p>A failure here must never cost the user an import.  The transaction is already categorized
     * and the import has already done its work; a broken lookup simply means no question, which is
     * the behaviour that shipped before this phase.
     *
     * @param clearedTransaction the transaction being reconciled with its provisional copy
     * @return the suggestion, or null if there is no memo, no history, or the lookup failed
     */
    private MemoBudgetItemHistory.Suggestion lookUpMemoSuggestion(Transaction clearedTransaction) {

        if (clearedTransaction == null) {
            return null;
        }

        // The common case, answered without a query:  most transactions carry no memo at all.
        String memo = clearedTransaction.getUserDescription();
        if (memo == null || memo.isBlank()) {
            return null;
        }

        try {
            return new MemoBudgetItemHistory().lookup(clearedTransaction, budget);
        } catch (Exception e) {
            return null;
        }
    }
}
