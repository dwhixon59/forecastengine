package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.parser.TransactionParser;
import com.hixon.financialApp.model.qfx.QfxParser;
import com.hixon.financialApp.model.qfx.QfxStatement;
import com.hixon.financialApp.model.qfx.QfxTransaction;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static com.hixon.financialApp.utility.Utility.formatDollarAmount;

/**
 * Abstract base class for financial institution controllers that handle transaction imports,
 * provisional transaction reconciliation, and institution-specific parsing logic.
 *
 * This class provides common functionality shared across different financial institution
 * implementations, including register management, budget tracking, and generic provisional
 * transaction reconciliation with tip detection.
 *
 * Specific financial institutions should extend this class and implement the abstract methods
 * defined in FinancialInstitutionInt to provide institution-specific behavior.
 */
public abstract class FinancialInstitution implements FinancialInstitutionInt {

    /**
     * The session controller.
     */
    protected SessionController sessionController;

    /**
     * The budget used for categorizing and tracking transactions.
     */
    protected Budget budget;

    /**
     * The forecast used for planning and projecting future transactions.
     */
    protected Forecast forecast;

    /**
     * The view interface for interacting with the user.
     */
    protected ViewInt view;

    /**
     * The notification service for sending asynchronous notifications.
     */
    protected NotificationServiceInt notificationService;

    /**
     * Gets the register from the session controller.
     * This ensures we always use the current register, even if it changes during the session.
     *
     * @return The current register from the session controller
     */
    protected Register getRegister() {
        return sessionController.getRegister();
    }

    // QFX import fields
    private TransactionParser<QfxTransaction> qfxParser;
    private boolean isQfxOpen = false;

    // CSV import fields (using Apache Commons CSV directly)
    private org.apache.commons.csv.CSVParser csvApacheParser;
    private Iterator<CSVRecord> csvRecordIterator;
    private InputStreamReader csvReader;
    private boolean isCsvOpen = false;

    /**
     * Constructs a new FinancialInstitution with the specified dependencies.
     *
     * @param sessionController The session controller containing register, budget, forecast, view, and notificationService.
     */
    protected FinancialInstitution(SessionController sessionController) {
        this.sessionController = sessionController;
        this.budget = sessionController.getBudget();
        this.forecast = sessionController.getForecast();
        this.view = sessionController.getView();
        this.notificationService = sessionController.getNotificationService();
    }

    /**
     * Generic implementation of provisional transaction reconciliation.
     * This method handles the common logic for reconciling a cleared transaction with
     * its provisional counterpart, including tip detection and balance adjustments.
     *
     * When a cleared transaction comes through, this method:
     * 1. Transfers properties (ID, flags) from the provisional to the cleared transaction
     * 2. Detects if there's a tip (difference in amount between provisional and cleared)
     * 3. Adjusts the register balance by the tip amount if a tip is detected
     * 4. Updates the first split to include the tip amount
     * 5. Logs the tip information for the user
     *
     * Financial institutions can override this method if they have specific
     * reconciliation requirements.
     *
     * @param clearedTransaction The cleared transaction from CSV import
     * @param provisionalTransaction The matching provisional transaction (or null)
     * @param register The register to update
     * @param splits The splits list (will be updated in place if tip detected)
     * @return true if provisional transaction was found and reconciled
     * @throws Exception if an error occurs during reconciliation
     * @throws IllegalArgumentException if either transaction is null
     */
    @Override
    public boolean reconcileProvisionalTransaction(Transaction clearedTransaction,
                                                   Transaction provisionalTransaction,
                                                   Register register,
                                                   List<TransactionSplit> splits) throws Exception {

        if (provisionalTransaction == null || clearedTransaction == null) {
            throw new IllegalArgumentException("Provisional or cleared transaction is null in reconcileProvisionalTransaction.");
        }

        // Transfer properties from provisional to cleared transaction
        clearedTransaction.setId(provisionalTransaction.getId());
        clearedTransaction.setIdMerchant(provisionalTransaction.getIdMerchant());
        clearedTransaction.setMerchant(provisionalTransaction.getMerchant());
        clearedTransaction.setMerchantPayee(provisionalTransaction.getMerchantPayee());
        clearedTransaction.setIsImproper(provisionalTransaction.getIsImproper());
        clearedTransaction.setIsNew(false);

        // Check if there's a tip (cleared amount differs from provisional amount)
        double tipAmount = clearedTransaction.getAmount() - provisionalTransaction.getAmount();
        boolean hasTip = Math.abs(tipAmount) > 0.01; // More than 1 cent difference

        // If there's a tip, handle the balance adjustment and split updates
        if (hasTip) {
            // Adjust the register balance by the tip amount (difference between cleared and provisional)
            register.setBalance(register.getBalance() + tipAmount);
            register.update();

            // Store the tip information on the transaction for display later
            // (after the transaction log heading is shown)
            clearedTransaction.setTipAmount(tipAmount);
            clearedTransaction.setProvisionalAmount(provisionalTransaction.getAmount());

            // If splits exist, we need to adjust them to account for the tip
            // The tip will be added to the first split (typically the meal/service charge)
            if (splits != null && !splits.isEmpty()) {
                TransactionSplit firstSplit = splits.get(0);
                firstSplit.setAmount(firstSplit.getAmount() + tipAmount);
                firstSplit.setDirty(true);
            }
        }

        return true;
    }

