package com.hixon.financialApp.model.merchant;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.entity.IndependentEntityInt;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.utility.Utility.getDbConnection;
import static com.hixon.financialApp.utility.Utility.getView;

public class Merchant extends IndependentEntity implements IndependentEntityInt {

    /*
     * Fields in the Merchant class:
     */
    // The merchant is currently unknown because the user skipped assignment of the merchant:
    public static final String UNKNOWN = "Unknown";

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


    /*
     * CRUD methods:
     */
    public static Merchant getById(UUID idMerchant) throws EntityException, RegisterException {
        ResultSet rs = EntityInt.getRSById(selectQuery + " where m.idMerchant = ", idMerchant,
                "trying to retrieve a Merchant by it's ID.");
        return new Merchant(rs);
    }

    public static void deleteByName(String merchantPayeeString) {
        try {
            Statement statement = getDbConnection().createStatement();
            String escapedName = Utility.escapeSqlString(merchantPayeeString);
            statement.executeUpdate("delete from merchant where name = '" + escapedName + "'");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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

    public static String getType() {
        return "Merchant";
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

    public List<MerchantPayee> getPayees() throws SQLException, EntityException {
        return MerchantPayee.getPayeesForMerchant(this);
    }

    @Override
    public String getInsertQuery() throws BudgetException, ForecastException {
        StringBuilder query = new StringBuilder(insertQuery);
        query.append("uuid_to_bin('").append(id).append("'), ");
        String escapedName = Utility.escapeSqlString(name);
        query.append("'").append(escapedName).append("', ");
        query.append(askAlways);

        if (idUser != null) {
            query.append(", uuid_to_bin('").append(idUser).append("')");
        } else {
            query.append(", null");
        }

        query.append(")");
        return query.toString();
    }

    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
        return null;
    }

    @Override
    public String getUpdateByIdQuery() throws BudgetException {
        StringBuilder query = new StringBuilder("UPDATE merchant SET ");
        String escapedName = Utility.escapeSqlString(name);
        query.append("name = '").append(escapedName).append("', ");
        query.append("askAlways = ").append(askAlways);

        if (idUser != null) {
            query.append(", User_idUser = uuid_to_bin('").append(idUser).append("')");
        } else {
            query.append(", User_idUser = null");
        }

        query.append(" WHERE idMerchant = uuid_to_bin('").append(id).append("')");
        return query.toString();
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
        return "merchant";
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
        super(true);
        loadFromResultSet(rs);
    }


    /*
     * Load and save methods:
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

    private void loadFromResultSet(ResultSet rs) throws RegisterException {
        try {

            if (rs == null)
                throw new RegisterException("Result set passed into loadFromResultSet from must not be null.");
            this.id = UUID.fromString(rs.getString("m.idMerchant"));
            this.name = rs.getString("m.name");
            this.askAlways = rs.getBoolean("m.askAlways");
            String user = rs.getString("m.idUser");
            this.idUser = (user != null) ? UUID.fromString(user) : null;
            setDirty(false);

        } catch (SQLException e) {

            RegisterException re = new RegisterException("Error reading in the Merchant-Payee row for " + rs, e);
            throw (re);
        }
    }  // End loadFromResultSet().

    @Override
    public boolean loadByName(IndependentEntity scope, String name) throws EntityException {

        // Find the ID of the merchant that uses the passed in name:
        String escapedName = Utility.escapeSqlString(name);
        String query = selectQuery + " where m.name = '" + escapedName + "'";
        try {
            Statement statement = getDbConnection().createStatement();
            ResultSet rs = statement.executeQuery(query);
            if (rs.next()) {
                loadFromResultSet(rs);
                return true;
            } else {
                return false;
            }
        } catch (SQLException | RegisterException e) {
            EntityException ee = new EntityException("Database error occurred trying to get the Merchant for the " +
                    "name " + name, e);
            throw ee;
        }
    }


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
        String escapedPayee = Utility.escapeSqlString(payee);
        String query = selectJoinPayeeQuery + "where mp.payee = '" + escapedPayee + "'";
        try {
            Statement statement = getDbConnection().createStatement();
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
        String escapedName = Utility.escapeSqlString(name);
        String query = selectQuery + " where m.name = '" + escapedName + "'";
        try {
            Statement statement = getDbConnection().createStatement();
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
        try {
            // Determine if this is a new merchant or an existing one
            // New merchants will have isDirty() true and typically come from constructors
            // We can check if the merchant exists in the database by checking if we can load it
            boolean exists = false;
            if (id != null) {
                try {
                    Merchant.getById(id);
                    exists = true;
                } catch (Exception e) {
                    // Merchant doesn't exist, so it's new
                    exists = false;
                }
            }

            // Use the appropriate save method
            if (exists) {
                save(SaveMethod.UPDATE);
            } else {
                save(SaveMethod.INSERT);
            }

            // Save the merchant payees:
            for (MerchantPayee merchantPayee : merchantPayees) {
                merchantPayee.save();
            }
        } catch (SQLException e) {
            throw new RegisterException("Error saving merchant", e);
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
                        getView().say("That budget item is already associated with this merchant.");
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

    /**
     * The getDisplayString method is responsible for generating a display string for a merchant item that will be used in
     * lists of budget items to clearly identify which merchant this is.
     *
     * @return A string that displays the merchant's member variables.
     */
    public String getDisplayString() {

        String displayString = getName();
        displayString += " (";
        displayString += (askAlways) ? "ask before using" : "use automatically";
        try {
            if (getIdUser() != null) {
                displayString += ", " + User.getById(idUser).getFirstName();
            }
            else {
                displayString += ", no user assigned";
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        };
        displayString += ')';

        // Return the string:
        return displayString;
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
