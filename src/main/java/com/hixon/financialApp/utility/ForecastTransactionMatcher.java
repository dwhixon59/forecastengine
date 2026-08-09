package com.hixon.financialApp.utility;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Transaction;

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

        return findMatchingForecastTransaction(
                transaction.getDate(),
                transaction.getAmount(),
                forecast,
                possibleMerchants,
                daysBefore,
                daysAfter);
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

        // Score each remaining forecast transaction
        ForecastTransaction bestMatch = null;
        double bestScore = 0.0;

        for (ForecastTransaction ft : candidateForecastTransactions) {
            double score = calculateMatchScore(date, amount, ft, possibleMerchants);

            if (score > bestScore) {
                bestScore = score;
                bestMatch = ft;
            }
        }

        // Only return a match if confidence is at least 70%
        if (bestScore >= 70.0) {
            return bestMatch;
        }

        return null;
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
