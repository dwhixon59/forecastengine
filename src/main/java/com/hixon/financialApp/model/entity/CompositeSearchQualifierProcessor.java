package com.hixon.financialApp.model.entity;

import java.util.Arrays;
import java.util.List;

/**
 * A composite SearchQualifierProcessor that chains multiple processors together.
 * Each processor is applied in sequence, with the output of one becoming the input
 * of the next.
 *
 * <p>This allows combining different types of qualifiers (budget, category, date range, etc.)
 * in a single search query.
 *
 * <p>Example usage:
 * <pre>
 * SearchQualifierProcessor combined = new CompositeSearchQualifierProcessor(
 *     new BudgetSearchQualifierProcessor(currentBudget, "bi.Budget_idBudget"),
 *     new CategorySearchQualifierProcessor(),
 *     new DateRangeSearchQualifierProcessor()
 * );
 *
 * // User can now search with: "budget:all:category:expense:2024-01-01:2024-12-31:groceries"
 * </pre>
 */
public class CompositeSearchQualifierProcessor implements SearchQualifierProcessor {
    private final List<SearchQualifierProcessor> processors;

    /**
     * Creates a composite processor from multiple processors.
     * Processors are applied in the order provided.
     *
     * @param processors The processors to chain together
     */
    public CompositeSearchQualifierProcessor(SearchQualifierProcessor... processors) {
        this.processors = Arrays.asList(processors);
    }

    /**
     * Creates a composite processor from a list of processors.
     *
     * @param processors The list of processors to chain together
     */
    public CompositeSearchQualifierProcessor(List<SearchQualifierProcessor> processors) {
        this.processors = processors;
    }

    @Override
    public SearchContext process(String searchTerm, String baseQuery) {
        SearchContext context = new SearchContext(searchTerm, baseQuery);

        // Apply each processor in sequence
        for (SearchQualifierProcessor processor : processors) {
            context = processor.process(context.getCleanedSearchTerm(), context.getModifiedQuery());
        }

        return context;
    }
}

