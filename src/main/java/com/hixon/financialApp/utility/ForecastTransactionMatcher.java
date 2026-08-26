package com.hixon.financialApp.utility;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.view.base.ViewInt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for matching cleared transactions with forecast transactions.
 * Provides scoring algorithms and matching logic to determine how well a transaction matches a planned forecast transaction.
 */
public class ForecastTransactionMatcher {

    /**
     * Maximum fractional difference between a cleared transaction amount and a forecast
     * transaction's remaining amount that is still considered close enough to auto-assign
     * WITHOUT asking the user. If a candidate forecast transaction matches on merchant/date
     * but its amount differs from the transaction by more than this fraction, the match must
     * be confirmed by the user rather than silently auto-assigned.
     *
     * <p>This is the single, shared definition of "amounts are too different to auto-assign"
     * used by every matching code path (and therefore by every financial institution), so we
     * do not have to re-add this safeguard each time a new institution is introduced.
     */
    public static final double AUTO_MATCH_AMOUNT_TOLERANCE = 0.05; // 5%

    /**
     * What the bank reference numbers alone say about a candidate, before any scoring.
     *
     * <p>The rule is:  <b>the reference confirms a match, it never gates one.</b>  Coverage is about
     * 43% and outside our control, so {@link #UNDECIDED} -- "the existing score decides, exactly as
     * it does today" -- has to be the answer whenever either side lacks a reference, which is the
     * majority of the time.
     */
    public enum ReferenceVerdict {
        /** Both sides carry the same reference:  an exact identity.  Take it, skipping the score. */
        CERTAIN,
        /** Both carry references and they differ:  not the same money, whatever the score says. */
        RULED_OUT,
        /** At least one side carries none:  no information, so nothing changes. */
        UNDECIDED
    }

    /**
     * Compare the bank reference of a transaction with that of a candidate forecast transaction.
     *
     * <p>Kept separate from the matching loop, and free of any database access, so the rule can be
     * exercised directly:  the case that must never regress is the third one, which is 57% of
     * transfers.
     *
     * @param transactionReference the transaction's reference, or null if it has none
     * @param candidateReference   the candidate's reference, or null if it has none
     */
    public static ReferenceVerdict compareReferences(String transactionReference, String candidateReference) {
        if (BankReferenceNumber.areSameMovement(transactionReference, candidateReference)) {
            return ReferenceVerdict.CERTAIN;
        }
        if (BankReferenceNumber.areDifferentMovements(transactionReference, candidateReference)) {
            return ReferenceVerdict.RULED_OUT;
        }
        return ReferenceVerdict.UNDECIDED;
    }

    /**
     * Whether an unpaired transfer counterpart may be scored against this transaction at all.
     *
     * <p><b>An unpaired counterpart is admitted only on an identity, never on proximity.</b>  Its
     * budget item is a placeholder rather than an answer, so it can never actually be assigned:
     * every caller checks {@link ForecastTransaction#isTransferPairingUnknown()} and falls through
     * to the ordinary import questions whenever one wins.  Letting it also compete on date/amount
     * proximity therefore cannot produce a match -- it can only produce noise, and the noise is
     * indiscriminate, because a counterpart is deliberately exempt from the merchant filter and so
     * is a candidate for <em>every</em> transaction in the date window.  Observed in a real import:
     * a $1.00 placeholder scoring 40 against a $2.00 savings transfer and 32 against a $35.00 credit
     * card payment, in the same session.
     *
     * <p>The identity is the bank reference where there is one -- handled before this, and stronger
     * -- or, for the majority of transfers that carry none, the exact amount the counterpart was
     * created with.  The two sides of one movement of money are the same size; anything else is a
     * different movement.
     *
     * <p>Every candidate that is not an unpaired counterpart is admitted unchanged.  Nothing about
     * ordinary forecast matching may shift because of this rule.
     *
     * @param amount    the transaction amount being matched
     * @param candidate the candidate forecast transaction
     * @return true if the candidate may be scored
     */
    public static boolean admitsUnpairedCounterpart(double amount, ForecastTransaction candidate) {
        if (candidate == null || !candidate.isTransferPairingUnknown()) {
            return true;
        }
        return Utility.isEqualCurrency(amount, candidate.getRemainingAmount());
    }

