# Citi Pending Transactions — Design

> **Status:** Proposed on 2026-09-15, revised the same day, and taken up for implementation on
> 2026-09-20. The revision used a second portal sample (pending, posted and payment rows), a check of
> the QFX download, and the decision to attribute charges to the cardholder. The 09-20 pass answered
> the remaining questions (§6) and found §3.7 already built. Implementation is staged: **Phase A**
> imports pending charges, **Phase B** adds cardholder attribution.
>
> **Goal:** Give the Citi AAdvantage Mastercard register the same pending-transaction import that
> Wells Fargo registers already have. A charge then appears in the register and forecast when it is
> authorized, instead of days later when Citi's QFX download includes it. Each charge is attributed to
> the cardholder who made it.

---

## 1. What the samples show

### 1.1 The portal's activity table

This is the *Your Activity* table, time period **Since Sep 11, 2026**, with *Running Balance* shown.
The screenshot shows the same five rows.

```
Time Period
Since Sep 11, 2026
...
Transactions
    Date
    Description
    Name
    Amount
    Running Balance
Pending Total
Pending Purchases
$80.23

Sep 15, 2026
LA FITNESS IRVINE USA
DAVID W HIXON
$80.23
-----
Posted Total
Posted Total
-$662.40

Sep 13, 2026
ADT SECURITY*320925392 BOCA RATON FL
DAVID W HIXON
$53.49
$11,610.12

Sep 12, 2026
ONLINE PAYMENT, THANK YOU
DAVID W HIXON
-$750.00
$11,556.63

Sep 12, 2026
VXNBILL.COM CAMDEN DE
DAVID W HIXON
$9.95
$12,306.63

Sep 11, 2026
Spotify USA New York NY
DAVID W HIXON
$24.16
$12,296.68
```

The same paste also contains a lot that is not activity:
- the credit score (`as of 08/25/2026`) and offers
- `Statement closing Oct 12, 2026` and `Payment due on Oct 08, 2026`
- `Last sign on: Sep. 15, 2026 (4:52 AM ET)`

An earlier sample was filtered to `Sep 14, 2026 - Sep 15, 2026`, had *Running Balance* hidden, and
contained only the pending row.

### 1.2 What the samples establish

| Question | Answer |
|---|---|
| How does a purchase read? | a positive amount, `$80.23` |
| How does a payment or credit read? | a leading minus, `-$750.00` (green on screen) |
| How are pending and posted rows separated? | the headings `Pending Total` and `Posted Total` |
| What follows the amount? | the running balance, if shown: `-----` for pending, a dollar amount for posted |
| How is the time period written? | `Since Sep 11, 2026` or `Sep 14, 2026 - Sep 15, 2026` |
| Who made the charge? | the *Name* column, e.g. `DAVID W HIXON` |
| Does *Current Balance* include pending? | **No.** $11,610.12 is the running balance after the last *posted* row (ADT, 09-13) |
| Does the QFX download include pending? | **No.** Checked 09-15 |
| Does the CSV download include pending? | **No.** Checked 09-23 with two charges pending: every row is `Cleared` (§1.4) |

The balance check: the register held −$11,522.52 after the 09-14 import, which ran through the
09-12 payment. The three purchases posted since then add $53.49 + $9.95 + $24.16 = $87.60.
$11,522.52 + $87.60 = $11,610.12, so the pending $80.23 is not in it.

### 1.3 How the same charge reads once posted

| postDate   | amount  | payee (QFX)            |
|------------|---------|------------------------|
| 2026-08-15 | -80.23  | `LA FITNESS IRVINE CA` |
| 2026-07-15 | -80.23  | `LA FITNESS IRVINE CA` |

- **Sign.** The register stores charges as negative and payments as positive, the opposite of the
  portal.
- **Description.** Pending `… IRVINE USA` becomes posted `… IRVINE CA`, a country replaced by a state.
  The QFX payee is also truncated to about 27 characters (`STATE FARM INSURANCE BLOOMI`); the portal
  shows the full text.
- **No cardholder.** The QFX row carries no name. A posted row learns its cardholder only from the
  pending row it is merged into (§3.9).

