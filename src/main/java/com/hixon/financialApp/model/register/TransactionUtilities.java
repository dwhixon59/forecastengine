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