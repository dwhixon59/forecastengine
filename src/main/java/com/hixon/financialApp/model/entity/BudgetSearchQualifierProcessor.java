package com.hixon.financialApp.model.entity;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes budget-related search qualifiers to enable cross-budget searching
 * and specific budget targeting using a composable, order-independent syntax.
 *
 * <p>Supported qualifiers (can appear anywhere in search string):
 * <ul>
 *   <li><code>budget:all</code> - Search across all budgets</li>
 *   <li><code>budget:uuid</code> - Search only in budget with specific UUID</li>
 *   <li><code>category:CategoryName</code> - Filter by category</li>
 * </ul>
 *
 * <p>Qualifiers are order-independent and composable:
 * <pre>
 * "n:hyundai budget:all category:Automotive"
 * "budget:all category:Food n:groceries"
 * "s:payment budget:all"
 * </pre>
 *
 * <p>Example usage:
 * <pre>
 * SearchQualifierProcessor processor = new BudgetSearchQualifierProcessor(
 *     currentBudget.getId(),
 *     "bi.Budget_idBudget"
 * );
 *
 * MatchQuery query = new MatchQuery(
 *     baseQuery,
 *     nameColumn,
 *     matchColumns,
 *     orderByClause,
 *     processor
 * );
 * </pre>
 */
public class BudgetSearchQualifierProcessor implements SearchQualifierProcessor {
    private final UUID currentBudgetId;
    private final String budgetFilterColumn;

    // Regex patterns for extracting qualifiers (matches anywhere in string)
    private static final Pattern BUDGET_ALL_PATTERN = Pattern.compile("\\bbudget:all\\b");
    private static final Pattern BUDGET_UUID_PATTERN = Pattern.compile("\\bbudget:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\b");
    // Category pattern: Captures category name with spaces
    // Best practice: Put category BEFORE search term or AFTER another qualifier
    // Examples: "category:Food and Beverage budget:all groceries" or "groceries category:Food and Beverage"
    // Pattern stops at: another qualifier keyword OR end of string
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("\\bcategory:([A-Za-z0-9\\s\\-]+?)(?=\\s+(?:budget:|category:|n:|b:|e:|l:|s:)|$)");

    /**
     * Creates a processor for budget-related search qualifiers.
     *
     * @param currentBudgetId The currently selected budget ID (used as default)
     * @param budgetFilterColumn The SQL column name for budget filtering (e.g., "bi.Budget_idBudget")
     */
    public BudgetSearchQualifierProcessor(UUID currentBudgetId, String budgetFilterColumn) {
        this.currentBudgetId = currentBudgetId;
        this.budgetFilterColumn = budgetFilterColumn;
    }

    @Override
    public SearchContext process(String searchTerm, String baseQuery) {
        String cleaned = searchTerm;
        String modifiedQuery = baseQuery;
        SearchContext context = new SearchContext(cleaned, modifiedQuery);

        // Check for budget:all qualifier
        Matcher budgetAllMatcher = BUDGET_ALL_PATTERN.matcher(searchTerm);
        if (budgetAllMatcher.find()) {
            // Remove budget filter from query
            modifiedQuery = removeBudgetFilter(modifiedQuery);

            // Remove the qualifier from search term
            cleaned = budgetAllMatcher.replaceAll("").trim();

            context = new SearchContext(cleaned, modifiedQuery)
                .withMetadata("searchAllBudgets", true)
                .withMetadata("displayHint", "Searching across all budgets");
        }

        // Check for budget:uuid qualifier
        Matcher budgetUuidMatcher = BUDGET_UUID_PATTERN.matcher(cleaned.isEmpty() ? searchTerm : cleaned);
        if (budgetUuidMatcher.find()) {
            String budgetIdStr = budgetUuidMatcher.group(1);

            try {
                UUID specificBudgetId = UUID.fromString(budgetIdStr);

                // Replace budget filter in query
                modifiedQuery = context.getModifiedQuery();
                modifiedQuery = replaceBudgetFilter(modifiedQuery, specificBudgetId);

                // Remove the qualifier from search term
                cleaned = budgetUuidMatcher.replaceAll("").trim();

                context = new SearchContext(cleaned, modifiedQuery)
                    .withMetadata("specificBudgetId", specificBudgetId)
                    .withMetadata("displayHint", "Searching in specific budget: " + budgetIdStr);
            } catch (IllegalArgumentException e) {
                // Invalid UUID format - ignore this qualifier
            }
        }

        // Check for category:CategoryName qualifier
        Matcher categoryMatcher = CATEGORY_PATTERN.matcher(context.getCleanedSearchTerm().isEmpty() ? searchTerm : context.getCleanedSearchTerm());
        if (categoryMatcher.find()) {
            String categoryName = categoryMatcher.group(1).trim();

            // Add category filter to query
            modifiedQuery = context.getModifiedQuery();
            modifiedQuery = addCategoryFilter(modifiedQuery, categoryName);

            // Remove the qualifier from search term
            cleaned = categoryMatcher.replaceAll("").trim();

            // Merge with existing context
            context = new SearchContext(cleaned, modifiedQuery);
            // Preserve existing metadata
            if (context.getMetadata().isEmpty() && !context.getMetadata().isEmpty()) {
                for (String key : context.getMetadata().keySet()) {
                    context.withMetadata(key, context.getMetadata().get(key));
                }
            }
            context.withMetadata("categoryFilter", categoryName);

            // Update display hint
            String existingHint = context.getMetadata("displayHint", "");
            String newHint = existingHint.isEmpty()
                ? "Filtering by category: " + categoryName
                : existingHint + ", category: " + categoryName;
            context.withMetadata("displayHint", newHint);
        }

        // Clean up multiple spaces left by qualifier removal
        cleaned = context.getCleanedSearchTerm().replaceAll("\\s+", " ").trim();

        return new SearchContext(cleaned, context.getModifiedQuery(), context.getMetadata());
    }

