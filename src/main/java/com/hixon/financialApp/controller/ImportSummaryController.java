package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.hixon.financialApp.utility.Utility.calendarDateToStringDate;
import static com.hixon.financialApp.utility.Utility.formatDollarAmount;
import static com.hixon.financialApp.view.base.ViewInt.*;

/**
 * Displays the REVIEW IMPORTED TRANSACTIONS summary after both cleared and provisional
 * import steps have completed, and drives the optional in-session recategorization loop.
 *
 * <p>This controller is a separate daily-update step placed immediately after
 * "IMPORT PROVISIONAL TRANSACTIONS" and before "VERIFY REGISTER BALANCE".
 * It delegates recategorization to {@link TransactionController#recategorizeTransaction(Transaction)},
 * which is the same code path used by Manage Data → Recategorize.</p>
 */
public class ImportSummaryController {

    private static final Logger logger = LogManager.getLogger(ImportSummaryController.class);

    // Column widths for the summary table
    private static final int COL_NUM      = 4;   // "10 * "
    private static final int COL_DATE     = 6;   // "05-22 "
    private static final int COL_MERCHANT = 26;  // merchant name, truncated
    private static final int COL_AMOUNT   = 12;  // "+$1,245.00 "

    private final SessionController sessionController;
    private final ImportLog importLog;
    private final ViewInt view;
    private boolean forecastWasChanged = false;

    public ImportSummaryController(SessionController sessionController, ImportLog importLog) {
        this.sessionController = sessionController;
        this.importLog = importLog;
        this.view = sessionController.getView();
    }

