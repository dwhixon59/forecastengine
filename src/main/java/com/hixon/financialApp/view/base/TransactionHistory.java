package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.register.Transaction;

public class TransactionHistory extends IndependentEntityHistory<Transaction> {


    // Create the singleton TransactionHistory object:
    private static TransactionHistory instance = new TransactionHistory();

    // Make the constructor private so that this class cannot be instantiated:
    private TransactionHistory() {
    }

    /**
     * Get the transaction history object.
     *
     * @return The transaction history singleton.
     */
    public static TransactionHistory getInstance() {
        return instance;
    }

}