### 1.4 Third sample (09-23): two pending charges, and the CSV download

Saved as the test fixture `src/test/resources/citi/pending-activity-20260923.txt`. *Since Sep 11,
2026*, running balance shown. Under `Pending Total` (`Pending Purchases $485.00`):

| Date | Description | Name | Amount |
|---|---|---|---|
| Sep 22, 2026 | `PEACE RIVER ELECTRIC WAUCHULA USA` | `DAVID W HIXON` | `$405.01` |
| Sep 21, 2026 | `Spectrum SAINT LOUIS USA` | `DAVID W HIXON` | `$79.99` |

What it adds:
- **A charge can be listed as pending and posted at once.** Spectrum $79.99 on 09-21 also appears
  under `Posted Total` as `Spectrum SAINT LOUIS MO`. Spectrum bills once a month, so it is one charge,
  and the portal is slow to drop the pending row. The existing cleared-twin guard
  (`TransactionUtilities.findClearedTwinOfProvisional`, added 09-17 for Wells Fargo) already skips a
  pending row whose cleared copy the register holds. Nothing new is needed, but the parser test must
  still return the Spectrum pending row; skipping it is the merge's job, not the parser's.
- **The *Name* column can read `N/A`** (the posted `VISIBLE 8663313527 CO` row). The parser treats
  `N/A` as no cardholder.
- **The CSV download** (`Status,Date,Description,Debit,Credit,Member Name`) lists only `Cleared`
  rows, so the paste stays. It does carry the cardholder for posted rows (`Member Name`, blank for
  VISIBLE), which is noted for Phase B in §3.9.

---

## 2. How it works today

### Wells Fargo (the model to follow)

1. **Configuration.** The register has `provisionalTrxFileName` and `provisionalTrxFileDirectory`
   set, e.g. `BillPayDanni-ProvTrx.tsv` in `Downloads`. The daily update's
   **IMPORT PROVISIONAL TRANSACTIONS** step runs only when a file name is set.
2. **The file.** The user copies the pending rows from the Wells Fargo site into that file. Each line
   is tab-separated: an optional `Transaction Details for Row N` prefix, the date `09/14/26`, the
   description, then a deposit or a withdrawal column.
3. **Reading.** `ImportController.importCsvProvisionalTransactionFile` reads the file **one line at a
   time**. For each line it calls `financialInstitution.loadProvisionalTransactionFromCSV(line, register)`.
4. **Merging.** Each row gets an import record id of `P` + post date + counter. It is then merged
   against the register's uncleared rows by **payee + amount**:
   - new rows are categorized and reconciled;
   - rows already in the register are skipped;
   - uncleared register rows that are missing from the file and more than one business day old are
     offered for deletion ("fallen off").
5. **When the charge posts.** The cleared import's Phase 2 calls `getMatchingProvisionalTransaction`,
   which is `TransactionUtilities.findMatchingProvisionalTransaction`. It looks for an **exact amount
   within ±5 days**, with a fuzzy payee tie-break, then falls back to a **tip tolerance** of up to 30%.
   On a match, `FinancialInstitution.reconcileProvisionalTransaction` moves the pending row's id,
   merchant and splits onto the cleared row, and adjusts the balance and first split by any tip.

### Citi (what is missing)

| Piece | Today |
|---|---|
| Register `Citi AAdvantage Mastercard` | `provisionalTrxFileName` is empty, so the step is skipped |
| `CitiBank.loadProvisionalTransactionFromCSV` | throws `UnsupportedOperationException` |
| `CitiBank.getMatchingProvisionalTransaction` | returns `null` |
| Pending text format | multi-line blocks, not one row per line |
| Date format | `Sep 15, 2026`; the provisional `Transaction` constructor parses only slash dates |
| `CitiBank.normalizeCitiPayee` | drops a trailing *state* and city, but leaves a trailing `USA` alone |

The matching and reconciliation in step 5 are already bank-neutral: they live in
`TransactionUtilities` and the `FinancialInstitution` base class. So Citi needs a reader, a payee rule
and the switch turned on, not a new matching engine.

### Users (what attribution has to build on)

