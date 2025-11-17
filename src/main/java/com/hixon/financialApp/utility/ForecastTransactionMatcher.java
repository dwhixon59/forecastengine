package com.hixon.financialApp.utility;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Transaction;

import java.util.List;
import java.util.UUID;

/**
 * Utility class for matching cleared transactions with forecast transactions.
 * Provides scoring algorithms to determine how well a transaction matches a planned forecast transaction.
 */
public class ForecastTransactionMatcher {

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

