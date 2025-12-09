package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * Utility methods for transaction-related queries and batch operations.
 */
public class TransactionUtilities {


    // Flip this to true while debugging provisional matching
    private static final boolean DEBUG_PROVISIONAL_MATCHING = true;

    /*
     * Helper methods:
     */
    // Helper class to hold detailed fuzzy match info
    private static class FuzzyMatchResult {
        final boolean match;
        final double matchRatio;
        final List<String> matchedTokens;
        final List<String> shorterTokens;
        final List<String> longerTokens;

        FuzzyMatchResult(boolean match,
                         double matchRatio,
                         List<String> matchedTokens,
                         List<String> shorterTokens,
                         List<String> longerTokens) {
            this.match = match;
            this.matchRatio = matchRatio;
            this.matchedTokens = matchedTokens;
            this.shorterTokens = shorterTokens;
            this.longerTokens = longerTokens;
        }
    }

    /**
     * Logs detailed fuzzy match results for debugging purposes.
     *
     * @param comparedField The field being compared (e.g., "merchantPayee" or "payee").
     * @param clearedMerchantPayee The cleared transaction's merchant payee.
     * @param candidateFieldValue The candidate transaction's field value.
     * @param result The fuzzy match result details.
     */
    private static void logFuzzyResult(String comparedField,
                                       String clearedMerchantPayee,
                                       String candidateFieldValue,
                                       FuzzyMatchResult result) {
        if (!DEBUG_PROVISIONAL_MATCHING) return;

        System.out.println("  Fuzzy match against candidate " + comparedField + ":");
        System.out.println("    cleared merchantPayee: '" + clearedMerchantPayee + "'");
        System.out.println("    candidate " + comparedField + ": '" + candidateFieldValue + "'");
        System.out.println("    match?       " + result.match);
        System.out.println("    matchRatio:  " + result.matchRatio);
        System.out.println("    matchedTokens: " + result.matchedTokens);
    }


    /*
     * Main methods:
     */
    /**
     * Retrieves the first provisional (uncleared) transaction for a given merchant and amount.
     *
     * @param idMerchant The UUID of the merchant.
     * @param amount The transaction amount.
     * @return The first matching provisional Transaction, or null if none found.
     * @throws EntityException If a database or entity error occurs.
     * @throws SQLException If a SQL error occurs.
     */
    public static Transaction getFirstProvisionalTransaction(UUID idMerchant, double amount) throws EntityException, SQLException {
        ResultSet rs = EntityInt.getRS(Transaction.getSelectQuery() + " where tr.Merchant_idMerchant = uuid_to_bin('" + idMerchant +
                "') and tr.amount = " + amount + " and tr.cleared = false order by tr.postDate asc", "Database error" +
                " occured while trying to retrieve any provisional transactions that match a merchant and amount.");
        Transaction transaction = null;
        if (rs != null) {
            if (rs.next()) {
                transaction = new Transaction(rs);
            }
        }
        return transaction;
    }

