package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.Entity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import lombok.Getter;
import lombok.Setter;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.hixon.financialApp.utility.Utility.getView;

public class Register extends IndependentEntity {
    /*
     * Statics and constants:
     */
    public static final String CHECKING = "Checking";
    public static final String SAVINGS = "Savings";

    /*
     * Fields in the Register class:
     */
    @Getter
    @Setter
    private String name = null;
    @Getter
    private String nickname = null;
    @Getter
    @Setter
    private String accountType = null;
    @Getter
    @Setter
    private String default_view;
    @Getter
    @Setter
    private String accountNumber = null;
    @Getter
    @Setter
    private double balance = 0;
    @Getter
    @Setter
    private double skippedAmount = 0;
    @Getter
    @Setter
    private String financialInstitution = null;
    @Getter
    @Setter
    private String trxImportFileName = null;
    @Getter
    @Setter
    private String trxImportFileDirectory = null;
    @Getter
    @Setter
    private String provisionalTrxFileName = null;
    @Getter
    @Setter
    private String provisionalTrxFileDirectory = null;
    @Getter
    @Setter
    private UUID idBudget = null;
    @Getter
    @Setter
    private List<Transaction> significantEvents = new ArrayList<>();
    protected ViewInt view = null;
    protected NotificationServiceInt notificationService = null;


    /*
     * Getters and setters:
     */
    public UUID getId() {
        return id;
    }

    public String getReportType() {
        return default_view;
    }

    public void setDefaultView(String default_view) {
        this.default_view = default_view;
    }

    public String getTrxImportFilePath() {
        return getTrxImportFileDirectory() + "\\" + getTrxImportFileName();
    }

    public String getProvisionalTrxFilePath() {
        return getProvisionalTrxFileDirectory() + "\\" + getProvisionalTrxFileName();
    }

    public UUID getBudgetID() {
        return idBudget;
    }


    /*
     * Database CRUD methods:
     */
    private static final String selectQuery = "select bin_to_uuid(r.idRegister) as 'r.idRegister', r.name as 'r.name', " +
            "r.nickname as 'r.nickname', r.account_type as 'r.account_type', r.default_view as 'default_view', " +
            "r.account_number as 'r.account_number', r.balance as 'r.balance', r.skippedAmount as 'r.skippedAmount', " +
            "r.financialInstitution as 'r.financialInstitution', r.trxImportFileName as 'r.trxImportFileName', " +
            "r.trxImportFileDirectory as 'r.trxImportFileDirectory', r.provisionalTrxFileName as 'r.provisionalTrxFileName', " +
            "r.provisionalTrxFileDirectory as 'r.provisionalTrxFileDirectory', bin_to_uuid(r.Budget_idBudget) as 'r.idBudget' " +
            "from register r";

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
        return "name = '" + name + "', nickname = '" + nickname + "', account_type = '" + accountType + "', " +
                "default_view = '" + default_view + "', account_number = '" + accountNumber + "', balance = " +
                balance + ", skippedAmount = " + skippedAmount + ", financialInstitution = '" + financialInstitution +
                "', trxImportFileName = '" + trxImportFileName + "', trxImportFileDirectory = '" +
                Utility.doubleBackSlashes(trxImportFileDirectory) + "', provisionalTrxFileName = '" +
                provisionalTrxFileName + "', provisionalTrxFileDirectory = '" +
                Utility.doubleBackSlashes(provisionalTrxFileDirectory) + "', Budget_idBudget = uuid_to_bin('" + idBudget + "') " +
                "where idRegister = uuid_to_bin('" + id + "')";
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
                this.nickname = rs.getString("r.nickname");
                this.accountType = rs.getString("r.account_type");
                this.default_view = rs.getString("r.default_view");
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
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Register register = (Register) o;
        return id.equals(register.id); // identity defined as the primary keys match.
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // or consistent with equals
    }

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

    /**
     * Create a display string for the register.
     *
     * @return Display string for the register object.
     */
    @Override
    public String toString() {
        return "Register: " + getName() + " (" + getNickname() + ") " +
                "Account Type: " + getAccountType() + ", " +
                "Account Number: " + getAccountNumber() + ", " +
                "Balance: " + Utility.formatDollarAmount(getBalance()) + ", " +
                "Skipped Amount: " + Utility.formatDollarAmount(getSkippedAmount()) + ", " +
                "Financial Institution: " + getFinancialInstitution() + ", " +
                "Transaction Import File Name: " + getTrxImportFileName() + ", " +
                "Transaction Import File Directory: " + getTrxImportFileDirectory() + ", " +
                "Provisional Transaction File Name: " + getProvisionalTrxFileName() + ", " +
                "Provisional Transaction File Directory: " + getProvisionalTrxFileDirectory();
    }

    // Create a concise display string for the register.
    public String toStringConcise() {
        return "Register: " + getName() + " (" + getNickname() + ") " +
                "Account Type: " + getAccountType() + ", " +
                "Account Number: " + getAccountNumber() + ", " +
                "Financial Institution: " + getFinancialInstitution();
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
        String query = selectQuery + " where r.name = \"" + registerName + "\"";
        try {
            preparedStmt = Utility.getDbConnection().prepareStatement(query);
            rs = preparedStmt.executeQuery();
            Register register = null;
            if (rs != null && rs.next()) {
                register = new Register(rs);
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

        // Get a result set of the transactions that haven't been reported on before:
        ResultSet rs = TransactionUtilities.getNewTransactions(register);

        // Then for each transaction in the result set:
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
    public static void setTransactionsToNotNew(Register register) throws EntityException, RegisterException {
        EntityInt.executeUpdate(Transaction.getUpdateIsNewQuery() + register.getId() + "')",
                "updated the transactions in Register " + register.getName() + " to not new.");
    }

    /**
     * Check to see if there are skipped transactions in this register from previous update runs:
     *
     * @return True if there are skipped transactions.  Otherwise, false.
     */
    public boolean isSkippedTransactions(Forecast forecast) throws SQLException, EntityException, BudgetException,
            RegisterException {
        return TransactionUtilities.isSkippedTransactionsWrtForecast(forecast);
    }

    /**
     * Get a list of registers that are owned by the user:
     *
     * @return List<Entity>  A list of transactions.
     */
    public static List<Register> getListOfByUserAndType(User user, String accountType) throws SQLException,
            EntityException, RegisterException {

        final List<Register> items = new ArrayList<>();

        StringBuilder query = new StringBuilder(selectQuery);

        if (user != null) {
            query.append(" INNER JOIN user_register ur ON r.idRegister = ur.register_idRegister")
                    .append(" INNER JOIN user u ON ur.user_idUser = u.idUser");
        }

        boolean hasWhere = false;
        if (user != null || (accountType != null && !accountType.isEmpty())) {
            query.append(" WHERE");
            if (user != null) {
                query.append(" u.idUser = UUID_TO_BIN('").append(user.getId()).append("')");
                hasWhere = true;
            }
            if (accountType != null && !accountType.isEmpty()) {
                if (hasWhere) {
                    query.append(" AND");
                }
                query.append(" r.account_type = '").append(accountType).append("'");
            }
        }

        // Get a result set of the transactions that haven't been reported on before:
        ResultSet rs = EntityInt.getRS(query.toString(), "attempting to retrieve a list of registers by user and type.");

        // Then add each transaction in the result set to the list of new transactions:
        while (rs.next()) {
            items.add(new Register(rs));
        }

        return items;
    }
}
