package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for ForecastItem operations.
 * Provides helper methods for retrieving and managing forecast items.
 */
public class ForecastItemUtilities {

    /**
     * Get a list of all forecast items that reference a specific budget item across all forecasts.
     *
     * @param idBudgetItem The ID of the budget item to search for
     * @return a List containing all forecast items that reference this budget item
     * @throws BudgetException if there's an issue with budget item data
     * @throws EntityException if there's a database entity error
     * @throws SQLException if there's a database error
     * @throws ForecastException if there's an issue with forecast item data
     */
    public static List<ForecastItem> getAllByBudgetItemId(UUID idBudgetItem)
            throws BudgetException, EntityException, SQLException, ForecastException {

        String query = ForecastItem.getSelectQuery() + " where fi.BudgetItem_idBudgetItem = uuid_to_bin('" + idBudgetItem + "')";
        ResultSet rs = EntityInt.getRS(query, "attempting to get all forecast items for budget item ID: " + idBudgetItem);

        List<ForecastItem> items = new ArrayList<>();
        if (rs != null) {
            try {
                while (rs.next()) {
                    items.add(new ForecastItem(rs));
                }
            } finally {
                rs.close();
            }
        }
        return items;
    }
}

