package com.hixon.financialApp.model.budget;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for operations on collections of Budget objects.
 */
public class BudgetUtilities {

    /**
     * Get all budgets from the database.
     *
     * @return A list of all Budget objects
     * @throws BudgetException if a budget error occurs
     * @throws EntityException if an entity error occurs
     * @throws SQLException if a database error occurs
     */
    public static List<Budget> getAllBudgets() throws BudgetException, EntityException, SQLException {
        String selectQuery = "select bin_to_uuid(idBudget) as idbudget, name from budget ";
        ResultSet rs = EntityInt.getRS(selectQuery + "order by name", "get all budgets");
        List<Budget> budgets = new ArrayList<>();
        if (rs != null) {
            while (rs.next()) {
                budgets.add(new Budget(rs));
            }
        }
        return budgets;
    }
}
