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
    @Setter
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

    // Cache for the resolved import file path to avoid redundant file searches
    private String cachedTrxImportFilePath = null;


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

    /**
     * Get the full path to the transaction import file.
     * If the filename contains a date pattern (YYYYMMDD), this method will search for
     * the most recently modified file matching the pattern.
     *
     * The result is cached to avoid redundant file system searches during the same import session.
     * Call clearTrxImportFilePathCache() to force a fresh search.
     *
     * @return The full path to the import file
     */
    public String getTrxImportFilePath() {
        // Return cached path if available
        if (cachedTrxImportFilePath != null) {
            return cachedTrxImportFilePath;
        }

        String directory = getTrxImportFileDirectory();
        String filename = getTrxImportFileName();

        // Check if filename contains date pattern (YYYYMMDD)
        if (filename != null && filename.contains("YYYYMMDD")) {
            // Find the most recent file matching the pattern
            String actualFile = findMostRecentMatchingFile(directory, filename);
            if (actualFile != null) {
                System.out.println("Pattern '" + filename + "' matched file: " + actualFile);
                cachedTrxImportFilePath = directory + "\\" + actualFile;
                return cachedTrxImportFilePath;
            } else {
                // No matching file found - warn user
                System.err.println("WARNING: No files found matching pattern '" + filename + "' in directory '" + directory + "'");
                System.err.println("Looking for files like: " + filename.replace("YYYYMMDD", "20251223"));
                System.err.println("Please download the QFX file from Barclays and place it in the directory, or update the register's import file name.");
            }
        }

        // Default behavior: return exact path (may not exist if pattern was specified)
        // Cache this result as well
        cachedTrxImportFilePath = directory + "\\" + filename;
        return cachedTrxImportFilePath;
    }

    /**
     * Clears the cached transaction import file path, forcing the next call to
     * getTrxImportFilePath() to perform a fresh file search.
     * This should be called when you expect a new file might be available.
     */
    public void clearTrxImportFilePathCache() {
        cachedTrxImportFilePath = null;
    }

    /**
     * Find the most recently modified file matching a pattern with YYYYMMDD placeholder.
     *
     * @param directory The directory to search in
     * @param pattern The filename pattern with YYYYMMDD as a date placeholder
     * @return The filename of the most recently modified matching file, or null if none found
     */
    private String findMostRecentMatchingFile(String directory, String pattern) {
        if (directory == null || pattern == null) {
            System.err.println("DEBUG: findMostRecentMatchingFile called with null directory or pattern");
            return null;
        }

        try {
            java.io.File dir = new java.io.File(directory);
            System.out.println("DEBUG: Searching directory: " + directory);
            System.out.println("DEBUG: Directory exists: " + dir.exists() + ", is directory: " + dir.isDirectory());

            if (!dir.exists() || !dir.isDirectory()) {
                System.err.println("DEBUG: Directory does not exist or is not a directory");
                return null;
            }

            // Convert pattern to regex: qdlYYYYMMDD.qfx -> qdl\d{8}\.qfx
            String regexPattern = pattern
                .replace(".", "\\.")  // Escape dots
                .replace("YYYYMMDD", "\\d{8}");  // Replace date pattern with 8 digits

            System.out.println("DEBUG: Pattern: " + pattern);
            System.out.println("DEBUG: Regex pattern: " + regexPattern);

            // List all files in directory for debugging
            java.io.File[] allFiles = dir.listFiles();
            if (allFiles != null) {
                System.out.println("DEBUG: Total files in directory: " + allFiles.length);
                for (java.io.File f : allFiles) {
                    if (f.getName().toLowerCase().endsWith(".qfx")) {
                        System.out.println("DEBUG: Found QFX file: " + f.getName() + " (matches: " + f.getName().matches(regexPattern) + ")");
                    }
                }
            }

            // Find all matching files
            java.io.File[] matchingFiles = dir.listFiles((file, name) ->
                name.matches(regexPattern));

            if (matchingFiles == null || matchingFiles.length == 0) {
                System.err.println("DEBUG: No files matched pattern " + regexPattern);
                return null;
            }

            System.out.println("DEBUG: Found " + matchingFiles.length + " matching file(s)");

            // Find the most recently modified file
            java.io.File mostRecent = null;
            long mostRecentTime = 0;

            for (java.io.File file : matchingFiles) {
                System.out.println("DEBUG: Checking file: " + file.getName() + " (modified: " + new java.util.Date(file.lastModified()) + ")");
                if (file.lastModified() > mostRecentTime) {
                    mostRecentTime = file.lastModified();
                    mostRecent = file;
                }
            }

            if (mostRecent != null) {
                System.out.println("DEBUG: Selected most recent file: " + mostRecent.getName());
            }

            return mostRecent != null ? mostRecent.getName() : null;

        } catch (Exception e) {
            System.err.println("Error finding matching file for pattern " + pattern + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String getProvisionalTrxFilePath() {
        String directory = getProvisionalTrxFileDirectory();
        String filename = getProvisionalTrxFileName();

        // Check if filename contains date pattern (YYYYMMDD)
        if (filename != null && filename.contains("YYYYMMDD")) {
            // Find the most recent file matching the pattern
            String actualFile = findMostRecentMatchingFile(directory, filename);
            if (actualFile != null) {
                return directory + "\\" + actualFile;
            }
        }

        // Default behavior: return exact path
        return directory + "\\" + filename;
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
        // Handle null values properly for optional fields
        String nameVal = name != null ? "'" + name + "'" : "NULL";
        String nicknameVal = nickname != null ? "'" + nickname + "'" : "NULL";
        String accountTypeVal = accountType != null ? "'" + accountType + "'" : "NULL";
        String defaultViewVal = default_view != null ? "'" + default_view + "'" : "NULL";
        String accountNumberVal = accountNumber != null ? "'" + accountNumber + "'" : "NULL";
        String financialInstitutionVal = financialInstitution != null ? "'" + financialInstitution + "'" : "NULL";
        String trxImportFileNameVal = trxImportFileName != null ? "'" + trxImportFileName + "'" : "NULL";
        String trxImportFileDirectoryVal = trxImportFileDirectory != null ? "'" + Utility.doubleBackSlashes(trxImportFileDirectory) + "'" : "NULL";
        String provisionalTrxFileNameVal = provisionalTrxFileName != null ? "'" + provisionalTrxFileName + "'" : "NULL";
        String provisionalTrxFileDirectoryVal = provisionalTrxFileDirectory != null ? "'" + Utility.doubleBackSlashes(provisionalTrxFileDirectory) + "'" : "NULL";
        String idBudgetVal = idBudget != null ? "uuid_to_bin('" + idBudget + "')" : "NULL";

        return "insert into register (idRegister, name, nickname, account_type, default_view, account_number, " +
                "balance, skippedAmount, financialInstitution, trxImportFileName, trxImportFileDirectory, " +
                "provisionalTrxFileName, provisionalTrxFileDirectory, Budget_idBudget) values (" +
                "uuid_to_bin('" + id + "'), " +
                nameVal + ", " +
                nicknameVal + ", " +
                accountTypeVal + ", " +
                defaultViewVal + ", " +
                accountNumberVal + ", " +
                balance + ", " +
                skippedAmount + ", " +
                financialInstitutionVal + ", " +
                trxImportFileNameVal + ", " +
                trxImportFileDirectoryVal + ", " +
                provisionalTrxFileNameVal + ", " +
                provisionalTrxFileDirectoryVal + ", " +
                idBudgetVal + ")";
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
        // Helper method to safely convert null to empty string for SQL
        String safeName = (name != null ? name : "");
        String safeNickname = (nickname != null ? nickname : "");
        String safeAccountType = (accountType != null ? accountType : "");
        String safeDefaultView = (default_view != null ? default_view : "");
        String safeAccountNumber = (accountNumber != null ? accountNumber : "");
        String safeFinancialInstitution = (financialInstitution != null ? financialInstitution : "");
        String safeTrxImportFileName = (trxImportFileName != null ? trxImportFileName : "");
        String safeTrxImportFileDirectory = (trxImportFileDirectory != null ? Utility.doubleBackSlashes(trxImportFileDirectory) : "");
        String safeProvisionalTrxFileName = (provisionalTrxFileName != null ? provisionalTrxFileName : "");
        String safeProvisionalTrxFileDirectory = (provisionalTrxFileDirectory != null ? Utility.doubleBackSlashes(provisionalTrxFileDirectory) : "");

        return "name = '" + safeName + "', nickname = '" + safeNickname +
                "', account_type = '" + safeAccountType + "', " +
                "default_view = '" + safeDefaultView + "', account_number = '" +
                safeAccountNumber + "', balance = " + balance + ", skippedAmount = " +
                skippedAmount + ", financialInstitution = '" + safeFinancialInstitution +
                "', trxImportFileName = '" + safeTrxImportFileName +
                "', trxImportFileDirectory = '" + safeTrxImportFileDirectory +
                "', provisionalTrxFileName = '" + safeProvisionalTrxFileName +
                "', provisionalTrxFileDirectory = '" + safeProvisionalTrxFileDirectory +
                "', Budget_idBudget = uuid_to_bin('" + idBudget + "') " +
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
        //getView().say("\nUpdate Register call.  New balance = " + Utility.formatDollarAmount(getBalance()));
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
        try (Statement statement = Utility.getDbConnection().createStatement()) {
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
        String query = selectQuery + " where r.name = \"" + registerName + "\"";
        try (PreparedStatement preparedStmt = Utility.getDbConnection().prepareStatement(query);
             ResultSet rs = preparedStmt.executeQuery()) {
            Register register = null;
            if (rs != null && rs.next()) {
                register = new Register(rs);
            }
            return register;
        } catch (SQLException e) {
            RegisterException re = new RegisterException("SQL error encountered trying to retrieve a list of registers.", e);
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

    /**
     * Gets the sum of all provisional (uncleared) transactions for this register.
     * @return the sum of provisional transaction amounts
     * @throws EntityException if a database error occurs
     * @throws SQLException if a SQL error occurs
     */
    public double getProvisionalBalance() throws EntityException, SQLException {
        String query = "select sum(amount) from transaction where Register_idRegister = uuid_to_bin('" + id + "') and cleared = false";
        ResultSet rs = getRS(query, "Database error encountered trying to get provisional balance.");
        if (rs.next()) {
            return rs.getDouble(1);
        }
        return 0.0;
    }

}
