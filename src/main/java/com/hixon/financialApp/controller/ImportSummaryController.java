package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hixon.financialApp.utility.Utility.calendarDateToStringDate;
import static com.hixon.financialApp.utility.Utility.formatDollarAmount;

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
}