    /**
     * Finds a matching provisional transaction with prioritized exact amount matching.
     * This method is designed to match Wells Fargo provisional transactions with their corresponding
     * posted transactions.
     *
     * Matching strategy:
     * 1. EXACT AMOUNT MATCH: If there's an exact amount match, that's almost certainly the right transaction
     *    - Use fuzzy payee matching ONLY to disambiguate if there are multiple exact matches
     *    - For transfers, skip fuzzy matching since payee isn't useful
     * 2. TIP TOLERANCE: Only if no exact matches found, look for amounts within tip range (up to 30% more)
     *
     * Matching criteria (all must match):
     * 1. Same register (bank account)
     * 2. Not cleared (provisional transactions are always uncleared)
     * 3. Date within ±5 days
     * 4. Amount (exact or with tip tolerance)
     *
     * @param idRegister The UUID of the register
     * @param clearedAmount The exact transaction amount of the cleared transaction
     * @param postDate The post date of the cleared transaction
     * @param merchantPayee The parsed merchant name from the cleared transaction
     * @return The first matching provisional Transaction, or null if none found
     */
    public static Transaction findMatchingProvisionalTransaction(UUID idRegister,
                                                                 double clearedAmount,
                                                                 Calendar postDate,
                                                                 String merchantPayee)
            throws EntityException, SQLException {

        // Calculate date range: ±5 days from the post date
        Calendar startDate = (Calendar) postDate.clone();
        startDate.add(Calendar.DAY_OF_MONTH, -5);
        Calendar endDate = (Calendar) postDate.clone();
        endDate.add(Calendar.DAY_OF_MONTH, 5);

        if (DEBUG_PROVISIONAL_MATCHING) {
            System.out.println("\n=== Provisional Matching Debug ===");
            System.out.println("Cleared txn:");
            System.out.println("  Register:   " + idRegister);
            System.out.println("  Amount:     " + clearedAmount);
            System.out.println("  Post date:  " + postDate.getTime());
            System.out.println("  MerchantPayee: '" + merchantPayee + "'");
            System.out.println("Search window:");
            System.out.println("  Date:   " + startDate.getTime() + " .. " + endDate.getTime());
        }

        // Determine if this is a transfer
        boolean isTransfer = merchantPayee != null &&
                (merchantPayee.toUpperCase().contains("TRANSFER") ||
                 merchantPayee.toUpperCase().contains("ONLINE TRANSFER") ||
                 merchantPayee.toUpperCase().contains("ATM TRANSFER"));

        // PHASE 1: Look for EXACT amount matches first
        if (DEBUG_PROVISIONAL_MATCHING) {
            System.out.println("\nPHASE 1: Looking for exact amount matches...");
        }

        String exactMatchQuery = Transaction.getSelectQuery() +
                " WHERE tr.Register_idRegister = uuid_to_bin('" + idRegister + "')" +
                " AND tr.amount = " + clearedAmount +
                " AND SIGN(tr.amount) = SIGN(" + clearedAmount + ")" + // Ensure same sign
                " AND tr.cleared = false" +
                " AND tr.postDate >= " +
                com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(startDate) +
                " AND tr.postDate <= " +
                com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(endDate) +
                " ORDER BY ABS(DATEDIFF(tr.postDate, " +
                com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(postDate) + ")) ASC";

        ResultSet exactRs = EntityInt.getRS(
                exactMatchQuery,
                "Database error occurred while trying to find exact amount match for provisional transaction."
        );

        List<Transaction> exactMatches = new ArrayList<>();
        if (exactRs != null) {
            while (exactRs.next()) {
                exactMatches.add(new Transaction(exactRs));
            }
        }

        if (DEBUG_PROVISIONAL_MATCHING) {
            System.out.println("Found " + exactMatches.size() + " exact amount match(es)");
        }

        // If we found exact matches, handle them
        if (!exactMatches.isEmpty()) {
            // If only one exact match, return it immediately
            if (exactMatches.size() == 1) {
                Transaction match = exactMatches.get(0);
                if (DEBUG_PROVISIONAL_MATCHING) {
                    System.out.println("\nSingle exact amount match found - returning it:");
                    System.out.println("  id:          " + match.getId());
                    System.out.println("  postDate:    " + match.getDate().getTime());
                    System.out.println("  amount:      " + match.getAmount());
                    System.out.println("  payee:       '" + match.getPayee() + "'");
                    System.out.println("  merchantPayee: '" + match.getMerchantPayee() + "'");
                    System.out.println("Reason: EXACT AMOUNT MATCH (only one)");
                    System.out.println("=== End Provisional Matching Debug ===");
                }
                return match;
            }

            // Multiple exact matches - use fuzzy matching to disambiguate (unless it's a transfer)
            if (DEBUG_PROVISIONAL_MATCHING) {
                System.out.println("\nMultiple exact amount matches found. Need to disambiguate.");
                if (isTransfer) {
                    System.out.println("This is a TRANSFER - returning closest by date (fuzzy matching not useful)");
                }
            }

            if (isTransfer) {
                // For transfers, just return the one closest by date
                Transaction match = exactMatches.get(0); // Already sorted by date
                if (DEBUG_PROVISIONAL_MATCHING) {
                    System.out.println("  Selected transfer match (closest by date):");
                    System.out.println("    id:          " + match.getId());
                    System.out.println("    postDate:    " + match.getDate().getTime());
                    System.out.println("    amount:      " + match.getAmount());
                    System.out.println("    payee:       '" + match.getPayee() + "'");
                    System.out.println("=== End Provisional Matching Debug ===");
                }
                return match;
            }

            // Use fuzzy matching to pick the best among multiple exact matches
            Transaction bestMatch = null;
            FuzzyMatchResult bestResult = null;
            String bestSource = null;
            double bestMatchScore = -1;

            for (int i = 0; i < exactMatches.size(); i++) {
                Transaction candidate = exactMatches.get(i);

                if (DEBUG_PROVISIONAL_MATCHING) {
                    System.out.println("\nExact match candidate #" + (i + 1) + ":");
                    System.out.println("  id:          " + candidate.getId());
                    System.out.println("  postDate:    " + candidate.getDate().getTime());
                    System.out.println("  payee:       '" + candidate.getPayee() + "'");
                    System.out.println("  merchantPayee: '" + candidate.getMerchantPayee() + "'");
                }

                if (merchantPayee != null) {
                    // Try comparing to candidate.merchantPayee
                    if (candidate.getMerchantPayee() != null) {
                        FuzzyMatchResult result =
                                fuzzyPayeeMatchWithDetails(merchantPayee, candidate.getMerchantPayee());

                        if (DEBUG_PROVISIONAL_MATCHING) {
                            logFuzzyResult("merchantPayee", merchantPayee,
                                    candidate.getMerchantPayee(), result);
                        }

                        if (result.matchRatio > bestMatchScore) {
                            bestMatchScore = result.matchRatio;
                            bestMatch = candidate;
                            bestResult = result;
                            bestSource = "merchantPayee";
                        }
                    }

                    // Try comparing to raw payee
                    if (candidate.getPayee() != null) {
                        FuzzyMatchResult result =
                                fuzzyPayeeMatchWithDetails(merchantPayee, candidate.getPayee());

                        if (DEBUG_PROVISIONAL_MATCHING) {
                            logFuzzyResult("payee", merchantPayee,
                                    candidate.getPayee(), result);
                        }

                        if (result.matchRatio > bestMatchScore) {
                            bestMatchScore = result.matchRatio;
                            bestMatch = candidate;
                            bestResult = result;
                            bestSource = "payee";
                        }
                    }
                }
            }

            // If we found a good fuzzy match among the exact amounts, return it
            if (bestMatch != null && bestMatchScore >= 0.5) {
                if (DEBUG_PROVISIONAL_MATCHING) {
                    System.out.println("\nSelected exact amount match with best fuzzy score:");
                    System.out.println("  id:          " + bestMatch.getId());
                    System.out.println("  postDate:    " + bestMatch.getDate().getTime());
                    System.out.println("  amount:      " + bestMatch.getAmount());
                    System.out.println("  payee:       '" + bestMatch.getPayee() + "'");
                    System.out.println("  merchantPayee: '" + bestMatch.getMerchantPayee() + "'");
                    System.out.println("Reason: EXACT AMOUNT + Best fuzzy match");
                    System.out.println("  Matched on:  " + bestSource);
                    System.out.println("  matchRatio:  " + bestResult.matchRatio);
                    System.out.println("  matchedTokens: " + bestResult.matchedTokens);
                    System.out.println("=== End Provisional Matching Debug ===");
                }
                return bestMatch;
            }

            // No good fuzzy match, just return the first (closest by date)
            Transaction match = exactMatches.get(0);
            if (DEBUG_PROVISIONAL_MATCHING) {
                System.out.println("\nNo strong fuzzy match found. Returning closest by date:");
                System.out.println("  id:          " + match.getId());
                System.out.println("  postDate:    " + match.getDate().getTime());
                System.out.println("  amount:      " + match.getAmount());
                System.out.println("  payee:       '" + match.getPayee() + "'");
                System.out.println("Reason: EXACT AMOUNT + Closest date (fuzzy match inconclusive)");
                System.out.println("=== End Provisional Matching Debug ===");
            }
            return match;
        }

        // PHASE 2: No exact matches found, look for tip tolerance matches
        if (DEBUG_PROVISIONAL_MATCHING) {
            System.out.println("\nPHASE 2: No exact matches. Looking with tip tolerance...");
        }

        // For negative amounts (debits), the cleared amount is usually more negative than the provisional
        // (because of tips). For example: provisional = -50, cleared = -60.
        double minAmount, maxAmount;
        if (clearedAmount < 0) {
            minAmount = clearedAmount;           // more negative (e.g., -60)
            maxAmount = clearedAmount / 1.30;    // less negative (e.g., ~ -46.15)
        } else {
            // For credits (positive amounts), allow up to 30% higher than the cleared amount
            minAmount = clearedAmount;
            maxAmount = clearedAmount * 1.30;
        }

        if (DEBUG_PROVISIONAL_MATCHING) {
            System.out.println("  Amount range: " + minAmount + " .. " + maxAmount);
        }

        String tipQuery = Transaction.getSelectQuery() +
                " WHERE tr.Register_idRegister = uuid_to_bin('" + idRegister + "')" +
                " AND tr.amount >= " + minAmount +
                " AND tr.amount <= " + maxAmount +
                " AND tr.amount <> " + clearedAmount + // Exclude exact matches (already checked)
                " AND SIGN(tr.amount) = SIGN(" + clearedAmount + ")" + // Ensure same sign
                " AND tr.cleared = false" +
                " AND tr.postDate >= " +
                com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(startDate) +
                " AND tr.postDate <= " +
                com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(endDate) +
                " ORDER BY ABS(DATEDIFF(tr.postDate, " +
                com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(postDate) + ")) ASC" +
                ", ABS(tr.amount - " + clearedAmount + ") ASC";

        ResultSet tipRs = EntityInt.getRS(
                tipQuery,
                "Database error occurred while trying to find tip-tolerance match for provisional transaction."
        );

        if (tipRs == null) {
            if (DEBUG_PROVISIONAL_MATCHING) {
                System.out.println("No provisional candidates returned by tip query.");
                System.out.println("=== End Provisional Matching Debug ===");
            }
            return null;
        }

        int candidateIndex = 0;
        Transaction bestMatch = null;
        FuzzyMatchResult bestResult = null;
        String bestSource = null;

        // For tip matches, require fuzzy payee matching
        while (tipRs.next()) {
            candidateIndex++;
            Transaction candidate = new Transaction(tipRs);

            if (DEBUG_PROVISIONAL_MATCHING) {
                System.out.println("\nTip-tolerance candidate #" + candidateIndex + ":");
                System.out.println("  id:          " + candidate.getId());
                System.out.println("  postDate:    " + candidate.getDate().getTime());
                System.out.println("  amount:      " + candidate.getAmount());
                System.out.println("  payee:       '" + candidate.getPayee() + "'");
                System.out.println("  merchantPayee: '" + candidate.getMerchantPayee() + "'");
            }

            if (merchantPayee != null) {
                // Try comparing to candidate.merchantPayee
                if (candidate.getMerchantPayee() != null) {
                    FuzzyMatchResult result =
                            fuzzyPayeeMatchWithDetails(merchantPayee, candidate.getMerchantPayee());

                    if (DEBUG_PROVISIONAL_MATCHING) {
                        logFuzzyResult("merchantPayee", merchantPayee,
                                candidate.getMerchantPayee(), result);
                    }

                    if (result.match) {
                        bestMatch = candidate;
                        bestResult = result;
                        bestSource = "merchantPayee";
                        break; // First good match wins (ordered by date/amount closeness)
                    }
                }

                // Fall back to comparing against raw payee
                if (candidate.getPayee() != null) {
                    FuzzyMatchResult result =
                            fuzzyPayeeMatchWithDetails(merchantPayee, candidate.getPayee());

                    if (DEBUG_PROVISIONAL_MATCHING) {
                        logFuzzyResult("payee", merchantPayee,
                                candidate.getPayee(), result);
                    }

                    if (result.match) {
                        bestMatch = candidate;
                        bestResult = result;
                        bestSource = "payee";
                        break;
                    }
                }
            }
        }

        if (DEBUG_PROVISIONAL_MATCHING) {
            if (bestMatch != null) {
                System.out.println("\nSelected tip-tolerance match:");
                System.out.println("  id:          " + bestMatch.getId());
                System.out.println("  postDate:    " + bestMatch.getDate().getTime());
                System.out.println("  amount:      " + bestMatch.getAmount());
                System.out.println("  payee:       '" + bestMatch.getPayee() + "'");
                System.out.println("  merchantPayee: '" + bestMatch.getMerchantPayee() + "'");
                System.out.println("Reason: TIP TOLERANCE + Fuzzy match");
                System.out.println("  Matched on:  " + bestSource);
                System.out.println("  matchRatio:  " + bestResult.matchRatio);
                System.out.println("  matchedTokens: " + bestResult.matchedTokens);
            } else {
                System.out.println("\nNo matching provisional transaction found.");
            }
            System.out.println("=== End Provisional Matching Debug ===");
        }

        return bestMatch;
    }

