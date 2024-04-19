package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import lombok.Getter;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.utility.Utility.getView;

public class Register extends IndependentEntity {
    /*
     * Statics and constants:
     */


    /*
     * Fields in the Register class:
     */
    private String name = null;
    private String accountType = null;
    private String accountNumber = null;
    private double balance = 0;
    @Getter
    private double skippedAmount = 0;
    @Getter
    private String financialInstitution = null;
    @Getter
    private String trxImportFileName = null;
    @Getter
    private String trxImportFileDirectory = null;
    @Getter
    private String provisionalTrxFileName = null;
    @Getter
    private String provisionalTrxFileDirectory = null;
    private UUID idBudget = null;
    private List<Transaction> significantEvents = new ArrayList<>();
    protected ViewInt view = null;
    protected NotificationServiceInt notificationService = null;



    /*
     * Getters and setters:
     */
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getAccountType() {
        return accountType;
    }
    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getAccountNumber() {
        return accountNumber;
    }
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public double getBalance() {
        return balance;
    }
    public void setBalance(double balance) {
        this.balance = balance;
    }

    public double getSkippedAmount() {
        return skippedAmount;
    }
     public void setSkippedAmount(double skippedAmount) {
        this.skippedAmount = skippedAmount;
    }

    public String getFinancialInstitution() {
        return financialInstitution;
    }
    public void setFinancialInstitution(String financialInstitution) {
        this.financialInstitution = financialInstitution;
    }

    public String getTrxImportFileName() {
        return trxImportFileName;
    }
    public void setTrxImportFileName(String trxImportFileName) {
        this.trxImportFileName = trxImportFileName;
    }

    public String getTrxImportFileDirectory() {
        return trxImportFileDirectory;
    }
    public void setTrxImportFileDirectory(String trxImportFileDirectory) {
        this.trxImportFileDirectory = trxImportFileDirectory;
    }
    public String getTrxImportFilePath() {
        return getTrxImportFileDirectory() + "\\" + getTrxImportFileName();
    }

    public String getProvisionalTrxFileName() {
        return provisionalTrxFileName;
    }

    public void setProvisionalTrxFileName(String provisionalTrxFileName) {
        this.provisionalTrxFileName = provisionalTrxFileName;
    }

    public String getProvisionalTrxFileDirectory() {
        return provisionalTrxFileDirectory;
    }
    public void setProvisionalTrxFileDirectory(String provisionalTrxFileDirectory) {
        this.provisionalTrxFileDirectory = provisionalTrxFileDirectory;
    }

    public String getProvisionalTrxFilePath() {
        return getProvisionalTrxFileDirectory() + "\\" + getProvisionalTrxFileName();
    }

    public UUID getBudgetID() {
        return idBudget;
    }

    public void setIdBudget(UUID idBudget) {
        this.idBudget = idBudget;
    }

    public void setSignificantEvents(List<Transaction> significantEvents) {
        this.significantEvents = significantEvents;
    }

    public List<Transaction> getSignificantEvents() {
        return significantEvents;
    }


    /*
     * Database CRUD methods:
     */
    private static final String selectQuery = "select bin_to_uuid(r.idRegister) as 'r.idRegister', r.name as 'r.name', " +
            "r.account_type as 'r.account_type', r.account_number as 'r.account_number', r.balance as 'r.balance', " +
            "r.skippedAmount as 'r.skippedAmount', r.financialInstitution as 'r.financialInstitution', " +
            "r.trxImportFileName as 'r.trxImportFileName', r.trxImportFileDirectory as 'r.trxImportFileDirectory', " +
            "r.provisionalTrxFileName as 'r.provisionalTrxFileName', r.provisionalTrxFileDirectory as 'r.provisionalTrxFileDirectory', " +
            "bin_to_uuid(r.Budget_idBudget) as 'r.idBudget' from register r";

    public static String getSelectQuery() {
        return selectQuery;
    }

