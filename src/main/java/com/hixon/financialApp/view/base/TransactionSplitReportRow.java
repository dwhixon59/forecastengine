package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionSplit;

/**
 * This class represents a row in a tabular report that represents a budget item.  It contains some meta-data
 * about that item, such as the number of splits associated wit the iem.  The reason for the meta-data is to help the
 * reporting classes plan the layout of the portion of the report that contains the item and it's splits.
 */
public class TransactionSplitReportRow extends ReportRow {

    /*
     * Fields:
     */
    private Transaction transaction;
    private TransactionSplit transactionSplit;

    /*
     * Getters and Setters:
     */
    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public TransactionSplit getTransactionSplit() {
        return transactionSplit;
    }

    public void setTransactionSplit(TransactionSplit transactionSplit) {
        this.transactionSplit = transactionSplit;
    }


    /*
     * Constructors:
     */
    /**
     * Create a TransactionSplitCategoryRow information object.
     *
     * @param transactionSplit A transaction split object.
     * @param transaction      A transaction object.
     */
    public TransactionSplitReportRow(TransactionSplit transactionSplit, Transaction transaction) {
        this.transactionSplit = transactionSplit;
        this.transaction = transaction;
    }
}