    // Wrapper used by other code that just needs a boolean
    private static boolean fuzzyPayeeMatch(String payee1, String payee2) {
        return fuzzyPayeeMatchWithDetails(payee1, payee2).match;
    }

    /**
     * Performs fuzzy matching between two payee strings to determine if they likely refer
     * to the same transaction. This is useful for matching Wells Fargo provisional transactions
     * with their posted counterparts, where the payee strings differ significantly.
     *
     * The algorithm:
     * 1. Normalizes both strings (uppercase, removes special chars)
     * 2. Tokenizes into words
     * 3. Filters out common/generic words
     * 4. Checks if at least 50% of significant words from the shorter string appear in the longer string
     *
     * @param payee1 First payee string
     * @param payee2 Second payee string
     * @return true if the payees likely match, false otherwise
     */
    private static FuzzyMatchResult fuzzyPayeeMatchWithDetails(String payee1, String payee2) {
        if (payee1 == null || payee2 == null) {
            return new FuzzyMatchResult(false, 0.0,
                    List.of(), List.of(), List.of());
        }

        // Normalize: uppercase and remove special characters except spaces
        String normalized1 = payee1.toUpperCase().replaceAll("[^A-Z0-9 ]", " ");
        String normalized2 = payee2.toUpperCase().replaceAll("[^A-Z0-9 ]", " ");

        // Split into tokens
        String[] tokens1 = normalized1.split("\\s+");
        String[] tokens2 = normalized2.split("\\s+");

        // Common words to ignore when matching
        java.util.Set<String> stopWords = java.util.Set.of(
                "PURCHASE", "AUTHORIZED", "ON", "CARD", "DEBIT", "CREDIT",
                "TRANSACTION", "RECURRING", "PAYMENT", "THE", "AND", "OR",
                "AT", "IN", "TO", "FROM", "FOR", "WITH", "OF"
        );

        // Filter out stop words and very short tokens
        java.util.List<String> significantTokens1 = new java.util.ArrayList<>();
        java.util.List<String> significantTokens2 = new java.util.ArrayList<>();

        for (String token : tokens1) {
            if (token.length() >= 3 && !stopWords.contains(token) && !token.matches("\\d+")) {
                significantTokens1.add(token);
            }
        }

        for (String token : tokens2) {
            if (token.length() >= 3 && !stopWords.contains(token) && !token.matches("\\d+")) {
                significantTokens2.add(token);
            }
        }

        // If either has no significant tokens, can't match
        if (significantTokens1.isEmpty() || significantTokens2.isEmpty()) {
            return new FuzzyMatchResult(false, 0.0,
                    List.of(), significantTokens1, significantTokens2);
        }

        // Count how many tokens from the shorter list appear in the longer list
        java.util.List<String> shorterList = significantTokens1.size() <= significantTokens2.size() ?
                significantTokens1 : significantTokens2;
        java.util.List<String> longerList = significantTokens1.size() > significantTokens2.size() ?
                significantTokens1 : significantTokens2;

        int matchCount = 0;
        java.util.List<String> matchedTokens = new java.util.ArrayList<>();

        for (String token : shorterList) {
            for (String longerToken : longerList) {
                if (longerToken.contains(token) || token.contains(longerToken)) {
                    matchCount++;
                    matchedTokens.add(token);
                    break;
                }
            }
        }

        double matchRatio = (double) matchCount / shorterList.size();
        boolean isMatch = matchRatio >= 0.5;

        return new FuzzyMatchResult(isMatch, matchRatio, matchedTokens,
                shorterList, longerList);
    }