    @Override
    public String getInsertQuery() throws BudgetException, ForecastException {
        return null;
    }

    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
        return null;
    }

    // The update query:
    public static final String updateQuery = "update register set ";

    public static String getUpdateQuery() {
        return updateQuery;
    }

    public String getUpdateClause() {
        return "name = '" + name + "', account_type = '" + accountType + "', account_number = '" + accountNumber +
                "', balance = " + balance + ", skippedAmount = " + skippedAmount + ", financialInstitution = '" +
                financialInstitution + "', trxImportFileName = '"  + trxImportFileName + "', trxImportFileDirectory = '" +
                Utility.doubleBackSlashes(trxImportFileDirectory) + "', provisionalTrxFileName = '" +
                provisionalTrxFileName + "', provisionalTrxFileDirectory = '" +
                Utility.doubleBackSlashes(provisionalTrxFileDirectory) + "', Budget_idBudget = uuid_to_bin('" + idBudget
                + "') where idRegister = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getUpdateByIdQuery() throws BudgetException {
        return getUpdateQuery() + getUpdateClause();
    }

    @Override
    public String getDeleteByIdQuery() {
        return null;
    }

    @Override
    public String getPrintableTypeName() {
        return getPrintableTypeName_static();
    }

    public static String getPrintableTypeName_static() {
        return "register";
    }


    /*
     * Constructors:
     */
    public Register() {
        super(true);
    }

    public Register(ResultSet rs) throws RegisterException, SQLException {
        super(false);
        try {
            if (rs != null) {

                this.id = UUID.fromString(rs.getString("r.idRegister"));
                this.name = rs.getString("r.name");
                this.accountType = rs.getString("r.account_type");
                this.accountNumber = rs.getString("r.account_number");
                this.balance = rs.getDouble("r.balance");
                this.skippedAmount = rs.getDouble("r.skippedAmount");
                this.financialInstitution = rs.getString("r.financialInstitution");
                this.trxImportFileName = rs.getString("r.trxImportFileName");
                this.trxImportFileDirectory = rs.getString("r.trxImportFileDirectory");
                this.provisionalTrxFileName = rs.getString("r.provisionalTrxFileName");
                this.provisionalTrxFileDirectory = rs.getString("r.provisionalTrxFileDirectory");
                this.idBudget = UUID.fromString(rs.getString("r.idBudget"));

            } else {
                throw new RegisterException("Result set passed into Register(rs) is empty or null.");
            }
        } catch (SQLException e) {
            System.out.println("[SEVERE]  SQL error encountered trying to create a register from a result set.");
            rs.close();
            throw e;
        }
    }


    /*
     * Helper methods:
     */
    public void addSignificantEvent(Transaction transaction) {
        significantEvents.add(transaction);
    }

    public void update() throws BudgetException, SQLException, EntityException, RegisterException {
        getView().say("Update Register call.  New balance = " + Utility.formatDollarAmount(getBalance()));
        super.update();
    }

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


    /*
     * Load and save methods:
     */
    public static Register getById(UUID idRegister) throws EntityException, SQLException, RegisterException {
        ResultSet rs = EntityInt.getRSById(selectQuery + " where r.idRegister = ", idRegister,
                "Database error encountered trying to retrieve register with id = " + idRegister);
        return new Register(rs);
    }

    public static Register getByLastFourDigits(String lastFourDigits) throws RegisterException {

        String query = selectQuery + " where r.Account_Number like '%" + lastFourDigits + "'";
        try {
            Statement statement = Utility.getDbConnection().createStatement();
            ResultSet rs = statement.executeQuery(query);
            if (rs.next()) {
                return new Register(rs);
            } else {
                return null;
            }
        } catch (SQLException e) {
            throw new RegisterException("Database error occurred trying to retrsieve a register with the " +
                    "sql statement " + query, e);
        }
    }

    public static Register getByName(String registerName) throws RegisterException, SQLException {
        // Find the ID of the named budget:
        PreparedStatement preparedStmt = null;
        ResultSet rs = null;
        String query = selectQuery + " where r.name = '" + registerName + "'";
        try {
            preparedStmt = Utility.getDbConnection().prepareStatement(query);
            rs = preparedStmt.executeQuery();
            Register register;
            if (rs != null && rs.next()) {
                register = new Register(rs);
            } else {
                throw new RegisterException("Register named " + registerName + " not found in the database.");
            }
            return register;
        } catch (SQLException e) {
            RegisterException re = new RegisterException("SQL error encountered trying to retrieve a list of registers.", e);
            if (preparedStmt != null) preparedStmt.close();
            if (rs != null) rs.close();
            throw re;
        }
    }

    public static List<Register> getListOf() throws RegisterException {

        try (Statement statement = Utility.getDbConnection().createStatement()) {

            ResultSet rs;
            rs = statement.executeQuery(selectQuery + " order by r.name");
            List<Register> registers = new ArrayList<>();
            while (rs.next()) {
                Register register = new Register(rs);
                registers.add(register);
            }
            return registers;

        } catch (SQLException | RegisterException e) {
            RegisterException re = new RegisterException("Database error occurred trying to retrieve a register with the " +
                    "sql statement " + selectQuery);
            re.initCause(e);
            throw re;
        }
    }


    /*
     * Main methods:
     */

    /**
     * Get a list of transactions that haven't been reported on before:
     *
     * @return List<Entity>  A list of transactions.
     */
    public static List<Entity> getNewTransactions(Register register) throws SQLException, EntityException {

        final List<Entity> items = new ArrayList<>();

        // Get a results set of the transactions that haven't been reported on before:
        ResultSet rs = Transaction.getNewTransactions(register);

        // Then for each transactions in the result set:
        while (rs.next()) {

            // add it to the list of new transactions:
            items.add(new Transaction(rs));
        }

        return items;
    }

    /**
     * Set the isNew flag for transactions in this register to false to reflect that the transactions have all been
     * reported on already.
     */
    public void setTransactionsToNotNew() throws EntityException, RegisterException {
        EntityInt.executeUpdate(Transaction.getUpdateIsNewQuery(), "updated the transactions in Register " +
                name + " to not new.");
    }

    /**
     * Check to see if there are skipped transactions in this register from previous update runs:
     *
     * @return True if there are skipped transactions.  Otherwise, false.
     */
    public boolean isSkippedTransactions(Forecast forecast) throws SQLException, EntityException, BudgetException,
            RegisterException {
        return Transaction.isSkippedTransactionsWrtForecast(forecast);
    }
}
