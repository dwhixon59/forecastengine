# Design: Import Summary with In-Session Recategorization

## Executive Summary

After each import the user currently sees individual transaction messages scroll by but has
no consolidated view of what was processed. This design adds a dedicated
**REVIEW IMPORTED TRANSACTIONS** step in the daily-update workflow, placed immediately
after "IMPORT PROVISIONAL TRANSACTIONS". The step shows a single summary covering
**both cleared and provisional transactions** from the current session, followed by an
optional **recategorize** loop that lets the user fix any mis-categorized transaction
before the forecast is rendered — without leaving the daily-update workflow.

---

## User Experience

### 1. Summary Screen (shown as a separate daily-update step after IMPORT PROVISIONAL TRANSACTIONS)

The daily-update step sequence becomes:

```
IMPORT CLEARED TRANSACTIONS
IMPORT PROVISIONAL TRANSACTIONS
REVIEW IMPORTED TRANSACTIONS        ← new step
VERIFY REGISTER BALANCE
RENDER THE LONG TERM FORECAST
…
```

The summary covers all transactions logged during both import steps in the current session:

```
REVIEW IMPORTED TRANSACTIONS
────────────────────────────────────────────────────────────────────────────────
IMPORT SUMMARY — Bill Pay Dave  (21 transactions: 19 cleared + 2 provisional, 5/27/2026)
════════════════════════════════════════════════════════════════════════════════
 #   Date       Merchant                   Amount      Budget Item(s) / Memo
───  ─────────  ─────────────────────────  ──────────  ──────────────────────────────────────
 1   05-19      Bill Pay Danni             +$60.00     [already imported — skipped]
 2   05-19      CVS Pharmacy              -$31.79     [already imported — skipped]
 3   05-19      SimonMed Imaging          -$265.98    [already imported — skipped]
 4   05-19      AAdvantage Credit Card  -$2,000.00   [already imported — skipped]
 5   05-19      Joint Savings Account       -$3.00     [already imported — skipped]
 6   05-20      Bill Pay Danni            +$133.00     Other ($133.00) · Cover overdrafts
 7   05-20      Banfield Pet Hospital      -$94.95     [splits already set — not re-assigned]
 8   05-20      Protective Life Insurance -$135.62     Life insurance – David ($135.62)
 9   05-21      Bill Pay Danni            +$250.00     Reimbursement ($250.00)
10   05-22      Christine                +$1,245.00    Other ($1,245.00) · Wrong account
11   05-22      Dave                      -$125.00     Dave's work expenses ($125.00)
12   05-22      Bill Pay Danni             -$49.00     Reimbursement ($49.00)
13   05-22      Bill Pay Danni          -$1,245.00    Other ($1,245.00) · Wrong account
14   05-26      Christine                  +$65.00     Other ($65.00) · Wrong account
15   05-26      Christine                  +$40.00     Other ($40.00) · Wrong account
16   05-26      Klarna                     -$16.11     Other ($16.11)
17   05-26      SunPass                    -$10.00     Tolls ($10.00)
18   05-26      Amazon Prime Credit Card   -$16.04     Amazon Prime Credit Card ($16.04)
19   05-26      Joint Savings Account       -$2.00     Savings ($2.00)
── Provisional ─────────────────────────────────────────────────────────────────
20   06-01      Electric Bill             -$185.00     Electric Bill ($185.00)
21   06-15      Mortgage                -$1,950.00    Mortgage ($1,950.00)
────────────────────────────────────────────────────────────────────────────────
Recategorize a transaction? Enter number (or press Enter to continue):
```

**Column rules:**
| Column | Content |
|--------|---------|
| `#` | 1-based sequence; same order as processed |
| `Date` | Transaction post date (MM-DD) |
| `Merchant` | `merchant.getName()` |
| `Amount` | Positive = credit/deposit (+), negative = debit (-) |
| `Budget Item(s) / Memo` | One line per split: `<BudgetItem.name> ($<amount>)[ · <memo>]`; or status tag for skipped/pre-assigned |

**Status tags** (shown instead of budget items when not applicable):
- `[already imported — skipped]` — duplicate import record, no splits assigned this session
- `[splits already set — not re-assigned]` — transaction had prior splits; no change made
- `[skipped by user]` — user pressed **s** during the import; no splits assigned

### 2. Recategorization Flow

User types a number and presses **Enter**:

```
Recategorizing: 05-22 Christine  +$1,245.00

Current splits:
  1.  Other  $1,245.00  · Wrong account

Delete current splits and re-assign? (y/n) [y]:
```

If confirmed:
- Existing `TransactionSplit` rows for this transaction are deleted.
- Any forecast transaction created solely for this split (i.e., a new on-demand forecast
  transaction with `remaining = 0`) is also deleted.
- The standard split-assignment flow runs interactively
  (`TransactionSplitsController.getSplits()`).
- After splits are saved, the forecast is reconciled for this transaction only.

