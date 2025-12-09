package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.TransactionHistory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.UUID;

import static com.hixon.financialApp.model.entity.EntityInt.getRS;
import static com.hixon.financialApp.model.entity.EntityInt.getRSById;
import static com.hixon.financialApp.utility.Utility.formatDollarAmount;
import static com.hixon.financialApp.utility.Utility.stringDateSlashToCalendarDate;

/**
 * Represents a financial transaction in a register.
 * Handles transaction data, persistence, and utility methods for transaction management.
 */
public class Transaction extends IndependentEntity {

    /*
     * Statics and Constants:
     */
    /** File name for cleared transactions. */
    public static final String CLEARED_TRANSACTIONS_FILE = "cleared transactions";
    /** File name for provisional transactions. */
    public static final String PROVISIONAL_TRANSACTIONS_FILE = "provisional transactions";

    /*
     * Fields of the Transaction class:
     */
    /** The post date of the transaction. */
    private Calendar postDate = null;
    /** The authorization date of the transaction. */
    private Calendar authorizationDate;
    /** Whether the transaction is cleared. */
    private boolean cleared = false;
    /** The check number associated with the transaction. */
    private int checkNumber = 0;
    /** The payee for the transaction. */
    private String payee = null;
    /** The amount of the transaction. */
    private double amount = 0;
    /** The balance after the transaction. */
    private double balance = 0;
    /** The UUID of the register associated with the transaction. */
    private UUID idRegister = null;
    /** The register object associated with the transaction. */
    private Register register = null;
    /** The UUID of the merchant associated with the transaction. */
    private UUID idMerchant = null;
    /** The merchant payee string. */
    private String merchantPayee = null;
    /** Whether the transaction is improper/disputed. */
    private boolean isImproper = false;
    /** Whether the transaction is new. */
    private boolean isNew = true;
    /** The import record ID for the transaction. */
    private String importRecordId = null;
    /** The merchant object associated with the transaction. */
    private Merchant merchant;


    private static final String selectColumns = "bin_to_uuid(tr.idTransaction) as 'tr.idTransaction', " +
            "tr.postDate as 'tr.postDate', tr.authorizationDate as 'tr.authorizationDate', tr.amount as 'tr.amount', " +
            "tr.cleared as 'tr.cleared', tr.checkNumber as 'tr.checkNumber', tr.payee as 'tr.payee', " +
            "tr.balance as 'tr.balance', tr.isImproper as 'tr.isImproper', tr.isNew as 'tr.isNew', " +
            "tr.importRecordId as 'tr.importRecordId', bin_to_uuid(tr.Register_idRegister) as 'tr.idRegister', " +
            "bin_to_uuid(tr.Merchant_idMerchant) as 'tr.idMerchant'";

    /**
     * Returns the columns used in select queries for transactions.
     * @return SQL select columns string
     */
    public static String getSelectColumns() {
        return selectColumns;
    }

    private static final String selectQuery = "select " + getSelectColumns() + " from transaction tr";

    /**
     * Returns the SQL select query for transactions.
     * @return SQL select query string
     */
    public static String getSelectQuery() {
        return selectQuery;
    }

    private static final String countQuery = "select count(*) from transaction tr";

    /**
     * Returns the SQL count query for transactions.
     * @return SQL count query string
     */
    public static String getCountQuery() {
        return countQuery;
    }

    private static final String insertQuery = "insert into transaction (idTransaction, " +
            "postDate, authorizationDate, amount, cleared, checkNumber, payee, balance, isImproper, isNew, " +
            "importRecordId, Register_idRegister, Merchant_idMerchant) values(";

    /**
     * Returns the SQL insert query for this transaction.
     * @return SQL insert query string
     */
    @Override
    public String getInsertQuery() {
        return insertQuery + "uuid_to_bin('" + id + "'), " + Utility.calendarDateToSqlDateString(postDate) + ", " +
                Utility.calendarDateToSqlDateString(authorizationDate) + ", " + amount + ", " + cleared + ", " +
                checkNumber + ", \"" + payee + "\", " + balance + ", " + isImproper + ", " + isNew + ", \"" +
                importRecordId + "\", uuid_to_bin('" + getIdRegister() + "'), uuid_to_bin('" + getIdMerchant() + "'))";
    }

