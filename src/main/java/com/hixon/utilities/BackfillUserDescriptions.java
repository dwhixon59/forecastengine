package com.hixon.utilities;

import com.hixon.financialApp.utility.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class BackfillUserDescriptions {
    public static void main(String[] args) throws Exception {
        // Credentials come from db.properties (excluded from version control) — never hardcode them.
        DatabaseConnectionManager mgr = DatabaseConnectionManager.fromProperties();

        try (Connection conn = mgr.getConnection()) {

            // Clear out the existing user_description field:
            String clearSql = "UPDATE transaction SET user_description = NULL WHERE user_description IS NOT NULL";
            try (PreparedStatement clearStmt = conn.prepareStatement(clearSql)) {
                clearStmt.executeUpdate();
            }

            // 1. Select rows that need backfilling
            String selectSql = "SELECT bin_to_uuid(idTransaction) as idTransaction, payee FROM transaction WHERE user_description IS NULL";
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                // 2. Prepare update statement
                String updateSql = "UPDATE transaction SET user_description = ? WHERE idTransaction = uuid_to_bin(?)";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    while (rs.next()) {
                        UUID id = UUID.fromString(rs.getString("idTransaction"));
                        String rawText = rs.getString("payee");

                        // 3. Extract description (customize this logic)
                        String description = extractUserDescription(rawText);

                        // 4. Update row
                        if (description == null) {
                            System.out.println("No description found for transaction payee: " + rawText);
                            continue;
                        }
                        if (description.isBlank()) {
                            System.out.println("Empty description found for transaction payee: " + rawText);
                            continue;
                        }
                        if (description.length() > 64) {
                            System.out.println("Description too long for transaction payee: " + rawText);
                            continue;
                        }
                        if (description.equals("null")) {
                            System.out.println("Description is 'null' for transaction payee: " + rawText);
                            continue;
                        }
                        if (description.equals(" ")) {
                            System.out.println("Description is blank for transaction payee: " + rawText);
                            continue;
                        }
                        if (description.equals("0")) {
                            System.out.println("Description is '0' for transaction payee: " + rawText);
                            continue;
                        }
                        if (description.equals("0.0")) {
                            System.out.println("Description is '0.0' for transaction payee: " + rawText);
                            continue;
                        }
                        updateStmt.setString(1, description);
                        updateStmt.setObject(2, id.toString());
                        updateStmt.executeUpdate();
                    }
                }
            }
        }
    }

    private static final Set<String> STOPWORDS = Set.of(
            "RECURRING", "TRANSFER", "TO", "FROM", "REF", "#",
            "EVERYDAY", "CHECKING", "SAVINGS", "WAY2SAVE",
            "ACCOUNT", "JOINT", "BANKING", "BA", "ONLINE"
    );

    private static final Pattern maskedAccountPattern = Pattern.compile("X{4,}\\d{2,}");
    private static final Pattern refPrefixPattern = Pattern.compile("^#?[A-Z0-9]{10,}(\\s+ON\\s+\\d{2}/\\d{2}/\\d{2,4})?", Pattern.CASE_INSENSITIVE);
    private static final Pattern dateOnlyPattern = Pattern.compile("^ON\\s+\\d{2}/\\d{2}/\\d{2,4}$", Pattern.CASE_INSENSITIVE);

    private static String extractUserDescription(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;

        rawText = rawText.trim();

        // Step 1: Remove leading #REFCODE and optional date
        rawText = refPrefixPattern.matcher(rawText).replaceFirst("").trim();

        // Step 2: Look for the last occurrence of "REF #" and take everything after it
        int refIndex = rawText.toUpperCase().lastIndexOf("REF #");
        if (refIndex == -1) return null;

        String afterRef = rawText.substring(refIndex + 5).trim();
        String[] words = afterRef.split("\\s+");

        List<String> cleaned = new ArrayList<>();
        for (String word : words) {
            String upper = word.toUpperCase();

            if (STOPWORDS.contains(upper) || maskedAccountPattern.matcher(upper).matches()) {
                continue;
            }

            if (word.matches("^[A-Z]{2}\\d[A-Z0-9]{7,}$")) {
                continue; // Skip duplicate reference codes
            }

            cleaned.add(word);
        }

        if (cleaned.isEmpty()) return null;

        String result = String.join(" ", cleaned);

        // Step 3: Reject if the remaining description is just a date
        if (dateOnlyPattern.matcher(result.toUpperCase()).matches()) {
            return null;
        }

        return result;
    }
}

