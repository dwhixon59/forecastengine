package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;

import java.util.ArrayList;
import java.util.List;

import static com.hixon.financialApp.utility.Utility.*;
import static com.hixon.financialApp.utility.Utility.calendarDateToStringDate;

/**
 * This class encapsulates the logic around logging what was done while importing transactions.
 */
public class ImportLog {

    /*
     * Fields:
     */
    private List<Transaction> importedTransactions = new ArrayList<>();


    /*
     * Getters and setters:
     */

    public List<Transaction> getImportedTransactions() {
        return importedTransactions;
    }


    /*
     * Helper methods:
     */

    public void logImportEvent(Transaction transaction) throws EntityException, RegisterException {

        // Save the transaction in case the user wants to recategorize the transaction later:
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
        getResolver().say("\nImported a " + creditOrDebitString + transaction.getMerchant().getName() + " for " +
                formatDollarAmount(Math.abs(transaction.getAmount())) + " on " +
                ((transaction.getAuthorizationDate() != null) ?
                        calendarDateToStringDate(transaction.getAuthorizationDate()) :
                        calendarDateToStringDate(transaction.getPostDate())));
    }
}
