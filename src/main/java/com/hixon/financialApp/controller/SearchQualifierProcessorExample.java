package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.entity.BudgetSearchQualifierProcessor;
import com.hixon.financialApp.model.entity.MatchQuery;
import com.hixon.financialApp.model.entity.SearchContext;
import com.hixon.financialApp.model.entity.SearchQualifierProcessor;
import com.hixon.financialApp.view.base.ViewInt;

import java.util.List;
import java.util.UUID;

/**
 * Example of how to use SearchQualifierProcessor with MatchQuery
 * to enable cross-budget searching.
 *
 * This is a code example - not meant to be executed directly.
 */
public class SearchQualifierProcessorExample {

    /**
     * Example 1: Basic usage with BudgetSearchQualifierProcessor
     * Shows composable, order-independent qualifiers
     */
    public void exampleBasicBudgetSearch(Budget currentBudget, ViewInt view) throws Exception {
        UUID currentBudgetId = currentBudget.getId();

        // Create the processor
        SearchQualifierProcessor processor = new BudgetSearchQualifierProcessor(
            currentBudgetId,
            "bi.Budget_idBudget"
        );

        // Create MatchQuery with processor
        MatchQuery matchQuery = new MatchQuery(
            "SELECT bi.* FROM budget_item bi " +
            "WHERE bi.Budget_idBudget = uuid_to_bin('" + currentBudgetId + "') AND ",
            "bi.payee",
            "bi.category, bi.payee, bi.memo",
            "ORDER BY bi.payee ASC",
            processor
        );

        // Get user input - qualifiers can appear anywhere!
        String userInput = view.getResponseString(
            "Search for budget item (try: 'n:hyundai budget:all category:Automotive'):",
            ViewInt.ALLOW_CANCEL,
            ViewInt.ALLOW_QUIT,
            ViewInt.DO_NOT_ALLOW_SKIP
        );

        // Process qualifiers to get context info
        SearchContext context = processor.process(userInput, "");

        // Show context-aware messages
        if (context.hasMetadata("searchAllBudgets")) {
            view.say("═══ Searching All Budgets ═══");
        }
        if (context.hasMetadata("categoryFilter")) {
            String category = context.getMetadata("categoryFilter", "");
            view.say("Filtering by category: " + category);
        }

        // Get the SQL query
        String sqlQuery = matchQuery.getQuery(userInput);

        // Execute query (pseudo-code)
        // List<BudgetItem> results = budgetItemDAO.search(sqlQuery);

        // Display results with context
        // displayResults(results, context);

        // EXAMPLE USER INPUTS THAT WORK:
        // "budget:all hyundai"
        // "hyundai budget:all"
        // "category:Automotive budget:all"
        // "n:hyundai budget:all category:Automotive"
        // "budget:all category:Food groceries"
    }

    /**
     * Example 2: Inline lambda processor for simple cases
     */
    public void exampleInlineLambdaProcessor(ViewInt view) {
        // Simple processor that filters for active items only
        SearchQualifierProcessor activeProcessor = (searchTerm, baseQuery) -> {
            if (searchTerm.startsWith("active:")) {
                String cleaned = searchTerm.substring(7);
                String modified = baseQuery + " AND bi.active = true AND ";
                return new SearchContext(cleaned, modified)
                    .withMetadata("filterActive", true)
                    .withMetadata("displayHint", "Showing active items only");
            }
            return new SearchContext(searchTerm, baseQuery);
        };

        MatchQuery matchQuery = new MatchQuery(
            "SELECT bi.* FROM budget_item bi WHERE ",
            "bi.payee",
            "bi.category, bi.payee",
            "ORDER BY bi.payee",
            activeProcessor
        );

        // User can search: "active:groceries" to find only active items matching "groceries"
    }

    /**
     * Example 3: No processor needed (backward compatible)
     */
    public void exampleNoProcessor() {
        // Existing code continues to work - uses IDENTITY processor by default
        MatchQuery matchQuery = new MatchQuery(
            "SELECT m.* FROM merchant m WHERE ",
            "m.name",
            "m.name",
            "ORDER BY m.name ASC"
        );

        // Standard search without qualifiers
        String query = matchQuery.getQuery("amazon");
    }

    /**
     * Helper method to display results with budget context
     */
    private void displayResults(List<BudgetItem> results, SearchContext context, ViewInt view) {
        view.say("Found " + results.size() + " items:");

        for (int i = 0; i < results.size(); i++) {
            BudgetItem item = results.get(i);

            // Show budget name if searching all budgets
            if (context.hasMetadata("searchAllBudgets")) {
                try {
                    String budgetName = item.getBudget().getName();
                    view.say((i + 1) + ". [" + budgetName + "] " + item.getPayee());
                } catch (Exception e) {
                    view.say((i + 1) + ". " + item.getPayee());
                }
            } else {
                view.say((i + 1) + ". " + item.getPayee());
            }
        }
    }
}

