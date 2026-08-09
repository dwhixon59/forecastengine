# Update Forecast From External Source — Test Case Design

> **Status:** DRAFT for review. Nothing here is implemented yet.
> Once we agree on the scope below, I'll implement the tests + bug fixes.

## 1. Feature overview

The **"update from external source"** feature lets the user edit the forecast in a
spreadsheet (Excel `.xlsx`/`.xls` — preferred — or CSV/TSV) and then re-import their
edits back into the database.

Entry point: `MainController` goal `updateFromExternalSource` →
`ForecastController.updateFromExternalSource()`.

The flow has **two distinct layers**, and bugs can live in either:

| Layer | Class / method | Responsibility |
| ----- | -------------- | -------------- |
| **Parse** | `ExcelForecastView.openForecastTransactionSource()` / `CsvForecastView.openForecastTransactionSource()` | Read the spreadsheet rows into `ForecastTransaction` objects |
| **Reconcile** | `ForecastController.updateFromExternalSource()` | Compare each imported row against the DB and update / insert |

**Important:** the existing test class `UpdateFromExternalSourceTest` only exercises the
**Reconcile** layer — it mocks `openForecastTransactionSource()` and hands the controller
ready-made objects. The two reported bugs both live in the **Parse** layer, which currently
has **no unit tests**.

## 2. Reported bugs to fix

### Bug A — Balance column should be ignored
The running-balance column is read and parsed as a number in both views:
- Excel: `ExcelForecastView` column 6 → `getCellValueAsDouble()` → `setRunningBalance(...)`
- CSV: `CsvForecastView` `BALANCE` header → `parseDollarAmount(...)` → `setRunningBalance(...)`

…but `updateFromExternalSource()` **never uses `runningBalance`**. Parsing it adds no value
and creates a failure point. **Desired:** the balance column is ignored entirely and does
**not** need to contain a numeric (or any) value.

### Bug B — Deleted row produces `#REF!` and crashes the import
When the user deletes a spreadsheet row, Excel rewrites formulas in the following rows to
`#REF!`. On import this reaches numeric parsing:
- CSV: the literal text `#REF!` hits `Utility.parseDollarAmount("#REF!")` →
  `Double.parseDouble` → **`NumberFormatException`** → the whole file import aborts with
  *"Exception while processing the transactions file … on line N."*
- Excel: error cells (`CellType.ERROR`, or `FORMULA` with a cached error result) are only
  partially handled; not every column path is safe.

**Desired:** an Excel error / `#REF!` (and the other Excel error literals `#DIV/0!`,
`#N/A`, `#VALUE!`, `#NAME?`, `#NULL!`, `#NUM!`) in any cell is treated as empty/ignored
and never aborts the import.

## 3. Proposed fixes (for review)

1. **`Utility.parseDollarAmount`** — return `0.0` (instead of throwing) for values that are
   not parseable as a number, specifically Excel error strings. *(Open question Q1 below —
   throw vs. treat-as-zero.)*
2. **Balance column** — stop parsing it in both `ExcelForecastView` and `CsvForecastView`.
   Do not call `setRunningBalance` from the import path at all.
3. **Excel error cells** — add explicit `CellType.ERROR` handling in `getCellValueAsString`
   and `getCellValueAsDouble` so they return `""` / `0.0` instead of relying on exception
   fall-through.
4. Consider extracting a small shared helper (e.g. `isExcelError(String)`) so both views and
   the utility agree on what an "error value" is.

## 4. Test case catalog

Legend: **P** = Parse-layer test, **R** = Reconcile-layer test (controller).
IDs marked 🔴 are expected to FAIL against current code (they document the bugs).

### 4.1 CSV parsing — `CsvForecastView.openForecastTransactionSource` (P)

| ID | Scenario | Input row(s) | Expected result |
| -- | -------- | ------------ | --------------- |
| CSV-01 | Happy path, full row | date, category, payee, credit/debit, amount, id, version | One `ForecastTransaction` with correct fields |
| CSV-02 | Month-header row | `March 2026` in DATE col | Sets planned month/year, produces no transaction |
| CSV-03 | Day row after header | `15th` | Planned date day = 15 |
| CSV-04 | Blank date + blank payee | empty, empty | Row skipped |
| CSV-05 | New transaction (no ID) | valid row, TRANSACTION_ID empty | Transaction with `id == null` |
| CSV-06 🔴 | **Balance is `#REF!`** | BALANCE = `#REF!`, rest valid | Row imported; balance ignored; **no exception** |
| CSV-07 🔴 | **Balance is non-numeric text** | BALANCE = `n/a` | Row imported; balance ignored |
| CSV-08 | Balance blank | BALANCE empty | Row imported normally |
| CSV-09 🔴 | **Credit is `#REF!`** | CREDIT = `#REF!` | Credit treated as 0; no exception *(pending Q2)* |
| CSV-10 🔴 | **Amount is `#REF!`** | AMOUNT = `#REF!` | Amount derived from credit−debit or 0; no exception *(pending Q2)* |
| CSV-11 | Amount explicit vs derived | AMOUNT present | Uses AMOUNT, not credit−debit |
| CSV-12 | Amount absent | AMOUNT empty | Uses credit − debit |
| CSV-13 | Dollar formatting | `$1,234.56` | Parsed as 1234.56 |
| CSV-14 | Invalid UUID in ID col | TRANSACTION_ID = `not-a-uuid` | *(pending Q3 — currently would throw)* |
| CSV-15 | File not found | missing file | Returns `null` (current behavior) |
| CSV-16 | Importance / howOccurs parsing | populated + blank | Correct enums / defaults |

### 4.2 Excel parsing — `ExcelForecastView.openForecastTransactionSource` (P)

