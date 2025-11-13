package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * Utility methods for transaction-related queries and batch operations.
 */
public class TransactionUtilities {

    /**
     * Retrieves the first provisional (uncleared) transaction for a given merchant and amount.
     *
     * @param idMerchant The UUID of the merchant.
     * @param amount The transaction amount.
     * @return The first matching provisional Transaction, or null if none found.
     * @throws EntityException If a database or entity error occurs.
     * @throws SQLException If a SQL error occurs.
     */
    public static Transaction getFirstProvisionalTransaction(UUID idMerchant, double amount) throws EntityException, SQLException {
        ResultSet rs = EntityInt.getRS(Transaction.getSelectQuery() + " where tr.Merchant_idMerchant = uuid_to_bin('" + idMerchant +
                "') and tr.amount = " + amount + " and tr.cleared = false order by tr.postDate asc", "Database error" +
                " occured while trying to retrieve any provisional transactions that match a merchant and amount.");
        Transaction transaction = null;
        if (rs != null) {
            if (rs.next()) {
                transaction = new Transaction(rs);
            }
        }
        return transaction;
    }

    /**
     * Finds a matching provisional transaction using improved fuzzy matching logic.
     * This method is designed to match Wells Fargo provisional transactions with their corresponding
     * posted transactions, even when the payee strings differ significantly.
     *
     * Matching criteria (all must match):
     * 1. Same register (bank account)
     * 2. Amount match with tip tolerance:
     *    a) Exact match, OR
     *    b) Cleared amount is larger (for tips): cleared >= provisional AND cleared <= provisional * 1.30
     *       (allows up to 30% tip on the provisional amount)
     * 3. Not cleared (provisional transactions are always uncleared)
     * 4. Date within ±5 days (provisional date often differs from posted date by 1-2 days)
     * 5. Either:
     *    a) Same merchant (if merchant is assigned), OR
     *    b) Fuzzy payee match (if merchant not assigned) - checks if significant words appear in both payees
     *
     * @param idRegister The UUID of the register
     * @param clearedAmount The exact transaction amount of the cleared transaction
     * @param postDate The post date of the cleared transaction
     * @param merchantPayee The parsed merchant name from the cleared transaction
     * @param idMerchant The merchant UUID if known, or null
     * @return The first matching provisional Transaction, or null if none found
     * @throws EntityException If a database or entity error occurs
     * @throws SQLException If a SQL error occurs
     */
    public static Transaction findMatchingProvisionalTransaction(UUID idRegister, double clearedAmount,
                                                                  Calendar postDate, String merchantPayee,
                                                                  UUID idMerchant)
            throws EntityException, SQLException {

        // Calculate date range: ±5 days from the post date
        Calendar startDate = (Calendar) postDate.clone();
        startDate.add(Calendar.DAY_OF_MONTH, -5);
        Calendar endDate = (Calendar) postDate.clone();
        endDate.add(Calendar.DAY_OF_MONTH, 5);

        // For negative amounts (debits), calculate the range to search
        // The provisional amount should be >= cleared amount (less negative)
        // and <= cleared amount * 1.30 (to allow for tips up to 30%)
        double minAmount, maxAmount;
        if (clearedAmount < 0) {
            // For debits: provisional is less negative (closer to zero) than cleared when tip is added
            // Example: provisional = -50, cleared = -60 (with $10 tip)
            // Search range: -50 to -46.15 (cleared / 1.30)
            minAmount = clearedAmount / 1.30;  // Less negative (tip makes it more negative)
            maxAmount = clearedAmount;  // Exact match or provisional is closer to zero
        } else {
            // For credits: provisional is less positive than cleared when tip is added
            // (This is rare but handle it symmetrically)
            minAmount = clearedAmount;
            maxAmount = clearedAmount * 1.30;
        }

        // Build query to find provisional transactions in the date and amount range
        String query = Transaction.getSelectQuery() +
                " WHERE tr.Register_idRegister = uuid_to_bin('" + idRegister + "')" +
                " AND tr.amount >= " + minAmount +
                " AND tr.amount <= " + maxAmount +
                " AND tr.cleared = false" +
                " AND tr.postDate >= " + com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(startDate) +
                " AND tr.postDate <= " + com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(endDate) +
                " ORDER BY ABS(DATEDIFF(tr.postDate, " + com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(postDate) + ")) ASC" +
                ", ABS(tr.amount - " + clearedAmount + ") ASC";  // Prefer closer amount matches

        ResultSet rs = EntityInt.getRS(query, "Database error occurred while trying to find matching provisional transaction.");

        if (rs == null) {
            return null;
        }

        // Iterate through candidates and find the best match
        while (rs.next()) {
            Transaction candidate = new Transaction(rs);

            // If merchant is assigned, check if merchants match
            if (idMerchant != null && candidate.getIdMerchant() != null) {
                if (candidate.getIdMerchant().equals(idMerchant)) {
                    return candidate; // Perfect match on merchant
                }
                continue; // Merchants don't match, try next candidate
            }

            // If no merchant assigned to one or both transactions, do fuzzy payee matching
            if (merchantPayee != null && candidate.getMerchantPayee() != null) {
                if (fuzzyPayeeMatch(merchantPayee, candidate.getMerchantPayee())) {
                    return candidate; // Good fuzzy match
                }
            } else if (merchantPayee != null && candidate.getPayee() != null) {
                // Compare against original payee if merchantPayee not set
                if (fuzzyPayeeMatch(merchantPayee, candidate.getPayee())) {
                    return candidate;
                }
            }
        }

        return null; // No match found
    }

