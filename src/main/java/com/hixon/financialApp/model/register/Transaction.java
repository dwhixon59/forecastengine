package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.TransactionHistory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.model.entity.EntityInt.getRS;
import static com.hixon.financialApp.model.entity.EntityInt.getRSById;
import static com.hixon.financialApp.utility.Utility.formatDollarAmount;
import static com.hixon.financialApp.utility.Utility.stringDateSlashToCalendarDate;


public class Transaction extends IndependentEntity {

    /*
     * Statics and Constants:
     */
    public static final String CLEARED_TRANSACTIONS_FILE = "cleared transactions";

    public static final String PROVISIONAL_TRANSACTIONS_FILE = "provisional transactions";


    /*
     * Fields of the Transaction class:
     */
    private Calendar postDate = null;
    private Calendar authorizationDate;
    private boolean cleared = false;
    private int checkNumber = 0;
    private String payee = null;
    private double amount = 0;
    private double balance = 0;
    private UUID idRegister = null;
    private Register register = null;
    private UUID idMerchant = null;
    private String merchantPayee = null;
    private boolean isImproper = false;
    private boolean isNew = true;
    private String importRecordId = null;
    private Merchant merchant;


    public enum Headers {
        TRANSACTION_DATE, AMOUNT, CLEARED, CHECK_NUMBER, PAYEE
    }

    private static final String selectColumns = "bin_to_uuid(tr.idTransaction) as 'tr.idTransaction', " +
            "tr.postDate as 'tr.postDate', tr.authorizationDate as 'tr.authorizationDate', tr.amount as 'tr.amount', " +
            "tr.cleared as 'tr.cleared', tr.checkNumber as 'tr.checkNumber', tr.payee as 'tr.payee', " +
            "tr.balance as 'tr.balance', tr.isImproper as 'tr.isImproper', tr.isNew as 'tr.isNew', " +
            "tr.importRecordId as 'tr.importRecordId', bin_to_uuid(tr.Register_idRegister) as 'tr.idRegister', " +
            "bin_to_uuid(tr.Merchant_idMerchant) as 'tr.idMerchant'";

    public static String getSelectColumns() {
        return selectColumns;
    }

    private static final String selectQuery = "select " + getSelectColumns() + " from transaction tr";

    public static String getSelectQuery() {
        return selectQuery;
    }

    private static final String countQuery = "select count(*) from transaction tr";
    public static String getCountQuery() {
        return countQuery;
    }

    private static final String insertQuery = "insert into transaction (idTransaction, " +
            "postDate, authorizationDate, amount, cleared, checkNumber, payee, balance, isImproper, isNew, " +
            "importRecordId, Register_idRegister, Merchant_idMerchant) values(";

