package com.hixon.financial.model.register;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.UUID;

// This class represents a transaction register which corresponds to some set of transactions over a period of time in
// a bank account.
public class VirtualRegister extends Register {

    //  VirtualRegister class fields:
    private final String budgetname;
    private final UUID idBudget;
    private Calendar runDate;
    private double startingBalance;
    private double endingBalance;
    private final Connection dbConnection;


    // Getters and Setters:
    public Calendar getRunDate() { return runDate; }
    public double getStartingBalance() { return startingBalance; }
    public double getEndingBalance() { return endingBalance; }


    // Constructors:
    public VirtualRegister(String registerName, String budgetName, Connection dbConnection) throws SQLException, RegisterException {

        // Setup the register:
        super();

        // Setup the virtual register:
        this.budgetname = budgetName;
        this.startingBalance = 0;
        this.endingBalance = 0;
        this.dbConnection = dbConnection;

        // Find the ID of the named budget:
        PreparedStatement preparedStmt = null;
        ResultSet rs = null;
        try {
            String query = "select bin_to_uuid(idBudget) from ForecastDatabase.Budget where name = ?";
            preparedStmt = dbConnection.prepareStatement(query);
            preparedStmt.setString(1, budgetName);
            rs = preparedStmt.executeQuery();
            if (rs != null && rs.next()) {
                this.idBudget = UUID.fromString(rs.getString(1));
            } else {
                throw new RegisterException("Budget named " + budgetName + " not found in the database.");
            }
        } catch (SQLException e) {
            System.out.println("[SEVERE]  SQL error encountered trying to retrieve the budget ID.");
            if (preparedStmt != null) preparedStmt.close();
            if (rs != null) rs.close();
            throw e;
        }

        System.out.println("The register object was successfully initialized.");

    } // End VirtualRegister(String registerName, String budgetName, Connection dbConnection)

} // End class VirtualRegister.
