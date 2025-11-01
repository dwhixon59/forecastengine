package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.DependentEntity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition;
import static com.hixon.financialApp.utility.Utility.*;
import static java.util.Calendar.DATE;

public class TransactionSplit extends DependentEntity {
    public static final double TAX_RATE = 0.07;

    /*
     * Fields:
     */

    protected double amount;
    UUID idBudgetItem;
    BudgetItem budgetItem = null;
    UUID idTransaction;
    Transaction transaction = null;
    String memo;

    // Disposition of this split in the current forecast while it is being reconciled.  Since it only applies to the
    // current forecast during reconciliation, it is not persisted in the database.
    SplitDisposition disposition = SplitDisposition.ASSIGN;


    /*
     * Getters and setters for BudgetItemMerchant:
     */
    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public UUID getIdBudgetItem() {
        return idBudgetItem;
    }

    public void setIdBudgetItem(UUID idBudgetItem) {
        this.idBudgetItem = idBudgetItem;
    }

    public BudgetItem getBudgetItem() throws EntityException, BudgetException, SQLException {
        if (budgetItem == null) {
            budgetItem = BudgetItem.getById(idBudgetItem);
        }
        return budgetItem;
    }

    public void setBudgetItem(BudgetItem budgetItem) {
        this.budgetItem = budgetItem;
    }

    public UUID getIdTransaction() {
        return idTransaction;
    }

    public void setIdTransaction(UUID idTransaction) {
        this.idTransaction = idTransaction;
    }

    public Transaction getTransaction() throws EntityException, SQLException {
        if (transaction == null) {
            transaction = Transaction.getById(idTransaction);
        }
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public SplitDisposition getDisposition() {
        return disposition;
    }

    public void setDisposition(SplitDisposition disposition) {
        this.disposition = disposition;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }


    /*
     * Constructors for BudgetItemMerchant:
     */
    public TransactionSplit(double splitAmount, BudgetItemMerchant budgetItemMerchant, Transaction transaction, String memo) {
        super();
        this.amount = splitAmount;
        this.idBudgetItem = budgetItemMerchant.getIdBudgetItem();
        this.budgetItem = budgetItemMerchant.getBudgetItem();
        this.idTransaction = transaction.getId();
        this.transaction = transaction;
        this.memo = memo;
        setDirty(true);
    }

    public TransactionSplit(double amount, UUID idBudgetItem, UUID idTransaction, String memo) {
        super();
        this.amount = amount;
        this.idBudgetItem = idBudgetItem;
        this.idTransaction = idTransaction;
        this.memo = memo;
        setDirty(true);
    }

    public TransactionSplit(ResultSet rs) throws RegisterException {
        try {

            if (rs == null)
                throw new RegisterException("Result set passed into TransactionSplit constructor must not be " +
                        "null.");
            this.amount = rs.getDouble("ts.amount");
            this.idBudgetItem = UUID.fromString(rs.getString("ts.idBudgetItem"));
            this.idTransaction = UUID.fromString(rs.getString("ts.idTransaction"));
            this.memo = rs.getString("ts.memo");
            setDirty(false);

        } catch (SQLException e) {

            RegisterException re = new RegisterException("Error reading in the Merchant-Payee row.  ", e);
            throw (re);
        }
    }


    /*
     * Load and save methods:
     */
    private static final String selectColumns = "ts.amount as 'ts.amount', bin_to_uuid(ts.BudgetItem_idBudgetItem) as " +
            "'ts.idBudgetItem', bin_to_uuid(ts.Transaction_idTransaction) as 'ts.idTransaction', ts.memo as 'ts.memo' ";

    public static String getSelectColumns() {
        return selectColumns;
    }

    public static String getSelectQuery() {
        return "select " + getSelectColumns() + "from transaction_split ts ";
    }

    private static final String insertQuery = "insert into transaction_split (amount, " +
            "BudgetItem_idBudgetItem, Transaction_idTransaction, memo) values (";

    @Override
    public String getInsertQuery() throws BudgetException, ForecastException {
        return null;
    }

    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
        return null;
    }