    /**
     * Removes the budget filter condition from the query.
     * Ensures the query ends in a valid state for MatchQuery to append conditions.
     *
     * @param query The original query
     * @return The query with budget filter removed and proper WHERE clause
     */
    private String removeBudgetFilter(String query) {
        // Remove the budget filter condition
        // Pattern handles: "bi.Budget_idBudget = uuid_to_bin('...') AND "
        // We need to escape the dot in the column name
        String columnPattern = budgetFilterColumn.replace(".", "\\.");
        String pattern = columnPattern + "\\s*=\\s*uuid_to_bin\\('[^']*'\\)\\s*AND\\s*";
        String result = query.replaceAll(pattern, "");

        // Clean up the WHERE clause
        // If it ends with "WHERE AND", fix it to "WHERE "
        result = result.replaceAll("(?i)WHERE\\s+AND\\s*$", "WHERE ");

        // If it ends with just "WHERE", that's fine - keep it
        // If it doesn't have WHERE at all, add it
        if (!result.trim().toUpperCase().endsWith("WHERE")) {
            // Query doesn't end with WHERE, add it
            result = result.trim() + " WHERE ";
        }

        return result;
    }

    /**
     * Replaces the existing budget filter with a new budget ID.
     *
     * @param query The original query
     * @param newBudgetId The new budget ID to filter by
     * @return The query with updated budget filter
     */
    private String replaceBudgetFilter(String query, UUID newBudgetId) {
        // Replace existing budget filter with new one
        // Pattern handles: "bi.Budget_idBudget = uuid_to_bin('old-uuid')"
        String pattern = "(?i)(" + escapeRegex(budgetFilterColumn) +
                        "\\s*=\\s*uuid_to_bin\\(')[^']*('\\))";
        return query.replaceAll(pattern, "$1" + newBudgetId.toString() + "$2");
    }

    /**
     * Adds a category filter to the query.
     *
     * @param query The original query
     * @param categoryName The category name to filter by
     * @return The query with category filter added
     */
    private String addCategoryFilter(String query, String categoryName) {
        // Add category filter before the final AND
        // This assumes query ends with "AND " for additional conditions
        if (query.trim().toUpperCase().endsWith("AND")) {
            return query + "bi.category = '" + escapeSql(categoryName) + "' AND ";
        } else if (query.trim().toUpperCase().endsWith("WHERE")) {
            return query + " bi.category = '" + escapeSql(categoryName) + "' AND ";
        } else {
            // Query doesn't end with AND or WHERE, append with AND
            return query + " AND bi.category = '" + escapeSql(categoryName) + "' AND ";
        }
    }

    /**
     * Escapes special regex characters in a column name.
     *
     * @param text The text to escape
     * @return The escaped text safe for use in regex patterns
     */
    private String escapeRegex(String text) {
        return text.replace(".", "\\.");
    }

    /**
     * Escapes single quotes for SQL safety.
     *
     * @param text The text to escape
     * @return The escaped text safe for SQL
     */
    private String escapeSql(String text) {
        return text.replace("'", "''");
    }
}

