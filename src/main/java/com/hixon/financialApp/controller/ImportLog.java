package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.TransactionSplit;
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


    // ── Inner record class ────────────────────────────────────────────────────

    /**
     * Structured record of a single transaction processed during an import session.
     * Used to render the REVIEW IMPORTED TRANSACTIONS summary screen.
     */
    public static class ImportRecord {

        public enum Status {
            /** Processed this session — splits assigned (manually or from provisional). */
            NEWLY_IMPORTED,
            /** Duplicate import record — transaction already existed with splits; no change made. */
            ALREADY_IMPORTED,
            /** User pressed 's' during split or merchant assignment — no splits assigned. */
            SKIPPED_BY_USER
        }

        @Getter private final Transaction transaction;
        @Getter private Status status;
        /** Live splits — loaded lazily / refreshed after recategorization. */
        private List<TransactionSplit> splits;
        @Getter private boolean recategorizedThisSession;

        public ImportRecord(Transaction transaction, Status status) {
            this.transaction = transaction;
            this.status = status;
        }

        /** Re-loads splits from the database (call after recategorization). */
        public void refreshSplits() throws RegisterException {
            this.splits = TransactionSplit.getSplitsForTransaction(transaction);
        }

        /**
         * Returns the current splits, loading them from the DB on first access.
         */
        public List<TransactionSplit> getSplits() throws RegisterException {
            if (splits == null) {
                splits = TransactionSplit.getSplitsForTransaction(transaction);
            }
            return splits;
        }

        public void setRecategorizedThisSession(boolean v) { this.recategorizedThisSession = v; }
        public void setStatus(Status s) { this.status = s; }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    /** Structured records — used for the post-import summary screen. */
    private final List<ImportRecord> importRecords = new ArrayList<>();

    /** Legacy list kept for any existing callers that iterate importedTransactions. */
    private final List<Transaction> importedTransactions = new ArrayList<>();

    // ── Logging methods ───────────────────────────────────────────────────────

    /** Log a newly imported transaction (splits assigned this session). */
    public void logImportEvent(Transaction transaction) throws EntityException, RegisterException {
        logImportEvent(transaction, ImportRecord.Status.NEWLY_IMPORTED);
    }

    /**
     * Legacy boolean overload — kept so existing call sites compile without change.
     * {@code isNewTransaction=true} → NEWLY_IMPORTED; {@code false} → ALREADY_IMPORTED.
     */
    public void logImportEvent(Transaction transaction, boolean isNewTransaction) throws EntityException, RegisterException {
        logImportEvent(transaction,
                isNewTransaction ? ImportRecord.Status.NEWLY_IMPORTED : ImportRecord.Status.ALREADY_IMPORTED);
    }

    /** Log a transaction with an explicit status. */
    public void logImportEvent(Transaction transaction, ImportRecord.Status status) throws EntityException, RegisterException {

        // Record for summary screen:
        importRecords.add(new ImportRecord(transaction, status));

        // Legacy list:
        importedTransactions.add(transaction);

        // Console output:
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

        String importStatus;
        switch (status) {
            case NEWLY_IMPORTED:  importStatus = "Imported a "; break;
            case ALREADY_IMPORTED: importStatus = "Already imported: "; break;
            case SKIPPED_BY_USER:  importStatus = "Skipped: "; break;
            default: importStatus = "Processed: ";
        }

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