    @Override
    public String getUpdateByIdQuery() throws BudgetException {
        return null;
    }

    private static final String deleteQuery = "delete from transaction_split ";

    @Override
    public String getDeleteByIdQuery() {
        return deleteQuery + "where BudgetItem_idBudgetItem = uuid_to_bin('" + idBudgetItem + "') " +
                "and Transaction_idTransaction = uuid_to_bin('" + idTransaction + "')";
    }

    @Override
    public String getPrintableTypeName() {
        return getPrintableTypeName_static();
    }

    public static String getPrintableTypeName_static() {
        return "transaction split";
    }

    public static TransactionSplit getbyId(UUID idBudgetItem, UUID idTransaction) throws EntityException, RegisterException {
        ResultSet rs = EntityInt.getSingletonRS(getSelectQuery() + "where Budget_Item_idbBudgetItem = " +
                        "uuid_to_bin('" + idBudgetItem + "') and Transaction_idTransaction = uuid_to_bin('" + idTransaction + "'))",
                "trying to retrieve a transaction split");
        return (rs != null) ? new TransactionSplit(rs) : null;
    }

    public void save() throws RegisterException, EntityException {
        String memoString = (memo != null) ? "\"" + memo + "\"" : "null";
        super.executeQueryForThis(insertQuery + amount + ", uuid_to_bin('" + idBudgetItem + "'), " +
                "uuid_to_bin('" + idTransaction + "'), " + memoString + ")", "Databsae error occurred inserting " +
                "a TransactionSplit into the database.");
    }


    /*
     * Helper methods:
     */