    /**
     * Retrieves a ResultSet of new transactions for a given register.
     *
     * @param register The register to query.
     * @return ResultSet of new transactions.
     * @throws EntityException If a database or entity error occurs.
     */
    public static ResultSet getNewTransactions(Register register) throws EntityException {
        String query =
                Transaction.getSelectQuery() + " " +
                        "where " +
                        "tr.isNew = true and " +
                        "tr.Register_idRegister = uuid_to_bin('" + register.getId() + "') " +
                        "order by tr.authorizationDate asc";
        return EntityInt.getRS(query, "attempting to retrieve a list of transactions that were not " +
                "reported on in a previous new transactions report.");
    }

    /**
     * Checks if there are any transactions skipped with respect to a forecast during the import process.
     *
     * @param forecast The forecast to check against.
     * @return True if there are skipped transactions, false otherwise.
     * @throws EntityException If a database or entity error occurs.
     * @throws SQLException If a SQL error occurs.
     * @throws BudgetException If a budget error occurs.
     * @throws RegisterException If a register error occurs.
     */
    public static boolean isSkippedTransactionsWrtForecast(Forecast forecast) throws EntityException, SQLException, BudgetException, RegisterException {
        Calendar startDate = forecast.getStartDate();
        Calendar fourMonthsAgo = Calendar.getInstance();
        fourMonthsAgo.add(Calendar.MONTH, -4);
        if (fourMonthsAgo.after(startDate)) startDate = fourMonthsAgo;
        String query = Transaction.getCountQuery() + " " +
                "where tr.postDate >= " + com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(fourMonthsAgo) + " and " +
                "tr.Register_idRegister = uuid_to_bin('" + forecast.getBudget().getRegisters().get(0).getId() + "') and " +
                "tr.idTransaction not in " +
                "(select idTransaction from transaction " +
                "inner join transaction_split on idTransaction = Transaction_idTransaction " +
                "inner join forecast_transaction_split on Transaction_idTransaction = Transaction_Split_idTransaction and " +
                "BudgetItem_idBudgetItem = Transaction_Split_idBudgetitem " +
                ") " +
                "order by postDate asc";

        ResultSet rs = EntityInt.getRS(query, "attempting to retrieve a count of the transactions that were previously " +
                "skipped during the import process.");
        rs.next();
        int count = rs.getInt(1);
        return (count > 0);
    }

