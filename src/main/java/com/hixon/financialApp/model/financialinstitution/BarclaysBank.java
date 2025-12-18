package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.parser.TransactionParser;
import com.hixon.financialApp.model.qfx.QfxParser;
import com.hixon.financialApp.model.qfx.QfxTransaction;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.commons.csv.CSVRecord;

import java.io.FileInputStream;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

/**
 * Barclays Bank financial institution implementation.
 *
 * <p>Handles transaction imports from Barclays credit card accounts using
 * QFX (Quicken Financial Exchange) format files.
 *
 * <p><strong>Supported Formats:</strong>
 * <ul>
 *   <li>QFX/OFX files (via QfxParser)</li>
 * </ul>
 *
 * <p><strong>Iterator Pattern:</strong> This class implements {@link Iterator} to provide
 * sequential access to transactions. The ImportController can iterate through transactions
 * without knowing they came from a QFX file.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * BarclaysBank barclays = new BarclaysBank(
 *     filename, register, budget, forecast, view, notificationService
 * );
 *
 * try {
 *     while (barclays.hasNext()) {
 *         Transaction t = barclays.next();
 *         // Process transaction...
 *     }
 * } finally {
 *     barclays.close();
 * }
 * }</pre>
 *
 * @see QfxParser
 * @see QfxTransaction
 * @see FinancialInstitution
 */
public class BarclaysBank extends FinancialInstitution implements Iterator<Transaction> {

    private TransactionParser<QfxTransaction> parser;
    private String filename;
    private boolean isOpen = false;

    /**
     * Creates a new BarclaysBank instance.
     *
     * @param filename the QFX file to import
     * @param register the register to import transactions into
     * @param budget the budget for transaction categorization
     * @param forecast the forecast for planning
     * @param view the view for user interaction
     * @param notificationService the notification service
     * @throws Exception if the QFX file cannot be opened or parsed
     */
    public BarclaysBank(String filename, Register register, Budget budget, Forecast forecast,
                       ViewInt view, NotificationServiceInt notificationService) throws Exception {
        super(register, budget, forecast, view, notificationService);

        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        this.filename = filename;
        this.parser = new QfxParser();

        // Open the parser immediately
        this.parser.open(new FileInputStream(filename));
        this.isOpen = true;
    }

    // ========================================
    // Iterator<Transaction> Implementation
    // ========================================

    @Override
    public boolean hasNext() {
        if (!isOpen) {
            return false;
        }
        return parser.hasNext();
    }

    @Override
    public Transaction next() {
        if (!isOpen) {
            throw new NoSuchElementException("BarclaysBank is not open");
        }

        try {
            // Get next QFX transaction from parser
            QfxTransaction qfxTxn = parser.getNext();

            // Convert QfxTransaction to Transaction
            return convertToTransaction(qfxTxn);

        } catch (Exception e) {
            throw new RuntimeException("Error converting QFX transaction to Transaction: " + e.getMessage(), e);
        }
    }

    /**
     * Closes the parser and releases all resources.
     *
     * @throws Exception if an error occurs closing the parser
     */
    public void close() throws Exception {
        try {
            if (parser != null) {
                parser.close();
            }
        } finally {
            isOpen = false;
        }
    }

    // ========================================
    // QFX-Specific Conversion
    // ========================================

    /**
     * Converts a QfxTransaction to a Transaction domain object.
     *
     * @param qfxTxn the QFX transaction
     * @return a Transaction object
     * @throws Exception if conversion fails
     */
    private Transaction convertToTransaction(QfxTransaction qfxTxn) throws Exception {
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

        // Parse merchant/payee using Barclays-specific logic
        String merchantPayee = parseMerchantPayee(postDate, qfxTxn.getAmount(), payee);
        transaction.setMerchantPayee(merchantPayee);

        return transaction;
    }

    // ========================================
    // FinancialInstitutionInt Implementation
    // (CSV-specific methods - not used for QFX)
    // ========================================

    @Override
    public Class<? extends Enum<?>> getCsvHeadersClass() {
        throw new UnsupportedOperationException("Barclays uses QFX format, not CSV");
    }

    @Override
    public String getRegisterImportRecordBaseName(CSVRecord record) throws ParseException {
        throw new UnsupportedOperationException("Barclays uses QFX format, not CSV");
    }

    @Override
    public Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws Exception {
        throw new UnsupportedOperationException("Barclays uses QFX format, not CSV");
    }

    @Override
    public Transaction loadProvisionalTransactionFromCSV(String line, Register register) throws Exception {
        throw new UnsupportedOperationException("Barclays uses QFX format, not CSV. QFX does not support provisional transactions.");
    }

    @Override
    public Transaction getMatchingProvisionalTransaction(Transaction clearedTransaction)
            throws RegisterException, SQLException, EntityException, ParseException, Exception {
        // QFX transactions are always cleared, no provisional transactions
        return null;
    }

    // ========================================
    // Institution-Specific Methods
    // ========================================

    @Override
    public String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception {
        // Barclays credit card payees are typically clean merchant names
        // For now, just return the payee as-is
        // TODO: Add Barclays-specific payee parsing if needed
        return payee;
    }

    @Override
    public String extractUserDescription(String payee) {
        // Barclays doesn't typically have user descriptions in payee field
        return "";
    }

    @Override
    public List<User> extractUsers(String payee) {
        // Barclays transactions don't contain user information
        return new ArrayList<>();
    }

    @Override
    public String extractAccountType(String payee) {
        // For Barclays, it's always a credit card
        return "CREDIT_CARD";
    }
}

