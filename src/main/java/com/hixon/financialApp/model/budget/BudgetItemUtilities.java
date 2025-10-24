package com.hixon.financialApp.model.budget;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class BudgetItemUtilities {
    /**
     * Returns budget items for the given budget.
     * @param budget the budget to retrieve items for
     * @param includeExpired if true, returns all items; if false, returns only unexpired items
     * @return List of BudgetItem objects
     * @throws Exception if retrieval fails
     */
    public static List<BudgetItem> getBudgetItemsForBudget(Budget budget, boolean includeExpired) throws Exception {
        List<BudgetItem> items = new ArrayList<>();
        ResultSet rs;

        if (includeExpired) {
            rs = BudgetItem.getAllBudgetItems(budget);
        } else {
            Calendar today = Calendar.getInstance();
            rs = BudgetItem.getAllUnexpiredBudgetItems(today, budget);
        }

        try {
            while (rs.next()) {
                items.add(new BudgetItem(rs));
            }
        } finally {
            if (rs != null) rs.close();
        }
        return items;
    }

    /**
     * Returns all unexpired budget items for the given budget.
     * @param budget the budget to retrieve items for
     * @return List of BudgetItem objects
     * @throws Exception if retrieval fails
     */
    public static List<BudgetItem> getAllUnexpiredBudgetItemsForBudget(Budget budget) throws Exception {
        return getBudgetItemsForBudget(budget, false);
    }

    /**
     * Returns all budget items (both expired and unexpired) for the given budget.
     * @param budget the budget to retrieve items for
     * @return List of BudgetItem objects
     * @throws Exception if retrieval fails
     */
    public static List<BudgetItem> getAllBudgetItemsForBudget(Budget budget) throws Exception {
        return getBudgetItemsForBudget(budget, true);
    }
}