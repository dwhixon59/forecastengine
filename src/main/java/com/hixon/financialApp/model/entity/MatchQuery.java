package com.hixon.financialApp.model.entity;

public class MatchQuery {

    // Fields for MatchQuery
    private String selectQuery;
    private String selectQueryAfterMatch;
    private String matchColumnList;
    private String nameColumn;
    private SearchQualifierProcessor qualifierProcessor;

    public MatchQuery() {
    }

    /**
     * The MatchQuery class represents a query used for matching against a name in natural language mode, boolean mode,
     * or a regular query based on the prefix of the name.  The SQL query should be a select statement that includes
     * everything but the MATCH clause.  A match clause will be appended to the query at runtime with the list of
     * columns to match against, and the user search string substituted in.  Because of this, the query must work with a
     * MATCH clause appended to it, and there must be a full-text index on the table.  The name column should be the
     * name of the column that should be matched against if the user specifies they want an exact match, or LIKE clause,
     * to be used against the primary index.
     *
     * @param selectQuery The SQL SELECT query as a string without the MATCH clause
     * @param nameColumn The name of the column that should be matched against if the exact match or LIKE clause is
     *                   requested
     * @param matchColumnList The list of columns to be used in the MATCH clause as a string
     */
    public MatchQuery(String selectQuery, String nameColumn, String matchColumnList) {
        this.selectQuery = selectQuery;
        this.nameColumn = nameColumn;
        this.matchColumnList = matchColumnList;
        this.selectQueryAfterMatch = "";
        this.qualifierProcessor = SearchQualifierProcessor.IDENTITY;
    }

    /**
     * Enhanced constructor that allows specifying query portions before and after the match conditions.
     * This is useful for adding ORDER BY, LIMIT, or other clauses that should come after WHERE conditions.
     *
     * @param selectQueryBeforeMatch The SQL SELECT query up to and including WHERE with trailing AND
     * @param nameColumn The name of the column that should be matched against if the exact match or LIKE clause is requested
     * @param matchColumnList The list of columns to be used in the MATCH clause as a string
     * @param selectQueryAfterMatch The SQL query portion to append after match conditions (e.g., "ORDER BY column DESC")
     */
    public MatchQuery(String selectQueryBeforeMatch, String nameColumn, String matchColumnList, String selectQueryAfterMatch) {
        this.selectQuery = selectQueryBeforeMatch;
        this.nameColumn = nameColumn;
        this.matchColumnList = matchColumnList;
        this.selectQueryAfterMatch = selectQueryAfterMatch != null ? selectQueryAfterMatch : "";
        this.qualifierProcessor = SearchQualifierProcessor.IDENTITY;
    }

    /**
     * Full constructor that accepts a custom qualifier processor for handling domain-specific
     * search qualifiers like "budget:all:" or "category:expense:".
     *
     * @param selectQueryBeforeMatch The SQL SELECT query up to and including WHERE with trailing AND
     * @param nameColumn The name of the column that should be matched against if the exact match or LIKE clause is requested
     * @param matchColumnList The list of columns to be used in the MATCH clause as a string
     * @param selectQueryAfterMatch The SQL query portion to append after match conditions (e.g., "ORDER BY column DESC")
     * @param qualifierProcessor Processor for handling search qualifiers (budget:all:, category:, etc.)
     */
    public MatchQuery(String selectQueryBeforeMatch, String nameColumn, String matchColumnList,
                      String selectQueryAfterMatch, SearchQualifierProcessor qualifierProcessor) {
        this.selectQuery = selectQueryBeforeMatch;
        this.nameColumn = nameColumn;
        this.matchColumnList = matchColumnList;
        this.selectQueryAfterMatch = selectQueryAfterMatch != null ? selectQueryAfterMatch : "";
        this.qualifierProcessor = qualifierProcessor != null ? qualifierProcessor : SearchQualifierProcessor.IDENTITY;
    }

    // Helper method to escape SQL characters to prevent SQL injection
    protected String escapeSQL(String input) {
        return input.replace("'", "''");
    }

    /**
     * Helper method to remove trailing AND or WHERE clauses from a SQL query and return all results
     * sorted alphabetically by the name column.
     *
     * @param query The query to process
     * @return The SQL query with trailing AND/WHERE removed and ORDER BY clause added
     */
    private String getAllResultsSortedByName(String query) {
        return query.replaceAll("(?i)\\s+(AND|WHERE)\\s*$", "") + " ORDER BY " + nameColumn + " ASC";
    }