    /**
     * Performs fuzzy matching between two payee strings to determine if they likely refer
     * to the same transaction. This is useful for matching Wells Fargo provisional transactions
     * with their posted counterparts, where the payee strings differ significantly.
     *
     * The algorithm:
     * 1. Normalizes both strings (uppercase, removes special chars)
     * 2. Tokenizes into words
     * 3. Filters out common/generic words
     * 4. Checks if at least 50% of significant words from the shorter string appear in the longer string
     *
     * @param payee1 First payee string
     * @param payee2 Second payee string
     * @return true if the payees likely match, false otherwise
     */
    private static boolean fuzzyPayeeMatch(String payee1, String payee2) {
        if (payee1 == null || payee2 == null) {
            return false;
        }

        // Normalize: uppercase and remove special characters except spaces
        String normalized1 = payee1.toUpperCase().replaceAll("[^A-Z0-9 ]", " ");
        String normalized2 = payee2.toUpperCase().replaceAll("[^A-Z0-9 ]", " ");

        // Split into tokens
        String[] tokens1 = normalized1.split("\\s+");
        String[] tokens2 = normalized2.split("\\s+");

        // Common words to ignore when matching
        java.util.Set<String> stopWords = java.util.Set.of(
                "PURCHASE", "AUTHORIZED", "ON", "CARD", "DEBIT", "CREDIT",
                "TRANSACTION", "RECURRING", "PAYMENT", "THE", "AND", "OR",
                "AT", "IN", "TO", "FROM", "FOR", "WITH", "OF"
        );

        // Filter out stop words and very short tokens
        java.util.List<String> significantTokens1 = new java.util.ArrayList<>();
        java.util.List<String> significantTokens2 = new java.util.ArrayList<>();

        for (String token : tokens1) {
            if (token.length() >= 3 && !stopWords.contains(token) && !token.matches("\\d+")) {
                significantTokens1.add(token);
            }
        }

        for (String token : tokens2) {
            if (token.length() >= 3 && !stopWords.contains(token) && !token.matches("\\d+")) {
                significantTokens2.add(token);
            }
        }

        // If either has no significant tokens, can't match
        if (significantTokens1.isEmpty() || significantTokens2.isEmpty()) {
            return false;
        }

        // Count how many tokens from the shorter list appear in the longer list
        java.util.List<String> shorterList = significantTokens1.size() <= significantTokens2.size() ?
                significantTokens1 : significantTokens2;
        java.util.List<String> longerList = significantTokens1.size() > significantTokens2.size() ?
                significantTokens1 : significantTokens2;

        int matchCount = 0;
        for (String token : shorterList) {
            // Check if this token appears in any token in the longer list (allows partial matches)
            for (String longerToken : longerList) {
                if (longerToken.contains(token) || token.contains(longerToken)) {
                    matchCount++;
                    break;
                }
            }
        }

        // Require at least 50% of significant words to match
        double matchRatio = (double) matchCount / shorterList.size();
        return matchRatio >= 0.5;
    }