After recategorization the summary is **re-printed with the updated row** highlighted:

```
 10 *  05-22  Christine  +$1,245.00   Short term borrowing ($1,245.00)   ← updated
```

The user may then recategorize another transaction, or press Enter to continue.

### 3. Cancellation / Quit

- Pressing **Enter** with no input exits the loop and proceeds to the next daily-update step.
- Typing `q` quits the entire daily update (consistent with the rest of the app).
- Typing `c` is an alias for Enter (continue).

---

## Data Model Changes

### `ImportLog.java` — extend `ImportRecord` inner class

Currently `ImportLog` stores a `List<Transaction>` and prints status messages.  Extend it
to hold a structured record per transaction so the summary can be rendered at any time.

```java
// New inner record class inside ImportLog
public static class ImportRecord {
    public enum Status {
        NEWLY_IMPORTED,       // processed and splits assigned this session
        ALREADY_IMPORTED,     // duplicate — skipped, no change
        SPLITS_PREEXISTING,   // imported record existed; splits were already set
        SKIPPED_BY_USER       // user pressed 's' during split assignment
    }

    private final Transaction transaction;
    private final Status status;
    private List<TransactionSplit> splits;   // snapshot at the time of import
    private boolean recategorizedThisSession;

    // … constructor, getters, plus:
    public void refreshSplits() throws Exception {
        this.splits = TransactionSplit.getSplitsForTransaction(transaction);
    }
}
```

The existing `List<Transaction>` field becomes `List<ImportRecord> importRecords`.
`logImportEvent()` receives a `Status` argument and constructs an `ImportRecord`.

---

## Controller Changes

### New class: `ImportSummaryController.java`

Responsible for rendering the summary and driving the recategorize loop.
Lives in `com.hixon.financialApp.controller`.

```java
public class ImportSummaryController {

    public ImportSummaryController(SessionController sessionController,
                                   ImportLog importLog) { … }

    /**
     * Show the summary table and run the optional recategorize loop.
     * Returns true if the forecast was changed (so the caller can re-render).
     */
    public boolean showSummaryAndRecategorize() throws Exception { … }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void printSummary(List<ImportLog.ImportRecord> records) { … }

    private void recategorize(ImportLog.ImportRecord record) throws Exception { … }
}
```

#### `showSummaryAndRecategorize()` algorithm

```
1. Print the summary table (all ImportRecords).
2. Loop:
   a. Prompt: "Recategorize a transaction? Enter number (or Enter/c to continue, q to quit):"
   b. If blank / 'c'  → break loop.
   c. If 'q'          → throw QuitException.
   d. If integer n in [1..N] and record is NEWLY_IMPORTED or SPLITS_PREEXISTING:
        call recategorize(records.get(n-1)).
        re-print the updated summary.
   e. If integer n in [1..N] and record is ALREADY_IMPORTED or SKIPPED_BY_USER:
        view.say("This transaction cannot be recategorized here. Use Manage Data → Reprocess transaction instead.")
   f. Else: view.say("Invalid selection.")
3. Return forecastWasChanged.
```

#### `recategorize(ImportLog.ImportRecord record)` algorithm

**This method delegates entirely to the existing `TransactionController.recategorizeTransaction()`**,
which is the same code path used by Manage Data → Recategorize.  No split-deletion, forecast-reversal,
or DB-transaction logic is duplicated here.

`TransactionController.recategorizeTransaction()` already handles:
- Displaying current splits
- Prompting "Delete current splits and re-assign?"
- Deleting all `TransactionSplit` rows inside a DB transaction
- Running `reconcileTransaction()` to assign new splits interactively
- Reversing any forecast deductions from the old splits
- Rolling back atomically on cancel or error

```
1. Call transactionController.recategorizeTransaction(record.getTransaction()).
     → shows current splits, prompts for confirmation, deletes old splits,
       runs interactive split assignment, reconciles forecast — all in one call.
2. record.refreshSplits()               // update the in-memory snapshot
3. record.recategorizedThisSession = true
4. forecastWasChanged = true
```

`TransactionController` is obtained from `SessionController` (already available to
`ImportSummaryController`), so no new infrastructure is needed.

### Changes to `ImportLog.java`

1. Replace `List<Transaction>` with `List<ImportRecord>`.
2. Update `logImportEvent(Transaction t, boolean isNewTransaction)` →
   `logImportEvent(Transaction t, ImportRecord.Status status)`.
3. Add `getImportRecords()` getter.
4. Existing callers of `logImportEvent` updated to pass the correct `Status`.

### Changes to `ImportController.java`

`ImportController` already has an `importLog` field.  The only change needed is:

1. Add a `getImportLog()` getter so `DailyUpdateController` can retrieve the log after
   both import steps are complete.
2. Update `logImportEvent(Transaction t, boolean isNewTransaction)` →
   `logImportEvent(Transaction t, ImportRecord.Status status)` call sites to pass the
   correct `Status` enum value.

