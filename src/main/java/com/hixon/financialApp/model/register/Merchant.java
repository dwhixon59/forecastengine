package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class Merchant extends IndependentEntity {

    /*
     * Fields in the Merchant class:
     */
    private static final String selectColumns = "bin_to_uuid(m.idMerchant) as 'm.idMerchant', m.name as 'm.name', " +
            "m.askAlways as 'm.askAlways', bin_to_uuid(m.User_idUser) as 'm.idUser'";

    public static String getSelectColumns() {
        return selectColumns;
    }

    private static final String selectQuery = "select " + getSelectColumns() + " from merchant m";

    public static String getSelectQuery() {
        return selectQuery;
    }

    private static final String selectJoinPayeeQuery = selectQuery + " inner join merchant_payee mp on " +
            "m.idMerchant = mp.Merchant_idMerchant ";

    private static final String insertQuery = "insert into merchant (idMerchant, name, askAlways, " +
            "User_idUser) values (";

    private String name = null;
    boolean askAlways = false;
    private UUID idUser;
    private List<MerchantPayee> merchantPayees = new LinkedList<>();

    public static Merchant getById(UUID idMerchant) throws EntityException, RegisterException {
        ResultSet rs = EntityInt.getRSById(selectQuery + " where m.idMerchant = ", idMerchant,
                "trying to retrieve a Merchant by it's ID.");
        return new Merchant(rs);
    }


    /*
     * Getters and setters:
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        setDirty(true);
        this.name = name;
    }

    public boolean isAskAlways() {
        return askAlways;
    }

    public void setAskAlways(boolean askAlways) {
        setDirty(true);
        this.askAlways = askAlways;
    }

    public UUID getIdUser() {
        return idUser;
    }

    public void setIdUser(UUID idUser) {
        setDirty(true);
        this.idUser = idUser;
    }

    public List<MerchantPayee> getPayees() {
        return merchantPayees;
    }

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

    @Override
    public String getDeleteByIdQuery() {
        return null;
    }

    @Override
    public String getPrintableEntityTypeName() {
        return null;
    }


    /*
     * Constructors:
     */
    // Create a new merchant with the provided name:
    public Merchant(String merchantName) {
        super(true);
        name = merchantName;
    }

    // Create and load an existing merchant from the database:
    public Merchant(ResultSet rs) throws RegisterException {
        super(false);
        loadFromResultSet(rs);
    }


    /*
     * Load and save methods:
     */

    private void loadFromResultSet(ResultSet rs) throws RegisterException {
        try {

            if (rs == null)
                throw new RegisterException("Result set passed into loadFromResultSet from must not be null.");
            this.id = UUID.fromString(rs.getString("m.idMerchant"));
            this.name = rs.getString("m.name");
            this.askAlways = rs.getBoolean("m.askAlways");
            this.idUser = UUID.fromString(rs.getString("m.idUser"));
            setDirty(false);

        } catch (SQLException e) {

            RegisterException re = new RegisterException("Error reading in the Merchant-Payee row for " + rs, e);
            throw (re);
        }
    }  // End loadFromResultSet().


    public static Merchant loadFromCSV(String merchantName) throws RegisterException {

        String[] values = merchantName.split(",");
        if (values.length < 1) throw new RegisterException("Empty string passed into Merchant.loadFromCSV().");
        Merchant merchant = new Merchant(merchantName);
        if (values.length > 1) {
            merchant.setAskAlways(values[1].equalsIgnoreCase("y"));
        }
        if (values.length > 2) {
            merchant.setIdUser(UUID.fromString(values[2]));
        }
        System.out.println("Created new merchant " + merchantName);
        return merchant;
    }

    public static Merchant getByPayee(String payee) throws RegisterException {

        // Find the ID of the merchant that uses the passed in payee:
        String query = selectJoinPayeeQuery + "where mp.payee = \"" + payee + "\"";
        try {
            Statement statement = Utility.getDbConnection().createStatement();
            ResultSet rs = statement.executeQuery(query);
            if (rs.next()) {
                return new Merchant(rs);
            } else {
                return null;
            }
        } catch (SQLException e) {
            RegisterException re = new RegisterException("Database error occurred trying to get the Merchant for the " +
                    "payee " + payee + "\nSQL statement was:  " + query, e);
            throw re;
        }
    }

    public static Merchant getByName(String name) throws RegisterException {

        // Find the ID of the merchant that uses the passed in name:
        String query = selectQuery + " where m.name = \"" + name + "\"";
        try {
            Statement statement = Utility.getDbConnection().createStatement();
            ResultSet rs = statement.executeQuery(query);
            if (rs.next()) {
                return new Merchant(rs);
            } else {
                return null;
            }
        } catch (SQLException e) {
            RegisterException re = new RegisterException("Database error occurred trying to get the Merchant for the " +
                    "name " + name, e);
            throw re;
        }
    }

    public static Merchant getByNameLike(String name) throws RegisterException {
        // Find the ID of the merchant that uses the passed in name:
        String query = selectQuery + " where m.name like \"" + name + "%\"";
        try {
            ResultSet rs = EntityInt.getRS(query, "trying to get the Merchant with the name like " + name);
            if (rs.next()) {
                return new Merchant(rs);
            } else {
                return null;
            }
        } catch (EntityException | SQLException e) {
            RegisterException re = new RegisterException("Database error occurred.");
            re.initCause(e);
            throw re;
        }
    }

    // Get the name of a Merchant:
    public static String getNameById(UUID idMerchant) throws EntityException, SQLException {
        ResultSet rs = EntityInt.getRS(selectQuery + "where m.idMerchant = uuid_to_bin('" + idMerchant + "')",
                "Database error occurred trying to get the merchant with id = " + idMerchant);
        return rs.getString("m.name");
    }

    // Create a new payee associated with this merchant:
    public MerchantPayee addPayee(String payee) {

        MerchantPayee merchantPayee = new MerchantPayee(payee, id);
        merchantPayees.add(merchantPayee);
        return merchantPayee;
    }

    // Save this merchant if dirty, and any dirty merchant-payees:
    public void save() throws RegisterException, EntityException {

        // Save the merchant:
        if (idUser != null) {
            super.executeQueryForThis(insertQuery + "uuid_to_bin('" + id + "'), \"" + name + "\", " + askAlways +
                            ", uuid_to_bin('" + idUser + "'))",
                    "Problem with Insert of merchant.  Returned row count not equal to 1.");
        } else {
            super.executeQueryForThis(insertQuery + "uuid_to_bin('" + id + "'), \"" + name + "\", " + askAlways +
                    ", null)", "Problem with Insert of merchant.  Returned row count not equal to 1.");
        }

        // Save the merchant payees:
        for (MerchantPayee merchantPayee : merchantPayees) {
            merchantPayee.save();
        }
    }

    // Add a budget item to the merchant:
    public BudgetItemMerchant addBudgetItem(BudgetItem budgetItem, double amount, int percentage) throws EntityException,
            RegisterException, BudgetException, SQLException {
        BudgetItemMerchant budgetItemMerchant = null;
        try {
            budgetItemMerchant = new BudgetItemMerchant(budgetItem, this, amount, percentage);
            budgetItemMerchant.save();
        } catch (BudgetException be) {
            // If the budget item is already associated with the merchant, that is OK:
            Throwable e = be.getCause();
            boolean isSqlIntegrityException = false;
            if (e != null) {
                Throwable ec = e.getCause();
                if (ec != null) {
                    if (ec instanceof SQLIntegrityConstraintViolationException) {
                        Utility.getResolver().say("That budget item is already associated with this merchant.");
                        budgetItemMerchant = null;
                        isSqlIntegrityException = true;
                    }
                }
            }
            if (!isSqlIntegrityException) {
                throw be;
            }
        }
        return budgetItemMerchant;
    }

    // Nice "to String" function for debugging:

    @Override
    public String toString() {
        return "Merchant{" +
                "name='" + name + '\'' +
                ", askAlways=" + askAlways +
                ", idUser=" + idUser +
                ", merchantPayees=" + merchantPayees +
                ", id=" + id +
                '}';
    }
}
