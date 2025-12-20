package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.commons.csv.CSVRecord;

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
 *   <li>QFX/OFX files (inherited from {@link FinancialInstitution})</li>
 * </ul>
 *
 * <p><strong>Iterator Pattern:</strong> This class uses the inherited iterator implementation
 * from {@link FinancialInstitution} to provide sequential access to transactions.
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
 * @see FinancialInstitution
 */
public class BarclaysBank extends FinancialInstitution {

    /**
     * Creates a new BarclaysBank instance and opens the QFX file for import.
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

        // Use inherited QFX import functionality
        importQfxRegisterTrxFile(filename);
    }

    // ========================================
    // CSV Methods (Not Supported)
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
    // Barclays-Specific Methods
    // ========================================

    /**
     * Parses merchant/payee information from Barclays transaction data.
     * Barclays credit card payees are typically clean merchant names, so we return as-is.
     *
     * @param date the transaction date
     * @param amount the transaction amount
     * @param payee the payee string from Barclays
     * @return the parsed merchant/payee string
     */
    @Override
    public String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception {
        // Barclays credit card payees are typically clean merchant names
        // For now, just return the payee as-is
        // TODO: Add Barclays-specific payee parsing if needed in the future
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

