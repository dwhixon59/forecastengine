package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.StoreNumberStripper;
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
 * BarclaysBank barclays = new BarclaysBank(sessionController);
 *
 * // Import transactions from register's configured QFX file
 * barclays.importRegisterTrxFile();
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
     * Creates a new BarclaysBank instance.
     *
     * @param sessionController the session controller containing register, budget, forecast, view, and notificationService
     */
    public BarclaysBank(SessionController sessionController) {
        super(sessionController);
    }

    // ========================================
    // CSV Methods (Not Supported)
    // ========================================

    @Override
    public Class<? extends Enum<?>> getCsvHeadersClass() {
        throw new UnsupportedOperationException("Barclays uses QFX format, not CSV");
    }

    @Override
    public org.apache.commons.csv.CSVFormat getCsvFormat() {
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
     * Payment-processor prefixes that Barclays prepends to merchant names in the QFX NAME field.
     * These are stripped so the merchant-payee string used for matching/searching contains only
     * the actual merchant name (e.g. "PY *PRODIGY PEST SOLU" becomes "PRODIGY PEST SOLU").
     */
    private static final java.util.Set<String> PAYMENT_PROCESSOR_PREFIXES = java.util.Set.of(
            "PY", "SQ", "TST", "SP", "WP", "UEP", "ACH", "POS", "DEBIT", "CREDIT", "CHECKCARD",
            "GOOGLE", "AMAZON", "MKTPL", "MKTPLACE", "PRIME", "PAYPAL", "VENMO", "ZELLE");

    /**
     * Parses merchant/payee information from Barclays transaction data.
     * Barclays credit card payees are typically clean merchant names, but some are prefixed with
     * payment-processor tokens (e.g. "PY *PRODIGY PEST SOLU").  This method strips those prefixes
     * so the resulting merchant payee matches the actual merchant name.
     *
     * @param date the transaction date
     * @param amount the transaction amount
     * @param payee the payee string from Barclays
     * @return the parsed merchant/payee string
     */
    @Override
    public String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception {
        if (payee == null || payee.isBlank()) {
            return payee;
        }

        String trimmed = payee.trim();
        // Split on whitespace and asterisk so "PY *PRODIGY" becomes ["PY", "", "PRODIGY"]
        String[] tokens = trimmed.split("[\\s\\*]+");
        int start = 0;
        while (start < tokens.length
                && (tokens[start].isEmpty()
                    || PAYMENT_PROCESSOR_PREFIXES.contains(tokens[start].toUpperCase()))) {
            start++;
        }

        if (start >= tokens.length) {
            // Everything was noise; fall back to the original string.
            return trimmed;
        }

        StringBuilder cleaned = new StringBuilder(tokens[start]);
        for (int i = start + 1; i < tokens.length; i++) {
            if (!tokens[i].isEmpty()) {
                cleaned.append(" ").append(tokens[i]);
            }
        }

        // Drop any store/reference number so every location of a brand yields the same merchant payee.
        return StoreNumberStripper.strip(cleaned.toString());
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