    /**
     * Retrieves a ResultSet of transactions that were skipped with respect to a forecast during the import process.
     *
     * @param forecast The forecast to check against.
     * @return ResultSet of skipped transactions.
     * @throws EntityException If a database or entity error occurs.
     * @throws BudgetException If a budget error occurs.
     * @throws SQLException If a SQL error occurs.
     * @throws RegisterException If a register error occurs.
     */
    public static ResultSet getSkippedTransactionsWrtForecast(Forecast forecast) throws EntityException, BudgetException, SQLException, RegisterException {
        Calendar startDate = forecast.getStartDate();
        Calendar fourMonthsAgo = Calendar.getInstance();
        fourMonthsAgo.add(Calendar.MONTH, -4);
        if (fourMonthsAgo.after(startDate)) startDate = fourMonthsAgo;
        String query = Transaction.getSelectQuery() + " " +
                "where tr.postDate >= " + com.hixon.financialApp.utility.Utility.calendarDateToSqlDateString(fourMonthsAgo) + " and " +
                "tr.Register_idRegister = uuid_to_bin('" + forecast.getBudget().getRegisters().get(0).getId() + "') and " +
                "tr.idTransaction not in " +
                "(select idTransaction from transaction " +
                "inner join transaction_split on idTransaction = Transaction_idTransaction " +
                "inner join forecast_transaction_split on Transaction_idTransaction = Transaction_Split_idTransaction and " +
                "BudgetItem_idBudgetItem = Transaction_Split_idBudgetitem " +
                ") " +
                "order by authorizationDate asc";
        return EntityInt.getRS(query, "attempting to retrieve a list of transactions that were previously " +
                "skipped during the import process.");
    }

