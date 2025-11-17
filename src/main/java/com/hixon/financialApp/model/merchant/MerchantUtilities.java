package com.hixon.financialApp.model.merchant;

import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static com.hixon.financialApp.utility.Utility.getDbConnection;

/**
 * Utility class for merchant-related operations that don't belong in the core Merchant entity class.
 * Contains helper methods for merchant lookup, matching, and analysis.
 */
public class MerchantUtilities {

    /**
     * Get a list of possible merchants for a given transaction payee.
     * This method attempts to identify merchants without user interaction.
     *
     * <p>The matching process:
     * <ol>
     *   <li>First tries exact match in merchant_payee table</li>
     *   <li>Falls back to fuzzy matching on merchant names:
     *     <ul>
     *       <li>Checks if merchant name is contained in payee (e.g., "PURCHASE AT WALMART" matches "Walmart")</li>
     *       <li>Checks if payee is contained in merchant name (e.g., "TARGET" matches "Target Corporation")</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param payee The transaction payee string
     * @return A list of possible merchants:
     *         <ul>
     *           <li>Empty list if payee is null/empty or no matches found</li>
     *           <li>Single item if exact match found</li>
     *           <li>Multiple items if fuzzy matching finds several possibilities</li>
     *         </ul>
     * @throws RegisterException if a database error occurs
     */
    public static List<Merchant> getPossibleMerchantsByPayee(String payee) throws RegisterException {
        List<Merchant> possibleMerchants = new ArrayList<>();

        if (payee == null || payee.trim().isEmpty()) {
            return possibleMerchants;
        }

        // First try exact match
        Merchant exactMatch = Merchant.getByPayee(payee);
        if (exactMatch != null) {
            possibleMerchants.add(exactMatch);
            return possibleMerchants;
        }

        // If no exact match, try fuzzy matching on merchant names within the payee
        // This handles cases like "PURCHASE AT WALMART" -> "Walmart"
        String escapedPayee = Utility.escapeSqlString(payee.toLowerCase());
        String query = Merchant.getSelectQuery() + " where LOWER(m.name) LIKE '%" + escapedPayee + "%' " +
                "OR '" + escapedPayee + "' LIKE CONCAT('%', LOWER(m.name), '%')";

        try {
            Statement statement = getDbConnection().createStatement();
            ResultSet rs = statement.executeQuery(query);
            while (rs.next()) {
                possibleMerchants.add(new Merchant(rs));
            }
            return possibleMerchants;
        } catch (SQLException e) {
            RegisterException re = new RegisterException("Database error occurred trying to get possible Merchants for the " +
                    "payee " + payee + "\nSQL statement was:  " + query, e);
            throw re;
        }
    }
}

