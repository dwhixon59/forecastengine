package com.hixon.financialApp.model.entity;

/**
 * Processes search qualifiers and modifies the SQL query accordingly.
 * This allows callers to inject custom logic for handling special search
 * prefixes like "budget:all:" or "category:expense:" without polluting
 * the MatchQuery class with domain-specific logic.
 *
 * <p>Implementations can handle domain-specific filtering such as:
 * <ul>
 *   <li>Budget filtering (budget:all:, budget:2:)</li>
 *   <li>Category filtering (category:expense:)</li>
 *   <li>Date range filtering (date:2024-01-01:2024-12-31:)</li>
 *   <li>Any other custom qualifier logic</li>
 * </ul>
 */
@FunctionalInterface
public interface SearchQualifierProcessor {

    /**
     * Processes search qualifiers in the input string and returns a modified
     * query context with the cleaned search term and modified SQL.
     *
     * @param searchTerm The original search term (may contain qualifiers)
     * @param baseQuery The base SQL query to potentially modify
     * @return A SearchContext containing the cleaned term and modified query
     */
    SearchContext process(String searchTerm, String baseQuery);

    /**
     * A no-op processor that returns the input unchanged.
     * Use this when no qualifier processing is needed.
     */
    SearchQualifierProcessor IDENTITY = (term, query) -> new SearchContext(term, query);
}

