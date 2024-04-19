package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Transaction;

/**
 * This class represents a row in a tabular report that represents a budget item.  It contains some meta-data
 * about that item, such as the number of splits associated wit the iem.  The reason for the meta-data is to help the
 * reporting classes plan the layout of the portion of the report that contains the item and it's splits.
 */
public class TransactionSplitReportRow extends ReportRow {

    /*
     * Fields:
     */
    private final Transaction transaction;
    private final TransactionSplit transactionSplit;
    private final Merchant merchant;

    /*
     * Getters and Setters:
     */
    public Transaction getTransaction() {
        return transaction;
    }

    public TransactionSplit getTransactionSplit() {
        return transactionSplit;
    }

    public Merchant getMerchant() {
        return merchant;
    }


    /*
     * Constructors:
     */
    /**
     * Create a TransactionSplitCategoryRow information object.
     *  @param transactionSplit A transaction split object.
     * @param transaction      A transaction object.
     * @param merchant
     */
    public TransactionSplitReportRow(TransactionSplit transactionSplit, Transaction transaction, Merchant merchant) {
        this.transactionSplit = transactionSplit;
        this.transaction = transaction;
        this.merchant = merchant;
    }
}