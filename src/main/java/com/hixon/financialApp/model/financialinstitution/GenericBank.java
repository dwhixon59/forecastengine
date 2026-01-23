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
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.commons.csv.CSVRecord;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

/**
 * Generic Bank financial institution implementation for banks without specific parsing logic.
 *
 * <p>This is a fallback implementation for financial institutions that don't have
 * specialized parsers. It provides basic functionality but requires manual merchant/payee
 * identification and doesn't support automated transaction parsing.
 *
 * <p><strong>Use Cases:</strong>
 * <ul>
 *   <li>Small banks or credit unions without specific file formats</li>
 *   <li>Manual transaction entry</li>
 *   <li>Testing purposes</li>
 * </ul>
 *
 * @see FinancialInstitution
 */
public class GenericBank extends FinancialInstitution {

    /**
     * Creates a new GenericBank instance.
     *
     * @param sessionController the session controller containing register, budget, forecast, view, and notificationService
     */
    public GenericBank(SessionController sessionController) {
        super(sessionController);
    }

    // ========================================
    // CSV Methods (Not Supported)
    // ========================================

    @Override
    public Class<? extends Enum<?>> getCsvHeadersClass() {
        throw new UnsupportedOperationException("GenericBank does not support CSV import. Use manual transaction entry.");
    }

    @Override
    public org.apache.commons.csv.CSVFormat getCsvFormat() {
        throw new UnsupportedOperationException("GenericBank does not support CSV import. Use manual transaction entry.");
    }

    @Override
    public String getRegisterImportRecordBaseName(CSVRecord record) throws ParseException {
        throw new UnsupportedOperationException("GenericBank does not support CSV import. Use manual transaction entry.");
    }

    @Override
    public Transaction createFromCSVRecord(CSVRecord record, String importRecordId) throws Exception {
        throw new UnsupportedOperationException("GenericBank does not support CSV import. Use manual transaction entry.");
    }

    @Override
    public Transaction loadProvisionalTransactionFromCSV(String line, Register register) throws Exception {
        throw new UnsupportedOperationException("GenericBank does not support provisional transactions.");
    }

    @Override
    public Transaction getMatchingProvisionalTransaction(Transaction clearedTransaction)
            throws RegisterException, SQLException, EntityException, ParseException, Exception {
        // Generic bank doesn't support provisional transactions
        return null;
    }

    // ========================================
    // Generic Bank Methods
    // ========================================

    /**
     * Parses merchant/payee information from transaction data.
     * For generic banks, returns the payee string as-is.
     *
     * @param date the transaction date
     * @param amount the transaction amount
     * @param payee the payee string
     * @return the payee string unchanged
     */
    @Override
    public String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception {
        // Generic bank has no special payee parsing - return as-is
        return payee;
    }

    @Override
    public String extractUserDescription(String payee) {
        // Generic bank doesn't have user descriptions in payee field
        return "";
    }

    @Override
    public List<User> extractUsers(String payee) {
        // Generic bank transactions don't contain user information
        return new ArrayList<>();
    }

    @Override
    public String extractAccountType(String payee) {
        // Generic bank - unknown account type
        return "UNKNOWN";
    }
}