    /**
     * This method takes a name to match against and returns a query that matches against the name
     * in natural language mode, boolean mode, or a regular query based on the prefix of the name.
     * The query is generated by concatenating the name into the query string after stripping the prefix.
     *
     * @param name The name to match against, with a prefix indicating the query type.
     * @return The query string
     */
    public String getQuery(String name) {

        String escapedName = escapeSQL(name); // Escape SQL characters

        // Process qualifiers FIRST (e.g., "budget:all:", "category:")
        SearchContext context = qualifierProcessor.process(escapedName, selectQuery);
        String searchTerm = context.getCleanedSearchTerm();
        String modifiedQuery = context.getModifiedQuery();

        // Now process search mode prefixes (n:, b:, e:, l:, s:) with the cleaned term
        if (searchTerm.startsWith("n:")) {
            // Remove the prefix and create a natural language query
            String searchString = searchTerm.substring(2).trim();
            // If search string is empty or only wildcards, return all results sorted alphabetically
            if (searchString.isEmpty() || searchString.replace("*", "").trim().isEmpty()) {
                return getAllResultsSortedByName(modifiedQuery);
            }
            return modifiedQuery.replaceFirst("(?i)FROM", ", MATCH(" + matchColumnList + ") AGAINST(\"" + searchString +
                    "\" IN NATURAL LANGUAGE MODE) as relevance FROM") + " MATCH(" + matchColumnList + ") AGAINST(\"" +
                    searchString + "\" IN NATURAL LANGUAGE MODE) ORDER BY relevance DESC" +
                    (selectQueryAfterMatch.isEmpty() ? "" : ", " + selectQueryAfterMatch.replaceFirst("(?i)^ORDER BY\\s+", ""));
        } else if (searchTerm.startsWith("b:")) {
            // Remove the prefix and create a boolean mode query
            String searchString = searchTerm.substring(2).trim();
            // If search string is empty, return all results sorted alphabetically
            if (searchString.isEmpty()) {
                return getAllResultsSortedByName(modifiedQuery);
            }
            return modifiedQuery + " MATCH(" + matchColumnList + ") AGAINST(\"" + searchString +
                    "\" IN BOOLEAN MODE)" + addAfterMatchClause();
        } else if (searchTerm.startsWith("e:")) {
            // Remove the prefix and create a regular query
            String searchString = searchTerm.substring(2).trim();
            // If search string is empty, return all results sorted alphabetically
            if (searchString.isEmpty()) {
                return getAllResultsSortedByName(modifiedQuery);
            }
            return modifiedQuery + nameColumn + " = \"" + searchString + "\"" + addAfterMatchClause();
        } else if (searchTerm.startsWith("l:")) {
            // For LIKE pattern match, we assume that '%' wildcards are already provided in the input if needed.
            String searchString = searchTerm.substring(2).trim();
            // If search string is empty, return all results sorted alphabetically
            if (searchString.isEmpty()) {
                return getAllResultsSortedByName(modifiedQuery);
            }
            // Wrap OR conditions in parentheses to ensure correct precedence
            StringBuilder likeQuery = new StringBuilder(modifiedQuery);
            likeQuery.append("(");
            String[] columns = matchColumnList.split(",");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) {
                    likeQuery.append(" OR ");
                }
                likeQuery.append(columns[i].trim()).append(" LIKE '").append(searchString).append("'");
            }
            likeQuery.append(")");
            likeQuery.append(addAfterMatchClause());
            return likeQuery.toString();
        } else if (searchTerm.startsWith("s:")) {
            // Search mode - wrap search term with wildcards and group OR conditions in parentheses
            String searchString = searchTerm.substring(2).trim();
            // If search string is empty, return all results sorted alphabetically
            if (searchString.isEmpty()) {
                return getAllResultsSortedByName(modifiedQuery);
            }
            StringBuilder likeQuery = new StringBuilder(modifiedQuery);
            likeQuery.append("(");
            String[] columns = matchColumnList.split(",");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) {
                    likeQuery.append(" OR ");
                }
                likeQuery.append(columns[i].trim()).append(" LIKE '%").append(searchString).append("%'");
            }
            likeQuery.append(")");
            likeQuery.append(addAfterMatchClause());
            return likeQuery.toString();
        } else {
            // Default behavior: simple search mode (wrap search term with wildcards)
            String searchString = searchTerm.trim();
            // If search string is empty or only wildcards/special chars, return all results sorted alphabetically
            if (searchString.isEmpty() || searchString.replace("*", "").replace("%", "").trim().isEmpty()) {
                return getAllResultsSortedByName(modifiedQuery);
            }
            // Build simple LIKE query with wildcards (same as s: prefix)
            StringBuilder likeQuery = new StringBuilder(modifiedQuery);
            likeQuery.append("(");
            String[] columns = matchColumnList.split(",");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) {
                    likeQuery.append(" OR ");
                }
                likeQuery.append(columns[i].trim()).append(" LIKE '%").append(searchString).append("%'");
            }
            likeQuery.append(")");
            likeQuery.append(addAfterMatchClause());
            return likeQuery.toString();
        }
    }

    /**
     * Helper method to add the selectQueryAfterMatch clause with proper spacing.
     *
     * @return The selectQueryAfterMatch with a leading space, or empty string if not set
     */
    private String addAfterMatchClause() {
        return selectQueryAfterMatch.isEmpty() ? "" : " " + selectQueryAfterMatch;
    }

    /**
     *  Method to check the string for specified patterns from the SQL LIKE clause and the MySQL boolean mode operators.
     *  The method checks if the string starts with "b:" and contains boolean mode operators or starts with "l:" and
     *  contains SQL LIKE wildcards. The method returns true if the string meets any of the criteria, otherwise it
     *  returns false.
     *  @param input The string to check for patterns
     *  @return true if the string meets any of the criteria, otherwise false
     */
    public static boolean checkStringPattern(String input) {
        if (input == null || input.length() < 3) {
            // Early return if input is null or too short to meet the criteria
            return false;
        }

        // Check if string starts with "b:" and contains boolean mode operators
        if (input.startsWith("b:")) {
            String checkPart = input.substring(2); // Remove the prefix
            // Regex to find any boolean mode operator
            String booleanModeOperators = "[\\+\\-\\>\\<\\(\\)\\~\\*\\\"]";
            return checkPart.matches(".*" + booleanModeOperators + ".*");
        }

        // Check if string starts with "l:" and contains LIKE wildcards
        if (input.startsWith("l:")) {
            String checkPart = input.substring(2); // Remove the prefix
            // Regex to find any SQL LIKE wildcard
            String likeWildcards = "[%_]";
            return checkPart.matches(".*" + likeWildcards + ".*");
        }

        // Check if string starts with "s:":
        if (input.startsWith("s:")) {
             return true;
        }


        // Return false if none of the conditions are met
        return false;
    }


    /**
     * This method takes a string to match against that may contain a prefix indicating the query type.
     * n: for natural language mode, b: for boolean mode, or e: for a regular query based on the prefix of the name.
     * This method strips off the prefix and returns the cleaned string.
     *
     * @param name The name to match against, with a prefix indicating the query type.
     * @return The query string
     */
    public String cleanName(String name) {

        // Remove the prefix and any wildcards from the name and return:
        if (name.startsWith("n:")) {
            return name.substring(2);
        } else if (name.startsWith("b:")) {
            name = name.replace("*", "");
            name = name.replace("+", "");
            name = name.replace("-", "");
            name = name.replace("\"", "");
            name = name.replace("~", "");
            name = name.replace("<", "");
            name = name.replace(">", "");
            name = name.replace("(", "");
            name = name.replace(")", "");
            name = name.replace("\"", "");
            return name.substring(2);
        } else if (name.startsWith("e:")) {
            return name.substring(2);
        } else if (name.startsWith("l:")) {
            // For LIKE queries, strip the prefix and any surrounding quotes, but keep wildcards (% and _)
            String cleaned = name.substring(2);
            // Remove surrounding quotes if present
            if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            return cleaned;
        } else {
            return name;
        }
    }

    /**
     * Gets the list of columns that are searched in this match query.
     *
     * @return The comma-separated list of column names
     */
    public String getMatchColumnList() {
        return matchColumnList;
    }

    /**
     * Gets a user-friendly description of the searchable fields.
     * Converts database column names to readable field names.
     *
     * @return A formatted string describing searchable fields (e.g., "name, category, payee, memo")
     */
    public String getSearchableFieldsDescription() {
        if (matchColumnList == null || matchColumnList.isEmpty()) {
            return "name";
        }

        // Clean up column names for user display
        String[] columns = matchColumnList.split(",");
        StringBuilder description = new StringBuilder();

        for (int i = 0; i < columns.length; i++) {
            String column = columns[i].trim();

            // Remove table prefixes and convert to user-friendly names
            if (column.contains(".")) {
                column = column.substring(column.lastIndexOf(".") + 1);
            }

            // Convert common column names to user-friendly terms
            column = column.toLowerCase()
                    .replace("_", " ")
                    .replace("budgetitemname", "name")
                    .replace("merchantname", "name")
                    .replace("categoryname", "category")
                    .replace("payeename", "payee");

            if (i > 0) {
                description.append(", ");
            }
            description.append(column);
        }

        return description.toString();
    }
}