- **Transactions have no user.** The `transaction` table has no user column (`user_description` is a
  memo).
- **Merchants have one user** (`merchant.user_idUser`, shown as "use automatically, Danielle").
- **Registers have owners** in `user_register`. The Citi register has **none**.
- **`extractUsers(payee)`** is used only to find which register a transfer belongs to. It is not used
  to attribute charges.
- **The New Transaction Summary** is rendered once per user in `User.getAllUsers()`, but every user
  gets the same list: the register's new transactions. Nothing is filtered by person.

The users are Justin Hixon, Danielle Hixon, Christian Rybicki and David Hixon.

---

## 3. Design

### 3.1 Input file

- **File:** `CitiAAdvantage-PendingTrx.txt` in `C:\Users\dwhix\Downloads`, set on the register.
- **Filling it.** In the Citi portal, open *Your Activity* and choose a time period that reaches back
  to the last daily update; *Since* the last statement is simplest. Select and copy.
  - **Pasting the whole page is fine.** The reader ignores everything that is not a transaction row.
  - *Running Balance* may be shown or hidden.
- **Why not a download.** Neither the QFX download (checked 09-15) nor the CSV download (checked 09-23,
  §1.4) includes pending charges.
- **File type.** `ImportController.importProvisionalTransactionFile` accepts only `csv` and `tsv`
  today. Add `txt`.

### 3.2 Reading records instead of lines

A Citi transaction spans four or five lines, so the per-line hook cannot parse it. Add a record-level
hook to `FinancialInstitutionInt`:

```java
/** Every pending transaction in a pending-transactions file, in file order, and the dates the file covers. */
ProvisionalFileContents loadProvisionalTransactions(List<String> lines, Register register) throws Exception;
```

- **Default (in `FinancialInstitution`).** Loop over the lines calling the existing
  `loadProvisionalTransactionFromCSV`, and skip a line that throws `ParseException`, exactly as
  today. The covered range defaults to "unbounded". **Wells Fargo behaviour does not change.**
- **`CitiBank` override.** Delegates to a new pure class, `CitiPendingActivityParser`. It does no I/O,
  so it can be unit-tested directly.
- **Caller.** `importCsvProvisionalTransactionFile` reads all lines, calls the hook once, then assigns
  import record ids and runs the existing merge unchanged. That includes the free-id guard added on
  09-14.

### 3.3 `CitiPendingActivityParser`

**Before scanning:**
- Trim every line and discard blank ones.
- Split any line that contains tabs. A browser sometimes copies a table row as a single line:
  `Sep 15, 2026<TAB>LA FITNESS IRVINE USA<TAB>DAVID W HIXON<TAB>$80.23<TAB>-----`.

**Scanning** is a small state machine. Every pattern must match the whole line:

| Line | Pattern | Action |
|---|---|---|
| Time period | `Since MMM d, yyyy` or `MMM d, yyyy - MMM d, yyyy` | record the covered range (a *Since* range ends today) |
| Section heading | `Pending Total` / `Posted Total` | set the current section |
| Transaction date | `MMM d, yyyy` | start a record |
| Amount, inside a record | `-?\$[\d,]+\.\d{2}` | the record's amount; the record is complete |
| Running balance | `-----`, or a dollar amount right after a record's amount | ignore |
| Anything else, inside a record | — | first such line is the description, second the cardholder name |

**Rules:**
- **Whole-line matches only.** None of these starts a record:
  - `Statement closing Oct 12, 2026`
  - `Payment due on Oct 08, 2026`
  - `Last sign on: Sep. 15, 2026 (4:52 AM ET)`
  - `as of 08/25/2026`
- **Totals and balances are never records,** because no transaction date precedes them:
  - `Pending Purchases $80.23`
  - `Posted Total -$662.40`
  - `$11,610.12` after *Current Balance*
  - a row's running balance, which follows its amount
- **A record needs a date, a description and an amount.** The cardholder name is optional, and `N/A`
  counts as none. A date with
  no amount within four lines is dropped and logged at debug level.