    /**
     * Returns the SQL insert or update query for this transaction.
     * @return SQL insert or update query string
     * @throws BudgetException if a budget error occurs
     */
    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
        return getInsertQuery() + " on duplicate key update postDate = " + Utility.calendarDateToSqlDateString(postDate) +
                ", authorizationDate = " + Utility.calendarDateToSqlDateString(authorizationDate) + ", amount = " + amount
                + ", cleared = " + cleared + ", checkNumber = " + checkNumber + ", payee = \"" + payee + "\", balance = "
                + balance + ", isImproper = " + isImproper + ", isNew = " + isNew +
                ", importRecordId = \"" + importRecordId + "\", Register_idRegister = uuid_to_bin('" + getIdRegister() + "'), " +
                "Merchant_idMerchant = uuid_to_bin('" + getIdMerchant() + "')";
    }

    private static final String updateQuery =
            "update transaction set ";

    /**
     * Returns the SQL update query for setting isNew to false by register.
     * @return SQL update query string
     */
    public static String getUpdateIsNewQuery() {
        return "update transaction set isNew = false where register_idRegister = uuid_to_bin('";
    }

    /**
     * Validates the fields of the transaction.
     * @return true if the transaction is valid
     */
    @Override
    public boolean isValid() { return true; }

    /**
     * Returns the SQL update query for this transaction by ID.
     * @return SQL update query string
     */
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

    /**
     * Returns the SQL delete query for this transaction by ID.
     * @return SQL delete query string
     */
    @Override
    public String getDeleteByIdQuery() {
        return deleteQuery + "idTransaction = uuid_to_bin('" + id + "')";
    }

    /**
     * Returns the printable type name for this entity.
     * @return printable type name
     */
    @Override
    public String getPrintableTypeName() {
        return getPrintableTypeName_static();
    }

    /**
     * Returns the printable type name for transactions.
     * @return printable type name
     */
    public static String getPrintableTypeName_static() {
        return "transaction";
    }

    /*
     * Getters and setters:
     */

    /**
     * Sets the transaction ID.
     * @param idTransaction the transaction UUID
     */
    public void setIdTransaction(UUID idTransaction) {
        this.id = idTransaction;
    }

    /**
     * Gets the post date.
     * @return post date
     */
    public Calendar getPostDate() {
        return postDate;
    }

    /**
     * Sets the post date.
     * @param postDate the post date
     */
    public void setPostDate(Calendar postDate) {
        this.postDate = postDate;
    }

    /**
     * Checks if the transaction is cleared.
     * @return true if cleared
     */
    public boolean isCleared() {
        return cleared;
    }

    /**
     * Sets the cleared status.
     * @param cleared true if cleared
     */
    public void setCleared(boolean cleared) {
        this.cleared = cleared;
    }

    /**
     * Gets the check number.
     * @return check number
     */
    public int getCheckNumber() {
        return checkNumber;
    }

    /**
     * Sets the check number.
     * @param checkNumber the check number
     */
    public void setCheckNumber(int checkNumber) {
        this.checkNumber = checkNumber;
    }

    /**
     * Gets the payee.
     * @return payee
     */
    public String getPayee() {
        return payee;
    }

    /**
     * Sets the payee.
     * @param payee the payee
     */
    public void setPayee(String payee) {
        this.payee = payee;
    }

    /**
     * Gets the transaction amount.
     * @return amount
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Sets the transaction amount.
     * @param amount the amount
     */
    public void setAmount(double amount) {
        this.amount = amount;
    }

    /**
     * Gets the balance after the transaction.
     * @return balance
     */
    public double getBalance() {
        return balance;
    }

    /**
     * Sets the balance after the transaction.
     * @param balance the balance
     */
    public void setBalance(double balance) {
        this.balance = balance;
    }

    /**
     * Gets the register UUID.
     * @return register UUID
     */
    public UUID getIdRegister() {
        if (idRegister == null) {
            idRegister = register.getId();
        }
        return idRegister;
    }

    /**
     * Sets the register UUID.
     * @param idRegister register UUID
     */
    public void setIdRegister(UUID idRegister) {
        this.idRegister = idRegister;
    }

    /**
     * Gets the register object.
     * @return register
     * @throws EntityException if a database error occurs
     * @throws SQLException if a SQL error occurs
     * @throws RegisterException if a register error occurs
     */
    public Register getRegister() throws EntityException, SQLException, RegisterException {
        if (register == null) {
            register = Register.getById(idRegister);
        }
        return register;
    }

    /**
     * Sets the register object.
     * @param register the register
     */
    public void setRegister(Register register) {
        this.register = register;
    }

    /**
     * Gets the authorization date.
     * @return authorization date
     */
    public Calendar getAuthorizationDate() {
        return authorizationDate;
    }

    /**
     * Gets the effective date (authorization or post date).
     * @return effective date
     */
    public Calendar getDate() {
        return (authorizationDate != null) ? authorizationDate : postDate;
    }

    /**
     * Sets the authorization date.
     * @param authorizationDate the authorization date
     */
    public void setAuthorizationDate(Calendar authorizationDate) {
        this.authorizationDate = authorizationDate;
    }

    /**
     * Gets the merchant UUID.
     * @return merchant UUID
     */
    public UUID getIdMerchant() {
        if (idMerchant == null && merchant != null) {
            idMerchant = merchant.getId();
        }
        return idMerchant;
    }

    /**
     * Sets the merchant UUID.
     * @param idMerchant merchant UUID
     */
    public void setIdMerchant(UUID idMerchant) {
        this.idMerchant = idMerchant;
    }

    /**
     * Gets the merchant object.
     * @return merchant
     * @throws EntityException if a database error occurs
     * @throws RegisterException if a register error occurs
     */
    public Merchant getMerchant() throws EntityException, RegisterException {
        if (merchant == null && idMerchant != null && !idMerchant.equals("null")) {
            merchant = Merchant.getById(idMerchant);
        }
        return merchant;
    }

    /**
     * Sets the merchant object.
     * @param merchant the merchant
     */
    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    /**
     * Sets the merchant payee string.
     * @param merchantPayee merchant payee
     */
    public void setMerchantPayee(String merchantPayee) {
        this.merchantPayee = merchantPayee;
    }

    /**
     * Gets the merchant payee string.
     * @return merchant payee
     */
    public String getMerchantPayee() {
        return merchantPayee;
    }

    /**
     * Checks if the transaction is improper/disputed.
     * @return true if improper
     */
    public boolean getIsImproper() {
        return isImproper;
    }

    /**
     * Sets the improper/disputed status.
     * @param isImproper true if improper
     */
    public void setIsImproper(boolean isImproper) {
        this.isImproper = isImproper;
    }

    /**
     * Checks if the transaction is new.
     * @return true if new
     */
    public boolean getIsNew() {
        return isNew;
    }

    /**
     * Sets the new status.
     * @param isNew true if new
     */
    public void setIsNew(boolean isNew) {
        this.isNew = isNew;
    }

    /**
     * Gets the import record ID.
     * @return import record ID
     */
    public String getImportRecordId() {
        return importRecordId;
    }

    /**
     * Sets the import record ID.
     * @param importRecordId import record ID
     */
    public void setImportRecordId(String importRecordId) {
        this.importRecordId = importRecordId;
    }

    /*
     * Constructors:
     */

    /**
     * Constructs a new transaction from a register transaction CSV record.
     * @param register the register
     * @param postDate the post date
     * @param payee the payee
     * @param amount the amount
     * @param cleared whether cleared
     * @param checkNumber the check number
     * @param importRecordId the import record ID
     */
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

    /**
     * Constructs a transaction from a ResultSet record.
     * @param rs the ResultSet containing transaction data
     * @throws SQLException if a SQL error occurs
     */
    public Transaction(ResultSet rs) throws SQLException {
        super(false);
        loadFromResultSet(rs);

        // Add this transaction to the history list:
        TransactionHistory.getInstance().add(this);
    }

    /**
     * Constructs a provisional transaction from a CSV record.
     * @param register the register
     * @param postDate the post date string
     * @param payee the payee
     * @param amount the amount
     * @param merchantPayee the merchant payee
     * @throws ParseException if the date cannot be parsed
     */
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

    /**
     * Retrieves a transaction by its UUID.
     * @param idTransaction the transaction UUID
     * @return the Transaction object
     * @throws EntityException if a database error occurs
     * @throws SQLException if a SQL error occurs
     */
    public static Transaction getById(UUID idTransaction) throws EntityException, SQLException {
        return new Transaction(getRSById(getSelectQuery() + " where tr.idTransaction =", idTransaction,
                "Database error encountered trying to retrieve a transaction."));
    }

    /**
     * Retrieves a transaction by its import record ID.
     * @param importRecordId the import record ID
     * @return the Transaction object, or null if not found
     * @throws EntityException if a database error occurs
     * @throws SQLException if a SQL error occurs
     */
    public static Transaction getByImportRecordId(String importRecordId) throws EntityException, SQLException {
        ResultSet rs = getRS(getSelectQuery() + " where tr.importRecordId = \"" + importRecordId + "\"",
                "Database error encountered trying to retrieve a transaction by importRecordId.");
        Transaction transaction = null;
        if (rs.next()) {
            transaction = new Transaction(rs);
        }
        return transaction;
    }

    /**
     * Loads transaction data from a ResultSet.
     * @param rs the ResultSet
     * @throws SQLException if a SQL error occurs
     */
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

    /**
     * Returns a summary string representation of the transaction.
     * @return summary string
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

    /**
     * Returns a detailed string representation of the transaction.
     * @return detailed string
     */
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

    /**
     * Returns a concise string representation of the transaction.
     * @return concise string
     */
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

    /**
     * Returns a very concise string representation of the transaction.
     * @return very concise string
     * @throws Exception if a database or query error occurs
     */
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

    /**
     * Returns a string representation for provisional transactions.
     * @return provisional transaction string
     */
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

    /**
     * Reconciles this transaction with a provisional transaction if one exists.
     * @return true if reconciliation was successful, false otherwise
     * @throws EntityException if a database or entity error occurs
     * @throws SQLException if a SQL error occurs
     */
    public Boolean reconcileWithProvisional() throws EntityException, SQLException {
        Boolean result = false;
        Transaction provisionalTransaction = TransactionUtilities.getFirstProvisionalTransaction(merchant.getId(), amount);
        if (provisionalTransaction != null) {
            setId(provisionalTransaction.getId());
            result = true;
        }
        return result;
    }
}
