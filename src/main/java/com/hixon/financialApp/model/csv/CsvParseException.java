package com.hixon.financialApp.model.csv;

/**
 * Exception thrown when CSV parsing fails.
 *
 * <p>This exception indicates that a CSV file could not be parsed,
 * either due to format errors, missing columns, or invalid data.
 *
 * @see CsvParser
 */
public class CsvParseException extends Exception {

    /**
     * Creates a new CsvParseException with the specified message.
     *
     * @param message the error message
     */
    public CsvParseException(String message) {
        super(message);
    }

    /**
     * Creates a new CsvParseException with the specified message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public CsvParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