- **Sign.** A portal purchase `$80.23` becomes `-80.23`. A portal credit `-$750.00` becomes `+750.00`.
- **Section.** Only records under `Pending Total` are returned. Rows under `Posted Total` belong to the
  QFX import. A page with no headings at all is treated as pending; the merge and duplicate checks
  catch anything already posted.
- **Dates.** The transaction date becomes both `postDate` and `authorizationDate`. Add a `Transaction`
  constructor that takes a `Calendar`; the slash-date constructor stays untouched.
- **Cardholder.** The raw name (`DAVID W HIXON`) is kept on the parsed transaction and resolved to a
  user as described in §3.9.

**For sample §1.1** the parser returns exactly one transaction:
`2026-09-15, -80.23, "LA FITNESS IRVINE USA", cardholder "DAVID W HIXON"`, with a covered range of
`2026-09-11 .. today`. It skips the four posted rows, including the `-$750.00` payment.

### 3.4 Payee normalization

Extend `CitiBank.normalizeCitiPayee` with one rule, applied **before** the state rule:
- If the **last** token is a country code (`USA`, `US`), drop it together with the city before it. A
  multi-word city is recognized as the state rule recognizes it, but looked up in any state (the
  country names none), else one token is dropped. *(Built 09-20; the multi-word lookup added 09-23.)*
- Keep the existing guard that at least one merchant token always remains.

| Raw | Today | Proposed |
|---|---|---|
| `LA FITNESS IRVINE USA` (pending) | `LA FITNESS IRVINE USA` | `LA FITNESS` |
| `LA FITNESS IRVINE CA` (posted) | `LA FITNESS` | `LA FITNESS` (unchanged) |
| `Spotify USA New York NY` | state rule applies | unchanged: `USA` is not the last token |
| `NETFLIX USA` | `NETFLIX USA` | unchanged: stripping would leave no merchant token |
| `Netflix.com Los Gatos USA` | `Netflix Los Gatos USA` | `Netflix`, the same as the posted `… Los Gatos CA` |
| `Spectrum SAINT LOUIS USA` | unchanged | `Spectrum SAINT`, the same as the posted `… SAINT LOUIS MO` |

The `cities` table spells the city `St. Louis`, so `SAINT LOUIS` is not recognized in either form.
The two still agree, which is what matching needs. Teaching the lookup `SAINT` = `ST.` is out of scope.

Pending and posted rows then resolve to the same merchant payee. That matters for the payee→merchant
mapping and for the fuzzy tie-break when a pending row is matched to its posted row.

### 3.5 Pending → posted

`CitiBank.getMatchingProvisionalTransaction` does what Wells Fargo does: it delegates to
`TransactionUtilities.findMatchingProvisionalTransaction(register, amount, date, merchantPayee)`.
**Built 09-23:** both banks now call `FinancialInstitution.findPendingRowFor`, which is that delegation.
`reconcileProvisionalTransaction` is inherited, plus the cardholder carry-over in §3.9.

- **Common case.** A subscription or retail charge matches on exact amount within ±5 days
  (`-80.23` = `-80.23`). Pending charges usually post one to three days later.
- **Restaurants.** The tip-tolerance phase covers a posted amount up to 30% above the hold. The tip
  goes to the balance and the first split, as for Wells Fargo.
- **A hold that never posts** (hotel, gas pump, car rental) disappears from the portal. The fallen-off
  branch offers it for deletion, limited as in §3.6.
- **Order of steps.** The daily update imports cleared transactions before provisional ones. A charge
  that posted overnight is merged into its pending row first, and the provisional import then sees it
  as cleared rather than re-adding it.

### 3.6 Only treat a row as "fallen off" within the dates the paste covers

The first sample was filtered to **Sep 14 – Sep 15**. Today's fallen-off rule is "an uncleared
register row not in the file". Under that rule, every pending Citi row dated before Sep 14 would be
offered for deletion, even though the paste simply did not reach back that far.

**Rule:** a register row is a fall-off candidate only when its date is inside the covered range. The
range comes from:
1. the parsed time period, when there is one:
   - `Since Sep 11, 2026` → 09-11 .. today
   - `Sep 14, 2026 - Sep 15, 2026` → 09-14 .. 09-15
