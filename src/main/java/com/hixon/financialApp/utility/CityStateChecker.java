package com.hixon.financialApp.utility;

import com.hixon.financialApp.model.entity.EntityException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CityStateChecker {

    /**
     * Checks if a given city and state_id exist in the cities table.
     *
     * @param city      The name of the city (ASCII version).
     * @param stateId   The 2-letter state ID (e.g., "NY").
     * @return          True if the city and state_id pair exists, otherwise false.
     */
    public static boolean exists(String city, String stateId) throws SQLException, EntityException {
        String query = "SELECT 1 FROM cities WHERE city_ascii = ? AND state_id = ? LIMIT 1";

        try (PreparedStatement stmt = Utility.getDbConnection().prepareStatement(query)) {
            // Set parameters for the prepared statement
            stmt.setString(1, city);
            stmt.setString(2, stateId);

            // Execute query and handle ResultSet inside try-with-resources
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();  // If a row is returned, the city-state pair exists
            }

        } catch (SQLException e) {
            // Handle SQL exceptions and wrap in EntityException
            EntityException ee = new EntityException("Database error occurred trying to retrieve a city-state pair. " +
                    "\nSQL statement was " + query + ".  \nCity: " + city + ", State: " + stateId);
            ee.initCause(e);
            throw ee;
        }
    }

    /**
     * Checks if a given city exists in any state.  Used where a descriptor names a country rather than
     * a state ("LOS GATOS USA"), so there is no state to narrow the lookup by.
     *
     * @param city The name of the city (ASCII version).
     * @return     True if some state has a city of that name, otherwise false.
     */
    public static boolean existsInAnyState(String city) throws SQLException, EntityException {
        String query = "SELECT 1 FROM cities WHERE city_ascii = ? LIMIT 1";

        try (PreparedStatement stmt = Utility.getDbConnection().prepareStatement(query)) {
            stmt.setString(1, city);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            EntityException ee = new EntityException("Database error occurred trying to retrieve a city. " +
                    "\nSQL statement was " + query + ".  \nCity: " + city);
            ee.initCause(e);
            throw ee;
        }
    }
}