    /**
     * Validate the fields of an object.  Every entity is required to provide a method that validates the contents of
     * the entity.
     *
     * @return true if the object is valid
     */
    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String toString() {
        String s = null;
        try {
            String fromOrTo = (amount < 0) ? " to " : " from ";
            String memoString = (memo != null) ? ".  Memo:  " + memo : "";
            s = "Split: amount of " + formatDollarAmount(amount) + " on " +
                    calendarDateToStringDate(getTransaction().getDate()) + fromOrTo +
                    getTransaction().getMerchant().getName() + " applied to budget item " + getBudgetItem().getPayee() +
                    memoString;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }

    public String toStringConcise() {
        String s = null;
        try {
            String memoString = (memo != null) ? ".  Memo:  " + memo : ".";
            s = "Split:  " + getBudgetItem().getPayee() + ", " + formatDollarAmount(amount) + memoString;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }


    /*
     * Main methods for TransactionSplit
     */

    // Get a list of transaction split items for a transaction:
    public static List<TransactionSplit> getSplitsForTransaction(Transaction transaction) throws RegisterException {

        // Find out what budget items are associated with the transaction for this transaction:
        String query = getSelectQuery() + "where ts.Transaction_idTransaction = uuid_to_bin('" + transaction.getId() + "')";
        try {
            Statement statement = getDbConnection().createStatement();
            ResultSet rs = statement.executeQuery(query);
            List<TransactionSplit> transactionSplits = new ArrayList<>();
            while (rs.next()) {
                transactionSplits.add(new TransactionSplit(rs));
            }
            return (transactionSplits.size() > 0) ? transactionSplits : null;

        } catch (SQLException e) {
            RegisterException re = new RegisterException("Database error occurred trying to get the transaction splits for " +
                    "transaction " + transaction.getPayee() + " for $" + transaction.getAmount(), e);
            throw re;
        }
    }

    // Delete the splits for a transaction:
    public static void deleteSplitsForTransaction(UUID id) throws EntityException {
        EntityInt.deleteMultiple(deleteQuery + "where Transaction_idTransaction = uuid_to_bin('" + id + "')",
                "Databsae error occurred deleting TransactionSplits from the database.");
    }

    // Get a list of new transactions for a budget item:
    public static List<Transaction> getNewTransactionsForBudgetItem(BudgetItem budgetItem)
            throws EntityException, SQLException {

        // Get the transactions for the budget item:
        List<Transaction> transactions = new ArrayList<>();

        // Create a query to get the new transactions for the budget item:
        String query =
            "select " +
                    TransactionSplit.getSelectColumns() + ", " + Transaction.getSelectColumns() +
            "from " +
                "transaction_split ts " +
                "inner join " +
                    "transaction tr on ts.Transaction_idTransaction = tr.idTransaction " +
            "where " +
              "ts.BudgetItem_idBudgetItem = uuid_to_bin('" + budgetItem.getId() + "') and " +
              "tr.isNew = true";

        // Get the transactions for the budget item:
        ResultSet rs = EntityInt.getRS(query, "while trying to get the new transactions for a budget item");
        while (rs.next()) {
            transactions.add(new Transaction(rs));
        }

        return transactions;
    }

    // Set the transactions for a budget item to not new:
    public static void setTransactionsForBudgetItemToNotNew(BudgetItem budgetItem) throws EntityException {
        String query =
            "update " +
                    "transaction t " +
            "inner join " +
                    "transaction_split ts on t.idTransaction = ts.Transaction_idTransaction " +
            "set " +
                    "t.isNew = false " +
            "where " +
                    "ts.BudgetItem_idBudgetItem = uuid_to_bin('" + budgetItem.getId() + "')";
        EntityInt.executeUpdate(query, "while trying to set the transactions for a budget item to not new");
    }

    // Get the splits associated with a budget item in a period:
    public static ResultSet getSplitsForBudgetItemInPeriod(BudgetItem budgetItem, Calendar startDate, Calendar endDate)
            throws EntityException {
        String selectQuery = "select ts.amount as 'ts.amount', bin_to_uuid(ts.BudgetItem_idBudgetItem) as 'ts.idBudgetItem', " +
                "bin_to_uuid(ts.Transaction_idTransaction) as 'ts.idTransaction', ts.memo as 'ts.memo', ";
        String query = selectQuery + "t.authorizationDate as 'date' from transaction_split ts " +
                "inner join transaction t on ts.Transaction_idTransaction = " +
                "t.idTransaction where ts.BudgetItem_idBudgetItem = uuid_to_bin('" + budgetItem.getId() + "') and " +
                "t.authorizationDate is not null and t.authorizationDate >= " + calendarDateToSqlDateString(startDate) +
                " and t.authorizationDate <= " + calendarDateToSqlDateString(endDate);
        query += " union ";
        query += selectQuery + " t.postDate as 'date' from transaction_split ts inner join " +
                "transaction t on ts.Transaction_idTransaction = " +
                "t.idTransaction where ts.BudgetItem_idBudgetItem = uuid_to_bin('" + budgetItem.getId() + "') and " +
                "t.authorizationDate is null and t.postDate >= " + calendarDateToSqlDateString(startDate) +
                " and t.postDate <= " + calendarDateToSqlDateString(endDate);
        query += " order by date asc";
        return EntityInt.getRS(query, "while trying to get the splits for a budget item");
    }

    // Get a list of splits for a budget item in a period:
    public static List<TransactionSplit> getSplitsListForBudgetItemInPeriod(BudgetItem budgetItem, Calendar startDate,
                                                                            Calendar endDate) throws RegisterException, SQLException, EntityException {
        List<TransactionSplit> splits = new ArrayList<>();
        ResultSet rs = getSplitsForBudgetItemInPeriod(budgetItem, startDate, endDate);
        while (rs.next()) {
            splits.add(new TransactionSplit(rs));
        }
        return splits;
    }

    // Get the splits associated with a budget item in the current month:
    public static List<TransactionSplit> getSplitsListForBudgetItemMTD(BudgetItem budgetItem) throws SQLException,
            RegisterException, EntityException {
        Calendar startDate = Calendar.getInstance();
        startDate.set(DATE, 1);
        setToLastBusinessDayBefore(startDate);
        Calendar endDate = Calendar.getInstance();
        endDate.set(DATE, 1);
        endDate.add(Calendar.MONTH, 1);
        setToLastBusinessDayBefore(endDate);

        return getSplitsListForBudgetItemInPeriod(budgetItem, startDate, endDate);
    }

    /**
     * Distribute the remainder (unallocated) amount of a transaction across some of the splits associated with the
     * transaction.  The remainder can be split two ways; by splitting the amount evenly across the designated splits, or
     * apportioned across the designated splits by the amount of the split.  Apportionment works well for things like adding
     * sales tax to split amounts. If there are both even and apportioned splits, the apportioned splits are done first
     * and then the remaining amount is evenly spread across the even splits.
     *
     * @param evenRemainders        The indexes of the splits to receive evenly divided amount of the remainder.
     * @param apportionedRemainders The indexes of the splits to receive apportioned amounts of the remainder.
     * @param addTaxItems           The indexes of the splits that need tax added.
     * @param splits                A list of the splits for the transaction.
     * @return The amount spread across the splits.
     */
    public static boolean splitRemainder(double transactionAmount, List<Integer> evenRemainders, List<Integer>
            apportionedRemainders, List<Integer> addTaxItems, List<TransactionSplit> splits) {

        // Compute the total number of splits to spread across:
        int numberOfSplits = apportionedRemainders.size() + evenRemainders.size();

        // Compute the remainder to spread across the splits:
        double splitsAmount = 0;
        for (TransactionSplit split : splits
        ) {
            splitsAmount += split.getAmount();
        }
        double remainder = transactionAmount - splitsAmount;

        // First add tax to any splits that require it and subtract the taxes from the remainder:
        double totalTax = 0;
        for (int index : addTaxItems
        ) {
            double tax = Math.round((splits.get(index).getAmount() * 100.0) * TAX_RATE) / 100.0;
            splits.get(index).setAmount(splits.get(index).getAmount() + tax);
            totalTax += tax;
        }
        remainder -= totalTax;

        // Apportion the remainder across the splits named in the apportionedRemainders list:
        double totalApportionedAmount = 0;
        for (int index : apportionedRemainders
        ) {
            TransactionSplit split = splits.get(index);
            double splitAmount = split.getAmount();
            double apportionedAmount = (splitAmount / transactionAmount) * splitsAmount;
            apportionedAmount = Math.round(apportionedAmount * 100.0) / 100.0;
            splitAmount += apportionedAmount;
            split.setAmount(splitAmount);
            totalApportionedAmount += apportionedAmount;
        }
        remainder -= totalApportionedAmount;

        // Now evenly split what's left over the splits name in the evenRemainders list:
        double evenRemainderAmount = remainder / evenRemainders.size();
        evenRemainderAmount = Math.round(evenRemainderAmount * 100.0) / 100.0;
        for (int index : evenRemainders
        ) {
            splits.get(index).setAmount(splits.get(index).getAmount() + evenRemainderAmount);
        }

        // Finally, fix up any rounding errors in the transaction total based on the splits.  The rounding error should
        // be no greater than one penney per split so only fix it if the difference is in that range:
        double totalSplitsAmount = 0;
        for (TransactionSplit split : splits
        ) {
            totalSplitsAmount += split.getAmount();
        }
        double roundingError = Math.round((transactionAmount - totalSplitsAmount) * 100.0) / 100.0;
        double maxRoundingError = (addTaxItems.size() + apportionedRemainders.size() + evenRemainders.size()) * 0.005;
        if (Math.abs(roundingError) >= 0.01 && Math.abs(roundingError) <= maxRoundingError) {
            splits.get(0).setAmount(splits.get(0).getAmount() + roundingError);
        }

        return true;
    }

}