    /**
     * Retrieves a ResultSet of new transactions for a given register.
     *
     * @param register The register to query.
     * @return ResultSet of new transactions.
     * @throws EntityException If a database or entity error occurs.
     */
    public static ResultSet getNewTransactions(Register register) throws EntityException {
        String query =
                Transaction.getSelectQuery() + " " +
                        "where " +
                        "tr.isNew = true and " +
                        "tr.Register_idRegister = uuid_to_bin('" + register.getId() + "') " +
                        "order by tr.authorizationDate asc";
        return EntityInt.getRS(query, "attempting to retrieve a list of transactions that were not " +
                "reported on in a previous new transactions report.");
    }

    /**
     * Checks if there are any transactions skipped with respect to a forecast during the import process.
     *
     * @param forecast The forecast to check against.
     * @return True if there are skipped transactions, false otherwise.
     * @throws EntityException If a database or entity error occurs.
     * @throws SQLException If a SQL error occurs.
     * @throws BudgetException If a budget error occurs.
     * @throws RegisterException If a register error occurs.
     */
    public static boolean isSkippedTransactionsWrtForecast(Forecast forecast) throws EntityException, SQLException, BudgetException, RegisterException {
        Calendar startDate = forecast.getStartDate();
        Calendar fourMonthsAgo = Calendar.getInstance();
        fourMonthsAgo.add(Calendar.MONTH, -4);
        if (fourMonthsAgo.after(startDate)) startDate = fourMonthsAgo;
        String query = Transaction.getCountQuery() + " " +
                "where tr.postDate >= " + com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(fourMonthsAgo) + " and " +
                "tr.Register_idRegister = uuid_to_bin('" + forecast.getBudget().getRegisters().get(0).getId() + "') and " +
                "tr.idTransaction not in " +
                "(select idTransaction from transaction " +
                "inner join transaction_split on idTransaction = Transaction_idTransaction " +
                "inner join forecast_transaction_split on Transaction_idTransaction = Transaction_Split_idTransaction and " +
                "BudgetItem_idBudgetItem = Transaction_Split_idBudgetitem " +
                ") " +
                "order by postDate asc";

        ResultSet rs = EntityInt.getRS(query, "attempting to retrieve a count of the transactions that were previously " +
                "skipped during the import process.");
        rs.next();
        int count = rs.getInt(1);
        return (count > 0);
    }

    /**
     * Retrieves a ResultSet of transactions that were skipped with respect to a forecast during the import process.
     *
     * @param forecast The forecast to check against.
     * @return ResultSet of skipped transactions.
     * @throws EntityException If a database or entity error occurs.
     * @throws BudgetException If a budget error occurs.
     * @throws SQLException If a SQL error occurs.
     * @throws RegisterException If a register error occurs.
     */
    public static ResultSet getSkippedTransactionsWrtForecast(Forecast forecast) throws EntityException, BudgetException, SQLException, RegisterException {
        Calendar startDate = forecast.getStartDate();
        Calendar fourMonthsAgo = Calendar.getInstance();
        fourMonthsAgo.add(Calendar.MONTH, -4);
        if (fourMonthsAgo.after(startDate)) startDate = fourMonthsAgo;
        String query = Transaction.getSelectQuery() + " " +
                "where tr.postDate >= " + com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(fourMonthsAgo) + " and " +
                "tr.Register_idRegister = uuid_to_bin('" + forecast.getBudget().getRegisters().get(0).getId() + "') and " +
                "tr.idTransaction not in " +
                "(select idTransaction from transaction " +
                "inner join transaction_split on idTransaction = Transaction_idTransaction " +
                "inner join forecast_transaction_split on Transaction_idTransaction = Transaction_Split_idTransaction and " +
                "BudgetItem_idBudgetItem = Transaction_Split_idBudgetitem " +
                ") " +
                "order by authorizationDate asc";
        return EntityInt.getRS(query, "attempting to retrieve a list of transactions that were previously " +
                "skipped during the import process.");
    }

    /**
     * Sets a list of transactions as not new and updates them in the database.
     *
     * @param transactions The list of transactions to update.
     * @throws SQLException If a SQL error occurs.
     * @throws EntityException If a database or entity error occurs.
     */
    public static void setTransactionsNotNew(List<Transaction> transactions) throws SQLException, EntityException {
        for (Transaction transaction : transactions) {
            transaction.setIsNew(false);
            transaction.save(Transaction.SaveMethod.UPDATE);
        }
    }

