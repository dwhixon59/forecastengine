package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.*;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;
import org.jetbrains.annotations.NotNull;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class BudgetItemMerchantController {

    private final Budget budget;
    private final ViewInt view;
    private final NotificationServiceInt notificationService;
    private static final double AMOUNT_MATCH_THRESHOLD = 0.05; // 5% threshold

    public BudgetItemMerchantController(Budget budget, ViewInt view, NotificationServiceInt notificationService) {
        this.budget = budget;
        this.view = view;
        this.notificationService = notificationService;
    }

    // TODO:  Add in the use of the memo matched against the merchant (e.g. merchant is COSTCO and their exists a budget
    // item with a memo of COSTCO)
    /**
     * Sorts a list of BudgetItemMerchant entries based on how likely the budget items are the specific budget item that
     * should be associated with the specified transaction.  This considers factors such as:
     * - Transaction amount is within 5% of any fixed amounts specified
     * - Transaction date falls within the budget item's valid period
     * - Budget item is currently active
     * - How frequently the budget item has been associated with the merchant in the past compared with other budget items.
     * - When the budget item is used, how frequently the budget item is associated with the merchant for the transaction.
     *
     * @param budgetItemMerchants List of BudgetItemMerchant entries to filter
     * @param transaction         The transaction to check against
     * @return List of relevance scores for each budget item merchant
     */
    public List<Double> scoreAndSortListForTransaction(
            List<BudgetItemMerchant> budgetItemMerchants,
            Transaction transaction)
    {
        if (budgetItemMerchants == null || budgetItemMerchants.isEmpty() || transaction == null) {
            return new ArrayList<>();
        }

        // Create a ranked list of budget items with scores based on their relevance to the transaction:
        @NotNull List<Map.Entry<BudgetItemMerchant, Double>> budgetItemsWithRelevancy = budgetItemMerchants.stream()
                .map(bim -> {
                    try {
                        return Map.entry(bim, scoreTransactionRelevance(bim, transaction));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
//                .filter(entry -> entry.getValue() > 0)
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .toList();

        // Recreate the passed in list of budget items based from the ranked list:
        budgetItemMerchants.clear();
        for (Map.Entry<BudgetItemMerchant, Double> entry : budgetItemsWithRelevancy) {
            budgetItemMerchants.add(entry.getKey());
        }

        // Return a list of relevance scores for each budget item merchant:
        return budgetItemsWithRelevancy.stream()
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * Scores the budget item in the given BudgetItemMerchant object based on its relevance to the provided transaction.
     * The score is calculated based on: 
     * 1. Past associations (0-40 points)
     * 2. Ratio of past associations to this particular merchant (0-10 points)
     * 2. Amount similarity (0-30 points)
     * 3. Date proximity (0-30 points)
     *
     * If there is a budget item that scores well on amount similarity, and date proximity, it is probably the most
     * likely candidate for the transaction.  If not, then the budget item with the most past associations is probably
     * the most likely.  If the past associations ratio is low, then the budget item is only rarely associated with this
     * merchant and is probably not the best candidate for the transaction.  Finally, the memo from the transaction can
     * be used to help identify the budget item.  If the memo contains a keyword associated with a budget item, then
     * that budget item is probably the best candidate for the transaction.
     *
     * @param budgetItemMerchant The BudgetItemMerchant object containing the budget item and its associated details.
     * @param transaction The transaction to evaluate relevance against the budget item.
     * @return A double representing the computed relevance score between 0 and 100.
     */
    // Score the budget item in the budgetItemMerchant based on its relevance to the transaction. 
    private double scoreTransactionRelevance(BudgetItemMerchant budgetItemMerchant, Transaction transaction)
            throws Exception {

        // If the transaction date is before the budget item's start date, it's not relevant:
        if (budgetItemMerchant.getBudgetItem().getStartDate() != null &&
                budgetItemMerchant.getBudgetItem().getStartDate().after(transaction.getDate())) {
            return 0;
        }

        // Calculate relevance score (0-100) based on:
        double score = 0;

        // 1. What percentage of the time does a transaction from this merchant end up being associated with this budget
        //    item (0-30 points)?
        int itemPastAssociationsToMerchant =
                TransactionSplitUtilities.getItemPastAssociationsToMerchantCount(
                    budgetItemMerchant.getIdBudgetItem(),
                    budgetItemMerchant.getIdMerchant()
                );
        int totalPastAssociationsToMerchant =
                TransactionSplitUtilities.getTotalPastAssociationsToMerchant(budgetItemMerchant.getIdMerchant());
        if (totalPastAssociationsToMerchant > 0) {
            score += ((double) itemPastAssociationsToMerchant / totalPastAssociationsToMerchant) * 30;
//            System.out.println("\n\tRelative frequency of past associations of merchant " +
//                    Merchant.getById(budgetItemMerchant.getIdMerchant()).getName() + "with budget item " +
//                    BudgetItem.getById(budgetItemMerchant.getIdBudgetItem()).getPayee() + " is " + score);
        }

        // 2. What percentage of the time when this budget item is used, is it associated with merchant (0-10 points)?:
        int totalItemPastAssociationsCount =
                TransactionSplitUtilities.getTotalItemPastAssociationsCount(budgetItemMerchant.getIdBudgetItem());
        if (totalItemPastAssociationsCount > 0) {
            double associationRatio = (double) itemPastAssociationsToMerchant / totalItemPastAssociationsCount;
            score += 10 * associationRatio;
//            System.out.println("\n\tRelative frequency of past associations of budget item " +
//                    BudgetItem.getById(budgetItemMerchant.getIdBudgetItem()).getPayee() + " to the merchant " +
//                    Merchant.getById(budgetItemMerchant.getIdMerchant()).getName() + " out of all association is " + score);
        }

        // 3. Amount similarity (0-30 points)
        double amount = (budgetItemMerchant.getAmount() > 0) ? budgetItemMerchant.getAmount() :
                Math.abs(budgetItemMerchant.getBudgetItem().getAmount());
        if (amount > 0) {
            double amountDiff = Math.abs(1 - (Math.abs(transaction.getAmount() / amount)));
            if (amountDiff <= AMOUNT_MATCH_THRESHOLD) {
                score += 30 * (1 - (amountDiff / AMOUNT_MATCH_THRESHOLD));
//                System.out.println("\tAmount similarity score: " + 40 * (1 - (amountDiff / AMOUNT_MATCH_THRESHOLD)) +
//                        " (budget amount: " + amount + ", transaction amount: " + Math.abs(transaction.getAmount()) + ")");
            }
        }

        // 4. Date proximity (0-30 points)
        Calendar closestOccurrence = ItemUtilities.getClosestOccurrence(
                BudgetItem.getById(budgetItemMerchant.getIdBudgetItem()), Calendar.getInstance());
        if (closestOccurrence != null) {
            long daysDiff = Math.abs(ChronoUnit.DAYS.between(
                    transaction.getDate().toInstant(),
                    closestOccurrence.toInstant()
            ));
            if (daysDiff <= 30) {
                score += 30 * (1 - (daysDiff / 30.0));
//                System.out.println("\tDate proximity score: " + 20 * (1 - (daysDiff / 30.0)) +
//                        " (closest occurrence: " + closestOccurrence.getTime() +
//                        ", days difference: " + daysDiff + ")");
            }
        }
//        System.out.println("\tTotal score: " + score);

        return score;
    }



//    public List<Map.Entry<BudgetItem, Double>> filterBudgetItemsFromDatabaseForTransaction(Transaction transaction)
//            throws EntityException {
//
//        if (transaction == null) {
//            return new ArrayList<>();
//        }
//
//        // Get all unexpired budget items from the database as a result set:
//        ResultSet resultSet = BudgetItem.getAllUnexpiredBudgetItems(Calendar.getInstance(), budget);
//
//        //
//        return budgetItems.stream()
//                .map(bim -> Map.entry(bim, scoreTransactionRelevance(bim, transaction)))
//                .filter(entry -> entry.getValue() > 0)
//                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
//                .limit(10)
//                .collect(Collectors.toList());
//    }
}

