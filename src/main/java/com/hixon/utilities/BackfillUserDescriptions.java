package com.hixon.utilities;

import com.hixon.financialApp.model.financialinstitution.WellsFargoBank;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.utility.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Rebuilds {@code transaction.user_description} for every historical row.
 *
 * <p>The column is written at import time now, so this tool only has to catch up the rows that were
 * imported before that happened -- and to re-extract the rows whose values came from an older,
 * buggier version of the extraction.  It clears the column and rebuilds it from the payee, so it is
 * safe to run more than once and always leaves the column consistent with the current extraction.
 *
 * <p>Extraction is delegated to {@link WellsFargoBank#extractMemoFromPayee(String)}.  This class used
 * to keep its own copy of that logic, so fixes to the real extraction never reached the historical
 * rows; do not reintroduce a second copy here.
 */
public class BackfillUserDescriptions {
    public static void main(String[] args) throws Exception {
        // Credentials come from db.properties (excluded from version control) — never hardcode them.
        DatabaseConnectionManager mgr = DatabaseConnectionManager.fromProperties();

        int scanned = 0, filled = 0, tooLong = 0;

        try (Connection conn = mgr.getConnection()) {

            // Clear out the existing user_description field, so values left by an older extraction
            // do not survive the rebuild:
            String clearSql = "UPDATE transaction SET user_description = NULL WHERE user_description IS NOT NULL";
            try (PreparedStatement clearStmt = conn.prepareStatement(clearSql)) {
                int cleared = clearStmt.executeUpdate();
                System.out.println("Cleared " + cleared + " existing user descriptions.");
            }

            // 1. Select rows that need backfilling
            String selectSql = "SELECT bin_to_uuid(idTransaction) as idTransaction, payee FROM transaction "
                    + "WHERE user_description IS NULL";
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                // 2. Prepare update statement
                String updateSql = "UPDATE transaction SET user_description = ? WHERE idTransaction = uuid_to_bin(?)";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    while (rs.next()) {
                        scanned++;
                        UUID id = UUID.fromString(rs.getString("idTransaction"));
                        String rawText = rs.getString("payee");

                        // 3. Extract the memo exactly the way an import does.
                        String description = WellsFargoBank.extractMemoFromPayee(rawText);

                        // 4. Update the row, if the user typed a memo at all.  Most rows are
                        //    transfers without one, or are not transfers, and are simply skipped.
                        if (description == null || description.isBlank()) {
                            continue;
                        }
                        if (description.length() > Transaction.USER_DESCRIPTION_MAX_LENGTH) {
                            // Truncate rather than skip, matching Transaction.setUserDescription.
                            description = description
                                    .substring(0, Transaction.USER_DESCRIPTION_MAX_LENGTH).trim();
                            tooLong++;
                        }
                        updateStmt.setString(1, description);
                        updateStmt.setObject(2, id.toString());
                        updateStmt.executeUpdate();
                        filled++;
                    }
                }
            }
        }

        System.out.println("Scanned " + scanned + " transactions; wrote " + filled
                + " user descriptions (" + tooLong + " truncated to "
                + Transaction.USER_DESCRIPTION_MAX_LENGTH + " characters).");
    }
}