    /**
     * Sets a list of transactions as not new and updates them in the database.
     *
     * @param transactions The list of transactions to update.
     * @throws SQLException If a SQL error occurs.
     * @throws EntityException If a database or entity error occurs.
     */
    public static void setTransactionsNotNew(List<Transaction> transactions) throws SQLException, EntityException {
        for (Transaction transaction : transactions) {
            transaction.setIsNew(false);
            transaction.save(Transaction.SaveMethod.UPDATE);
        }
    }

    /**
     * Retrieves the most recent transaction by payee and amount, ignoring the REF # in the payee.
     *
     * @param payee The payee to search for.
     * @param amount The transaction amount.
     * @return The most recent matching Transaction, or null if none found.
     * @throws Exception If a database or query error occurs.
     */
    public static Transaction getMostRecentTransactionByPayee(String payee, double amount) throws Exception {
        String query =
                Transaction.getSelectQuery() + " " +
                        "INNER JOIN merchant m on " +
                        "tr.Merchant_idMerchant = m.idMerchant " +
                        "WHERE " +
                        "tr.payee LIKE '" + payee.replaceAll("REF #\\S+", "REF #%" ) + "' " +
                        "AND tr.amount BETWEEN " + (amount * 0.9) + " AND " + (amount * 1.1) + " " +
                        "ORDER BY " +
                        "tr.postDate DESC " +
                        "lIMIT 1";

        ResultSet rs = EntityInt.getRS(query, "attempting to retrieve the most recent transaction by payee.");
        if (rs.next()) {
            return new Transaction(rs);
        } else {
            return null;
        }
    }

