package com.hixon.financialApp.model.parser;

import java.time.LocalDate;

/**
 * Common interface for transaction data extracted from various file formats.
 *
 * <p>This interface defines the minimum set of fields that all transaction formats
 * must provide. Implementations include:
 * <ul>
 *   <li>{@link com.hixon.financialApp.model.qfx.QfxTransaction} - from QFX/OFX files</li>
 *   <li>{@link com.hixon.financialApp.model.csv.CsvTransaction} - from CSV files</li>
 * </ul>
 *
 * <p><strong>Purpose:</strong> This interface allows FinancialInstitution classes to work
 * with transaction data from any format without knowing the specific format details.
 *
 * <p><strong>Design Pattern:</strong> Adapter pattern - adapts various file formats to a common interface.
 *
 * @see com.hixon.financialApp.model.qfx.QfxTransaction
 * @see com.hixon.financialApp.model.parser.TransactionParser
 */
public interface TransactionData {

    /**
     * Gets the posted date of the transaction.
     * This is the date when the transaction was posted to the account.
     *
     * @return the posted date, never null
     */
    LocalDate getPostDate();

    /**
     * Gets the authorization date of the transaction.
     * This is the date when the transaction was authorized (e.g., card swipe date).
     * May be the same as posted date or null if not applicable.
     *
     * @return the authorization date, or null if not available
     */
    LocalDate getAuthorizationDate();

    /**
     * Gets the transaction amount.
     * Negative amounts represent debits (money out).
     * Positive amounts represent credits (money in).
     *
     * @return the transaction amount
     */
    double getAmount();

    /**
     * Gets the payee or merchant name for the transaction.
     * This is the description of who the transaction was with.
     *
     * @return the payee name, never null
     */
    String getPayee();

    /**
     * Indicates whether this transaction is cleared (posted) or provisional (pending).
     *
     * @return true if the transaction is cleared, false if provisional
     */
    boolean isCleared();

    /**
     * Gets the unique import record identifier for this transaction.
     * This is used to prevent duplicate imports of the same transaction.
     *
     * <p>The format is implementation-specific but should uniquely identify
     * the transaction within the source file.
     *
     * @return the import record ID, never null
     */
    String getImportRecordId();
}

