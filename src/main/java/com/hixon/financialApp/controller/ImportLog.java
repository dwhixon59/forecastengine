package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

import static com.hixon.financialApp.utility.Utility.*;

/**
 * This class encapsulates the logic around logging what was done while importing transactions.
 */
@Getter
public class ImportLog {

    /*
     * Fields:
     */
    private final List<Transaction> importedTransactions = new ArrayList<>();


    /*
     * Helper methods:
     */

    public void logImportEvent(Transaction transaction) throws EntityException, RegisterException {
        logImportEvent(transaction, true);
    }

    public void logImportEvent(Transaction transaction, boolean isNewTransaction) throws EntityException, RegisterException {

        // Save the transaction in case the user wants to change the assigned category of the transaction later:
        importedTransactions.add(transaction);

        String creditOrDebitString;
        if (transaction.getPayee().contains("TRANSFER FROM")) {
            creditOrDebitString = "transfer from ";
        } else if (transaction.getPayee().contains("TRANSFER TO")) {
            creditOrDebitString = "transfer to ";
        } else if (transaction.getAmount() > 0) {
            creditOrDebitString = "deposit from ";
        } else {
            creditOrDebitString = "debit to ";
        }

        String importStatus = isNewTransaction ? "Imported a " : "Already imported: ";

        // Log the import record ID for better traceability
        getView().sayH3(importStatus + creditOrDebitString + transaction.getMerchant().getName() + " for " +
                formatDollarAmount(Math.abs(transaction.getAmount())) + " on " +
                ((transaction.getAuthorizationDate() != null) ?
                        calendarDateToStringDate(transaction.getAuthorizationDate()) :
                        calendarDateToStringDate(transaction.getPostDate())) +
                " (Import Record ID: " + transaction.getImportRecordId() + ")");

        // If a tip was detected during provisional/cleared reconciliation, display it now
        if (transaction.hasTipInfo()) {
            getView().say(String.format("Tip detected: %s (Provisional: %s, Cleared: %s)",
                    formatDollarAmount(transaction.getTipAmount()),
                    formatDollarAmount(transaction.getProvisionalAmount()),
                    formatDollarAmount(transaction.getAmount())));
        }
    }
}
