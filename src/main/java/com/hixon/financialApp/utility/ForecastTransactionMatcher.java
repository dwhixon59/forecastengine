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

        // Calculate the date window
        Calendar startDate = (Calendar) transaction.getDate().clone();
        startDate.add(Calendar.DATE, -daysBefore);

        Calendar endDate = (Calendar) transaction.getDate().clone();
        endDate.add(Calendar.DATE, daysAfter);

        // Get all forecast transactions in the date window for this budget
        List<ForecastTransaction> candidateForecastTransactions =
                ForecastTransactionUtilities.getForecastTransactionsInDateRange(forecast.getId(), startDate, endDate);

        // If no candidates, return null
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
            double score = calculateMatchScore(transaction, ft, possibleMerchants);

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
     *   <li><b>Date proximity (0-40 points):</b> Closer dates score higher, -8 points per day difference</li>
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

        double score = 0.0;

        // 1. Date Proximity Score (0-40 points)
        long daysDiff = Math.abs(
                (transaction.getDate().getTimeInMillis() - forecastTransaction.getPlannedDate().getTimeInMillis())
                        / (1000 * 60 * 60 * 24)
        );
        score += Math.max(0, 40 - (daysDiff * 8)); // -8 points per day difference

        // 2. Amount Similarity Score (0-40 points)
        double transactionAmount = Math.abs(transaction.getAmount());
        double forecastAmount = Math.abs(forecastTransaction.getForecastItem().getAmount());
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

            for (BudgetItemMerchant bim : budgetItemMerchants) {
                for (Merchant possibleMerchant : possibleMerchants) {
                    if (bim.getIdMerchant().equals(possibleMerchant.getId())) {
                        score += 20;
                        break;
                    }
                }
            }
        }

        return score;
    }
}