    /**
     * Determines whether a cleared transaction amount is close enough to a forecast
     * transaction's remaining amount to be auto-assigned without user confirmation.
     *
     * <p>Amounts are compared by absolute value (sign compatibility is validated separately
     * during scoring) as a fraction of the larger magnitude. Two amounts are considered a
     * confident amount match when they differ by no more than {@link #AUTO_MATCH_AMOUNT_TOLERANCE}.
     *
     * @param transactionAmount the cleared transaction amount (sign ignored)
     * @param forecastAmount    the forecast transaction's remaining amount (sign ignored)
     * @return true if the amounts are within tolerance (safe to auto-assign), false if they
     *         differ enough that the user should be asked before assigning
     */
    public static boolean isAmountWithinAutoMatchTolerance(double transactionAmount, double forecastAmount) {
        double txn = Math.abs(transactionAmount);
        double forecast = Math.abs(forecastAmount);
        double larger = Math.max(txn, forecast);

        // If both amounts are effectively zero there is nothing to distinguish - treat as a match.
        if (larger == 0.0) {
            return true;
        }

        double percentDiff = Math.abs(txn - forecast) / larger;
        return percentDiff <= AUTO_MATCH_AMOUNT_TOLERANCE;
    }

    /**
     * Attempts to find a matching forecast transaction for a cleared transaction based on date and amount proximity.
     * This method provides automatic matching for planned transactions without requiring merchant identification
     * or budget item selection from the user.
     *
     * <p>The matching process:
     * <ol>
     *   <li>Retrieves forecast transactions within the date window (daysBefore to daysAfter)</li>
     *   <li>Filters by possible merchants if provided (null = no filtering)</li>
     *   <li>Scores remaining forecast transactions based on date and amount similarity</li>
     *   <li>Returns the best match if confidence is high enough (70%+), otherwise null</li>
     * </ol>
     *
     * @param transaction The cleared transaction to match
     * @param forecast The forecast to search in
     * @param possibleMerchants List of possible merchants (null = no merchant filtering,
     *                          empty = no merchants match, 1+ = filter to these merchants)
     * @param daysBefore Number of days before transaction date to search
     * @param daysAfter Number of days after transaction date to search
     * @return The best matching ForecastTransaction if found with sufficient confidence (70%+), null otherwise
     * @throws Exception if database or other errors occur
     */
    public static ForecastTransaction findMatchingForecastTransaction(
            Transaction transaction,
            Forecast forecast,
            List<Merchant> possibleMerchants,
            int daysBefore,
            int daysAfter) throws Exception {

        // ---- TEMP INSTRUMENTATION (Phase 2.5) — remove when done tuning ----
        // Show the raw input payee (the line from the import file) and the parsed payee so the
        // matcher output can be judged against the actual source text.
        ViewInt payeeDebugView = Utility.getView();
        payeeDebugView.say("");
        payeeDebugView.say("[Phase2.5] raw payee    : " + transaction.getPayee());
        payeeDebugView.say("[Phase2.5] parsed payee : " + transaction.getMerchantPayee());
        // ---- END TEMP INSTRUMENTATION ----

        // The bank's own reference for a transfer, when it issued one.  It lives only inside the
        // payee varchar, so reading it means parsing; it is null for the majority of transactions
        // and everything below treats that as "no information", never as a reason to reject.
        return findMatchingForecastTransaction(
                transaction.getDate(),
                transaction.getAmount(),
                forecast,
                possibleMerchants,
                daysBefore,
                daysAfter,
                BankReferenceNumber.extract(transaction.getPayee()));
    }

    /**
     * Attempts to find a matching forecast transaction based on date and amount proximity.
     * This method provides automatic matching for planned transactions without requiring merchant identification
     * or budget item selection from the user.
     *
     * <p>The matching process:
     * <ol>
     *   <li>Retrieves forecast transactions within the date window (daysBefore to daysAfter)</li>
     *   <li>Filters by possible merchants if provided (null = no filtering)</li>
     *   <li>Scores remaining forecast transactions based on date and amount similarity</li>
     *   <li>Returns the best match if confidence is high enough (70%+), otherwise null</li>
     * </ol>
     *
     * @param date The transaction date to match
     * @param amount The transaction amount to match
     * @param forecast The forecast to search in
     * @param possibleMerchants List of possible merchants (null = no merchant filtering,
     *                          empty = no merchants match, 1+ = filter to these merchants)
     * @param daysBefore Number of days before transaction date to search
     * @param daysAfter Number of days after transaction date to search
     * @return The best matching ForecastTransaction if found with sufficient confidence (70%+), null otherwise
     * @throws Exception if database or other errors occur
     */
    public static ForecastTransaction findMatchingForecastTransaction(
            Calendar date,
            double amount,
            Forecast forecast,
            List<Merchant> possibleMerchants,
            int daysBefore,
            int daysAfter) throws Exception {

        return findMatchingForecastTransaction(date, amount, forecast, possibleMerchants,
                daysBefore, daysAfter, null);
    }