    // ========================================
    // Transaction File Import Methods
    // ========================================

    /**
     * Imports transactions from the register's configured import file.
     *
     * <p>This method reads the import file path from the register,
     * determines the file type by extension, and creates the appropriate parser
     * (CSV, QFX, etc.) to read transactions.
     *
     * <p><strong>Supported File Extensions:</strong>
     * <ul>
     *   <li>.qfx - QFX/OFX format (uses QfxParser)</li>
     *   <li>.csv, .tsv - CSV format (institution-specific, not yet implemented here)</li>
     * </ul>
     *
     * <p>Subclasses can override this method to provide custom import logic
     * or to handle additional file formats.
     *
     * @throws Exception if the file cannot be found, opened, or parsed
     * @throws IllegalStateException if register doesn't have import file configured
     */
    public void importRegisterTrxFile() throws Exception {
        // Get full import file path from register
        String fullPath = getRegister().getTrxImportFilePath();

        if (fullPath == null || fullPath.trim().isEmpty()) {
            throw new IllegalStateException(
                "Register '" + getRegister().getName() + "' does not have an import file path configured. " +
                "Please set trxImportFileName and trxImportFileDirectory fields."
            );
        }

        // Determine file type by extension
        String extension = "";
        int lastDot = fullPath.lastIndexOf('.');
        if (lastDot > 0) {
            extension = fullPath.substring(lastDot + 1).toLowerCase();
        }

        // Create appropriate parser based on file extension
        switch (extension) {
            case "qfx", "ofx" -> {
                // QFX/OFX format - use QFX parser
                importQfxRegisterTrxFile(fullPath);
            }
            case "csv", "tsv" -> {
                // CSV/TSV format - use CSV parser
                importCsvRegisterTrxFile(fullPath);
            }
            default -> {
                throw new IllegalArgumentException(
                    "Unsupported import file format: '" + extension + "'. " +
                    "Supported formats: .qfx, .ofx, .csv, .tsv. " +
                    "File: " + fullPath
                );
            }
        }
    }

    // ========================================
    // QFX Import Methods (shared by all institutions using QFX format)
    // ========================================

    /**
     * Imports transactions from a QFX file.
     * This method is used by any financial institution that supports QFX/OFX format.
     *
     * The QFX parser loads all transactions into memory during the open() call,
     * so we can safely close the FileInputStream immediately after parsing.
     *
     * @param filename the QFX file to import
     * @throws Exception if the file cannot be opened or parsed
     */
    protected void importQfxRegisterTrxFile(String filename) throws Exception {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("QFX filename cannot be null or empty");
        }

        this.qfxParser = new QfxParser();

        // Use try-with-resources to ensure FileInputStream is properly closed after parsing
        // The parser loads all transactions into memory, so we don't need to keep the stream open
        try (FileInputStream fis = new FileInputStream(filename)) {
            // Open the parser - this reads and parses the entire file into memory
            this.qfxParser.open(fis);
            // Stream is automatically closed here by try-with-resources
        } catch (Exception e) {
            // If opening fails, make sure we clean up
            this.isQfxOpen = false;
            this.qfxParser = null;
            throw e;
        }