2. otherwise, the earliest through the latest transaction date in the file.

This is a pure helper in the shared merge, `isWithinCoveredRange(Calendar date, DateRange range)`.
Wells Fargo's range is unbounded, so its behaviour is unchanged.

**Built 09-23** as `ImportController.mayHaveFallenOff(rowDate, today, coveredRange)`, which also holds
the existing "more than one business day old" rule. Two additions settled while building it:
- **A paste that covers nothing lets nothing fall off.** With no rows and no time period (a paste of the
  wrong page), the range is empty rather than unbounded.
- **A paste with a time period and no pending rows still runs the check.** Every pending register row in
  those dates has posted or been withdrawn. The import says so ("No provisional transactions were found
  in the file, which covers ...") and offers the old rows for deletion as usual. A Wells Fargo file with
  nothing in it still does nothing, as before.

### 3.7 Register balance verification

**Problem.** Pending rows are added to the register balance when imported, but the bank's balance
counts only posted activity. The sample confirms this for Citi (§1.2): *Current Balance* $11,610.12
excludes the pending $80.23. **VERIFY REGISTER BALANCE** would therefore report every pending charge
as a discrepancy. Wells Fargo already shows the same effect: on 09-14-2026 Bill Pay Danni was reported
"off by $3,456.51", exactly the pending McConnaughhay deposit.

**Proposed:** compare the downloaded balance against *register balance minus the total of uncleared
rows*, and say so ("excluding $80.23 pending"). This helps both banks.

**To confirm during implementation:**
- Read the verify-balance code.
- Check that Citi's QFX `LEDGERBAL` equals the portal's posted-only *Current Balance*. The register
  matched it at −$11,522.52 after the 09-14 import.

### 3.8 Configuration

Set once, through Manage Data or a one-line `UPDATE register`:

| Field | Value |
|---|---|
| `provisionalTrxFileName` | `CitiAAdvantage-PendingTrx.txt` |
| `provisionalTrxFileDirectory` | `C:\Users\dwhix\Downloads` |

After a successful import the file is versioned and cleared, as for Wells Fargo.

**Applied 09-23** directly against `ForecastDatabase` (the scripts were temporary and are not kept):

```sql
UPDATE register
   SET provisionalTrxFileName      = 'CitiAAdvantage-PendingTrx.txt',
       provisionalTrxFileDirectory = trxImportFileDirectory
 WHERE idRegister = uuid_to_bin('6781379b-7dbb-4f27-a6ee-d4c83e4f1f0f');
```

To switch the import off, set both columns back to `''`, which is what they held before. The directory
is copied from the register's QFX directory rather than spelled out: a Windows path in a MySQL string
needs every backslash doubled, and a first run with single backslashes stored `C:UsersdwhixDownloads`
(corrected in the same session).
An empty `CitiAAdvantage-PendingTrx.txt` was created in Downloads so the daily update does not stop to
report a missing file; an empty file imports nothing and lets nothing fall off.

### 3.9 Cardholder attribution

**Decision (09-15):** each Citi charge is attributed to the cardholder in the portal's *Name* column.

**Storage.** Add a nullable column `transaction.Cardholder_idUser` (`binary(16)`, foreign key to
`user.idUser`, `ON DELETE SET NULL`), with `Transaction.getIdCardholder()` / `setIdCardholder()`.
- It is a separate column, not `user_description`, which holds memos.
- It is not the merchant's user, which describes a merchant rather than who made a particular charge.

**Resolving the name to a user** — `CitiBank.resolveCardholder(String name)`, pure given the user list:
1. Uppercase the name and split it into tokens (`DAVID W HIXON` → `DAVID`, `W`, `HIXON`).
2. Match a user whose `firstName` equals the first token **and** whose `lastName` equals the last token.
   `DAVID … HIXON` → David Hixon.
3. Otherwise, match on first name alone, but only if exactly one user has it.
4. Otherwise, attribute no one. Say once per import:
   `No user matches cardholder "<name>"; charges left unattributed.`

With today's users, `DAVID W HIXON` → David and `DANIELLE M HIXON` → Danielle. No first names collide.

`CitiBank.extractUsers(payee)` keeps its current meaning (transfer register resolution) and stays
empty. Attribution has its own method so the two uses do not share one ambiguous hook.

**Carrying it through:**
- **Pending import.** The parsed cardholder is resolved and saved with the new pending row.
- **Posting.** `reconcileProvisionalTransaction` copies `idCardholder` from the pending row to the
  cleared row, alongside the id, merchant and splits it already copies.
- **Charges never seen pending.** A charge that posts before any paste includes it arrives from QFX
  with no name, and stays unattributed.
  - *Optional, phase 2:* the paste's `Posted Total` rows do carry names, and so does the CSV
    download's `Member Name` column (§1.4). The import could backfill
    `Cardholder_idUser` on cleared register rows with the same date and amount and a matching
    normalized payee, without importing anything.

**Where it shows:**
- **Import summary.** In REVIEW IMPORTED TRANSACTIONS, the merchant column gains the first name:
  `LA FITNESS (David)`.
- **New Transaction Summary.** Each row shows the cardholder's first name.
- **Reports are not filtered by person** (decided 09-15). Every user keeps receiving the full list of
  new transactions, as today. The cardholder name is a label on each row, not a filter.

**Migration** (DDL, applied on its own, never inside a dry-run transaction):

```sql
ALTER TABLE transaction
  ADD COLUMN Cardholder_idUser BINARY(16) NULL,
  ADD CONSTRAINT fk_Transaction_Cardholder FOREIGN KEY (Cardholder_idUser)
      REFERENCES user (idUser) ON DELETE SET NULL;
```

`Transaction`'s select, insert, update and upsert queries gain the column. Existing rows stay `NULL`.

---

## 4. Changes by file

| File | Change |
|---|---|
| `FinancialInstitutionInt` / `FinancialInstitution` | add `loadProvisionalTransactions` (the default loops lines) and `ProvisionalFileContents`; copy `idCardholder` in `reconcileProvisionalTransaction`. **Hook built 09-23**, as a `default` on the interface so `GenericClassifer` needs nothing; the cardholder copy is Phase B. |
| `CitiBank` | override `loadProvisionalTransactions` and `getMatchingProvisionalTransaction`; country rule in `normalizeCitiPayee`; `resolveCardholder`. **Built 09-23:** the country rule, `loadProvisionalTransactions` and `getMatchingProvisionalTransaction` (shared with Wells Fargo as `FinancialInstitution.findPendingRowFor`). |
| `CitiPendingActivityParser` (new) | the pure block parser (§3.3). **Built 09-23.** |
| `DateRange` (new) | the covered range, with `contains(Calendar)` for §3.6. **Built 09-23.** |
| `Transaction` | provisional constructor taking a `Calendar`; `idCardholder` field and the column in every query. **Built 09-23:** the constructor (the slash-date one now delegates to it). |
| `add_transaction_cardholder_column.sql` (new) | the migration in §3.9, plus a rollback script |
| `ImportController` | accept `.txt`; read all lines and call the hook once; resolve cardholders; restrict fall-off to the covered range (§3.6). **Built 09-23:** everything but the cardholders (Phase B). |
| `ImportSummaryController`, `NewTransactionSummaryReport` | show the cardholder's first name |
| Verify-balance step | exclude uncleared rows from the comparison (§3.7) |
| `TRANSACTION_IMPORT_ALGORITHM.md` | document the record hook, the covered-range rule and cardholder carry-over. **Built 09-24:** §4A and Phase 2 cover the hook, the covered range and pending/posted matching; the cardholder carry-over waits for Phase B. |

---

## 5. Tests

- **`CitiPendingActivityParserTest`** (new; fixtures are the three real samples)
  - The 09-23 fixture gives exactly two records, `PEACE RIVER ELECTRIC WAUCHULA USA` -405.01 (09-22)
    and `Spectrum SAINT LOUIS USA` -79.99 (09-21), range from 09-11. The posted Spectrum twin and the
    `N/A` row are skipped.
  - The §1.1 paste gives exactly one record: `-80.23`, 2026-09-15, `LA FITNESS IRVINE USA`,
    `DAVID W HIXON`, range from 09-11.
  - The 09-14..09-15 paste, with running balance hidden, gives the same record, range 09-14..09-15.
  - Posted rows are skipped, including the `-$750.00` payment and `Spotify USA New York NY`.
  - Dates in page noise never start a record: statement closing, payment due, last sign-on, and the
    credit-score date.
  - Totals and running balances are never records: `$80.23`, `-$662.40`, `$11,610.12`, `-----`.
  - A tab-separated table copy parses the same as the multi-line copy.
  - Several pending records, including two identical charges on one day.
  - A pending credit `-$25.00` becomes `+25.00`.
  - A date with no amount within four lines is dropped.
- **`CitiBankPayeeNormalizationTest`** (extend): the trailing-country rule, the mid-string `USA` case,
  and the single-token guard.
- **`CitiCardholderResolutionTest`** (new)
  - `DAVID W HIXON` → David, and `DANIELLE M HIXON` → Danielle.
  - A first name alone resolves only when it is unique.
  - An unknown name, or an ambiguous first name, resolves to no one.
  - Case and spacing do not matter.
- **Cardholder carry-over:** `reconcileProvisionalTransaction` copies `idCardholder` to the cleared row.
- **Fall-off range:** a register row outside the covered range is never a candidate; one inside it is.
- **Wells Fargo regression:** the default hook produces the same transactions as today's per-line loop
  for `BillPayDave-ProvTrx_old.tsv`-shaped input.
- **Balance verification:** the reported difference excludes uncleared rows.

---

## 6. Open questions

**Answered on 09-15:**
- *How is a pending credit shown?* With a leading minus, `-$750.00`, the same as posted credits. A
  **pending** credit has not been seen yet, but the portal formats both sections the same way.
- *What separates posted rows?* The `Posted Total` heading.
- *Does the bank's balance include pending?* Not the portal's *Current Balance*. QFX is to be
  confirmed (§3.7).
- *Does the download include pending rows?* Not in QFX format. Other formats are untried.
- *Should charges be attributed to the cardholder?* Yes (§3.9).
- *Should reports be filtered by person?* No. Everyone keeps getting the full list, labeled with the
  cardholder (§3.9).

**Answered on 09-20, against the code and the live database:**

- *Is the verify-balance change in §3.7 still needed?* **No — it is already built.**
  `FinancialInstitution.getImportedLedgerBalance()` adds the register's uncleared total to the bank's
  figure, and `RegisterController.downloadedBalanceLine()` says so in words ("plus $X of pending
  transactions the bank has not counted yet"). Pending Citi charges will not be reported as a
  discrepancy. §4's row for the verify-balance step is therefore obsolete.
- *Does Citi's QFX `LEDGERBAL` equal the portal's posted-only Current Balance?* **Yes.**
  `qdl20260914_old.QFX` carries `BALAMT -11690.35` as of 09-16; the portal's 09-15 posted balance was
  $11,610.12, and $11,610.12 + $80.23 (the LA Fitness pending charge, posted by the 16th) =
  $11,690.35. `LEDGERBAL` counts posted activity only, so the adjustment above is the right one.
- *Is the optional cardholder backfill from `Posted Total` rows in scope?* **No.** A charge that posts
  before any paste sees it stays unattributed, as §7 has it.
- *How are the migration and the register configuration applied?* Directly against
  `ForecastDatabase`. The scripts are temporary and not committed; the doc records what was run (§3.8).
- *One change or several?* **Staged.** Phase A is the import (parser, record hook, payee country
  rule, covered-range fall-off, `.txt`, configuration); Phase B is attribution (the column, the
  carry-over, and the cardholder's name in the two reports).

**Answered on 09-23:**
- *Does the CSV download include pending rows?* **No** (§1.4). The paste is the input; nothing is
  still open.

---

## 7. Out of scope

- Fetching pending transactions automatically — see `PLAID_INTEGRATION_DESIGN.md`.
- Importing posted rows from the paste; the QFX import owns them. The optional cardholder backfill in
  §3.9 only labels rows that are already there.
- Pending transactions for other QFX-only institutions (Barclays).
