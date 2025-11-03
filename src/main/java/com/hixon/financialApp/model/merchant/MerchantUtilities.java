package com.hixon.financialApp.model.merchant;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.register.RegisterException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for merchant-related operations.
 * Provides helper methods for working with merchants across controllers.
 */
public final class MerchantUtilities {

    private MerchantUtilities() {
        // Private constructor to prevent instantiation
    }

    /**
     * Retrieves all merchants from the database.
     *
     * @return List of all Merchant objects
     * @throws EntityException if there's a database error
     * @throws SQLException if there's a SQL error
     * @throws RegisterException if there's an error creating Merchant objects
     */
    public static List<Merchant> getAllMerchants() throws EntityException, SQLException, RegisterException {
        String selectQuery = "select " + Merchant.getSelectColumns() + " from merchant m";
        ResultSet rs = EntityInt.getRS(selectQuery, "retrieving all merchants");

        List<Merchant> merchants = new ArrayList<>();
        if (rs != null) {
            while (rs.next()) {
                merchants.add(new Merchant(rs));
            }
        }

        return merchants;
    }
}

