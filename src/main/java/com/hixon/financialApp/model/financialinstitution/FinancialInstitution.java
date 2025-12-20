package com.hixon.financialApp.model.financialinstitution;

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
     * @param register The register to manage transactions for
     * @param budget The budget to use for transaction categorization
     * @param forecast The forecast to use for future transaction planning
     * @param view The view interface for user interaction
     * @param notificationService The notification service for async notifications
     */
    protected FinancialInstitution(Register register, Budget budget, Forecast forecast, ViewInt view,
                                   NotificationServiceInt notificationService) {
        this.register = register;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
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