    @Override
    public String getInsertQuery() {
        return insertQuery + "uuid_to_bin('" + id + "'), " + Utility.calendarDateToSqlDateString(postDate) + ", " +
                Utility.calendarDateToSqlDateString(authorizationDate) + ", " + amount + ", " + cleared + ", " +
                checkNumber + ", \"" + payee + "\", " + balance + ", " + isImproper + ", " + isNew + ", \"" +
                importRecordId + "\", uuid_to_bin('" + getIdRegister() + "'), uuid_to_bin('" + getIdMerchant() + "'))";
    }

    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
        return getInsertQuery() + " on duplicate key update postDate = " + Utility.calendarDateToSqlDateString(postDate) +
                ", authorizationDate = " + Utility.calendarDateToSqlDateString(authorizationDate) + ", amount = " + amount
                + ", cleared = " + cleared + ", checkNumber = " + checkNumber + ", payee = \"" + payee + "\", balance = "
                + balance + ", isImproper = " + isImproper + ", isNew = " + isNew
                + ", Register_idRegister = uuid_to_bin('" + getIdRegister() + "'), Merchant_idMerchant = " +
                "uuid_to_bin('" + getIdMerchant() + "')";
    }

    private static final String updateQuery =
            "update transaction set ";

    public static String getUpdateIsNewQuery() {
        return "update transaction set isNew = false where register_idRegister = uuid_to_bin('";
    }


    /**
     * Validate the fields of an object.  Every entity is required to provide a method that validates the contents of
     * the entity.
     *
     * @return true if the object is valid
     */
    @Override
    public boolean isValid() { return true; }

    @Override
    public String getUpdateByIdQuery() {
        return updateQuery + "postdate = " + Utility.calendarDateToSqlDateString(postDate) + ", authorizationDate = " +
                Utility.calendarDateToSqlDateString(authorizationDate) + ", amount = " + amount + ", cleared = " +
                cleared + ", checkNumber = " + checkNumber + ", payee = '" + payee + "', balance = " + balance +
                ", isImproper = " + isImproper + ", isNew = " + isNew + ", importRecordId = '" + importRecordId +
                "', Register_idRegister = uuid_to_bin('" + idRegister + "'), Merchant_idMerchant = uuid_to_bin('" +
                idMerchant + "') " +
                "where idTransaction = uuid_to_bin('" + id + "')";
    }

    private static final String deleteQuery = "delete from transaction where ";

    @Override
    public String getDeleteByIdQuery() {
        return deleteQuery + "idTransaction = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getPrintableTypeName() {
        return getPrintableTypeName_static();
    }

    public static String getPrintableTypeName_static() {
        return "transaction";
    }


    /*
     * Getters and setters:
     */
    public void setIdTransaction(UUID idTransaction) {

        this.id = idTransaction;
    }

    public Calendar getPostDate() {
        return postDate;
    }

    public void setPostDate(Calendar postDate) {
        this.postDate = postDate;
    }

    public boolean isCleared() {
        return cleared;
    }

    public void setCleared(boolean cleared) {
        this.cleared = cleared;
    }

    public int getCheckNumber() {
        return checkNumber;
    }

    public void setCheckNumber(int checkNumber) {
        this.checkNumber = checkNumber;
    }

    public String getPayee() {
        return payee;
    }

    public void setPayee(String payee) {
        this.payee = payee;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public UUID getIdRegister() {
        if (idRegister == null) {
            idRegister = register.getId();
        }
        return idRegister;
    }

    public void setIdRegister(UUID idRegister) {
        this.idRegister = idRegister;
    }

    public Register getRegister() throws EntityException, SQLException, RegisterException {
        if (register == null) {
            register = Register.getById(idRegister);
        }
        return register;
    }

    public void setRegister(Register register) {
        this.register = register;
    }

    public Calendar getAuthorizationDate() {
        return authorizationDate;
    }

    public Calendar getDate() {
        return (authorizationDate != null) ? authorizationDate : postDate;
    }

    public void setAuthorizationDate(Calendar authorizationDate) {
        this.authorizationDate = authorizationDate;
    }

    public UUID getIdMerchant() {
        if (idMerchant == null && merchant != null) {
            idMerchant = merchant.getId();
        }
        return idMerchant;
    }

    public void setIdMerchant(UUID idMerchant) {
        this.idMerchant = idMerchant;
    }

    public Merchant getMerchant() throws EntityException, RegisterException {
        if (merchant == null) {
            merchant = Merchant.getById(idMerchant);
        }
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public void setMerchantPayee(String merchantPayee) {
        this.merchantPayee = merchantPayee;
    }

    public String getMerchantPayee() {
        return merchantPayee;
    }

    public boolean getIsImproper() {
        return isImproper;
    }

    public void setIsImproper(boolean isImproper) {
        this.isImproper = isImproper;
    }

    public boolean getIsNew() {
        return false;
    }

    public void setIsNew(boolean isNew) {
        this.isNew = isNew;
    }

    public String getImportRecordId() {
        return importRecordId;
    }

    public void setImportRecordId(String importRecordId) {
        this.importRecordId = importRecordId;
    }


    /*
     * Constructors:
     */
    // Constructor for creating a new transaction from a register transaction CSV record:
    public Transaction(Register register, Calendar postDate, String payee, double amount, boolean cleared,
                       int checkNumber, String importRecordId) {
        super(true);
        setDirty(true);

        this.postDate = postDate;
        this.authorizationDate = postDate;
        this.cleared = cleared;
        this.checkNumber = checkNumber;
        this.payee = payee;
        this.amount = amount;
        this.balance = 0;
        this.idRegister = register.getId();
        this.register = register;
        this.idMerchant = null;
        this.merchantPayee = null;
        this.isImproper = false;
        this.isNew = true;
        this.importRecordId = importRecordId;
        this.merchant = null;

        // Add this transaction to the history list:
        TransactionHistory.getInstance().add(this);
    }

    // Constructor for creating a transaction from a ResultSet record:
    public Transaction(ResultSet rs) throws SQLException {
        super(false);
        loadFromResultSet(rs);

        // Add this transaction to the history list:
        TransactionHistory.getInstance().add(this);
    }

    // Constructor for importing a provisional transaction from a CSV record:
    public Transaction(Register register, String postDate, String payee, double amount, String merchantPayee)
            throws ParseException {

        super(true);
        this.postDate = (stringDateSlashToCalendarDate(postDate));
        // Wells Fargo provisional transactions don't have an authorization date, so use today's date.
        authorizationDate = Calendar.getInstance();
        cleared = false;
        checkNumber = 0;
        this.payee = payee;
        this.amount = amount;
        balance = 0;
        isImproper = false;
        isNew = true;
        importRecordId = null;
        this.register = register;
        idRegister = register.getId();
        merchant = null;
        idMerchant = null;
        this.merchantPayee = merchantPayee;

        // Add this transaction to the history list:
        TransactionHistory.getInstance().add(this);
    }


    /*
     * Load and save methods:
     */
    public static Transaction getById(UUID idTransaction) throws EntityException, SQLException {
        return new Transaction(getRSById(getSelectQuery() + " where tr.idTransaction =", idTransaction,
                "Database error encountered trying to retrieve a transaction."));
    }

    public static Transaction getByImportRecordId(String importRecordId) throws EntityException, SQLException {
        ResultSet rs = getRS(getSelectQuery() + " where tr.importRecordId = \"" + importRecordId + "\"",
                "Database error encountered trying to retrieve a transaction by importRecordId.");
        Transaction transaction = null;
        if (rs.next()) {
            transaction = new Transaction(rs);
        }
        return transaction;
    }

    public void loadFromResultSet(ResultSet rs) throws SQLException {

        id = UUID.fromString(rs.getString("tr.idTransaction"));
        postDate = Utility.localDateToCalendarDate(rs.getObject("tr.postDate", LocalDate.class));
        authorizationDate = Utility.localDateToCalendarDate(rs.getObject("tr.authorizationDate", LocalDate.class));
        cleared = rs.getBoolean("tr.cleared");
        checkNumber = rs.getInt("tr.checkNumber");
        payee = rs.getString("tr.payee");
        amount = rs.getDouble("tr.amount");
        balance = rs.getDouble("tr.balance");
        isImproper = rs.getBoolean("tr.isImproper");
        isNew = rs.getBoolean("tr.isNew");
        importRecordId = rs.getString("tr.importRecordId");
        idRegister = UUID.fromString(rs.getString("tr.idRegister"));
        idMerchant = UUID.fromString(rs.getString("tr.idMerchant"));
    }


    /*
     * Helper methods:
     */
    public String toStringSummary() {
        String authDate = (authorizationDate != null) ? "\n\tAuthorization date = " +
                Utility.calendarDateToStringDate(authorizationDate) : "";
        String checkNumberString = (checkNumber != 0) ? "\n\tCheck number = " + checkNumber : "";
        String merchantName = (merchant != null) ? merchant.getName() : "not assigned yet";
        String s = null;
        s = "\tPost date = " + Utility.calendarDateToStringDate(postDate) +
                authDate +
                "\n\tMerchant = " + merchantName +
                "\n\tamount = " + formatDollarAmount(amount) +
                "\n\tCleared = " + cleared +
                "\n\tOriginal Payee = " + payee +
                checkNumberString;
        return s;
    }

    public String toString() {
        String s = null;
        try {
            String merchantName;
            if (merchant != null) {
                merchantName = merchant.getName();
            } else {
                merchantName = "null";
            }
            String registerName;
            if (register != null) {
                registerName = register.getName();
            } else {
                registerName = "null";
            }
            s = "Transaction:  Post date = " + Utility.calendarDateToStringDate(postDate) + ", Authorization date = " +
                    Utility.calendarDateToStringDate(authorizationDate) + ", Cleared = " + cleared + ", Check number = " +
                    checkNumber + ", Merchant = " + merchantName + ", amount = " + formatDollarAmount(amount) +
                    ",\n\tPayee = " + payee + ", Balance = " + balance + ", Register = " + getRegister().getName()
                    + ", Merchant payee = " + merchantPayee + ", Disputed = " + isImproper + ", isNew = " + isNew;
        } catch (EntityException | SQLException | RegisterException e) {
            e.printStackTrace();
        }
        return s;
    }

    public String toStringConcise() {
        String checkNumberString = (checkNumber != 0) ? "Check number = " + checkNumber + ", " : "";
        String authDate = (authorizationDate != null) ? ", Authorized = " +
                Utility.calendarDateToStringDate(authorizationDate) : "";
        String merchantName = (merchant != null) ? merchant.getName() : "not assigned yet";
        String s = null;
        s = checkNumberString +
                "Posted = " + Utility.calendarDateToStringDate(postDate) +
                authDate +
                ", Merchant = " + merchantName +
                ", Amount = " + formatDollarAmount(amount) +
                ", Original Payee = " + payee;
        return s;
    }

    public String toStringVeryConcise() throws Exception {
        String date =
            (authorizationDate != null) ?
                Utility.calendarDateToStringDate(authorizationDate) :
                Utility.calendarDateToStringDate(postDate);

        String merchantName = "";
        if (merchant == null) {
            if (idMerchant != null) {
                merchant = Merchant.getById(idMerchant);
            }
        }
        merchantName = (merchant != null) ? merchant.getName() : "not assigned yet";
        String s =
                "Date = " + date +
                ", Merchant = " + merchantName +
                ", Amount = " + formatDollarAmount(amount);
        return s;
    }

    public String provisionalToString() {
        String s = null;
        try {
            String postDateString = "\tPost date = " + Utility.calendarDateToStringDate(postDate) + "\n";
            String checkNumberString = (checkNumber != 0) ? "\tCheck number = " + checkNumber + "\n" : "";
            Merchant merchant = getMerchant();
            String merchantString = (merchant != null) ? "\tMerchant = " + merchant.getName() + "\n" : "";
            String amountString = "\tAmount = " + formatDollarAmount(amount) + "\n";
            String balanceString = (balance > 0.0) ? "\t" + formatDollarAmount(balance) + "\n" : "";
            Register register = getRegister();
            String registerNameString = (register != null) ? "\tRegister name = " + register.getName() + "\n" : "";
            String merchantPayeeString = (merchantPayee != null) ? "\tMerchant payee = " + merchantPayee + "\n" : "";
            String disputedString = (isImproper) ? "\tDisputed transaction.\n" : "";
            String isNewString = (isNew) ? "\tNew Transaction.\n" : "";
            s = new StringBuilder().append("Transaction:  ").append(importRecordId).append("\n").append(postDateString).
                    append(checkNumberString).append(merchantString).append(amountString).append(balanceString).
                    append(registerNameString).append(merchantPayeeString).append(disputedString).append(isNewString).toString();
        } catch (EntityException | SQLException | RegisterException e) {
            e.printStackTrace();
        }
        return s;
    }


    /*
     * Main methods:
     */
    // Find provisional transactions based on the merchant and amount and return the first one found:
    public static Transaction getFirstProvisionalTransaction(UUID idMerchant, double amount) throws EntityException,
            SQLException {

        ResultSet rs = getRS(getSelectQuery() + " where tr.Merchant_idMerchant = uuid_to_bin('" + idMerchant +
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


    // Get a list of transactions that have not been previously reported on:
    public static ResultSet getNewTransactions(Register register) throws EntityException {
        String query = getSelectQuery() + " where tr.isNew = true order by tr.authorizationDate asc";
        return getRS(query, "attempting to retrieve a list of transactions that were not " +
                "reported on in a previous new transactions report.");
    }

    /**
     * Find out if there are transactions that were skipped with respect to a particular forecast during the
     * importRegisterTransactions() process.  We know they were skipped because the transactions have not been
     * reconciled to the specified forecast.
     *
     * @param forecast The forecast that the transactions were skipped in.
     * @return
     * @throws EntityException
     * @throws SQLException
     */
    public static boolean isSkippedTransactionsWrtForecast(Forecast forecast) throws EntityException, SQLException,
            BudgetException, RegisterException {
        Calendar startDate = forecast.getStartDate();
        Calendar fourMonthsAgo = Calendar.getInstance();
        fourMonthsAgo.add(Calendar.MONTH, -4);
        if (fourMonthsAgo.after(startDate)) startDate = fourMonthsAgo;
        String query = getCountQuery() + " " +
                "where tr.postDate >= " + Utility.calendarDateToSqlDateString(fourMonthsAgo) + " and " +
                "tr.Register_idRegister = uuid_to_bin('" + forecast.getBudget().getRegisters().get(0).getId() + "') and " +
                "tr.idTransaction not in " +
                "(select idTransaction from transaction " +
                "inner join transaction_split on idTransaction = Transaction_idTransaction " +
                "inner join forecast_transaction_split on Transaction_idTransaction = Transaction_Split_idTransaction and " +
                "BudgetItem_idBudgetItem = Transaction_Split_idBudgetitem " +
                ") " +
                "order by postDate asc";

        ResultSet rs = getRS(query, "attempting to retrieve a count of the transactions that were previously " +
                "skipped during the import process.");
        rs.next();
        int count = rs.getInt(1);
        return (count > 0) ? true : false;
    }

    /**
     * Get a list of transactions that were skipped with respect to a particular forecast during the
     * importRegisterTransactions() process.  We know they were skipped because the transactions have not been
     * reconciled to the specified forecast.
     *
     * @param forecast The forecast that the transactions were skipped in.
     * @return
     * @throws EntityException
     */
    public static ResultSet getSkippedTransactionsWrtForecast(Forecast forecast) throws EntityException, BudgetException,
            SQLException, RegisterException {
        Calendar startDate = forecast.getStartDate();
        Calendar fourMonthsAgo = Calendar.getInstance();
        fourMonthsAgo.add(Calendar.MONTH, -4);
        if (fourMonthsAgo.after(startDate)) startDate = fourMonthsAgo;
        String query = getSelectQuery() + " " +
                "where tr.postDate >= " + Utility.calendarDateToSqlDateString(fourMonthsAgo) + " and " +
                "tr.Register_idRegister = uuid_to_bin('" + forecast.getBudget().getRegisters().get(0).getId() + "') and " +
                "tr.idTransaction not in " +
                "(select idTransaction from transaction " +
                "inner join transaction_split on idTransaction = Transaction_idTransaction " +
                "inner join forecast_transaction_split on Transaction_idTransaction = Transaction_Split_idTransaction and " +
                "BudgetItem_idBudgetItem = Transaction_Split_idBudgetitem " +
                ") " +
                "order by authorizationDate asc";
        return getRS(query, "attempting to retrieve a list of transactions that were previously " +
                "skipped during the import process.");
    }


    // Combine a cleared transaction with an uncleared transaction:
    public Boolean reconcileWithProvisional() throws EntityException, SQLException {
        Boolean result = false;
        Transaction provisionalTransaction = getFirstProvisionalTransaction(merchant.getId(), amount);
        if (provisionalTransaction != null) {
            setId(provisionalTransaction.getId());
            result = true;
        }
        return result;
    }

    // Set a list of transactions to not new:
    public static void setTransactionsNotNew(List<Transaction> transactions) throws SQLException, EntityException {
        for (Transaction transaction : transactions
        ) {
            transaction.setIsNew(false);
            transaction.save(SaveMethod.UPDATE);
        }
    }

    /**
     * Get the most recent transaction by payee.
     *
     * @param payee The payee to search for.
     * @return The most recent transaction by payee.  Null if no transaction is found.
     */
    public static Transaction getMostRecentTransactionByPayee(String payee, double amount) throws Exception {

        // Create the SQL query to get the most recent transaction by payee ignoring the REF #:
        String query =
            getSelectQuery() + " " +
                "INNER JOIN merchant m on " +
                    "tr.Merchant_idMerchant = m.idMerchant " +
                "WHERE " +
                    "tr.payee LIKE '" + payee.replaceAll("REF #\\S+", "REF #%" ) + "' " +
                    "AND tr.amount BETWEEN " + (amount * 0.9) + " AND " + (amount * 1.1) + " " +
                "ORDER BY " +
                    "tr.postDate DESC " +
                "lIMIT 1";

        // Execute the query and return the result set:
        ResultSet rs = getRS(query, "attempting to retrieve the most recent transaction by payee.");
        if (rs.next()) {
            return new Transaction(rs);
        } else {
            return null;
        }
    }

    /**
     * Get similar transactions by user entered description.
     *
     * @param userDescription The payee to search for.
     * @return Up to 10 similar transactions.  Null if no transaction is found.
     */
    public static List<Transaction> getByUserDescriptionFullText(String userDescription) throws Exception {

        // Create the SQL query to get the most similar transactions using a full text search on the payee description:
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

        // Execute the query:
        ResultSet rs = getRS(query, "attempting to retrieve similar transactions by payee.");

        // Create a list to hold the transactions:
        List<Transaction> transactions = new ArrayList<>();

        // Loop through the result set and add each transaction to the list:
        while (rs.next()) {
            transactions.add(new Transaction(rs));
        }

        return transactions;
    }
}

