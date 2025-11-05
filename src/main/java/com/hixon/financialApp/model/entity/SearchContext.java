package com.hixon.financialApp.model.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates the result of processing search qualifiers.
 * Contains the cleaned search term (with qualifiers removed) and the
 * potentially modified SQL query.
 *
 * <p>Also includes optional metadata that can be used for:
 * <ul>
 *   <li>Display hints (e.g., "Searching all budgets")</li>
 *   <li>Tracking which qualifiers were applied</li>
 *   <li>Passing context to the view layer</li>
 * </ul>
 */
public class SearchContext {
    private final String cleanedSearchTerm;
    private final String modifiedQuery;
    private final Map<String, Object> metadata;

    /**
     * Creates a SearchContext with just a cleaned term and modified query.
     *
     * @param cleanedSearchTerm The search term with qualifiers removed
     * @param modifiedQuery The potentially modified SQL query
     */
    public SearchContext(String cleanedSearchTerm, String modifiedQuery) {
        this.cleanedSearchTerm = cleanedSearchTerm;
        this.modifiedQuery = modifiedQuery;
        this.metadata = new HashMap<>();
    }

    /**
     * Creates a SearchContext with term, query, and metadata.
     *
     * @param cleanedSearchTerm The search term with qualifiers removed
     * @param modifiedQuery The potentially modified SQL query
     * @param metadata Optional metadata for display hints or tracking
     */
    public SearchContext(String cleanedSearchTerm, String modifiedQuery, Map<String, Object> metadata) {
        this.cleanedSearchTerm = cleanedSearchTerm;
        this.modifiedQuery = modifiedQuery;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * Gets the cleaned search term with qualifiers removed.
     *
     * @return The cleaned search term
     */
    public String getCleanedSearchTerm() {
        return cleanedSearchTerm;
    }

    /**
     * Gets the modified SQL query.
     *
     * @return The modified query
     */
    public String getModifiedQuery() {
        return modifiedQuery;
    }

    /**
     * Gets the metadata map.
     *
     * @return The metadata map (mutable)
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Convenience method for adding metadata.
     * Returns this for method chaining.
     *
     * @param key The metadata key
     * @param value The metadata value
     * @return This SearchContext for chaining
     */
    public SearchContext withMetadata(String key, Object value) {
        metadata.put(key, value);
        return this;
    }

    /**
     * Convenience method to check if a metadata key exists.
     *
     * @param key The metadata key to check
     * @return true if the key exists
     */
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    /**
     * Convenience method to get a metadata value with a default.
     *
     * @param key The metadata key
     * @param defaultValue The default value if key doesn't exist
     * @param <T> The type of the value
     * @return The value or defaultValue
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        return (T) metadata.getOrDefault(key, defaultValue);
    }
}