    /**
     * Renders the import summary table and runs the optional recategorize loop.
     *
     * @return {@code true} if any recategorization changed the forecast (so the caller
     *         should call {@code forecastController.updateForecast()}).
     */
    public boolean showSummaryAndRecategorize() throws Exception {
        List<ImportLog.ImportRecord> records = importLog.getImportRecords();

        if (records.isEmpty()) {
            view.say("No transactions were processed this session.");
            return false;
        }

        printSummary(records);

        // Recategorize loop
        while (true) {
            String input;
            try {
                input = view.getResponseStringMenuSelection(
                        "Recategorize a transaction? Enter number [1-" + records.size() + "] or press Enter to continue",
                        true,   // allowNone = allow pressing Enter to continue
                        false,  // isCancelAllowed
                        true,   // isQuitAllowed
                        false   // isSkipAllowed
                ).trim();
            } catch (QuitException qe) {
                throw qe;
            } catch (CancelException e) {
                break;
            }

            if (input.isEmpty()) {
                break;
            }

            int selection;
            try {
                selection = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                view.say("Invalid selection. Enter a transaction number or press Enter to continue.");
                continue;
            }

            if (selection < 1 || selection > records.size()) {
                view.say("Invalid selection. Enter a number between 1 and " + records.size() + ".");
                continue;
            }

            ImportLog.ImportRecord record = records.get(selection - 1);

            if (record.getStatus() == ImportLog.ImportRecord.Status.ALREADY_IMPORTED) {
                view.say("This transaction was already imported in a previous session and cannot be " +
                        "recategorized here. Use Manage Data → Recategorize Transaction instead.");
                continue;
            }

            if (record.getStatus() == ImportLog.ImportRecord.Status.SKIPPED_BY_USER) {
                view.say("This transaction was skipped. Use Manage Data → Reprocess Transaction to categorize it.");
                continue;
            }

            // NEWLY_IMPORTED — recategorize via the same path as Manage Data
            recategorize(record);
            printSummary(records);

            // E9: After a successful recategorization, check if adjacent records share the same
            // merchant.  If so, offer to apply the same categorization to all of them at once.
            offerGroupRecategorize(records, selection - 1);
        }

        return forecastWasChanged;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Factory method — overridable in tests to inject a mock {@link TransactionController}.
     */
    protected TransactionController createTransactionController() {
        return new TransactionController(sessionController);
    }

    private void recategorize(ImportLog.ImportRecord record) throws Exception {
        TransactionController transactionController = createTransactionController();
        try {
            transactionController.recategorizeTransaction(record.getTransaction());
            record.refreshSplits();
            record.setRecategorizedThisSession(true);
            forecastWasChanged = true;
        } catch (CancelException | SkipException e) {
            // User cancelled — nothing changed; record stays as-is
            logger.debug("Recategorization cancelled for transaction: {}",
                    record.getTransaction().getImportRecordId());
        }
    }

    private void printSummary(List<ImportLog.ImportRecord> records) throws Exception {
        long cleared     = records.stream().filter(r -> r.getTransaction().isCleared()).count();
        long provisional = records.stream().filter(r -> !r.getTransaction().isCleared()).count();
        long newlyImported  = records.stream()
                .filter(r -> r.getStatus() == ImportLog.ImportRecord.Status.NEWLY_IMPORTED).count();
        long alreadyImported = records.stream()
                .filter(r -> r.getStatus() == ImportLog.ImportRecord.Status.ALREADY_IMPORTED).count();
        long skipped = records.stream()
                .filter(r -> r.getStatus() == ImportLog.ImportRecord.Status.SKIPPED_BY_USER).count();

        // Build the status breakdown string
        StringBuilder statusBreakdown = new StringBuilder();
        statusBreakdown.append(newlyImported).append(" newly imported");
        if (alreadyImported > 0) {
            statusBreakdown.append(", ").append(alreadyImported).append(" already imported");
        }
        if (skipped > 0) {
            statusBreakdown.append(", ").append(skipped).append(" skipped");
        }

        // Build the cleared/provisional breakdown (when there is a mix)
        String header;
        if (provisional > 0) {
            header = "IMPORT SUMMARY — " + sessionController.getRegister().getName() +
                    "  (" + records.size() + " transactions: " + cleared + " cleared + " +
                    provisional + " provisional; " + statusBreakdown + ")";
        } else {
            header = "IMPORT SUMMARY — " + sessionController.getRegister().getName() +
                    "  (" + records.size() + " transactions: " + statusBreakdown + ")";
        }

        String divider = "─".repeat(80);
        String doubleDivider = "═".repeat(80);

        view.say(doubleDivider);
        view.say(header);
        view.say(doubleDivider);
        view.say(String.format(" %-4s %-6s %-26s %-12s %s",
                "#", "Date", "Merchant", "Amount", "Budget Item(s) / Memo"));
        view.say(divider);

        boolean inProvisionalSection = false;
        for (int i = 0; i < records.size(); i++) {
            ImportLog.ImportRecord record = records.get(i);
            Transaction txn = record.getTransaction();

            // Print provisional section divider on first provisional transaction
            if (!txn.isCleared() && !inProvisionalSection) {
                view.say("── Provisional " + "─".repeat(65));
                inProvisionalSection = true;
            }

            String num = (i + 1) + (record.isRecategorizedThisSession() ? "*" : "");
            String date = formatDate(txn);
            String merchantName;
            try {
                merchantName = txn.getMerchant() != null ? txn.getMerchant().getName() : "?";
            } catch (Exception e) {
                merchantName = "?";
            }
            String merchant = truncate(merchantName, COL_MERCHANT);
            String amount = formatAmount(txn.getAmount());

            // Build split column
            String splitCol;
            try {
                splitCol = buildSplitColumn(record);
            } catch (Exception e) {
                splitCol = "[error loading splits]";
                logger.debug("Error loading splits for summary: {}", e.getMessage());
            }

            // First line
            view.say(String.format(" %-4s %-6s %-26s %-12s %s", num, date, merchant, amount, splitCol));

            // Continuation lines for multi-split
            try {
                List<TransactionSplit> splits = record.getSplits();
                if (splits != null && splits.size() > 1) {
                    for (int s = 1; s < splits.size(); s++) {
                        String continuation = buildSplitLine(splits.get(s));
                        view.say(String.format(" %-4s %-6s %-26s %-12s %s", "", "", "", "", continuation));
                    }
                }
            } catch (Exception e) {
                logger.debug("Error loading continuation splits: {}", e.getMessage());
            }
        }

        view.say(divider);
    }

    /**
     * Builds the Budget Item(s) / Memo column for the FIRST split line (or a status tag).
     */
    private String buildSplitColumn(ImportLog.ImportRecord record) throws Exception {
        switch (record.getStatus()) {
            case ALREADY_IMPORTED:
                return "[already imported — skipped]";
            case SKIPPED_BY_USER:
                return "[skipped by user]";
            default:
                break;
        }

        List<TransactionSplit> splits = record.getSplits();
        if (splits == null || splits.isEmpty()) {
            return "[no splits assigned]";
        }
        return buildSplitLine(splits.get(0));
    }

    /** Formats a single TransactionSplit as "BudgetItemName ($amount) · memo". */
    private String buildSplitLine(TransactionSplit split) throws Exception {
        BudgetItem budgetItem = split.getBudgetItem();
        String name = budgetItem != null ? budgetItem.getName() : "Unknown";
        String amount = formatDollarAmount(Math.abs(split.getAmount()));
        String memo = split.getMemo() != null && !split.getMemo().isBlank()
                ? " · " + split.getMemo() : "";
        return name + " (" + amount + ")" + memo;
    }

    /** Returns "MM-DD" from the transaction's auth date or post date. */
    private String formatDate(Transaction txn) {
        try {
            String full = (txn.getAuthorizationDate() != null)
                    ? calendarDateToStringDate(txn.getAuthorizationDate())
                    : calendarDateToStringDate(txn.getPostDate());
            // full is MM-DD-YYYY; return MM-DD
            return full.length() >= 5 ? full.substring(0, 5) : full;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Formats amount with sign prefix: "+$1,245.00" or "-$135.62". */
    private String formatAmount(double amount) {
        String formatted = formatDollarAmount(Math.abs(amount));
        return (amount >= 0 ? "+" : "-") + formatted;
    }

    /** Right-truncates a string to maxLen, appending "…" if truncated. */
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 1) + "…";
    }

    // ── E9: Group recategorization ────────────────────────────────────────────

    /**
     * E9: After recategorizing the record at {@code sourceIdx}, scans forward in the list for
     * NEWLY_IMPORTED records that share the same merchant and haven't been recategorized yet.
     * If any are found, asks the user once whether to apply the same categorization to all of them.
     *
     * <p>For a <em>single-split</em> source record the same budget item is copied to each
     * matching record (no further user interaction needed).  For a <em>multi-split</em> source
     * record the user is walked through {@link #recategorize} for each matching record
     * individually.</p>
     */
    private void offerGroupRecategorize(List<ImportLog.ImportRecord> records, int sourceIdx) throws Exception {
        ImportLog.ImportRecord source = records.get(sourceIdx);

        // Resolve the merchant name of the just-recategorized transaction
        String sourceMerchantName = resolveMerchantName(source);
        if (sourceMerchantName == null || sourceMerchantName.equals("?")) {
            return;  // Can't determine merchant — skip group offer
        }

        // Collect indices (0-based) of subsequent NEWLY_IMPORTED, not-yet-recategorized
        // records that share the same merchant
        List<Integer> groupIndices = new ArrayList<>();
        for (int j = sourceIdx + 1; j < records.size(); j++) {
            ImportLog.ImportRecord other = records.get(j);
            if (other.getStatus() != ImportLog.ImportRecord.Status.NEWLY_IMPORTED) continue;
            if (other.isRecategorizedThisSession()) continue;
            if (sourceMerchantName.equals(resolveMerchantName(other))) {
                groupIndices.add(j);
            }
        }

        if (groupIndices.isEmpty()) return;

        // Build a user-friendly description of the row numbers (1-based)
        StringBuilder rowDesc = new StringBuilder();
        for (int k = 0; k < groupIndices.size(); k++) {
            if (k > 0) rowDesc.append(", ");
            rowDesc.append(groupIndices.get(k) + 1);
        }

        // Retrieve the source splits (already refreshed by recategorize())
        List<TransactionSplit> sourceSplits;
        try {
            sourceSplits = source.getSplits();
        } catch (Exception e) {
            logger.debug("E9: could not load source splits: {}", e.getMessage());
            return;
        }
        if (sourceSplits == null || sourceSplits.isEmpty()) return;

        boolean singleSplit = (sourceSplits.size() == 1);
        String prompt;
        if (singleSplit) {
            String biName = "same budget item";
            try {
                BudgetItem bi = sourceSplits.get(0).getBudgetItem();
                if (bi != null) biName = "'" + bi.getDisplayString() + "'";
            } catch (Exception ignored) {}
            prompt = "Row(s) " + rowDesc + " also from '" + sourceMerchantName +
                    "'. Apply " + biName + " to all? (y/n) [n]:";
        } else {
            prompt = "Row(s) " + rowDesc + " also from '" + sourceMerchantName +
                    "'. Recategorize each individually? (y/n) [n]:";
        }

        try {
            String answer = view.getResponseString(prompt, "n", ALLOW_NONE,
                    DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
            if (!answer.equalsIgnoreCase("y")) return;
        } catch (CancelException | SkipException ignored) {
            return;
        }

        for (int idx : groupIndices) {
            ImportLog.ImportRecord groupRecord = records.get(idx);
            if (singleSplit) {
                applyGroupSplit(groupRecord, sourceSplits.get(0));
            } else {
                recategorize(groupRecord);
            }
        }

        printSummary(records);
    }

    /**
     * Copies a single split's budget item to the given record, replacing its existing splits.
     * Scales the amount to the target transaction's amount.
     */
    private void applyGroupSplit(ImportLog.ImportRecord record, TransactionSplit sourceSplit) throws Exception {
        Transaction txn = record.getTransaction();

        // Delete existing splits for this transaction
        String deleteQuery = "DELETE FROM transaction_split WHERE Transaction_idTransaction = uuid_to_bin('"
                + txn.getId() + "')";
        EntityInt.executeUpdate(deleteQuery, "deleting splits for group recategorization");

        // Resolve a BudgetItemMerchant for the target transaction's merchant + source budget item
        BudgetItem bi = sourceSplit.getBudgetItem();
        Merchant merchant = null;
        try { merchant = txn.getMerchant(); } catch (Exception ignored) {}

        BudgetItemMerchant bim = null;
        if (bi != null && merchant != null) {
            try {
                bim = BudgetItemMerchant.getByItemAndMerchant(bi, merchant);
            } catch (Exception ignored) {}
            if (bim == null) {
                bim = new BudgetItemMerchant(merchant, bi);
            }
        }

        if (bim != null) {
            TransactionSplit newSplit = new TransactionSplit(txn.getAmount(), bim, txn, sourceSplit.getMemo());
            newSplit.save();
            view.say("Applied '" + (bi != null ? bi.getDisplayString() : "budget item") +
                    "' to row " + "transaction #" + txn.getImportRecordId() + ".");
        } else {
            logger.warn("E9: could not resolve BudgetItemMerchant for group split — skipping row.");
        }

        record.refreshSplits();
        record.setRecategorizedThisSession(true);
        forecastWasChanged = true;
    }

    /** Returns the merchant name for a record, or {@code null} / {@code "?"} if unavailable. */
    private String resolveMerchantName(ImportLog.ImportRecord record) {
        try {
            Merchant m = record.getTransaction().getMerchant();
            return (m != null) ? m.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }
}