    /**
     * Retrieves up to 10 similar transactions using a full text search on the user description.
     *
     * @param userDescription The user description to search for.
     * @return List of up to 10 similar transactions.
     * @throws Exception If a database or query error occurs.
     */
    public static List<Transaction> getByUserDescriptionFullText(String userDescription) throws Exception {
        String query =
                "WITH ranked_transactions AS ( " +
                        "SELECT " +
                        "tr.*, " +
                        "BIN_TO_UUID(tr.idTransaction) AS uuidTransaction, " +
                        "BIN_TO_UUID(tr.Register_idRegister) AS uuidRegister, " +
                        "BIN_TO_UUID(tr.Merchant_idMerchant) AS uuidMerchant, " +
                        "TRIM( " +
                        "CONCAT( " +
                        "SUBSTRING_INDEX(tr.payee, '#', 1), " +
                        "SUBSTRING(tr.payee, LOCATE(' ', tr.payee, LOCATE('#', tr.payee)) + 1) " +
                        ") " +
                        ") AS normalized_payee, " +
                        "MATCH (user_description) AGAINST ('ALIMONY' IN NATURAL LANGUAGE MODE) AS relevance, " +
                        "ROW_NUMBER() OVER ( " +
                        "PARTITION BY " +
                        "TRIM( " +
                        "CONCAT( " +
                        "SUBSTRING_INDEX(tr.payee, '#', 1), " +
                        "SUBSTRING(tr.payee, LOCATE(' ', tr.payee, LOCATE('#', tr.payee)) + 1) " +
                        " ) " +
                        "), " +
                        "tr.amount, " +
                        "tr.Merchant_idMerchant " +
                        "ORDER BY " +
                        "tr.postDate DESC " +
                        ") AS rn " +
                        "FROM transaction tr " +
                        "WHERE MATCH (user_description) AGAINST ('" + userDescription + "' IN NATURAL LANGUAGE MODE) " +
                        ") " +
                        "SELECT " +
                        "uuidTransaction AS 'tr.idTransaction', " +
                        "postDate AS 'tr.postDate', " +
                        "authorizationDate AS 'tr.authorizationDate', " +
                        "amount AS 'tr.amount', " +
                        "cleared AS 'tr.cleared', " +
                        "checkNumber AS 'tr.checkNumber', " +
                        "normalized_payee AS 'tr.payee', " +
                        "user_description AS 'tr.user_description', " +
                        "balance AS 'tr.balance', " +
                        "isImproper AS 'tr.isImproper', " +
                        "isNew AS 'tr.isNew', " +
                        "importRecordId AS 'tr.importRecordId', " +
                        "uuidRegister AS 'tr.idRegister', " +
                        "uuidMerchant AS 'tr.idMerchant', " +
                        "relevance " +
                        "FROM ranked_transactions " +
                        "WHERE rn = 1 " +
                        "ORDER BY relevance DESC " +
                        "LIMIT 10";

        ResultSet rs = EntityInt.getRS(query, "attempting to retrieve similar transactions by payee.");
        List<Transaction> transactions = new ArrayList<>();
        while (rs.next()) {
            transactions.add(new Transaction(rs));
        }
        return transactions;
    }
}