| ID | Scenario | Input | Expected result |
| -- | ----- | ----- | --------------- |
| XLS-01 | Happy path, full row | all columns populated | One correct `ForecastTransaction` |
| XLS-02 | No header row | sheet without `Date` in col A | `ControllerException` (clear message) |
| XLS-03 | Month header + day rows | `March 2026`, `15th` | Planned date set correctly |
| XLS-04 | Blank payee | payee empty | Row skipped |
| XLS-05 🔴 | **Balance cell is `#REF!` error** | col 6 = error | Row imported; balance ignored; no exception |
| XLS-06 🔴 | **Balance cell is a formula error** | col 6 = `=A1/0` | Row imported; balance ignored |
| XLS-07 🔴 | **Credit cell is `#REF!`** | col 4 = error | Credit = 0; no exception *(pending Q2)* |
| XLS-08 🔴 | **Amount cell is `#REF!`** | col 12 = error | Amount handled gracefully *(pending Q2)* |
| XLS-09 | Numeric vs string balance | col 6 numeric | Ignored either way |
| XLS-10 | Whole-number formatting | numeric 150 | `getCellValueAsString` → `"150"` |
| XLS-11 | ID not in DB | valid UUID absent from DB | Row skipped + warning; counted in missing-id total |
| XLS-12 | >50% IDs missing | many missing | Warning banner displayed |
| XLS-13 | Invalid UUID string | col 10 = garbage | Warning + row skipped (existing behavior) |
| XLS-14 | Version parsing | col 11 timestamp | Version calendar set |
| XLS-15 | Locked file | file open elsewhere | `ControllerException` with helpful message |

### 4.3 `Utility.parseDollarAmount` unit tests (P)

| ID | Input | Expected |
| -- | ----- | -------- |
| PDA-01 | `""` | 0.0 |
| PDA-02 | `"$1,234.56"` | 1234.56 |
| PDA-03 | `"-50"` | -50.0 |
| PDA-04 🔴 | `"#REF!"` | 0.0 (no exception) *(pending Q1)* |
| PDA-05 🔴 | `"#DIV/0!"`, `"#N/A"`, `"#VALUE!"`, `"#NAME?"`, `"#NULL!"`, `"#NUM!"` | 0.0 each |
| PDA-06 | `"abc"` | *(pending Q1 — 0.0 or exception?)* |

### 4.4 Reconcile layer — `ForecastController.updateFromExternalSource` (R)

These already exist in `UpdateFromExternalSourceTest` and should continue to pass. Listed
here for completeness / regression tracking. New/changed ones marked **NEW**.

| ID | Scenario | Status |
| -- | -------- | ------ |
| REC-01 | No file found → `ForecastException` | exists |
| REC-02 | Null transaction list → "no forecast transactions" | exists |
| REC-03 | Same date & amount → marks found, no prompt | exists |
| REC-04 | Date changed, same version → auto-overwrite | exists |
| REC-05 | Date changed, older version → prompt (imported/db) | exists |
| REC-06 | Amount changed > $0.50, same version → overwrite | exists |
| REC-07 | Amount changed ≤ $0.50 → ignored (rounding) | exists |
| REC-08 | Amount changed, older version → prompt | exists |
| REC-09 | ID present but not in DB → treated as new | exists |
| REC-10 | New txn, existing ForecastItem → insert | exists |
| REC-11 | New txn, single BudgetItem match → new ForecastItem | exists |
| REC-12 | New txn, multiple BudgetItem matches → prompt | exists |
| REC-13 | New txn, **no BudgetItem match** → currently NPE | exists (documents bug) |
| REC-14 | `zeroNotFound` + close source called | exists |
| REC-15 **NEW** | Row with balance omitted still reconciles | to add |

## 5. Open questions for you (let's resolve before I implement)

- **Q1 — `parseDollarAmount` policy:** Should it return `0.0` for *any* unparseable string,
  or only for the known Excel error literals (and still throw on genuine garbage like
  `"abc"`)? My recommendation: treat Excel error literals as `0.0`; keep other malformed
  input strict. Your call.
- **Q2 — Error in Credit/Debit/Amount (not balance):** If `#REF!` shows up in a *money*
  column, do you want it (a) silently treated as 0, (b) that single row skipped with a
  warning, or (c) abort with a clear message naming the row? (For **balance** the answer is
  settled: ignore it.)
- **Q3 — Invalid UUID in CSV ID column:** Excel already warns and skips the row. CSV
  currently throws. Should CSV match Excel's warn-and-skip behavior?
- **Q4 — Scope of "balance ignored":** Remove balance parsing entirely from the import path
  (my plan), or keep reading it but tolerate non-numeric values? Removing is cleaner since
  the value is unused downstream.
- **Q5 — Test data location:** OK to add small sample `.csv` and `.xlsx` fixtures under
  `src/test/resources/forecast/` for the parse-layer tests? (Excel tests will build the
  workbook in-memory with POI where possible to avoid binary fixtures.)

## 6. Implementation plan (after sign-off)

1. Fix `parseDollarAmount` per Q1.
2. Remove/neutralize balance parsing in both views (Bug A).
3. Add `CellType.ERROR` handling + error-literal tolerance in Excel view (Bug B).
4. Make CSV numeric parsing tolerant of error literals (Bug B).
5. Add new test classes:
   - `CsvForecastViewTest` (section 4.1)
   - `ExcelForecastViewTest` (section 4.2)
   - `ParseDollarAmountTest` (section 4.3, or fold into an existing `UtilityTest`)
   - extend `UpdateFromExternalSourceTest` (REC-15)
6. Run `mvn test`, fix any failures, iterate.
7. Remind user to commit.

