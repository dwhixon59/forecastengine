package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Budget extends IndependentEntity {

    /*
     * Fields:
     */
    private String budgetName = null;
    private static final String selectQuery = "select bin_to_uuid(idBudget) as idbudget, name from " +
            "budget ";


    /*
     * Getters and setters:
     */

    public String getName() {
        return budgetName;
    }

    public void setBudgetName(String budgetName) {
        this.budgetName = budgetName;
        setDirty(true);
    }

    @Override
    public String getInsertQuery() throws BudgetException, ForecastException {
        String nameVal = budgetName != null ? "'" + budgetName + "'" : "NULL";
        return "insert into budget (idBudget, name) values (uuid_to_bin('" + id + "'), " + nameVal + ")";
    }

    @Override
    public String getInsertOnDuplicateUpdateQuery() throws BudgetException {
        return null;
    }

    @Override
    public String getUpdateByIdQuery() throws BudgetException {
        String nameVal = budgetName != null ? "'" + budgetName + "'" : "NULL";
        return "update budget set name = " + nameVal + " where idBudget = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getDeleteByIdQuery() {
        return "delete from budget where idBudget = uuid_to_bin('" + id + "')";
    }

    @Override
    public String getPrintableTypeName() {
        return getPrintableTypeName_static();
    }

    public static String getPrintableTypeName_static() {
        return "budget";
    }


    /*
     * Constructors:
     */
    public Budget() {
        super(false);
    }

    public Budget(ResultSet rs) throws SQLException, BudgetException {
        super(false);
        try {
            if (rs == null)
                throw new BudgetException("Result set to Budget.loadFromResultSet() from must not be null.");

            id = UUID.fromString(rs.getString(1));
            budgetName = rs.getString("name");
            setDirty(false);

        } catch (SQLException e) {
            System.out.println("Error reading in the Budget Item row.");
            e.printStackTrace();
            throw e;
        }
    }


    /*
     * Load and save methods:
     */
    public static Budget getById(UUID idBudget) throws BudgetException, EntityException, SQLException {
        ResultSet rs = EntityInt.getRSById(selectQuery + "where idBudget = ", idBudget,
                "No budget found with id " + idBudget);
        return new Budget(rs);
    }

    public static Budget getByName(String name) throws BudgetException, EntityException, SQLException {
        ResultSet rs = EntityInt.getSingletonRS(selectQuery + "where name = '" + name + "'",
                "No budget found with name " + name);
        return new Budget(rs);
    }

    public static List<Budget> getListOf() throws BudgetException, SQLException {
        try (java.sql.Statement statement = com.hixon.financialApp.utility.Utility.getDbConnection().createStatement()) {
            ResultSet rs = statement.executeQuery(selectQuery + "order by name");
            List<Budget> budgets = new ArrayList<>();
            while (rs.next()) {
                Budget budget = new Budget(rs);
                budgets.add(budget);
            }
            return budgets;
        } catch (SQLException | BudgetException e) {
            BudgetException be = new BudgetException("Database error occurred trying to retrieve budgets with the " +
                    "sql statement " + selectQuery);
            be.initCause(e);
            throw be;
        }
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
    public boolean isValid() { return true; }
    

    public List<Register> getRegisters() throws BudgetException, SQLException, RegisterException, EntityException {
        ResultSet rs = EntityInt.getRS(Register.getSelectQuery() + " where Budget_idBudget = uuid_to_bin('" +
                        id + "')", "get the Registers associated with the budget " + this);
        List<Register> registers = new ArrayList<>();
        if (rs != null) {
            while (rs.next()) {
                registers.add(new Register(rs));
            }
        } else {
            throw new BudgetException("No registers found for budget " + this);
        }
        return registers;
    }

    // Get the register for the budget:
    public Register getRegister() throws BudgetException, SQLException, RegisterException, EntityException {
        return getRegisters().get(0);
    }


    /*
     * Main methods:
     */

}
