package com.hixon.financial.model.budget;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class Budget {

    /*
     * Fields:
     */
    private final Connection dbConnection;
    private UUID idBudget = null;
    private String budgetName = null;
    private static final String selectQuery = "select bin_to_uuid(idBudget), name from ForecastDatabase.Budget ";


    /*
     * Constructors:
     */
    public Budget(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }


    /*
     * Getters and setters:
     */
    public UUID getIdBudget() { return idBudget; }
    public void setIdBudget(UUID idBudget) { this.idBudget = idBudget; }
    public String getBudgetName() { return budgetName; }
    public void setBudgetNmae(String budgetName) { this.budgetName = budgetName; }


    /*
     * Load and save methods:
     */
    private void loadFromResultSet(ResultSet rs) throws SQLException, BudgetException {
        try {
            if (rs == null) throw new BudgetException("Result set to Budget.loadFromResultSet() from must not be null.");

            idBudget = UUID.fromString(rs.getString(1));
            budgetName = rs.getString("name");

        } catch (SQLException e) {
            System.out.println("Error reading in the Budget Item row.");
            e.printStackTrace();
            throw e;
        }
    }

    public void loadFromName(String name) throws BudgetException {
        String query = selectQuery + "where name = '" + name + "'";
        try {
            Statement statement = dbConnection.createStatement();
            ResultSet rs = statement.executeQuery(query);
            if (rs.next()) {
                loadFromResultSet(rs);
            } else {
                throw new BudgetException("No budget found with name " + name);
            }
        } catch (SQLException e) {
            BudgetException be = new BudgetException("Database error occurred trying to get the budget named " + name);
            be.initCause(e);
            throw be;
        }

    }
}