`ImportController` does **not** call `ImportSummaryController` — that is driven by
`DailyUpdateController`.

### Changes to `DailyUpdateController.java`

Add a new daily-update step **after** `importProvisionalTransactions()`:

```java
// ── REVIEW IMPORTED TRANSACTIONS ──────────────────────────────────────────
view.sayH3("REVIEW IMPORTED TRANSACTIONS");
ImportSummaryController summaryController =
        new ImportSummaryController(sessionController, importController.getImportLog());
boolean forecastChanged = summaryController.showSummaryAndRecategorize();
if (forecastChanged) {
    forecastController.updateForecast();
}
```

The `importLog` accumulates records from **both** `importRegisterTransactionFile()` and
`importProvisionalTransactions()` because both methods call `importLog.logImportEvent()`.
`DailyUpdateController` reads the combined log once after both steps finish.

---

## Files to Create / Modify

| Action | File | Notes |
|--------|------|-------|
| **Create** | `ImportSummaryController.java` | Renders summary table; drives recategorize loop; delegates to `TransactionController.recategorizeTransaction()` |
| **Modify** | `ImportLog.java` | Add `ImportRecord` inner class; update `logImportEvent` signature; add `getImportRecords()` getter |
| **Modify** | `ImportController.java` | Add `getImportLog()` getter; update `logImportEvent` call sites to pass `Status` enum |
| **Modify** | `DailyUpdateController.java` | Add new "REVIEW IMPORTED TRANSACTIONS" step after provisional import; call `ImportSummaryController`; gate forecast update on `forecastChanged` |
| **No change** | `TransactionSplitsController.java` | Split deletion already handled inside `TransactionController.recategorizeTransaction()` |
| **No change** | `ForecastController.java` | Forecast reconciliation already handled inside `TransactionController.reconcileTransaction()` |

---

## Edge Cases and Design Decisions

| Scenario | Handling |
|----------|----------|
| Transaction has a **multi-way split** (e.g., $500 split across 3 budget items) | All splits shown on separate lines in the summary; all are deleted together on recategorize |
| Transaction was **auto-matched to an existing forecast transaction** | The deduction from the forecast transaction is reversed before re-running splits; the reconciliation step re-applies it |
| Transaction was **skipped by user** during import | Status shown as `[skipped by user]`; selecting it shows prompt to use "Manage Data → Reprocess" instead |
| Transaction is a **transfer** (e.g., to Bill Pay Danni) | Can still be recategorized normally; same flow |
| User recategorizes the same transaction **twice** | Allowed; each recategorize removes current splits and starts fresh |
| After recategorize, budget items were **changed** (inSync = false) | `forecastWasChanged = true`; Import Controller calls `forecastController.updateForecast()` before returning |
| **Quit** exception during recategorize | Propagated up; DailyUpdateController catches it as normal |
| Import file had **0 new transactions** (all skipped) | Summary is still shown; all rows have `[already imported]` status; user is told no recategorization is available |

---

## Display Format Details

### Amount formatting

- Credits: `+$1,245.00` (green in color-capable terminals, plain `+` prefix otherwise)
- Debits: `-$135.62`

### Merchant column width

Fixed at 26 characters, right-truncated with `…` if longer.

### Split lines for multi-split transactions

```
 6   05-20  Bill Pay Danni  +$133.00   Other ($133.00) · Cover overdrafts
10   05-22  Christine      +$1,245.00  Short term borrowing ($800.00)
                                       Other ($445.00) · Reimbursement portion
```
(Continuation rows have blank `#`, `Date`, `Merchant`, `Amount` columns.)

### Re-printed updated row

After recategorization a `*` appears in the `#` column:
```
 6 *  05-20  Bill Pay Danni  +$133.00  Household expenses ($133.00)
```

---

## Out of Scope

- Changing the **merchant** of a transaction (use Manage Data for that).
- Changing the **date or amount** (those come from the bank).
- Recategorizing **already-imported** (duplicate) transactions (use Manage Data).
- Persisting the summary to a file (log already exists in `logs/app.log`).

---

## Decisions Made

1. **Placement:** ✅ Separate daily-update step after "IMPORT PROVISIONAL TRANSACTIONS",
   before "VERIFY REGISTER BALANCE".

2. **Provisional transactions:** ✅ Yes — the summary covers both cleared and provisional
   transactions in one combined list because both import steps write to the same `ImportLog`.

3. **Auto-forecast update:** ✅ If any recategorization changes the forecast,
   `forecastController.updateForecast()` is called immediately in the review step to keep
   the forecast data consistent. The "RENDER THE LONG TERM FORECAST" step later in the
   daily-update run still executes normally — updating and rendering are separate operations
   and the render step is never skipped.

4. **Scroll length:** ✅ No pagination for now. The summary will be printed in full and
   left to the terminal's scroll buffer. Pagination can be added later if testing shows it
   is needed for optimal user experience.


