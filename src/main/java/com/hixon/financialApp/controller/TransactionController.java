package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.EntityOrStringResult;
import com.hixon.financialApp.view.base.ViewInt;
import lombok.Getter;
import lombok.Setter;

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

    /*
     * Member variables for the Transaction Controller:
     */
    private Register register;
    private Budget budget;
    private Forecast forecast;
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
    public TransactionController(Register register, Budget budget, Forecast forecast, ViewInt view,
                                  NotificationServiceInt notificationService) {
        this.register = register;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
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

                // Step 2: Search for a transaction
                Transaction selectedTransaction = selectTransactionFromRegister(lastSelectedRegister);

                if (selectedTransaction == null) {
                    // User cancelled the search - ask if they want to select a different register or quit
                    if (view.getYesOrNo("Do you want to select a different register?")) {
                        lastSelectedRegister = null;
                        continue;
                    } else {
                        done = true;
                        continue;
                    }
                }

                // User selected a transaction - show action menu
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

                    // Step 3: Ask what to do with this transaction
                    String action = view.selectFromMenu("What would you like to do with this transaction?",
                            List.of("view details", "update this transaction", "manage splits/categories",
                                    "delete this transaction", "search again"),
                            DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

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
                                    actionComplete = true;
                                } catch (Exception e) {
                                    view.say("Error deleting transaction: " + e.getMessage());
                                    // TODO: Use proper logging instead of System.err
                                    System.err.println("Error deleting transaction: " + e.getMessage());
                                }
                            } else {
                                view.say("Deletion cancelled.");
                            }
                            break;

                        case "s":  // search again
                            actionComplete = true;  // Go back to search
                            break;

                        default:
                            throw new InvalidEntryException("Unexpected option returned: " + action);
                    }
                }

            } catch (CancelException e) {
                done = true;
            }
        }
    }

    /**
     * Selects a transaction from a specified register using a flexible search interface.
     * Allows searching by payee, merchant, date range, amount range, and filter options.
     *
     * @param register The register to search within
     * @return The selected Transaction, or null if user cancels
     * @throws Exception if any error occurs during selection
     */
    private Transaction selectTransactionFromRegister(Register register) throws Exception {

        view.say("\n--- Transaction Search ---");
        view.say("You can search by:");
        view.say("  • Payee or merchant name");
        view.say("  • Date range (e.g., '2024-01-01 to 2024-12-31')");
        view.say("  • Amount or amount range");
        view.say("  • Budget item/category");
        view.say("  • Or press Enter to see all transactions");
        view.say();

        // Build search query with filters
        String searchPrompt = "Search for transaction (or use filters: cleared:yes, cleared:no, new:yes, disputed:yes)";
        String searchString = view.getResponseString(searchPrompt, null, ALLOW_NONE,
                DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                () -> helpText.getProperty("transaction.search", "No help available"));

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
            return null;
        }

        // Let user select from the list - use selectByNameFromListOrString with a custom display function
        // to avoid calling getName() which Transaction doesn't properly override
        EntityOrStringResult<Transaction> result = view.selectByNameFromListOrString(
                "Select a transaction (showing " + transactions.size() + " result(s))",
                transactions,
                t -> {
                    try {
                        return t.toStringVeryConcise();
                    } catch (Exception e) {
                        return "Transaction ID: " + t.getId();
                    }
                },
                DO_NOT_ALLOW_NONE,
                DO_NOT_ALLOW_CREATE,
                ALLOW_CANCEL,
                ALLOW_QUIT,
                DO_NOT_ALLOW_SKIP);

        return result.isEntitySelected() ? result.getSelectedEntity() : null;
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
            // Check for date range pattern (YYYY-MM-DD to YYYY-MM-DD)
            String dateRangePattern = "(\\d{4}-\\d{2}-\\d{2})\\s+to\\s+(\\d{4}-\\d{2}-\\d{2})";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(dateRangePattern);
            java.util.regex.Matcher matcher = pattern.matcher(searchString);

            if (matcher.find()) {
                // Found a date range
                try {
                    String startDateStr = matcher.group(1);
                    String endDateStr = matcher.group(2);

                    criteria.startDate = Utility.sqlDateStringToCalendarDate(startDateStr);
                    criteria.endDate = Utility.sqlDateStringToCalendarDate(endDateStr);

                    // Remove the date range from search text
                    searchString = searchString.replaceAll(dateRangePattern, "").trim();
                    criteria.searchText = searchString;
                } catch (Exception e) {
                    view.say("Warning: Could not parse date range. Format should be 'YYYY-MM-DD to YYYY-MM-DD'");
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

            // Only update searchText if we have non-date content
            String finalSearchText = textParts.toString().trim();
            if (!finalSearchText.isEmpty()) {
                criteria.searchText = finalSearchText;
            } else if (criteria.startDate != null) {
                // If we only have a date range, clear the search text
                criteria.searchText = "";
            }
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
            query.append(" AND (tr.payee LIKE '%").append(criteria.searchText).append("%'");
            query.append(" OR m.name LIKE '%").append(criteria.searchText).append("%')");
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
    }

    /**
     * Recategorize a transaction by reassigning its budget item splits.
     *
     * @param transaction The transaction to recategorize
     * @return true if recategorization was successful, false otherwise
     */
    public boolean recategorizeTransaction(Transaction transaction) {
        // TODO: Implement recategorization logic
        // This should use TransactionSplitsController to manage the splits
        return false;
    }
}