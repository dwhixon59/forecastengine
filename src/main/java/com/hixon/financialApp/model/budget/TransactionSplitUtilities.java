package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/// This class contains utility methods for handling transaction splits and associations
public class TransactionSplitUtilities {

    // Get the number of past associations between a budget item and a merchant. This method should return the number of
    // past associations between a budget item and a merchant.  For example, if the budget item has been associated with
    // the merchant 3 times in the past, this method should return 3.
    public static int getItemPastAssociationsToMerchantCount(UUID idBudgetItem, UUID idMerchant) throws Exception {
            if (idBudgetItem == null || idMerchant == null) {
                throw new IllegalArgumentException("Budget item ID and merchant ID must not be null");
            }
            String query =
                "SELECT COUNT(*) " +
                "FROM " +
                    "transaction_split ts inner join transaction t on ts.Transaction_idTransaction = t.idTransaction " +
                "WHERE " +
                    "ts.BudgetItem_idBudgetItem = uuid_to_bin('" + idBudgetItem + "') AND " +
                    "t.Merchant_idMerchant = uuid_to_bin('" + idMerchant + "')";

        // Execute the query with the provided parameters and return the count
        try {
            ResultSet rs;
            try (Statement statement = Utility.getDbConnection().createStatement()) {
                rs = statement.executeQuery(query);
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    // If no result is found, return 0
                    return 0;
                }
            }
        } catch (SQLException e) {
            BudgetException be = new BudgetException("Database error occurred trying to get the count of splits for " +
                    "budget items " + BudgetItem.getById(idBudgetItem) + " and " +
                    "merchant " + Merchant.getById(idMerchant));
            be.initCause(e);
            throw be;
        }

    }

    // Get the total number of splits associated with a particular merchant. For example, if the merchant has 5 splits
    // associated with it, this method should return 5.
    public static int getTotalPastAssociationsToMerchant(UUID idMerchant) throws Exception {

        String query =
                "SELECT COUNT(*) " +
                "FROM " +
                    "transaction_split ts inner join transaction t on ts.Transaction_idTransaction = t.idTransaction " +
                "WHERE " +
                    "t.Merchant_idMerchant = uuid_to_bin('" + idMerchant + "')";

        // Execute the query with the provided parameters and return the count
        try {
            ResultSet rs;
            try (Statement statement = Utility.getDbConnection().createStatement()) {
                rs = statement.executeQuery(query);
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    // If no result is found, return 0
                    return 0;
                }
            }
        } catch (SQLException e) {
            BudgetException be = new BudgetException("Database error occurred trying to get the count of splits for " +
                    "merchant " + Merchant.getById(idMerchant));
            be.initCause(e);
            throw be;
        }
    }

    // Get the number of splits associated with a particular budget item. For example, if the budget item has 3 splits
    // associated with it, this method should return 3.
    public static int getTotalItemPastAssociationsCount(UUID idBudgetItem) throws Exception {

        String query = "SELECT COUNT(*) FROM transaction_split WHERE BudgetItem_idBudgetItem = uuid_to_bin('" +
                idBudgetItem + "')";

        // Execute the query with the provided parameters and return the count
        try {
            ResultSet rs;
            try (Statement statement = Utility.getDbConnection().createStatement()) {
                rs = statement.executeQuery(query);
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    // If no result is found, return 0
                    return 0;
                }
            }
        } catch (SQLException e) {
            BudgetException be = new BudgetException("Database error occurred trying to get the count of splits for " +
                    "budget item " + BudgetItem.getById(idBudgetItem));
            be.initCause(e);
            throw be;
        }
    }
}