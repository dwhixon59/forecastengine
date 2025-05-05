package com.hixon.financialApp.utility;

/**
 * A generic record class representing a pair of values.
 *
 * @param <T> The type of the first value
 * @param <U> The type of the second value
 */
public record Pair<T, U>(T first, U second) {
    /**
     * Factory method for creating Pair instances.
     *
     * @param first  The first value
     * @param second The second value
     * @param <T>    The type of the first value
     * @param <U>    The type of the second value
     * @return A new Pair instance
     */
    public static <T, U> Pair<T, U> of(T first, U second) {
        return new Pair<>(first, second);
    }
}