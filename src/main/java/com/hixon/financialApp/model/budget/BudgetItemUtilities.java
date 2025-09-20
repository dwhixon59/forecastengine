package com.hixon.financialApp.model.budget;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class BudgetItemUtilities {
    /**
     * Returns all unexpired budget items for the given budget.
     * @param budget the budget to retrieve items for
     * @return List of BudgetItem objects
     * @throws Exception if retrieval fails
     */
    public static List<BudgetItem> getAllUnexpiredBudgetItemsForBudget(Budget budget) throws Exception {
        List<BudgetItem> items = new ArrayList<>();
        Calendar today = Calendar.getInstance();
        ResultSet rs = BudgetItem.getAllUnexpiredBudgetItems(today, budget);
        try {
            while (rs.next()) {
                items.add(new BudgetItem(rs));
            }
        } finally {
            if (rs != null) rs.close();
        }
        return items;
    }
}