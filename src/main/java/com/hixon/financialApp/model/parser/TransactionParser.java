package com.hixon.financialApp.model.parser;

import java.io.InputStream;

/**
 * Interface for parsers that extract transaction data from various file formats.
 *
 * <p>This interface defines a generic parser contract that can handle any transaction
 * file format (CSV, QFX, JSON, XML, etc.). Implementations provide format-specific
 * parsing logic while presenting a consistent iterator-style API.
 *
 * <p><strong>Usage Pattern:</strong>
 * <pre>{@code
 * TransactionParser<QfxTransaction> parser = new QfxParser();
 * try {
 *     parser.open(new FileInputStream("statement.qfx"));
 *     while (parser.hasNext()) {
 *         QfxTransaction txn = parser.getNext();
 *         // Process transaction...
 *     }
 * } finally {
 *     parser.close();
 * }
 * }</pre>
 *
 * <p><strong>Design Pattern:</strong> Iterator pattern - provides sequential access to transactions.
 *
 * <p><strong>Thread Safety:</strong> Implementations are NOT thread-safe. Create separate
 * parser instances for each thread.
 *
 * @param <T> the specific type of TransactionData this parser produces
 *
 * @see TransactionData
 * @see com.hixon.financialApp.model.qfx.QfxParser
 */
public interface TransactionParser<T extends TransactionData> {

    /**
     * Opens and initializes the parser with an input stream.
     *
     * <p>This method reads the file header, validates the format, and prepares
     * for iteration. Must be called before {@link #hasNext()} or {@link #getNext()}.
     *
     * <p><strong>Important:</strong> The caller is responsible for closing the
     * InputStream via {@link #close()}.
     *
     * @param input the input stream containing transaction data
     * @throws Exception if the file cannot be parsed or has an invalid format
     * @throws IllegalArgumentException if input is null
     * @throws IllegalStateException if parser is already open
     */
    void open(InputStream input) throws Exception;

    /**
     * Checks if there are more transactions to read.
     *
     * <p>This method does not consume or advance the parser position.
     * It can be called multiple times safely.
     *
     * @return true if there are more transactions, false if end of file reached
     * @throws IllegalStateException if parser is not open
     */
    boolean hasNext();

    /**
     * Retrieves the next transaction from the file.
     *
     * <p>This method advances the parser to the next transaction.
     * Subsequent calls return the next transaction in sequence.
     *
     * @return the next transaction data object
     * @throws Exception if an error occurs reading or parsing the transaction
     * @throws IllegalStateException if parser is not open or no more transactions available
     * @throws java.util.NoSuchElementException if {@link #hasNext()} returns false
     */
    T getNext() throws Exception;

    /**
     * Closes the parser and releases all resources.
     *
     * <p>This method closes the underlying InputStream and cleans up any
     * parser-specific resources. After calling this method, the parser
     * cannot be used again without calling {@link #open(InputStream)}.
     *
     * <p>This method is idempotent - calling it multiple times is safe.
     *
     * @throws Exception if an error occurs closing resources
     */
    void close() throws Exception;
}

