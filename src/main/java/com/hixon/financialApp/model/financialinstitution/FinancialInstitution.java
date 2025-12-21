package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.parser.TransactionParser;
import com.hixon.financialApp.model.qfx.QfxParser;
import com.hixon.financialApp.model.qfx.QfxTransaction;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;

import java.io.File;
import java.io.FileInputStream;
import java.util.Calendar;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

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
     * The register associated with this financial institution account.
     */
    protected Register register;

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

    // QFX import fields
    private TransactionParser<QfxTransaction> qfxParser;
    private String qfxFilename;
    private boolean isQfxOpen = false;

    /**
     * Constructs a new FinancialInstitution with the specified dependencies.
     *
     * @param sessionController The session controller containing register, budget, forecast, view, and notificationService.
     */
    protected FinancialInstitution(SessionController sessionController) {
        this.sessionController = sessionController;
        this.register = sessionController.getRegister();
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

            // Log the tip for the user
            view.say(String.format("Tip detected: %s (Provisional: %s, Cleared: %s)",
                    formatDollarAmount(tipAmount),
                    formatDollarAmount(provisionalTransaction.getAmount()),
                    formatDollarAmount(clearedTransaction.getAmount())));

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
     * <p>This method reads the import filename and directory from the register,
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
        // Get import file information from register
        String filename = register.getTrxImportFileName();
        String directory = register.getTrxImportFileDirectory();

        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalStateException(
                "Register '" + register.getName() + "' does not have an import filename configured. " +
                "Please set trxImportFileName field."
            );
        }

        // Construct full file path
        String fullPath;
        if (directory != null && !directory.trim().isEmpty()) {
            fullPath = new File(directory, filename).getAbsolutePath();
        } else {
            fullPath = filename; // Use filename as-is if no directory specified
        }

        // Determine file type by extension
        String extension = "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            extension = filename.substring(lastDot + 1).toLowerCase();
        }

        // Create appropriate parser based on file extension
        switch (extension) {
            case "qfx", "ofx" -> {
                // QFX/OFX format - use inherited QFX import
                importQfxRegisterTrxFile(fullPath);
            }
            case "csv", "tsv" -> {
                // CSV/TSV format - subclass must handle this
                // This is institution-specific (WellsFargo has its own CSV parser)
                throw new UnsupportedOperationException(
                    "CSV/TSV import must be handled by institution-specific subclass. " +
                    "File: " + fullPath
                );
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
     * @param filename the QFX file to import
     * @throws Exception if the file cannot be opened or parsed
     */
    protected void importQfxRegisterTrxFile(String filename) throws Exception {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("QFX filename cannot be null or empty");
        }

        this.qfxFilename = filename;
        this.qfxParser = new QfxParser();

        // Open the parser
        this.qfxParser.open(new FileInputStream(filename));
        this.isQfxOpen = true;
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
            register,
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

    // ========================================
    // Iterator<Transaction> Implementation
    // ========================================

    @Override
    public boolean hasNext() {
        if (!isQfxOpen || qfxParser == null) {
            return false;
        }
        return qfxParser.hasNext();
    }

    @Override
    public Transaction next() {
        if (!isQfxOpen || qfxParser == null) {
            throw new NoSuchElementException("QFX parser is not open");
        }

        try {
            // Get next QFX transaction from parser
            QfxTransaction qfxTxn = qfxParser.getNext();

            // Convert QfxTransaction to Transaction
            return convertQfxToTransaction(qfxTxn);

        } catch (Exception e) {
            throw new RuntimeException("Error converting QFX transaction to Transaction: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            if (qfxParser != null) {
                qfxParser.close();
            }
        } finally {
            isQfxOpen = false;
            qfxParser = null;
        }
    }
}
