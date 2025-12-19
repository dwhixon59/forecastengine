package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.NumberOrStringResponse;
import com.hixon.financialApp.view.base.ViewInt;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.util.*;

import static com.hixon.financialApp.utility.Utility.formatDollarAmount;
import static com.hixon.financialApp.view.base.ViewInt.*;

/**
 * This class is the controller for transaction-related business logic interface.
 */
@Getter
@Setter
public class TransactionController {

    private static final Logger logger = LogManager.getLogger(TransactionController.class);

    /*
     * Member variables for the Transaction Controller:
     */
    protected SessionController sessionController;
    protected Register register;
    protected Budget budget;
    protected Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;

    // Help text properties loaded from file
    private static final Properties helpText = new Properties();

    static {
        try (InputStream input = TransactionController.class.getClassLoader()
                .getResourceAsStream("help-text.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find help-text.properties");
            }
            helpText.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load help text properties", ex);
        }
    }

    /*
     * Constructors and destructor for the Transaction Controller:
     */
    public TransactionController(SessionController sessionController) {
        this.sessionController = sessionController;
        this.register = sessionController.getRegister();
        this.budget = sessionController.getBudget();
        this.forecast = sessionController.getForecast();
        this.view = sessionController.getView();
        this.notificationService = sessionController.getNotificationService();
    }

    /*
     * Main functions for the Transaction Controller:
     */

    /**
     * Allows the user to manage transactions interactively using a unified search-based interface.
     * The workflow is:
     * 1. Select which register to work with (if not already set)
     * 2. Search for a transaction (with various filter options)
     * 3. Choose what to do with it (view, update, delete, or manage splits)
     *
     * This approach is more intuitive than a menu-based system because users naturally
     * think about WHICH transaction first, then WHAT to do with it.
     *
     * @throws Exception if any error occurs during management operations
     * @throws QuitException if the user chooses to quit
     */
    public void manageTransactions() throws Exception, QuitException {
        Register lastSelectedRegister = register;  // Track the last selected register across operations
        boolean done = false;
        String pendingSearchString = null;  // Track search string from action menu

        while (!done) {
            try {
                // Step 1: Select which register to work with
                if (lastSelectedRegister == null) {
                    lastSelectedRegister = RegisterController.selectRegister(view);
                    register = lastSelectedRegister;
                    // Also ensure budget is set
                    if (budget == null) {
                        budget = Budget.getById(register.getBudgetID());
                    }
                }

                // Step 2: Search for and select transactions
                // Returns a TransactionSearchResult containing the list and selected transaction
                TransactionSearchResult searchResult = selectTransactionFromRegister(lastSelectedRegister, pendingSearchString);
                pendingSearchString = null;  // Clear after use

                // User selected a transaction - work with it and potentially select more from the same list
                boolean selectingFromCurrentSearch = true;
                while (selectingFromCurrentSearch) {
                    Transaction selectedTransaction = searchResult.getSelectedTransaction();

                    // User action loop for the current transaction
                    boolean actionComplete = false;
                    while (!actionComplete) {
                        // Display the selected transaction
                        view.say();
                        view.say("Selected transaction:");
                        view.say("  " + selectedTransaction.toStringVeryConcise());

                        // Warn if disputed/improper
                        if (selectedTransaction.getIsImproper()) {
                            view.say("\nNOTE: This transaction is marked as DISPUTED/IMPROPER.");
                        }

                        // Step 3: Ask what to do with this transaction using the flexible method
                        // User can enter:
                        // - A number (1-25) to select another transaction from the list
                        // - A single letter (v,u,a,r,m,d,s) for a menu command
                        // - Multi-character string for new search criteria
                        // - 'C' to cancel, 'Q' to quit

                        // Build display strings for transactions
                        List<String> transactionDisplayStrings = new ArrayList<>();
                        for (Transaction t : searchResult.getTransactions()) {
                            try {
                                transactionDisplayStrings.add(t.toStringVeryConcise());
                            } catch (Exception e) {
                                transactionDisplayStrings.add("Transaction ID: " + t.getId());
                            }
                        }

                        NumberOrStringResponse response = view.selectFromListByPositionOrMenuOrString(
                                null,  // Don't show the list here since we're in the action menu
                                transactionDisplayStrings,  // Transaction display strings for validation
                                "What would you like to do with this transaction?",
                                List.of("view details", "update this transaction", "assign/change merchant",
                                        "recategorize transaction", "manage splits/categories",
                                        "delete this transaction"),
                                ALLOW_CREATE,  // Allow multi-character strings as new search criteria
                                ALLOW_CANCEL,
                                ALLOW_QUIT,
                                DO_NOT_ALLOW_SKIP);

                        // Check if user entered multi-character string (new search criteria)
                        if (!response.isNumber() && response.getSearchString().length() > 1) {
                            // User entered new search criteria - store it and exit to perform new search
                            searchResult.setNextSearchString(response.getSearchString());
                            actionComplete = true;
                            selectingFromCurrentSearch = false;
                            continue;
                        }

                        // Check if user selected a different transaction by number
                        if (response.isNumber()) {
                            int index = response.getSelectedIndex();
                            selectedTransaction = searchResult.getTransactions().get(index);
                            searchResult.setSelectedTransaction(selectedTransaction);
                            // Stay in action loop with the new transaction
                            continue;
                        }

                        // User selected a menu command
                        String action = response.getSearchString();

                        switch (action) {
                            case "v":  // view details
                                view.say();
                                view.say("Transaction Details:");
                                view.say("──────────────────────────────────────");
                                displayTransactionDetails(selectedTransaction);
                                view.say("──────────────────────────────────────");
                                break;

                            case "u":  // update this transaction
                                Transaction updatedTransaction = getTransactionFromUser(selectedTransaction);
                                updatedTransaction.setId(selectedTransaction.getId()); // Preserve the original ID
                                updatedTransaction.update();
                                view.say("Transaction successfully updated.");
                                selectedTransaction = updatedTransaction; // Update reference for display
                                searchResult.setSelectedTransaction(selectedTransaction);
                                break;

                            case "a":  // assign/change merchant
                                try {
                                    assignMerchantToTransaction(selectedTransaction);
                                    view.say("Merchant successfully assigned.");
                                    // Reload the transaction to show updated merchant info
                                    selectedTransaction = Transaction.getById(selectedTransaction.getId());
                                    searchResult.setSelectedTransaction(selectedTransaction);
                                } catch (SkipException e) {
                                    view.say("Merchant assignment skipped.");
                                }
                                break;

                            case "r":  // recategorize transaction
                                try {
                                    recategorizeTransaction(selectedTransaction);
                                    view.say("Transaction successfully recategorized.");
                                    // Reload the transaction to show updated splits
                                    selectedTransaction = Transaction.getById(selectedTransaction.getId());
                                    searchResult.setSelectedTransaction(selectedTransaction);
                                } catch (SkipException e) {
                                    view.say("Recategorization skipped.");
                                }
                                break;

                            case "m":  // manage splits/categories
                                // Delegate to TransactionSplitsController
                                manageSplitsForTransaction(selectedTransaction);
                                break;

                            case "d":  // delete this transaction
                                view.say("\nYou are about to delete:");
                                view.say("  " + selectedTransaction.toStringVeryConcise());

                                // Check for transaction splits associated with this transaction
                                List<TransactionSplit> splits = getTransactionSplits(selectedTransaction);
                                if (!splits.isEmpty()) {
                                    view.say("\nWARNING: This transaction has " + splits.size() +
                                            " split(s) associated with it.");
                                    view.say("Deleting this transaction will CASCADE DELETE all associated splits.");
                                }

                                if (view.getYesOrNo("\nAre you sure you want to delete this transaction?")) {
                                    try {
                                        selectedTransaction.delete();
                                        view.say("Transaction deleted successfully.");

                                        // Remove from cached list
                                        searchResult.getTransactions().remove(selectedTransaction);

                                        // If list is now empty, go back to search
                                        if (searchResult.getTransactions().isEmpty()) {
                                            view.say("No more transactions in the current list.");
                                            selectingFromCurrentSearch = false;
                                            actionComplete = true;
                                        } else {
                                            actionComplete = true;
                                        }
                                    } catch (Exception e) {
                                        view.say("Error deleting transaction: " + e.getMessage());
                                        logger.error("Error deleting transaction", e);
                                    }
                                } else {
                                    view.say("Deletion cancelled.");
                                }
                                break;

                            default:
                                throw new InvalidEntryException("Unexpected option returned: " + action);
                        }
                    }

                    // After completing an action (except search again which exits the loop),
                    // automatically show the list again for the user to select another transaction
                    if (selectingFromCurrentSearch && !searchResult.getTransactions().isEmpty()) {
                        try {
                            Transaction nextTransaction = selectFromCachedList(searchResult.getTransactions());
                            searchResult.setSelectedTransaction(nextTransaction);
                        } catch (CancelException e) {
                            // User cancelled from selection - go back to search
                            selectingFromCurrentSearch = false;
                        }
                    }
                }

                // Check if user entered a new search string from the action menu
                if (searchResult.getNextSearchString() != null) {
                    pendingSearchString = searchResult.getNextSearchString();
                }

            } catch (CancelException e) {
                done = true;
            }
        }
    }

    /**
     * Selects a transaction from a specified register using a flexible search interface.
     * Allows searching by payee, merchant, date range, amount range, and filter options.
     * If user cancels from selection list, loops back to search prompt.
     * Only throws CancelException if user cancels from the search prompt itself.
     *
     * @param register The register to search within
     * @param initialSearchString Optional initial search string to use (can be null)
     * @return TransactionSearchResult containing the list of transactions and the selected one
     * @throws Exception if any error occurs during selection
     * @throws CancelException if the user cancels from the search prompt
     */
    private TransactionSearchResult selectTransactionFromRegister(Register register, String initialSearchString) throws Exception, CancelException {

        String searchString = initialSearchString; // Use provided search string or null to prompt user

        while (true) {
            // Show search menu only if we don't already have a search string from a previous iteration
            if (searchString == null) {
                view.say("\n--- Transaction Search ---");
                view.say("You can search by:");
                view.say("  • Payee or merchant name");
                view.say("  • Date range:");
                view.say("      - Full dates: '2024-01-01 to 2024-12-31'");
                view.say("      - Month-day: '01-15 to 03-20' (defaults to current year, wraps to next year if needed)");
                view.say("      - Day only: '15 to 20' (defaults to current month, wraps to next month if needed)");
                view.say("  • Amount (e.g., '25.00' for exact match)");
                view.say("  • Amount range (e.g., '10.00 to 50.00')");
                view.say("  • Filters: cleared:yes, cleared:no, new:yes, disputed:yes");
                view.say("  • Or press Enter to see all transactions");
                view.say();

                // Build search query with filters
                // Note: If user cancels here, CancelException will bubble up to caller
                String searchPrompt = "Search for transaction (payee, date range, amount, amount range or filters)";
                try {
                    searchString = view.getResponseString(searchPrompt, null, ALLOW_NONE,
                            DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                            () -> helpText.getProperty("transaction.search", "No help available"));
                } catch (CancelException e) {
                    // User cancelled from search prompt - bubble up to exit to entity management menu
                    throw e;
                }
            }

            // Parse search string and filters
            SearchCriteria criteria = parseSearchCriteria(searchString, register);

            // Build the query based on criteria
            String query = buildTransactionSearchQuery(criteria);

            // Execute the query
            ResultSet rs = EntityInt.getRS(query, "trying to search for transactions");

            // Build list of matching transactions
            List<Transaction> transactions = new ArrayList<>();
            while (rs.next()) {
                transactions.add(new Transaction(rs));
            }

            if (transactions.isEmpty()) {
                view.say("No transactions found matching your search criteria.");
                // Reset search string and loop back to search prompt
                searchString = null;
                continue;
            }

            // Build display strings for the transactions
            List<String> transactionDisplayStrings = new ArrayList<>();
            for (Transaction t : transactions) {
                try {
                    transactionDisplayStrings.add(t.toStringVeryConcise());
                } catch (Exception e) {
                    transactionDisplayStrings.add("Transaction ID: " + t.getId());
                }
            }

            // Let user select from the list or enter a new search string
            try {
                NumberOrStringResponse result = view.selectFromListByPositionOrMenuOrString(
                        "Select a transaction",
                        transactionDisplayStrings,
                        "", // No menu prompt - just list selection
                        List.of(), // No menu options - only list selection
                        ALLOW_CREATE, // Allow entering a new search string
                        ALLOW_CANCEL,
                        ALLOW_QUIT,
                        DO_NOT_ALLOW_SKIP);

                if (result.isNumber()) {
                    // User selected a transaction by number
                    int index = result.getSelectedIndex(); // Already 0-based from view layer
                    if (index >= 0 && index < transactions.size()) {
                        return new TransactionSearchResult(transactions, transactions.get(index));
                    } else {
                        view.say("Invalid selection. Please try again.");
                        searchString = null; // Reset and go back to search prompt
                        continue;
                    }
                } else {
                    // User entered a new search string - use it for the next iteration
                    searchString = result.getSearchString();
                    // Loop back to execute the new search
                    continue;
                }
            } catch (CancelException e) {
                // User cancelled from the selection list - exit to entity management menu
                throw e;
            }
        }
    }

    /**
     * Parses the search string and extracts search criteria and filters.
     */
    private SearchCriteria parseSearchCriteria(String searchString, Register register) {
        SearchCriteria criteria = new SearchCriteria();
        criteria.registerId = register.getId();
        criteria.searchText = searchString;

        // Parse filters from search string
        if (searchString != null && !searchString.isEmpty()) {
            // Check for date range pattern (supports YYYY-MM-DD, MM-DD, or DD formats)
            // Pattern matches: "something to something" where something contains digits and optionally dashes
            String dateRangePattern = "([\\d-]+)\\s+to\\s+([\\d-]+)";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(dateRangePattern);
            java.util.regex.Matcher matcher = pattern.matcher(searchString);

            if (matcher.find()) {
                // Found a potential date range - try parsing with flexible parser
                String dateRangeStr = matcher.group(0);  // Full match including "to"
                Calendar[] dates = Utility.parseFlexibleDateRange(dateRangeStr);

                if (dates != null) {
                    criteria.startDate = dates[0];
                    criteria.endDate = dates[1];

                    // Remove the date range from search text
                    searchString = searchString.replaceAll(java.util.regex.Pattern.quote(dateRangeStr), "").trim();
                    criteria.searchText = searchString;
                } else {
                    view.say("Warning: Could not parse date range. Supported formats:");
                    view.say("  - Full: 'YYYY-MM-DD to YYYY-MM-DD'");
                    view.say("  - Month-day: 'MM-DD to MM-DD'");
                    view.say("  - Day only: 'DD to DD'");
                }
            }

            // Check for amount range pattern (12.34 to 56.78)
            String amountRangePattern = "(\\d+\\.\\d{2})\\s+to\\s+(\\d+\\.\\d{2})";
            java.util.regex.Pattern amountPattern = java.util.regex.Pattern.compile(amountRangePattern);
            java.util.regex.Matcher amountMatcher = amountPattern.matcher(searchString);

            if (amountMatcher.find()) {
                // Found an amount range
                try {
                    String minAmountStr = amountMatcher.group(1);
                    String maxAmountStr = amountMatcher.group(2);

                    criteria.minAmount = Double.parseDouble(minAmountStr);
                    criteria.maxAmount = Double.parseDouble(maxAmountStr);

                    // Remove the amount range from search text
                    searchString = searchString.replaceAll(amountRangePattern, "").trim();
                } catch (Exception e) {
                    view.say("Warning: Could not parse amount range. Format should be '10.00 to 50.00'");
                }
            } else {
                // Check for single amount pattern (12.34)
                String singleAmountPattern = "\\b(\\d+\\.\\d{2})\\b";
                java.util.regex.Pattern singlePattern = java.util.regex.Pattern.compile(singleAmountPattern);
                java.util.regex.Matcher singleMatcher = singlePattern.matcher(searchString);

                if (singleMatcher.find()) {
                    // Found a single amount - search for exact match
                    try {
                        String amountStr = singleMatcher.group(1);
                        Double amount = Double.parseDouble(amountStr);
                        criteria.minAmount = amount;
                        criteria.maxAmount = amount;

                        // Remove the amount from search text
                        searchString = searchString.replaceAll(singleAmountPattern, "").trim();
                    } catch (Exception e) {
                        view.say("Warning: Could not parse amount. Format should be '25.00'");
                    }
                }
            }

            // Parse other filters
            String[] parts = searchString.split("\\s+");
            StringBuilder textParts = new StringBuilder();

            for (String part : parts) {
                if (part.contains(":")) {
                    String[] filter = part.split(":", 2);
                    String key = filter[0].toLowerCase();
                    String value = filter.length > 1 ? filter[1].toLowerCase() : "";

                    switch (key) {
                        case "cleared":
                            criteria.clearedFilter = value.equals("yes") ? Boolean.TRUE :
                                    value.equals("no") ? Boolean.FALSE : null;
                            break;
                        case "new":
                            criteria.newFilter = value.equals("yes");
                            break;
                        case "disputed":
                            criteria.disputedFilter = value.equals("yes");
                            break;
                        default:
                            textParts.append(part).append(" ");
                    }
                } else {
                    if (!part.isEmpty()) {
                        textParts.append(part).append(" ");
                    }
                }
            }

            // Update searchText with remaining text after filters have been extracted
            String finalSearchText = textParts.toString().trim();
            criteria.searchText = finalSearchText;
        }

        return criteria;
    }

    /**
     * Builds a SQL query based on the search criteria.
     */
    private String buildTransactionSearchQuery(SearchCriteria criteria) {
        StringBuilder query = new StringBuilder(Transaction.getSelectQuery());
        query.append(" LEFT JOIN merchant m ON tr.Merchant_idMerchant = m.idMerchant");
        query.append(" WHERE tr.Register_idRegister = uuid_to_bin('").append(criteria.registerId).append("')");

        // Add date range search if provided
        if (criteria.startDate != null) {
            query.append(" AND tr.postDate >= ").append(Utility.calendarDateToSqlDateString(criteria.startDate));
        }
        if (criteria.endDate != null) {
            query.append(" AND tr.postDate <= ").append(Utility.calendarDateToSqlDateString(criteria.endDate));
        }

        // Add text search if provided (and not empty after date range extraction)
        if (criteria.searchText != null && !criteria.searchText.isEmpty() && !criteria.searchText.equals("*")) {
            // Escape single quotes by doubling them to prevent SQL syntax errors
            String escapedSearchText = Utility.escapeSqlString(criteria.searchText);
            query.append(" AND (tr.payee LIKE '%").append(escapedSearchText).append("%'");
            query.append(" OR m.name LIKE '%").append(escapedSearchText).append("%')");
        }

        // Add amount search if provided
        if (criteria.minAmount != null && criteria.maxAmount != null) {
            if (criteria.minAmount.equals(criteria.maxAmount)) {
                // Exact amount match
                query.append(" AND ABS(tr.amount) = ").append(Math.abs(criteria.minAmount));
            } else {
                // Amount range
                query.append(" AND ABS(tr.amount) >= ").append(Math.abs(criteria.minAmount));
                query.append(" AND ABS(tr.amount) <= ").append(Math.abs(criteria.maxAmount));
            }
        }

        // Add filter conditions
        if (criteria.clearedFilter != null) {
            query.append(" AND tr.cleared = ").append(criteria.clearedFilter);
        }
        if (criteria.newFilter) {
            query.append(" AND tr.isNew = true");
        }
        if (criteria.disputedFilter) {
            query.append(" AND tr.isImproper = true");
        }

        // Order by date descending (most recent first)
        query.append(" ORDER BY tr.postDate DESC, tr.authorizationDate DESC LIMIT 100");

        return query.toString();
    }

    /**
     * Displays detailed information about a transaction.
     */
    private void displayTransactionDetails(Transaction transaction) throws Exception {
        view.say("ID: " + transaction.getId());
        view.say("Post Date: " + (transaction.getPostDate() != null ?
                Utility.calendarDateToStringDate(transaction.getPostDate()) : "None"));
        view.say("Authorization Date: " + (transaction.getAuthorizationDate() != null ?
                Utility.calendarDateToStringDate(transaction.getAuthorizationDate()) : "None"));
        view.say("Payee: " + (transaction.getPayee() != null ? transaction.getPayee() : "None"));

        Merchant merchant = transaction.getMerchant();
        view.say("Merchant: " + (merchant != null ? merchant.getName() : "Not assigned"));

        view.say("Amount: " + formatDollarAmount(transaction.getAmount()));
        view.say("Balance: " + formatDollarAmount(transaction.getBalance()));
        view.say("Cleared: " + (transaction.isCleared() ? "Yes" : "No"));
        view.say("Check Number: " + (transaction.getCheckNumber() != 0 ? transaction.getCheckNumber() : "None"));
        view.say("New: " + (transaction.getIsNew() ? "Yes" : "No"));
        view.say("Disputed/Improper: " + (transaction.getIsImproper() ? "Yes" : "No"));
        view.say("Import Record ID: " + (transaction.getImportRecordId() != null ? transaction.getImportRecordId() : "None"));

        // Show splits if any
        List<TransactionSplit> splits = getTransactionSplits(transaction);
        if (!splits.isEmpty()) {
            view.say("\nTransaction Splits:");
            for (TransactionSplit split : splits) {
                BudgetItem budgetItem = split.getBudgetItem();
                view.say("  • " + formatDollarAmount(split.getAmount()) + " → " +
                        (budgetItem != null ? budgetItem.getDisplayString() : "Unknown budget item") +
                        (split.getMemo() != null && !split.getMemo().isEmpty() ? " (" + split.getMemo() + ")" : ""));
            }
        } else {
            view.say("\nNo splits/categories assigned to this transaction.");
        }
    }

    /**
     * Prompts the user to update transaction fields.
     */
    private Transaction getTransactionFromUser(Transaction template) throws Exception {
        view.say("\nUpdate Transaction Fields (press Enter to keep current value):");

        // Post Date
        Calendar postDate = template.getPostDate();
        String postDateStr = view.getResponseString("Post Date (" +
                        Utility.calendarDateToStringDate(postDate) + ")", "",
                ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
        if (!postDateStr.isEmpty()) {
            postDate = Utility.stringDateDashToCalendarDate(postDateStr);
        }

        // Amount
        String amountStr = view.getResponseString("Amount (" + formatDollarAmount(template.getAmount()) + ")", "",
                ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, DO_NOT_ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
        double amount = template.getAmount();
        if (!amountStr.isEmpty()) {
            amount = Double.parseDouble(amountStr);
        }

        // Cleared status
        boolean cleared = view.getYesOrNo("Is this transaction cleared? (currently: " +
                (template.isCleared() ? "Yes" : "No") + ")");

        // Disputed/Improper status
        boolean disputed = view.getYesOrNo("Is this transaction disputed/improper? (currently: " +
                (template.getIsImproper() ? "Yes" : "No") + ")");

        // Update the template transaction
        template.setPostDate(postDate);
        template.setAmount(amount);
        template.setCleared(cleared);
        template.setIsImproper(disputed);

        return template;
    }

    /**
     * Gets all transaction splits for a transaction.
     */
    private List<TransactionSplit> getTransactionSplits(Transaction transaction) throws Exception {
        String query = "SELECT " +
                "ts.amount as 'ts.amount', " +
                "bin_to_uuid(ts.BudgetItem_idBudgetItem) as 'ts.idBudgetItem', " +
                "bin_to_uuid(ts.Transaction_idTransaction) as 'ts.idTransaction', " +
                "ts.memo as 'ts.memo' " +
                "FROM transaction_split ts " +
                "WHERE ts.Transaction_idTransaction = uuid_to_bin('" + transaction.getId() + "')";

        ResultSet rs = EntityInt.getRS(query, "trying to get transaction splits");
        List<TransactionSplit> splits = new ArrayList<>();
        while (rs.next()) {
            splits.add(new TransactionSplit(rs));
        }
        return splits;
    }

    /**
     * Manages splits for a transaction by delegating to TransactionSplitsController.
     *
     * @param transaction The transaction whose splits should be managed
     */
    private void manageSplitsForTransaction(Transaction transaction) {
        view.say("\nManaging splits/categories for transaction...");

        // TODO: Implement split management workflow
        // This would involve:
        // 1. Showing current splits
        // 2. Allowing user to add/remove/modify splits
        // 3. Using TransactionSplitsController.getSplits() method

        view.say("Split management interface not yet fully implemented.");
        view.say("This will be handled through the TransactionSplitsController.");
    }

    /**
     * Inner class to hold search criteria for transactions.
     */
    private static class SearchCriteria {
        UUID registerId;
        String searchText;
        Boolean clearedFilter = null;
        boolean newFilter = false;
        boolean disputedFilter = false;
        Calendar startDate = null;
        Calendar endDate = null;
        Double minAmount = null;
        Double maxAmount = null;
    }

    /**
     * Assigns or changes the merchant for a transaction.
     * This reuses the merchant assignment logic from the import process.
     *
     * @param transaction The transaction to assign a merchant to
     * @throws Exception if any error occurs during merchant assignment
     * @throws SkipException if the user skips the merchant assignment
     */
    public void assignMerchantToTransaction(Transaction transaction) throws Exception, SkipException {
        view.say("\n--- Assign/Change Merchant ---");

        Merchant currentMerchant = transaction.getMerchant();
        if (currentMerchant != null) {
            view.say("Current merchant: " + currentMerchant.getName());
        } else {
            view.say("No merchant currently assigned.");
        }

        // Use MerchantController to assign a merchant
        MerchantController merchantController = new MerchantController(view, notificationService);

        // Parse the merchant payee string for creating the MerchantPayee mapping
        // Note: merchantPayee is not stored in the transaction table, but is used to create
        // a MerchantPayee record that maps the cleaned payee string to the merchant
        String merchantPayeeString;
        try {
            // Get the register and financial institution to parse the merchant payee
            Register transactionRegister = transaction.getRegister();

            // For now, hardcode WellsFargoBank since that's what's currently used
            // TODO: Make this configurable based on register's financial institution
            com.hixon.financialApp.model.financialinstitution.FinancialInstitutionInt financialInstitution =
                    new com.hixon.financialApp.model.financialinstitution.WellsFargoBank(
                            transactionRegister, budget, forecast, view, notificationService);

            // Parse the merchant payee from the raw payee to get a cleaned, shortened version
            // Example: "PURCHASE AUTHORIZED ON 11/18 TARGET T-0799..." -> "TARGET T-0799 Sarasota FL"
            merchantPayeeString = financialInstitution.parseMerchantPayee(
                    transaction.getDate(),
                    transaction.getAmount(),
                    transaction.getPayee());

        } catch (Exception e) {
            // If parsing fails, fall back to using the raw payee
            // This may cause database errors if the raw payee is too long
            view.say("Warning: Could not parse merchant payee from raw payee. Using raw payee instead.");
            view.say("Error: " + e.getMessage());
            merchantPayeeString = transaction.getPayee();
        }

        Merchant merchant = merchantController.assignMerchant(
                merchantPayeeString,
                transaction.getPayee(),
                transaction.getAmount(),
                true);  // Always require confirmation for manual merchant assignment

        if (merchant != null) {
            // Update the transaction with the new merchant
            transaction.setMerchant(merchant);
            transaction.setIdMerchant(merchant.getId());
            transaction.update();
        }
    }

    /**
     * Recategorizes a transaction by deleting existing splits and reprocessing it.
     * This extracts and reuses the logic from processUnreconciledTransactions.
     * Uses database transaction management to ensure data integrity - all changes are
     * rolled back if the user cancels or an error occurs.
     *
     * @param transaction The transaction to recategorize
     * @throws Exception if any error occurs during recategorization
     * @throws SkipException if the user skips the recategorization
     */
    public void recategorizeTransaction(Transaction transaction) throws Exception, SkipException {
        view.say("\n--- Recategorize Transaction ---");

        // Show current splits
        List<TransactionSplit> currentSplits = getTransactionSplits(transaction);
        if (!currentSplits.isEmpty()) {
            view.say("Current categorization:");
            for (TransactionSplit split : currentSplits) {
                BudgetItem budgetItem = split.getBudgetItem();
                view.say("  • " + formatDollarAmount(split.getAmount()) + " → " +
                        (budgetItem != null ? budgetItem.getDisplayString() : "Unknown budget item"));
            }
            view.say();
        }

        // Start a database transaction to ensure atomicity
        java.sql.Connection conn = Utility.getDbConnection();
        boolean originalAutoCommit = conn.getAutoCommit();

        try {
            // Disable auto-commit to start transaction
            conn.setAutoCommit(false);

            // Delete existing splits using SQL (TransactionSplit.delete() returns null query)
            if (!currentSplits.isEmpty()) {
                String deleteQuery = "DELETE FROM transaction_split WHERE Transaction_idTransaction = uuid_to_bin('" +
                        transaction.getId() + "')";
                EntityInt.executeUpdate(deleteQuery, "deleting transaction splits for recategorization");
                view.say("Existing splits deleted.");
            }

            // Process the transaction to create new splits
            reconcileTransaction(transaction);

            // If we got here without exception, commit the transaction
            conn.commit();
            view.say("Transaction recategorization committed successfully.");

        } catch (CancelException | SkipException e) {
            // User cancelled - rollback all changes
            conn.rollback();
            view.say("Recategorization cancelled - all changes have been rolled back.");
            throw e;

        } catch (Exception e) {
            // Error occurred - rollback all changes
            conn.rollback();
            view.say("Error during recategorization - all changes have been rolled back.");
            throw e;

        } finally {
            // Always restore the original auto-commit state
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    /**
     * Reconciles a single transaction by assigning budget items and creating splits.
     * This method is extracted from RegisterController.processUnreconciledTransactions() to allow
     * reuse for recategorizing individual transactions.
     *
     * @param transaction The transaction to reconcile
     * @throws Exception if any error occurs during reconciliation
     * @throws SkipException if the user skips the reconciliation
     */
    public void reconcileTransaction(Transaction transaction) throws Exception, SkipException {
        BudgetController budgetController = new BudgetController(register, budget, forecast, view, notificationService);

        Merchant merchant = transaction.getMerchant();

        // If no merchant assigned, we need to assign one first
        if (merchant == null || merchant.getName().equalsIgnoreCase(Merchant.UNKNOWN)) {
            view.say("This transaction needs a merchant assigned before categorization.");
            assignMerchantToTransaction(transaction);
            merchant = transaction.getMerchant();

            // If still no merchant, we can't proceed
            if (merchant == null) {
                throw new SkipException("Cannot categorize transaction without a merchant.");
            }
        }

        // Get the assigned budget items for the merchant
        List<BudgetItemMerchant> budgetItemsForMerchant =
                BudgetItemMerchant.getAssignedUnexpiredBudgetItems(budget, merchant);

        // If we couldn't find any matching items, get some help from the user
        if (budgetItemsForMerchant.isEmpty()) {
            budgetController.assignBudgetItemsToMerchant(merchant, budgetItemsForMerchant);
        }

        // Get or create the splits for the transaction
        List<TransactionSplit> splits = TransactionSplit.getSplitsForTransaction(transaction);
        if (splits == null || splits.isEmpty()) {
            splits = budgetController.assignAmountsToBudgetItems(transaction, merchant, budget, budgetItemsForMerchant);
        }

        // Mark the transaction as new so it appears in the new transaction report
        transaction.setIsNew(true);

        // Save the transaction and associated splits
        transaction.save(EntityInt.SaveMethod.INSERT_ON_DUPLICATE_UPDATE);
        if (splits != null && !splits.isEmpty()) {
            for (TransactionSplit split : splits) {
                split.save();
            }

            // Ask the user if they want to reconcile with a forecast
            if (view.getYesOrNo("Do you want to reconcile this transaction with a forecast?")) {
                Forecast forecastToUse = forecast;

                // If no forecast in context, or user wants to choose a different one, let them select
                if (forecastToUse == null) {
                    // Let user select a forecast for the budget
                    forecastToUse = Forecast.selectForecast(budget);
                } else if (view.getYesOrNo("Current forecast: " + forecastToUse.getName() +
                        ". Do you want to select a different forecast?")) {
                    // Let user select a different forecast for the budget
                    forecastToUse = Forecast.selectForecast(budget);
                }

                if (forecastToUse != null) {
                    ForecastController forecastController = new ForecastController(sessionController);
                    forecastController.reconcile(transaction, splits);
                    view.say("Transaction categorized and reconciled with forecast '" + forecastToUse.getName() + "'.");
                } else {
                    view.say("Transaction categorized (no forecast selected for reconciliation).");
                }
            } else {
                view.say("Transaction categorized (forecast reconciliation skipped).");
            }
        }
    }

    /**
     * Allows user to select another transaction from a cached list of transactions.
     * This avoids having to re-execute the search query.
     *
     * @param transactions The cached list of transactions
     * @return The selected transaction
     * @throws Exception if any error occurs
     * @throws CancelException if the user cancels
     */
    private Transaction selectFromCachedList(List<Transaction> transactions) throws Exception, CancelException {
        // Build display strings for the transactions
        List<String> transactionDisplayStrings = new ArrayList<>();
        for (Transaction t : transactions) {
            try {
                transactionDisplayStrings.add(t.toStringVeryConcise());
            } catch (Exception e) {
                transactionDisplayStrings.add("Transaction ID: " + t.getId());
            }
        }

        // Let user select from the list
        NumberOrStringResponse result = view.selectFromListOrString(
                "Select another transaction from current list (showing " + transactions.size() + " result(s))",
                transactionDisplayStrings,
                DO_NOT_ALLOW_NONE,
                DO_NOT_ALLOW_CREATE,
                ALLOW_CANCEL,
                ALLOW_QUIT,
                DO_NOT_ALLOW_SKIP);

        if (result.isNumber()) {
            int index = result.getSelectedIndex(); // Already 0-based from view layer
            if (index >= 0 && index < transactions.size()) {
                return transactions.get(index);
            } else {
                throw new InvalidEntryException("Invalid transaction selection index: " + index);
            }
        } else {
            throw new InvalidEntryException("Expected a number selection, got string");
        }
    }

    /**
     * Helper class to hold search results and the currently selected transaction.
     * This allows us to cache the search results and select multiple transactions
     * from the same list without re-executing the query.
     */
    private static class TransactionSearchResult {
        @Getter
        private final List<Transaction> transactions;

        @Getter
        @Setter
        private Transaction selectedTransaction;

        @Getter
        @Setter
        private String nextSearchString;

        public TransactionSearchResult(List<Transaction> transactions, Transaction selectedTransaction) {
            this.transactions = transactions;
            this.selectedTransaction = selectedTransaction;
            this.nextSearchString = null;
        }
    }
}