    /**
     * As {@link #findMatchingForecastTransaction(Calendar, double, Forecast, List, int, int)}, with
     * the bank's own reference number for the transaction when it has one.
     *
     * <p><b>The reference confirms a match.  It never gates one.</b>  Only about 43% of transfers
     * carry a reference and that is outside our control, so no code path may require one to be
     * present and nothing may be suppressed for want of one -- anything conditional on presence
     * would silently do nothing for the majority.  Concretely:
     *
     * <table>
     *   <tr><th>Situation</th><th>Behaviour</th></tr>
     *   <tr><td>Both sides carry the same reference</td>
     *       <td>The match is certain.  Skip the score threshold and take it.</td></tr>
     *   <tr><td>Both carry references, but different ones</td>
     *       <td>Not the same movement.  Rule that candidate out regardless of its score.</td></tr>
     *   <tr><td>Either side carries none</td>
     *       <td>Unchanged -- the existing score decides.</td></tr>
     * </table>
     *
     * <p>The second row is the one that earns its keep:  it lets the matcher reject a plausible but
     * wrong candidate for the same money outright, which scoring alone cannot do, and so avoids
     * stranding the right one.
     *
     * @param transactionReference the bank reference for this transaction, or null if it has none
     */
    public static ForecastTransaction findMatchingForecastTransaction(
            Calendar date,
            double amount,
            Forecast forecast,
            List<Merchant> possibleMerchants,
            int daysBefore,
            int daysAfter,
            String transactionReference) throws Exception {

        // If no forecast is available, we cannot match
        if (forecast == null) {
            return null;
        }

        // Calculate the date window
        Calendar startDate = (Calendar) date.clone();
        startDate.add(Calendar.DATE, -daysBefore);

        Calendar endDate = (Calendar) date.clone();
        endDate.add(Calendar.DATE, daysAfter);

        // Get all forecast transactions in the date window for this budget
        List<ForecastTransaction> candidateForecastTransactions =
                ForecastTransactionUtilities.getForecastTransactionsInDateRange(forecast.getId(), startDate, endDate);

        // If no candidates, return null
        if (candidateForecastTransactions.isEmpty()) {
            return null;
        }

        // Filter out forecast transactions that have already been fully reconciled (remaining amount = 0)
        List<ForecastTransaction> unreconciledTransactions = new ArrayList<>();
        for (ForecastTransaction ft : candidateForecastTransactions) {
            if (ft.getRemainingAmount() != 0.0) {
                unreconciledTransactions.add(ft);
            }
        }
        candidateForecastTransactions = unreconciledTransactions;

        // If no unreconciled candidates, return null
        if (candidateForecastTransactions.isEmpty()) {
            return null;
        }

        // Filter by merchant if we have a merchant list (but not if it's null - null means "no info")
        if (possibleMerchants != null && !possibleMerchants.isEmpty()) {
            List<ForecastTransaction> filteredTransactions = new ArrayList<>();

            for (ForecastTransaction ft : candidateForecastTransactions) {

                // A transfer counterpart is exempt from merchant filtering: it was created from a
                // specific transaction in another register, so its identity does not depend on a
                // merchant at all. A transfer payee ("Transfer from Bill Pay Danni") rarely maps to
                // the merchants assigned to the far side's budget item, and filtering it out here
                // would leave the far import asking the questions this whole feature removes.
                if (ft.isTransferCounterpart()) {
                    filteredTransactions.add(ft);
                    continue;
                }

                // Get the budget item for this forecast transaction
                UUID idBudgetItem = ft.getForecastItem().getIdBudgetItem();
                BudgetItem budgetItem = BudgetItem.getById(idBudgetItem);

                // Get merchants assigned to this budget item
                List<BudgetItemMerchant> budgetItemMerchants =
                        BudgetItemMerchant.getAssignedMerchantsForBudgetItem(budgetItem);

                // Check if any of the budget item's merchants match our possible merchants
                boolean merchantMatches = false;
                if (budgetItemMerchants.isEmpty()) {
                    // No merchants assigned - keep this forecast transaction as a candidate
                    merchantMatches = true;
                } else {
                    for (BudgetItemMerchant bim : budgetItemMerchants) {
                        for (Merchant possibleMerchant : possibleMerchants) {
                            if (bim.getIdMerchant().equals(possibleMerchant.getId())) {
                                merchantMatches = true;
                                break;
                            }
                        }
                        if (merchantMatches) break;
                    }
                }

                if (merchantMatches) {
                    filteredTransactions.add(ft);
                }
            }

            candidateForecastTransactions = filteredTransactions;
        }

        // If no candidates remain after filtering, return null
        if (candidateForecastTransactions.isEmpty()) {
            return null;
        }

        // ============================ TEMP INSTRUMENTATION (Phase 2.5) ============================
        // Prints the possible merchants and every scored forecast candidate so the matching
        // algorithm can be judged during tuning. REMOVE this block (and the two smaller TEMP
        // blocks below, plus the ViewInt import) when done.
        ViewInt debugView = Utility.getView();
        debugView.say("[Phase2.5] Matching cleared txn  date=" + Utility.calendarDateToStringDate(date)
                + "  amount=" + Utility.formatDollarAmount(amount));
        if (possibleMerchants == null) {
            debugView.say("[Phase2.5]   possibleMerchants: null (no merchant filtering)");
        } else if (possibleMerchants.isEmpty()) {
            debugView.say("[Phase2.5]   possibleMerchants: (none)");
        } else {
            StringBuilder merchantNames = new StringBuilder();
            for (Merchant m : possibleMerchants) {
                if (merchantNames.length() > 0) merchantNames.append(", ");
                merchantNames.append(m.getName());
            }
            debugView.say("[Phase2.5]   possibleMerchants (" + possibleMerchants.size() + "): " + merchantNames);
        }
        debugView.say("[Phase2.5]   considering " + candidateForecastTransactions.size()
                + " forecast transaction(s) [score / threshold 70]:");
        // ========================== END TEMP INSTRUMENTATION ==========================

        // Score each remaining forecast transaction
        ForecastTransaction bestMatch = null;
        double bestScore = 0.0;

        for (ForecastTransaction ft : candidateForecastTransactions) {

            String candidateReference = ft.getSourceReference();
            ReferenceVerdict verdict = compareReferences(transactionReference, candidateReference);

            // Two different bank references cannot be the same movement of money, however well the
            // candidate scores.  This is the one judgement scoring cannot make.
            if (verdict == ReferenceVerdict.RULED_OUT) {
                debugView.say("[Phase2.5]     ruled out (bank reference " + candidateReference +
                        " != " + transactionReference + ")  " + ft.toStringConcise());
                continue;
            }

            // The same reference on both sides is an exact identity, so take it without scoring.
            if (verdict == ReferenceVerdict.CERTAIN) {
                debugView.say("[Phase2.5]   result: CERTAIN (bank reference " + candidateReference + ") -> " +
                        ft.toStringConcise());
                debugView.say("");
                return ft;
            }

            // Reaching here means the verdict was UNDECIDED, so an unpaired counterpart has only its
            // amount left to identify it with.
            if (!admitsUnpairedCounterpart(amount, ft)) {
                debugView.say("[Phase2.5]     not this movement (unpaired counterpart for "
                        + Utility.formatDollarAmount(ft.getRemainingAmount()) + ")  " + ft.toStringConcise());
                continue;
            }

            double score = calculateMatchScore(date, amount, ft, possibleMerchants);

            // ---- TEMP INSTRUMENTATION (Phase 2.5) ----
            debugView.say(String.format("[Phase2.5]     %6.2f  %s", score, ft.toStringConcise()));
            // ---- END TEMP INSTRUMENTATION ----

            if (score > bestScore) {
                bestScore = score;
                bestMatch = ft;
            }
        }

        // ---- TEMP INSTRUMENTATION (Phase 2.5) ----
        if (bestMatch == null) {
            debugView.say("[Phase2.5]   result: no candidate scored above 0");
        } else if (bestScore >= 70.0) {
            debugView.say(String.format("[Phase2.5]   result: AUTO-MATCH (best=%.2f) -> %s",
                    bestScore, bestMatch.toStringConcise()));
        } else {
            debugView.say(String.format("[Phase2.5]   result: NO MATCH (best=%.2f below threshold) -> %s",
                    bestScore, bestMatch.toStringConcise()));
        }
        debugView.say("");
        // ---- END TEMP INSTRUMENTATION ----

        // Only return a match if confidence is at least 70%
        if (bestScore < 70.0) {
            return null;
        }

        // Merchant validation gate: a high date/amount score alone isn't proof this is the same
        // recurring item - it can also mean an unrelated (possibly brand-new) charge coincidentally
        // landed on a similar date/amount, e.g. a new $20 Anthropic subscription lining up with an
        // existing $19.99 LinkedIn budget item. If the winning candidate's budget item has merchants
        // assigned and none of them match this transaction's identified merchant(s), don't silently
        // auto-assign - ask the user whether this is really another merchant for that budget item.
        // A transfer counterpart is exempt:  its identity comes from the source transaction it was
        // created from, not from a merchant.  A transfer's payee ("Transfer from Bill Pay Danni")
        // rarely maps to the merchants assigned to the far side's budget item, so applying the gate
        // here would ask about every transfer -- which is the question this whole feature exists to
        // stop asking.
        if (possibleMerchants != null && !bestMatch.isTransferCounterpart()) {
            UUID idBudgetItem = bestMatch.getForecastItem().getIdBudgetItem();
            BudgetItem budgetItem = BudgetItem.getById(idBudgetItem);
            List<BudgetItemMerchant> assignedMerchants =
                    BudgetItemMerchant.getAssignedMerchantsForBudgetItem(budgetItem);

            if (!assignedMerchants.isEmpty()) {
                boolean merchantMatches = false;
                for (BudgetItemMerchant bim : assignedMerchants) {
                    for (Merchant possibleMerchant : possibleMerchants) {
                        if (bim.getIdMerchant().equals(possibleMerchant.getId())) {
                            merchantMatches = true;
                            break;
                        }
                    }
                    if (merchantMatches) break;
                }

                if (!merchantMatches) {
                    String txnMerchantDescription = possibleMerchants.isEmpty()
                            ? "This transaction's merchant"
                            : "This transaction's merchant ('" + possibleMerchants.get(0).getName() + "')";

                    boolean confirmed = debugView.getYesOrNo(txnMerchantDescription
                            + " does not match any merchant assigned to budget item '" + budgetItem.getPayee()
                            + "', though it otherwise matches on date/amount. Is this transaction another "
                            + "merchant for '" + budgetItem.getPayee() + "'?");

                    if (!confirmed) {
                        debugView.say("[Phase2.5]   merchant mismatch declined by user -> no match");
                        return null;
                    }
                    debugView.say("[Phase2.5]   merchant mismatch confirmed by user -> proceeding with match");
                }
            }
        }

        return bestMatch;
    }