    /**
     * Retrieves the most recent transaction by payee and amount, ignoring the REF # in the payee.
     *
     * @param payee The payee to search for.
     * @param amount The transaction amount.
     * @return The most recent matching Transaction, or null if none found.
     * @throws Exception If a database or query error occurs.
     */
    public static Transaction getMostRecentTransactionByPayee(String payee, double amount) throws Exception {
        String query =
                Transaction.getSelectQuery() + " " +
                        "INNER JOIN merchant m on " +
                        "tr.Merchant_idMerchant = m.idMerchant " +
                        "WHERE " +
                        "tr.payee LIKE '" + payee.replaceAll("REF #\\S+", "REF #%" ) + "' " +
                        "AND tr.amount BETWEEN " + (amount * 0.9) + " AND " + (amount * 1.1) + " " +
                        "ORDER BY " +
                        "tr.postDate DESC " +
                        "lIMIT 1";

        ResultSet rs = EntityInt.getRS(query, "attempting to retrieve the most recent transaction by payee.");
        if (rs.next()) {
            return new Transaction(rs);
        } else {
            return null;
        }
    }

    /**
     * Retrieves up to 10 similar transactions using a full text search on the user description.
     *
     * @param userDescription The user description to search for.
     * @return List of up to 10 similar transactions.
     * @throws Exception If a database or query error occurs.
     */
    public static List<Transaction> getByUserDescriptionFullText(String userDescription) throws Exception {
        String query =
                "WITH ranked_transactions AS ( " +
                        "SELECT " +
                        "tr.*, " +
                        "BIN_TO_UUID(tr.idTransaction) AS uuidTransaction, " +
                        "BIN_TO_UUID(tr.Register_idRegister) AS uuidRegister, " +
                        "BIN_TO_UUID(tr.Merchant_idMerchant) AS uuidMerchant, " +
                        "TRIM( " +
                        "CONCAT( " +
                        "SUBSTRING_INDEX(tr.payee, '#', 1), " +
                        "SUBSTRING(tr.payee, LOCATE(' ', tr.payee, LOCATE('#', tr.payee)) + 1) " +
                        ") " +
                        ") AS normalized_payee, " +
                        "MATCH (user_description) AGAINST ('ALIMONY' IN NATURAL LANGUAGE MODE) AS relevance, " +
                        "ROW_NUMBER() OVER ( " +
                        "PARTITION BY " +
                        "TRIM( " +
                        "CONCAT( " +
                        "SUBSTRING_INDEX(tr.payee, '#', 1), " +
                        "SUBSTRING(tr.payee, LOCATE(' ', tr.payee, LOCATE('#', tr.payee)) + 1) " +
                        " ) " +
                        "), " +
                        "tr.amount, " +
                        "tr.Merchant_idMerchant " +
                        "ORDER BY " +
                        "tr.postDate DESC " +
                        ") AS rn " +
                        "FROM transaction tr " +
                        "WHERE MATCH (user_description) AGAINST ('" + userDescription + "' IN NATURAL LANGUAGE MODE) " +
                        ") " +
                        "SELECT " +
                        "uuidTransaction AS 'tr.idTransaction', " +
                        "postDate AS 'tr.postDate', " +
                        "authorizationDate AS 'tr.authorizationDate', " +
                        "amount AS 'tr.amount', " +
                        "cleared AS 'tr.cleared', " +
                        "checkNumber AS 'tr.checkNumber', " +
                        "normalized_payee AS 'tr.payee', " +
                        "user_description AS 'tr.user_description', " +
                        "balance AS 'tr.balance', " +
                        "isImproper AS 'tr.isImproper', " +
                        "isNew AS 'tr.isNew', " +
                        "importRecordId AS 'tr.importRecordId', " +
                        "uuidRegister AS 'tr.idRegister', " +
                        "uuidMerchant AS 'tr.idMerchant', " +
                        "relevance " +
                        "FROM ranked_transactions " +
                        "WHERE rn = 1 " +
                        "ORDER BY relevance DESC " +
                        "LIMIT 10";

        ResultSet rs = EntityInt.getRS(query, "attempting to retrieve similar transactions by payee.");
        List<Transaction> transactions = new ArrayList<>();
        while (rs.next()) {
            transactions.add(new Transaction(rs));
        }
        return transactions;
    }
}