        // Mark as open only after successful parse and stream closure
        this.isQfxOpen = true;
    }

    // ========================================
    // CSV Import Methods (shared by all institutions using CSV format)
    // ========================================

    /**
     * Imports transactions from a CSV file.
     * This method is used by any financial institution that supports CSV/TSV format.
     *
     * Unlike QFX, CSV files are parsed on-demand as we iterate through records,
     * so we need to keep the file and reader open until iteration is complete.
     *
     * @param filename the CSV file to import
     * @throws Exception if the file cannot be opened or parsed
     */
    protected void importCsvRegisterTrxFile(String filename) throws Exception {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("CSV filename cannot be null or empty");
        }

        if (isCsvOpen) {
            throw new IllegalStateException("CSV parser is already open. Call close() first.");
        }

        try {
            // Create file input stream and reader
            FileInputStream fis = new FileInputStream(filename);
            this.csvReader = new InputStreamReader(fis, StandardCharsets.UTF_8);

            // Get the institution-specific CSV format configuration
            // This allows each bank to specify its own CSV format (delimiter, headers, etc.)
            CSVFormat csvFormat = getCsvFormat();

            // Create Apache Commons CSV parser
            this.csvApacheParser = csvFormat.parse(csvReader);

            // Create iterator over CSV records
            this.csvRecordIterator = csvApacheParser.iterator();

            // Mark as open
            this.isCsvOpen = true;

        } catch (Exception e) {
            // If opening fails, make sure we clean up
            closeCsvResources();
            this.isCsvOpen = false;
            throw e;
        }
    }

    /**
     * Closes CSV parsing resources (parser and reader).
     */
    private void closeCsvResources() {
        try {
            if (csvApacheParser != null) {
                csvApacheParser.close();
            }
        } catch (IOException e) {
            // Log but don't throw
            System.err.println("Warning: Error closing CSV parser: " + e.getMessage());
        } finally {
            csvApacheParser = null;
        }

        try {
            if (csvReader != null) {
                csvReader.close();
            }
        } catch (IOException e) {
            // Log but don't throw
            System.err.println("Warning: Error closing CSV reader: " + e.getMessage());
        } finally {
            csvReader = null;
        }

        csvRecordIterator = null;
    }

    /**
     * Converts a QfxTransaction to a Transaction domain object.
     * Subclasses can override this if they need custom conversion logic.
     *
     * @param qfxTxn the QFX transaction
     * @return a Transaction object
     * @throws Exception if conversion fails
     */
    protected Transaction convertQfxToTransaction(QfxTransaction qfxTxn) throws Exception {
        // Convert LocalDate to Calendar
        Calendar postDate = Utility.localDateToCalendarDate(qfxTxn.getPostedDate());

        // Get payee from QFX transaction
        String payee = qfxTxn.getName();

        // QFX transactions are always cleared
        boolean cleared = true;

        // Credit cards don't have check numbers
        int checkNumber = 0;

        // Use FITID as import record ID
        String importRecordId = qfxTxn.getFitId();

        // Create the transaction
        Transaction transaction = new Transaction(
            getRegister(),
            postDate,
            payee,
            qfxTxn.getAmount(),
            cleared,
            checkNumber,
            importRecordId
        );

        // Parse merchant/payee using institution-specific logic
        String merchantPayee = parseMerchantPayee(postDate, qfxTxn.getAmount(), payee);
        transaction.setMerchantPayee(merchantPayee);

        return transaction;
    }

    /**
     * Converts a CSVRecord to a Transaction domain object.
     * This delegates to the institution-specific createFromCSVRecord method.
     *
     * @param csvRecord the CSV record
     * @return a Transaction object
     * @throws Exception if conversion fails
     */
    protected Transaction convertCsvToTransaction(CSVRecord csvRecord) throws Exception {
        // Get the import record base name from the institution-specific method
        String importRecordBaseName = getRegisterImportRecordBaseName(csvRecord);

        // Create transaction using institution-specific logic
        return createFromCSVRecord(csvRecord, importRecordBaseName);
    }

    // ========================================
    // Iterator<Transaction> Implementation
    // ========================================

    @Override
    public boolean hasNext() {
        // Check QFX parser first
        if (isQfxOpen && qfxParser != null) {
            return qfxParser.hasNext();
        }

        // Check CSV parser
        if (isCsvOpen && csvRecordIterator != null) {
            return csvRecordIterator.hasNext();
        }

        // No parser is open
        return false;
    }

    @Override
    public Transaction next() {
        try {
            // Handle QFX format
            if (isQfxOpen && qfxParser != null) {
                // Get next QFX transaction from parser
                QfxTransaction qfxTxn = qfxParser.getNext();
                // Convert QfxTransaction to Transaction
                return convertQfxToTransaction(qfxTxn);
            }

            // Handle CSV format
            if (isCsvOpen && csvRecordIterator != null) {
                // Get next CSV record from iterator
                CSVRecord csvRecord = csvRecordIterator.next();
                // Convert CSVRecord to Transaction
                return convertCsvToTransaction(csvRecord);
            }

            // No parser is open
            throw new NoSuchElementException("No parser is open");

        } catch (Exception e) {
            throw new RuntimeException("Error converting transaction: " + e.getMessage(), e);
        }
    }
    /**
     * Gets the ledger balance from the imported transaction file (if available).
     * This is only available for QFX/OFX files after calling importRegisterTrxFile().
     *
     * @return the ledger balance from the import file, or null if not available
     *         (e.g., for CSV files or before import)
     */
    @Override
    public Double getImportedLedgerBalance() {
        if (qfxParser != null && isQfxOpen) {
            try {
                // Cast to QfxParser to access getStatement method
                if (qfxParser instanceof QfxParser) {
                    QfxStatement statement = ((QfxParser) qfxParser).getStatement();
                    if (statement != null) {
                        return statement.getLedgerBalance();
                    }
                }
            } catch (Exception e) {
                // If we can't get the balance, return null
                return null;
            }
        }
        // Return null for CSV files or if QFX not open
        return null;
    }

    @Override
    public void close() throws Exception {
        try {
            // Close QFX parser if open
            if (qfxParser != null) {
                qfxParser.close();
            }

            // Close CSV resources if open
            if (isCsvOpen) {
                closeCsvResources();
            }
        } finally {
            isQfxOpen = false;
            qfxParser = null;
            isCsvOpen = false;
        }
    }
}