    /**
     * Calculates a match score (0-100) between a cleared transaction and a forecast transaction.
     * Higher scores indicate better matches. Scores of 70+ are considered confident matches.
     *
     * <p>Scoring breakdown:
     * <ul>
     *   <li><b>Date proximity (0-40 points):</b> Closer dates score higher, -8 points per business day difference
     *       (uses business days to account for weekends and holidays)</li>
     *   <li><b>Amount similarity (0-40 points):</b>
     *     <ul>
     *       <li>Exact match or within 1%: 40 points</li>
     *       <li>Within 5%: 20-40 points (likely same transaction)</li>
     *       <li>Within 25%: 0-20 points (could include tip or variance)</li>
     *     </ul>
     *   </li>
     *   <li><b>Merchant match (0-20 points):</b> Bonus if merchant matches budget item's assigned merchants</li>
     * </ul>
     *
     * @param transaction The cleared transaction to score
     * @param forecastTransaction The forecast transaction to score against
     * @param possibleMerchants List of possible merchants from the transaction payee (can be null)
     * @return Score from 0-100, where higher is better
     * @throws Exception if an error occurs accessing budget item or merchant data
     */
    public static double calculateMatchScore(
            Transaction transaction,
            ForecastTransaction forecastTransaction,
            List<Merchant> possibleMerchants) throws Exception {

        return calculateMatchScore(
                transaction.getDate(),
                transaction.getAmount(),
                forecastTransaction,
                possibleMerchants);
    }

