package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import org.apache.commons.csv.CSVRecord;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

/**
 * Interface for financial institution implementations that handle transaction imports.
 *
 * <p>This interface extends {@link Iterator} to provide format-agnostic sequential access
 * to transactions. Financial institutions can import from various formats (CSV, QFX, etc.)
 * and expose transactions through the iterator pattern.
 *
 * <p><strong>Usage by ImportController:</strong>
 * <pre>{@code
 * FinancialInstitutionInt institution = // ... created by factory
 * while (institution.hasNext()) {
 *     Transaction t = institution.next();
 *     // Process transaction...
 * }
 * institution.close();
 * }</pre>
 */
public interface FinancialInstitutionInt extends Iterator<Transaction>, AutoCloseable {

    /**
     * Imports transactions from the import file associated with the register.
     *
     * <p>This method reads the import filename and directory from the register,
     * determines the file type by extension, creates the appropriate parser,
     * and loads transactions into memory for iteration.
     *
     * <p>The import file location is specified in the register's:
     * <ul>
     *   <li>{@code trxImportFileName} - filename (e.g., "transactions.csv", "data.qfx")</li>
     *   <li>{@code trxImportFileDirectory} - directory path (optional)</li>
     * </ul>
     *
     * <p>After calling this method, use the Iterator methods ({@code hasNext()}, {@code next()})
     * to retrieve transactions one at a time.
     *
     * @throws Exception if the file cannot be found, opened, or parsed
     * @throws IllegalStateException if register doesn't have import file configured
     */
    void importRegisterTrxFile() throws Exception;

    /**
     * Returns the enum class representing CSV column headers for this financial institution.
     * This is used by CSV parsers to map column names to enum values.
     *
     * @return the Class object for the CSV headers enum
     */
    Class<? extends Enum<?>> getCsvHeadersClass();

    /**
     * Returns the CSV format configuration for this financial institution.
     * This defines how CSV files from this institution should be parsed, including:
     * - Header format (enum class for column names)
     * - Delimiter (comma, tab, etc.)
     * - Quote character
     * - Whether to skip header record
     * - Trimming behavior
     *
     * <p>Example implementation:
     * <pre>{@code
     * return CSVFormat.RFC4180.builder()
     *         .setHeader(getCsvHeadersClass())
     *         .setTrim(true)
     *         .build();
     * }</pre>
     *
     * @return the CSVFormat configuration for parsing this institution's CSV files
     */
    org.apache.commons.csv.CSVFormat getCsvFormat();

    // Get the base name that will be used in constructing the import record ID:
    String getRegisterImportRecordBaseName(CSVRecord record) throws ParseException;

    // Load a single record from a CSV file into a single transaction instance with associated merchant:
    Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws Exception;

    // Parse a payee string from a particular bank into a Merchant payee:
    String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception;

    // Create a transaction and load it from a provisional CSV record:
    Transaction loadProvisionalTransactionFromCSV(String line, Register register) throws Exception;

    // Get a provisional transaction from an import record:
    Transaction getMatchingProvisionalTransaction(Transaction clearedTransaction)
            throws RegisterException, SQLException, EntityException, java.text.ParseException, Exception;

    /**
     * Reconciles a cleared transaction with its matching provisional transaction (if one exists).
     * This method handles:
     * - Finding the matching provisional transaction
     * - Detecting and handling tips (amount differences)
     * - Adjusting register balance for tips
     * - Updating transaction splits for tips
     * - Transferring provisional transaction properties to the cleared transaction
     *
     * @param clearedTransaction The cleared transaction from the CSV import
     * @param provisionalTransaction The matching provisional transaction (or null if none found)
     * @param register The register to update
     * @param splits The splits for the provisional transaction (will be updated if tip detected)
     * @return true if a provisional transaction was found and reconciled, false otherwise
     * @throws Exception if an error occurs during reconciliation
     */
    boolean reconcileProvisionalTransaction(Transaction clearedTransaction,
                                           Transaction provisionalTransaction,
                                           Register register,
                                           List<com.hixon.financialApp.model.budget.TransactionSplit> splits)
            throws Exception;

    // Extract the memo or user description from the raw text of a register entry:
    String extractUserDescription(String payee);

    /**
     * Extracts the user identification information from the raw text of a register entry.  Then looks up the user and
     * returns the user object if found.
     *
     * @param payee the raw text of the register entry containing user information
     * @return a User object containing the extracted user data
     */
    List<User> extractUsers(String payee);

    /**
     * Extracts the account type information from the given payee string.
     *
     * @param payee the raw text of the payee containing account type information
     * @return a string representing the extracted account type
     */
    String extractAccountType(String payee);

    /**
     * Gets the ledger balance from the imported transaction file (if available).
     * This is only available for QFX/OFX files after calling importRegisterTrxFile().
     *
     * @return the ledger balance from the import file, or null if not available
     *         (e.g., for CSV files or before import)
     */
    Double getImportedLedgerBalance();
}

