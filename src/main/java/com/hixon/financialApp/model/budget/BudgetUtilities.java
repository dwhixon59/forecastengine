package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for budget-related operations.
 * Provides helper methods for working with budgets across controllers.
 */
public final class BudgetUtilities {

    private BudgetUtilities() {
        // Private constructor to prevent instantiation
    }

    /**
     * Retrieves all budgets from the database.
     *
     * @return List of all Budget objects
     * @throws EntityException if there's a database error
     * @throws SQLException if there's a SQL error
     * @throws BudgetException if there's an error creating Budget objects
     */
    public static List<Budget> getAllBudgets() throws EntityException, SQLException, BudgetException {
        String selectQuery = "select bin_to_uuid(idBudget) as idbudget, name from budget";
        ResultSet rs = EntityInt.getRS(selectQuery, "retrieving all budgets");

        List<Budget> budgets = new ArrayList<>();
        if (rs != null) {
            while (rs.next()) {
                budgets.add(new Budget(rs));
            }
        }

        return budgets;
    }
}