    /**
     * Calculates a match score (0-100) between a transaction date/amount and a forecast transaction.
     * Higher scores indicate better matches. Scores of 70+ are considered confident matches.
     *
     * <p>Scoring breakdown:
     * <ul>
     *   <li><b>Date proximity (0-40 points):</b> Closer dates score higher, -8 points per business day difference
     *       (uses business days to account for weekends and holidays)</li>
     *   <li><b>Amount similarity (0-40 points):</b>
     *     <ul>
     *       <li>Exact match or within 1%: 40 points</li>
     *       <li>Within 5%: 20-40 points (likely same transaction)</li>
     *       <li>Within 25%: 0-20 points (could include tip or variance)</li>
     *     </ul>
     *   </li>
     *   <li><b>Merchant match (0-20 points):</b> Bonus if merchant matches budget item's assigned merchants</li>
     * </ul>
     *
     * @param date The transaction date to score
     * @param amount The transaction amount to score
     * @param forecastTransaction The forecast transaction to score against
     * @param possibleMerchants List of possible merchants from the transaction payee (can be null)
     * @return Score from 0-100, where higher is better
     * @throws Exception if an error occurs accessing budget item or merchant data
     */
    public static double calculateMatchScore(
            Calendar date,
            double amount,
            ForecastTransaction forecastTransaction,
            List<Merchant> possibleMerchants) throws Exception {

        // **CRITICAL: Check sign compatibility FIRST before any scoring**
        // A positive transaction (deposit/credit) should NEVER match a negative forecast (expense/debit)
        // and vice versa. This prevents catastrophic mismatches like $500 deposit matching -$125 expense.
        double transactionAmount = amount;
        double forecastAmount = forecastTransaction.getRemainingAmount();

        if (Math.signum(transactionAmount) != Math.signum(forecastAmount)) {
            return 0.0;  // Incompatible signs - absolutely no match
        }

        double score = 0.0;

        // 1. Date Proximity Score (0-40 points)
        // Use business days instead of calendar days for more accurate matching
        // (e.g., Friday to Monday = 1 business day, not 3 calendar days)
        Calendar transactionDate = date;
        Calendar forecastDate = forecastTransaction.getPlannedDate();
        int businessDaysDiff;

        if (transactionDate.compareTo(forecastDate) > 0) {
            // Transaction is after forecast
            businessDaysDiff = Utility.businessDaysBeteween(transactionDate, forecastDate);
        } else {
            // Transaction is before forecast
            businessDaysDiff = Utility.businessDaysBeteween(forecastDate, transactionDate);
        }

        score += Math.max(0, 40 - (businessDaysDiff * 8)); // -8 points per business day difference

        // 2. Amount Similarity Score (0-40 points)
        // Use absolute values for similarity scoring (sign already checked above)
        transactionAmount = Math.abs(transactionAmount);
        forecastAmount = Math.abs(forecastAmount);
        double amountDiff = Math.abs(transactionAmount - forecastAmount);
        double percentDiff = amountDiff / Math.max(transactionAmount, forecastAmount);

        // Perfect match or very close
        if (percentDiff <= 0.01) {
            score += 40;
        }
        // Within 5% (likely same transaction)
        else if (percentDiff <= 0.05) {
            score += 40 - (percentDiff * 400); // Gradually decrease from 40 to 20
        }
        // Within 25% (could be same with tip)
        else if (percentDiff <= 0.25) {
            score += 20 - (percentDiff * 80); // Gradually decrease from 20 to 0
        }
        // Otherwise 0 points

        // 3. Merchant Match Score (0-20 points)
        if (possibleMerchants != null && !possibleMerchants.isEmpty()) {
            UUID idBudgetItem = forecastTransaction.getForecastItem().getIdBudgetItem();
            BudgetItem budgetItem = BudgetItem.getById(idBudgetItem);
            List<BudgetItemMerchant> budgetItemMerchants =
                    BudgetItemMerchant.getAssignedMerchantsForBudgetItem(budgetItem);

            // Award the merchant bonus at most once, even if several of the budget item's
            // merchants happen to match. Otherwise a transaction with a wildly different amount
            // (0 amount points) could still cross the 70-point auto-match threshold on the
            // strength of a repeated merchant bonus.
            boolean merchantMatched = false;
            for (BudgetItemMerchant bim : budgetItemMerchants) {
                for (Merchant possibleMerchant : possibleMerchants) {
                    if (bim.getIdMerchant().equals(possibleMerchant.getId())) {
                        merchantMatched = true;
                        break;
                    }
                }
                if (merchantMatched) {
                    break;
                }
            }
            if (merchantMatched) {
                score += 20;
            }
        }

        return score;
    }
}
