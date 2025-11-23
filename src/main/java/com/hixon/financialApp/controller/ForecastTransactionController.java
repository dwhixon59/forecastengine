package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.MatchQuery;
import com.hixon.financialApp.model.forecast.*;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.NumberOrStringResponse;
import com.hixon.financialApp.view.base.UserResponse;
import com.hixon.financialApp.view.base.ViewInt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT_ON_DUPLICATE_UPDATE;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.UPDATE;
import static com.hixon.financialApp.model.entity.EntityInt.executeUpdate;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.ASSIGN;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.IGNORE;
import static com.hixon.financialApp.utility.Utility.*;
import static com.hixon.financialApp.view.base.ViewInt.*;

public class ForecastTransactionController {

    /*
     * Fields for ForecastTransactionController:
     */
    protected Register register;
    protected Budget budget;
    protected Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;


    /**
     * Constructors and destructor for ForecastTransactionController:
     */
    public ForecastTransactionController(Register register, Budget budget, Forecast forecast, ViewInt view, NotificationServiceInt
            notificationService) {
        this.register = register;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
    }


    /**
     * Main methods for ForecastTransactionController:
     */

    /**
     * Manage forecast transactions interactively using an enhanced search-based interface.
     * Features:
     * - Cached search results for selecting multiple transactions without re-searching
     * - Automatic paging (25 items per page) with F/B navigation
     * - Direct number selection to jump to any transaction
     * - Multi-character input automatically triggers new search
     * - Single-letter commands for actions
     *
     * @throws Exception if any error occurs during management operations
     */
    public void manageForecastTransactions() throws Exception {
        boolean done = false;
        String pendingSearchString = null;  // Track search string from action menu

        while (!done) {
            try {
                // Step 1: Search for and select forecast transactions
                ForecastTransactionSearchResult searchResult = selectForecastTransactionFromForecast(pendingSearchString);
                pendingSearchString = null;  // Clear after use

                if (searchResult == null) {
                    // User cancelled from search
                    continue;
                }

                // Step 2: Loop through selecting transactions from cached search results
                boolean selectingFromCurrentSearch = true;
                while (selectingFromCurrentSearch && !searchResult.getTransactions().isEmpty()) {
                    ForecastTransaction selectedTransaction = searchResult.getSelectedTransaction();

                    // User action loop for the current transaction
                    boolean actionComplete = false;
                    while (!actionComplete) {
                        // Display the selected transaction
                        view.say();
                        view.say("Selected forecast transaction:");
                        view.say("  " + selectedTransaction.toStringConcise());

                        // Step 3: Ask what to do with this transaction using the flexible method
                        // User can enter:
                        // - A number (1-N) to select another transaction from the list
                        // - A single letter (v,u,d,m,s) for a menu command
                        // - Multi-character string for new search criteria
                        // - 'C' to cancel, 'Q' to quit

                        // Build display strings for transactions
                        List<String> transactionDisplayStrings = new ArrayList<>();
                        for (ForecastTransaction t : searchResult.getTransactions()) {
                            transactionDisplayStrings.add(t.toStringConcise());
                        }

                        NumberOrStringResponse response = view.selectFromListByPositionOrMenuOrString(
                                null,  // Don't show the list here since we're in the action menu
                                transactionDisplayStrings,  // Transaction display strings for validation
                                "What would you like to do with this forecast transaction?",
                                List.of("view details", "update this transaction", "delete this transaction",
                                        "manage splits/dispositions", "show list again"),
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
                                displayForecastTransactionDetails(selectedTransaction);
                                break;

                            case "u":  // update this transaction
                                updateForecastTransaction(selectedTransaction);
                                // Reload the transaction to show updated info
                                selectedTransaction = ForecastTransaction.getById(selectedTransaction.getId());
                                searchResult.setSelectedTransaction(selectedTransaction);
                                break;

                            case "d":  // delete this transaction
                                view.say("\nYou are about to delete:");
                                view.say("  " + selectedTransaction.toStringConcise());

                                view.say("\nWARNING: Deleting this forecast transaction cannot be undone.");
                                view.say("The transaction will be regenerated if you rebuild the forecast.");

                                if (view.getYesOrNo("\nAre you sure you want to delete this forecast transaction?")) {
                                    try {
                                        selectedTransaction.delete();
                                        view.say("Forecast transaction deleted successfully.");

                                        // Remove from cached list
                                        searchResult.getTransactions().remove(selectedTransaction);

                                        // If list is now empty, go back to search
                                        if (searchResult.getTransactions().isEmpty()) {
                                            view.say("No more forecast transactions in the current list.");
                                            selectingFromCurrentSearch = false;
                                            actionComplete = true;
                                        } else {
                                            actionComplete = true;
                                        }
                                    } catch (Exception e) {
                                        view.say("Error deleting forecast transaction: " + e.getMessage());
                                        System.err.println("Error deleting forecast transaction: " + e.getMessage());
                                    }
                                } else {
                                    view.say("Deletion cancelled.");
                                }
                                break;

                            case "m":  // manage splits/dispositions
                                manageForecastTransactionSplits(selectedTransaction);
                                break;

                            case "s":  // show list again
                                // Display the numbered list of forecast transactions
                                view.say();
                                view.sayH3("Current forecast transaction list (showing " + searchResult.getTransactions().size() + " result(s)):");
                                for (int i = 0; i < transactionDisplayStrings.size(); i++) {
                                    view.say("  " + (i + 1) + " - " + transactionDisplayStrings.get(i));
                                }
                                view.say();
                                break;

                            default:
                                throw new InvalidEntryException("Unexpected option returned: " + action);
                        }
                    }

                    // After completing an action, automatically show the list again for the user to select another transaction
                    if (selectingFromCurrentSearch && !searchResult.getTransactions().isEmpty()) {
                        try {
                            ForecastTransaction nextTransaction = selectFromCachedList(searchResult.getTransactions());
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
     * Selects a forecast transaction from the forecast using a flexible search interface.
     * Allows searching by planned date, category, payee, or memo.
     * Returns a search result containing the list of transactions and the selected one.
     *
     * @param initialSearchString Optional initial search string to use (can be null)
     * @return ForecastTransactionSearchResult containing the list and selected transaction
     * @throws Exception if any error occurs during selection
     * @throws CancelException if the user cancels from the search prompt
     */
    private ForecastTransactionSearchResult selectForecastTransactionFromForecast(String initialSearchString)
            throws Exception, CancelException {

        String searchString = initialSearchString; // Use provided search string or null to prompt user

        while (true) {
            // Show search menu only if we don't already have a search string from a previous iteration
            if (searchString == null) {
                view.say();
                view.say("--- Forecast Transaction Search ---");
                view.say("You can search by:");
                view.say("  • Planned date range (e.g., '2024-01-01 to 2024-12-31')");
                view.say("  • Category, payee, or memo");
                view.say("  • Or press Enter to see all forecast transactions");
                view.say();

                try {
                    searchString = view.getResponseString(
                            "Search for forecast transaction (date range, category, payee, memo, or filters):",
                            ALLOW_NONE,  // Allow empty input to show all transactions
                            DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                            null);
                } catch (CancelException e) {
                    throw e; // Let it bubble up to exit the whole manage operation
                }
            }

            // Parse search criteria
            SearchCriteria criteria = parseSearchCriteria(searchString);

            // Build query based on search criteria
            String query = buildForecastTransactionQuery(criteria);

            // Execute query and build list of forecast transactions
            List<ForecastTransaction> transactions = new ArrayList<>();
            ResultSet rs = EntityInt.getRS(query, "searching for forecast transactions");
            if (rs != null) {
                while (rs.next()) {
                    transactions.add(new ForecastTransaction(rs));
                }
            }

            if (transactions.isEmpty()) {
                view.say("No forecast transactions found matching your search criteria.");
                // Reset search string and loop back to search prompt
                searchString = null;
                continue;
            }

            // Build display strings for the transactions
            List<String> transactionDisplayStrings = new ArrayList<>();
            for (ForecastTransaction t : transactions) {
                transactionDisplayStrings.add(t.toStringConcise());
            }

            // Let user select from the list or enter a new search string
            try {
                NumberOrStringResponse result = view.selectFromListByPositionOrMenuOrString(
                        "Select a forecast transaction",
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
                        return new ForecastTransactionSearchResult(transactions, transactions.get(index));
                    } else {
                        view.say("Invalid selection.");
                        continue;
                    }
                } else {
                    // User entered a new search string
                    searchString = result.getSearchString();
                    // Loop back to search with the new string
                    continue;
                }
            } catch (CancelException e) {
                // User cancelled from selection list - loop back to search prompt
                searchString = null;
                continue;
            }
        }
    }

    /**
     * Selects a transaction from a cached list of forecast transactions.
     *
     * @param transactions The list of transactions to select from
     * @return The selected transaction
     * @throws CancelException if user cancels
     */
    private ForecastTransaction selectFromCachedList(List<ForecastTransaction> transactions) throws CancelException {
        // Build display strings
        List<String> displayStrings = new ArrayList<>();
        for (ForecastTransaction t : transactions) {
            displayStrings.add(t.toStringConcise());
        }

        try {
            NumberOrStringResponse result = view.selectFromListByPositionOrMenuOrString(
                    "Select another forecast transaction from current list",
                    displayStrings,
                    "", // No menu
                    List.of(), // No menu options
                    DO_NOT_ALLOW_CREATE, // Don't allow search strings here
                    ALLOW_CANCEL,
                    ALLOW_QUIT,
                    DO_NOT_ALLOW_SKIP);

            if (result.isNumber()) {
                int index = result.getSelectedIndex();
                return transactions.get(index);
            } else {
                throw new CancelException("User cancelled selection");
            }
        } catch (QuitException e) {
            throw new RuntimeException(e);
        } catch (SkipException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse search string into search criteria.
     */
    private SearchCriteria parseSearchCriteria(String searchString) {
        SearchCriteria criteria = new SearchCriteria();

        if (searchString == null || searchString.trim().isEmpty() || searchString.equals("*")) {
            criteria.searchAll = true;
            return criteria;
        }

        // Check for date range pattern (YYYY-MM-DD to YYYY-MM-DD)
        String dateRangePattern = "(\\d{4}-\\d{2}-\\d{2})\\s+to\\s+(\\d{4}-\\d{2}-\\d{2})";
        if (searchString.matches(dateRangePattern)) {
            String[] parts = searchString.split("\\s+to\\s+");
            try {
                criteria.startDate = stringDateDashToCalendarDate(parts[0]);
                criteria.endDate = stringDateDashToCalendarDate(parts[1]);
            } catch (Exception e) {
                // If parsing fails, treat as text search
                criteria.searchText = searchString;
            }
        } else {
            // Text search
            criteria.searchText = searchString;
        }

        return criteria;
    }

    /**
     * Build SQL query based on search criteria.
     */
    private String buildForecastTransactionQuery(SearchCriteria criteria) {
        StringBuilder query = new StringBuilder();
        query.append(ForecastTransaction.getSelectQuery()).append(" ");
        query.append("INNER JOIN forecast_item fi ON ft.ForecastItem_idForecastItem = fi.idForecastItem ");
        query.append("WHERE fi.Forecast_idForecast = uuid_to_bin('").append(forecast.getId()).append("') ");

        if (!criteria.searchAll) {
            if (criteria.startDate != null && criteria.endDate != null) {
                // Date range search
                query.append("AND ft.plannedDate >= '").append(calendarDateToStringDate(criteria.startDate)).append("' ");
                query.append("AND ft.plannedDate <= '").append(calendarDateToStringDate(criteria.endDate)).append("' ");
            } else if (criteria.searchText != null && !criteria.searchText.isEmpty()) {
                // Text search across category, payee, and memo
                String searchTerm = criteria.searchText.replace("'", "''"); // Escape single quotes
                query.append("AND (fi.category LIKE '%").append(searchTerm).append("%' ");
                query.append("OR fi.payee LIKE '%").append(searchTerm).append("%' ");
                query.append("OR ft.memo LIKE '%").append(searchTerm).append("%') ");
            }
        }

        // Order by date descending (most recent first)
        query.append("ORDER BY ft.plannedDate DESC LIMIT 100");

        return query.toString();
    }

    /**
     * Inner class to hold search criteria for forecast transactions.
     */
    private static class SearchCriteria {
        boolean searchAll = false;
        Calendar startDate = null;
        Calendar endDate = null;
        String searchText = null;
    }

    /**
     * Helper class to hold search results and the currently selected transaction.
     * This allows us to cache the search results and select multiple transactions
     * from the same list without re-executing the query.
     */
    private static class ForecastTransactionSearchResult {
        private final List<ForecastTransaction> transactions;
        private ForecastTransaction selectedTransaction;
        private String nextSearchString;

        public ForecastTransactionSearchResult(List<ForecastTransaction> transactions, ForecastTransaction selectedTransaction) {
            this.transactions = transactions;
            this.selectedTransaction = selectedTransaction;
            this.nextSearchString = null;
        }

        public List<ForecastTransaction> getTransactions() {
            return transactions;
        }

        public ForecastTransaction getSelectedTransaction() {
            return selectedTransaction;
        }

        public void setSelectedTransaction(ForecastTransaction transaction) {
            this.selectedTransaction = transaction;
        }

        public String getNextSearchString() {
            return nextSearchString;
        }

        public void setNextSearchString(String searchString) {
            this.nextSearchString = searchString;
        }
    }

    /**
     * @deprecated Use selectForecastTransactionFromForecast instead
     * Search for and select a forecast transaction.
     *
     * @return The selected ForecastTransaction, or null if cancelled
     * @throws Exception if any error occurs
     */
    @Deprecated
    private ForecastTransaction selectForecastTransaction() throws Exception {
        SelectionController selectionController = new SelectionController(view);

        // Build query that only selects forecast_transaction columns but joins for search/sort
        String queryBeforeMatch = "select " + ForecastTransaction.getSelectColumns() +
                "from forecast_transaction ft " +
                "inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem " +
                "WHERE fi.Forecast_idForecast = uuid_to_bin('" + forecast.getId() + "') AND ";

        String queryAfterMatch = "ORDER BY ft.plannedDate DESC";

        return selectionController.getByNameFullText(
                null,  // No seed name
                forecast,  // Scope to this forecast
                DO_NOT_ALLOW_NONE,
                DO_NOT_ALLOW_CREATE,
                ALLOW_CANCEL,
                ALLOW_QUIT,
                DO_NOT_ALLOW_SKIP,
                ForecastTransaction.getPrintableTypeName_static(),
                ForecastTransaction::toStringCompact,
                new MatchQuery(queryBeforeMatch,
                        "ft.plannedDate",
                        "fi.category, fi.payee, fi.memo",
                        queryAfterMatch),
                rs -> {
                    try {
                        return new ForecastTransaction(rs);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                (scope, newName) -> null);  // Don't allow creating new forecast transactions
    }

    /**
     * Display detailed information about a forecast transaction.
     *
     * @param transaction The forecast transaction to display
     * @throws Exception if any error occurs
     */
    private void displayForecastTransactionDetails(ForecastTransaction transaction) throws Exception {
        view.say();
        view.say("Forecast Transaction Details:");
        view.say("──────────────────────────────────────");
        view.say("Planned Date: " + calendarDateToStringDate(transaction.getPlannedDate()));
        view.say("Remaining Amount: " + formatDollarAmount(transaction.getRemainingAmount()));
        view.say("Running Balance: " + formatDollarAmount(transaction.getRunningBalance()));
        view.say("Memo: " + (transaction.getMemo() != null ? transaction.getMemo() : ""));
        view.say("First Occurrence: " + (transaction.isFirstOccurrence() ? "yes" : "no"));
        view.say("Overridden: " + (transaction.isOverridden() ? "yes" : "no"));
        view.say("Found: " + (transaction.isFound() ? "yes" : "no"));

        // Display forecast item details
        ForecastItem item = transaction.getForecastItem();
        view.say();
        view.say("Associated Forecast Item:");
        view.say("  Category: " + item.getCategory());
        view.say("  Payee: " + item.getPayee());
        view.say("  Budgeted Amount: " + formatDollarAmount(item.getAmount()));
        view.say("  Period: " + item.getPeriod());

        view.say("──────────────────────────────────────");
    }

    /**
     * Update a forecast transaction.
     *
     * @param transaction The forecast transaction to update
     * @throws Exception if any error occurs
     */
    private void updateForecastTransaction(ForecastTransaction transaction) throws Exception {
        view.say();
        view.say("──── Update Forecast Transaction ────");

        boolean done = false;
        while (!done) {
            // Show current values
            view.say();
            view.say("Current values:");
            view.say("  Planned Date: " + calendarDateToStringDate(transaction.getPlannedDate()));
            view.say("  Remaining Amount: " + formatDollarAmount(transaction.getRemainingAmount()));
            view.say("  Memo: " + (transaction.getMemo() != null ? transaction.getMemo() : ""));
            view.say("  Overridden: " + (transaction.isOverridden() ? "yes" : "no"));
            view.say("  Found: " + (transaction.isFound() ? "yes" : "no"));

            // Ask what to update
            String choice = view.selectFromMenu("What would you like to update?",
                    List.of("planned date", "remaining amount", "memo", "overridden flag", "found flag", "done - save changes"),
                    DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

            switch (choice) {
                case "p":  // planned date
                    String dateStr = view.getResponseString("Enter new planned date (MM-DD-YYYY):",
                            calendarDateToStringDate(transaction.getPlannedDate()), ALLOW_NONE,
                            DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    Calendar newDate = stringDateDashToCalendarDate(dateStr);
                    transaction.setPlannedDate(newDate);
                    transaction.setOverridden(true);  // Mark as overridden when date changes
                    break;

                case "r":  // remaining amount
                    double newAmount = view.getResponseCurrency("Enter new remaining amount:",
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    transaction.setRemainingAmount(newAmount);
                    transaction.setOverridden(true);  // Mark as overridden when amount changes
                    break;

                case "m":  // memo
                    String newMemo = view.getResponseString("Enter new memo:",
                            transaction.getMemo() != null ? transaction.getMemo() : "",
                            ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    transaction.setMemo(newMemo);
                    break;

                case "o":  // overridden flag
                    String overriddenStr = view.getResponseString("Mark as overridden? (y/n):",
                            transaction.isOverridden() ? "y" : "n", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    transaction.setOverridden(overriddenStr.equalsIgnoreCase("y"));
                    break;

                case "f":  // found flag
                    String foundStr = view.getResponseString("Mark as found? (y/n):",
                            transaction.isFound() ? "y" : "n", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    transaction.setFound(foundStr.equalsIgnoreCase("y"));
                    break;

                case "d":  // done
                    if (transaction.isDirty()) {
                        transaction.save(UPDATE);
                        view.say("Forecast transaction successfully updated.");
                    }
                    done = true;
                    break;

                case "c":  // cancel
                    view.say("Update cancelled.");
                    done = true;
                    break;

                default:
                    throw new InvalidEntryException("Unexpected menu option: " + choice);
            }
        }
    }

    /**
     * Delete a forecast transaction after confirmation.
     *
     * @param transaction The forecast transaction to delete
     * @throws Exception if any error occurs
     */
    private void deleteForecastTransaction(ForecastTransaction transaction) throws Exception {
        view.say();
        view.say("You are about to delete:");
        view.say("  " + transaction.toStringConcise());

        view.say();
        view.say("WARNING: Deleting this forecast transaction cannot be undone.");
        view.say("The transaction will be regenerated if you rebuild the forecast.");

        String confirm = view.getResponseString("Are you sure you want to delete this forecast transaction? (yes/no):",
                "no", ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

        if (confirm.equalsIgnoreCase("yes")) {
            transaction.delete();
            view.say("Forecast transaction successfully deleted.");
        } else {
            view.say("Delete cancelled.");
        }
    }

    /**
     * Manage splits/dispositions for a forecast transaction.
     *
     * @param transaction The forecast transaction to manage splits for
     * @throws Exception if any error occurs
     */
    private void manageForecastTransactionSplits(ForecastTransaction transaction) throws Exception {
        boolean done = false;

        while (!done) {
            try {
                view.say();
                view.say("──── Manage Splits for Forecast Transaction ────");
                view.say(transaction.toStringCompact());

                // Get splits for this forecast transaction
                List<ForecastTransactionSplit> splits = getForecastTransactionSplits(transaction);

                if (splits.isEmpty()) {
                    view.say("No splits currently associated with this forecast transaction.");
                    view.say("Splits are created during forecast reconciliation.");

                    view.getResponseString("Press Enter to return (or 'C' to cancel, 'Q' to quit):",
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    done = true;
                    continue;
                }

                // Display numbered list of splits
                view.say();
                view.say("Splits:");
                List<String> splitDisplayList = new ArrayList<>();
                for (ForecastTransactionSplit split : splits) {
                    // Query for the transaction split using proper column aliases
                    String query = TransactionSplit.getSelectQuery() +
                            "WHERE ts.BudgetItem_idBudgetItem = uuid_to_bin('" + split.getIdBudgetItem() + "') AND " +
                            "ts.Transaction_idTransaction = uuid_to_bin('" + split.getIdTransaction() + "')";
                    ResultSet rs = EntityInt.getRS(query, "getting transaction split");
                    if (rs != null && rs.next()) {
                        TransactionSplit transSplit = new TransactionSplit(rs);
                        String display = formatDollarAmount(transSplit.getAmount()) + " → " +
                                transSplit.getBudgetItem().getPayee() + " (" + split.getDisposition() + ")";
                        splitDisplayList.add(display);
                    }
                }

                Integer selectedIndex = view.selectByPositionFromList(
                        "Select a split to update its disposition:",
                        splitDisplayList,
                        DO_NOT_ALLOW_NONE,
                        ALLOW_CANCEL,
                        ALLOW_QUIT,
                        DO_NOT_ALLOW_SKIP);

                if (selectedIndex == null || selectedIndex < 0 || selectedIndex >= splits.size()) {
                    view.say("Invalid selection.");
                    continue;
                }

                ForecastTransactionSplit selectedSplit = splits.get(selectedIndex);

                // Update the disposition
                updateSplitDisposition(selectedSplit);

            } catch (CancelException e) {
                done = true;
            }
        }
    }

    /**
     * Get all splits for a forecast transaction.
     *
     * @param transaction The forecast transaction
     * @return List of ForecastTransactionSplit objects
     * @throws Exception if any error occurs
     */
    private List<ForecastTransactionSplit> getForecastTransactionSplits(ForecastTransaction transaction) throws Exception {
        String query = ForecastTransactionSplit.getSelectQuery() +
                " WHERE fts.ForecastTransaction_idForecastTransaction = uuid_to_bin('" + transaction.getId() + "')";
        ResultSet rs = EntityInt.getRS(query, "getting forecast transaction splits");

        List<ForecastTransactionSplit> splits = new ArrayList<>();
        if (rs != null) {
            while (rs.next()) {
                splits.add(new ForecastTransactionSplit(rs));
            }
        }
        return splits;
    }

    /**
     * Update the disposition of a forecast transaction split.
     *
     * @param split The split to update
     * @throws Exception if any error occurs
     */
    private void updateSplitDisposition(ForecastTransactionSplit split) throws Exception {
        view.say();
        view.say("Current disposition: " + split.getDisposition());
        view.say();
        view.say("Disposition meanings:");
        view.say("  ADJUST - Adjust the forecast transaction amount");
        view.say("  ASSIGN - Assign this split to the forecast transaction");
        view.say("  IGNORE - Ignore this split for this forecast transaction");
        view.say("  DISPUTE - Mark this split as disputed");
        view.say("  ROLL_FORWARD - Roll this amount forward to next occurrence");
        view.say("  ZERO_OUT - Zero out the forecast transaction");

        String newDispositionStr = view.selectFromMenu("Select new disposition:",
                List.of("ADJUST", "ASSIGN", "IGNORE", "DISPUTE", "ROLL_FORWARD", "ZERO_OUT"),
                DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

        if (newDispositionStr.equals("c")) {
            return;  // User cancelled
        }

        ForecastTransactionSplit.SplitDisposition newDisposition =
                ForecastTransactionSplit.SplitDisposition.valueOf(newDispositionStr.toUpperCase());

        split.setDisposition(newDisposition);
        split.save(UPDATE);
        view.say("Split disposition successfully updated.");
    }

    /**
     * Zero out the amounts for all the Forecast Transactions that are marked not found:
     *
     * @throws EntityException
     * @throws RegisterException
     * @throws SQLException
     * @throws BudgetException
     */
    public void zeroNotFound()
            throws EntityException, RegisterException, SQLException, BudgetException {

        // List the forecast transactions that are about to be zeroed out for the user:
        ResultSet rs = EntityInt.getRS(ForecastTransaction.getSelectQuery() + " " +
                        "inner join forecast_item fi on ft.ForecastItem_idForecastItem = " +
                        "fi.idForecastItem " +
                        "where found = false and remainingAmount <> 0 " +
                        "order by ft.plannedDate desc, fi.category asc, fi.payee asc",
                "Forecast Transactions that are marked not found."
        );
        boolean firstTime = true;
        while (rs.next()) {
            if (firstTime) {
                getView().say("\nThe following transactions were deleted from the spreadsheet and will be " +
                        "zeroed out in the forecast:  ");
                firstTime = false;
            }
            ForecastTransaction forecastTransaction = new ForecastTransaction(rs);
            getView().say(forecastTransaction.toStringConcise() + " .");
        }

        // Zero out the forcast transactions that were deleted from the spreadsheet:
        executeUpdate(ForecastTransaction.getUpdateQuery() + "remainingAmount = 0 where found = false and " +
                "remainingAmount <> 0", "to zero the Forecast Transactions that are marked not found.");
    }

    /**
     * This method finds the applicable forecast transaction for a given split.  The algorithm is to first get a list of
     * non-zero forecast transactions for the budget item in chronological order.  Then proceed through the list looking
     * for the forecast transaction where the date of the transaction associated with the split falls into the
     * applicability period.  Various special cases are handled based upon how the item occurs, like periodic vs.
     * collection type items and whether the split date falls just outside the forecast transaction applicability
     * period.
     *
     * @param forecast The forecast is which to look for applicable forecast transactions.
     * @param split    The split to match to a forecast transaction.
     * @return The applicable forecast transaction if one is found, else null.
     * @throws EntityException
     * @throws Exception
     * @throws BudgetException
     * @throws RegisterException
     */
    public ForecastTransaction getApplicableForecastTransaction(Forecast forecast, TransactionSplit split)
            throws EntityException, Exception, BudgetException, RegisterException {

        // Get a list of forecast transactions beginning with the earliest non-zero amount occurrence of a forecast
        // transaction in the forecast for the budget item associated with the split:
        ForecastTransactionIterator it =
                ForecastTransaction.getNonZeroForecastTransactionsForBudgetItem(split.getIdBudgetItem(), forecast.getId());

        // Find the forecast transaction in the list that this split applies to.  Roll up any old forecast transactions
        // encountered in the process:
        ForecastTransaction forecastTransaction = it.getNext();
        if (forecastTransaction != null) {

            ForecastTransaction.Timing timing = forecastTransaction.fallsWithinWindow(split.getTransaction().getDate());
            switch (timing) {

                case PRIOR_TO:  // The split occurs before the period of this forecast transaction:

                    switch (split.getBudgetItem().getHowOccurs()) {

                        case COLLECTION: // This split is an instance of overspending.
                            // If the transaction split occurred prior to the first occurrence of forecast item in the
                            // forecast, then it doesn't apply because collection forecast items always occur prior to any
                            // associated splits:
                            if (forecastTransaction.isFirstOccurrence()) {
                                getView().say("Split occurs before the first occurrence of the budget item " +
                                        split.getBudgetItem().getPayee() + " in the forecast.  Ignoring it.");
                                forecastTransaction = null;
                            } else {
                                // Set the applicable forecast transaction to the one applicable to the date of the
                                // split regardless of the fact that forecast transaction is exhausted:
                                forecastTransaction = ForecastTransaction.getApplicableZeroOccurrence(forecast,
                                        split.getIdBudgetItem(), split.getTransaction().getDate());
                            }
                            break;

                        case ENVELOPE: // We are before the effective start of this forecast item so nothing to reconcile to.
                            getView().say("Split occurred before the forecast item became effective.  Ignoring.");
                            split.setDisposition(IGNORE);
                            forecastTransaction = null;
                            break;

                        case PERIODIC: // The transaction was paid early?
                        case VARIABLE_PERIODIC:

                            // Determine if the actual date a forecast transaction occurred is "on or about" the planned date:
                            int variance = Utility.daysBetween(forecastTransaction.getPlannedDate(),
                                    split.getTransaction().getDate());

                            // If it is not on or about the planned date, then ask the user what they want to do:
                            if (!split.getBudgetItem().isWithinNormalDateVariance(variance)) {

                                // Ask the user to determine if the split is an occurrence of the forecast transaction:
                                ForecastController forecastController = new ForecastController(register, budget, forecast,
                                        view, notificationService);
                                UserResponse resp = forecastController.assignSplitDateToForecastTransaction(split,
                                        forecastTransaction);
                                split.setDisposition(resp.getDisposition());
                                switch (split.getDisposition()) {

                                    case ADJUST: // Change the seed date for the budget item:
                                        split.getBudgetItem().setStartDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        split.getBudgetItem().save(UPDATE);
                                        forecastTransaction.setPlannedDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        // TODO: ForecastTransaction.updateAllDates(forecastTransaction,
                                        //  Utility.stringDateDashToCalendarDate(resp.getResponse()));
                                        forecast.setInSync(false);
                                        forecast.save(UPDATE);
                                        break;

                                    case ASSIGN: // Assign the split to the forecast transaction:
                                        break;

                                    case IGNORE:
                                        forecastTransaction = null;
                                        break;

                                    case DISPUTE:
                                        split.getTransaction().setIsImproper(true);
                                        split.getTransaction().save(INSERT_ON_DUPLICATE_UPDATE);
                                        split.getTransaction().getRegister().addSignificantEvent(split.getTransaction());
                                        break;
                                }
                            }
                            break;

                        default:
                            throw new ForecastException("Invalid item howOccurs:  " + split.getBudgetItem().getHowOccurs()
                                    + ".");
                    }
                    break;

                case WITHIN:  // Found the applicable forecast transaction.
                    split.setDisposition(ASSIGN);
                    break;

                case AFTER:  // There is money left from a prior period for the budgeted item:

                    switch (split.getBudgetItem().getHowOccurs()) {

                        case COLLECTION: // This split is an instance of underspending.  Roll the money forward:
                            while (forecastTransaction.fallsWithinWindow(split.getTransaction().getDate()) == ForecastTransaction.Timing.AFTER) {
                                double remainingAmount = forecastTransaction.getRemainingAmount();
                                forecastTransaction.setRemainingAmount(0);
                                forecastTransaction.save(UPDATE);
                                forecastTransaction = it.getNext();
                                if (forecastTransaction != null) {
                                    forecastTransaction.setRemainingAmount(forecastTransaction.getRemainingAmount() +
                                            remainingAmount);
                                } else break;
                            }
                            split.setDisposition(ASSIGN);
                            break;

                        case ENVELOPE:  // Once the date for an envelope contribution passes, remove it:

                            // Roll up the expired items into the current item and mark them expired:
                            do {
                                // The budget item contains the running balance, so add this forecast transaction to it:
                                split.getBudgetItem().setRunningBalance(split.getBudgetItem().getRunningBalance() +
                                        forecastTransaction.getRemainingAmount());
                                split.getBudgetItem().save(UPDATE);

                                // and zero out the forecast transaction:
                                forecastTransaction.setRemainingAmount(0);
                                forecastTransaction.save(UPDATE);
                                forecastTransaction = it.getNext();
                            } while (forecastTransaction != null &&
                                    forecastTransaction.fallsWithinWindow(split.getTransaction().getDate()) == ForecastTransaction.Timing.AFTER);
                            split.setDisposition(ASSIGN);
                            break;

                        case PERIODIC: // The transaction was paid late?
                        case VARIABLE_PERIODIC:
                        case UNPLANNED:

                            // Determine if the actual date a forecast transaction occurred is "on or about" the planned date:
                            int variance = Utility.daysBetween(split.getTransaction().getDate(),
                                    forecastTransaction.getPlannedDate());
                            if (!split.getBudgetItem().isWithinNormalDateVariance(variance)) {

                                // Ask the user to determine if the split is an occurrence of the forecast transaction:
                                ForecastController forecastController = new ForecastController(register, budget, forecast,
                                        view, notificationService);
                                UserResponse resp = forecastController.assignSplitDateToForecastTransaction(split, forecastTransaction);
                                split.setDisposition(resp.getDisposition());
                                switch (split.getDisposition()) {

                                    case ADJUST: // Change the seed date for the budget item:
                                        split.getBudgetItem().setStartDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        split.getBudgetItem().save(UPDATE);

                                        forecastTransaction.getForecastItem().setNextDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        forecastTransaction.getForecastItem().save(UPDATE);

                                        forecastTransaction.setPlannedDate(Utility.stringDateDashToCalendarDate(
                                                resp.getResponse()));
                                        forecastTransaction.save(UPDATE);

                                        forecast.setInSync(false);
                                        forecast.save(UPDATE);
                                        break;

                                    case ASSIGN: // Assign the split to the forecast transaction:
                                        break;

                                    case IGNORE:
                                        forecastTransaction = null;
                                        break;

                                    case DISPUTE:
                                        split.getTransaction().setIsImproper(true);
                                        split.getTransaction().save(INSERT_ON_DUPLICATE_UPDATE);
                                        split.getTransaction().getRegister().addSignificantEvent(split.getTransaction());
                                        break;
                                }
                            }
                            break;
                    }
                    break;
            }
        }
        return forecastTransaction;
    }
}
