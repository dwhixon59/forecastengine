# Design: Reducing User Questions During QFX Import

## Executive Summary

Analysis of an import session for the **Bill Pay Danni** register (77 transactions from
`Checking3.qfx`) revealed approximately **23–25 distinct user interactions** that were
required. Most of these were avoidable. This document identifies the root causes and
proposes targeted enhancements to reduce required interactions to **3–5** for a typical
import session—reserving prompts only for genuinely ambiguous situations.

---

## Session Analysis

### Interaction Inventory

| # | Type | Count | Root Cause |
|---|------|-------|------------|
| 1 | Register selection at startup | 1 | Necessary — cannot eliminate |
| 2 | "Reprocess skipped transactions?" (y/n) | 1 | Could default to `y` or be configurable |
| 3 | Skipped transaction budget item (Radiology) | 4 sub-steps | Unavoidable new merchant — acceptable |
| 4 | "Old transaction — are you sure?" | 1 | Configurable threshold |
| 5 | Manual transfer register selection | 11 | See detail below |
| 6 | "Use merchant X for payee Y?" confirmations | 4 | Unnecessary for well-established patterns |
| 7 | Budget item selection for new transactions | 4 | No default budget item per merchant |

### Transfer Register Selection Detail (11 instances)

| Import ID | Payee NAME | Memo excerpt | Resolved by | Questions asked |
|-----------|-----------|--------------|-------------|-----------------|
| 202605052 | ONLINE TRANSFER FROM RYBICKI C | (no XXXXXX) | Manual | 1 |
| 202605114 | ONLINE TRANSFER FROM HIXON D | EVERYDAY CHECKING **XXXXXX7394** | Manual ❌ | 1 |
| 202605116 | ONLINE TRANSFER FROM HIXON D | EVERYDAY CHECKING **XXXXXX7394** | Manual ❌ | 1 |
| 202605117 | ONLINE TRANSFER FROM HIXON D | EVERYDAY CHECKING **XXXXXX7394** | Manual ❌ | 1 |
| 202605142 | ONLINE TRANSFER TO HIXON D REF | EVERYDAY CHECKING SPENDING MONEY | Manual | 1 |
| 202605181 | ONLINE TRANSFER FROM HIXON D REF | EVERYDAY CHECKING DAVE CREDIT CARD | Manual | 1 |
| 202605182 | ONLINE TRANSFER FROM HIXON D REF | EVERYDAY CHECKING MOVERS REIMBURSE | Manual | 1 |
| 2026051815 | ONLINE TRANSFER TO RYBICKI C | EVERYDAY CHECKING **XXXXXX8656** | Manual ❌ | 1 |
| 2026051818 | ONLINE TRANSFER TO HIXON D REF | EVERYDAY CHECKING JUSTIN GRADUATION PICTURES | Manual | 1 |
| 202605192 | ONLINE TRANSFER TO HIXON J REF | WELLS FARGO CLEAR ACCESS BA JUSTIN DOCTOR | Manual | 1 |
| 202605193 | ONLINE TRANSFER TO HIXON D REF | EVERYDAY CHECKING OVERDRAFT | Manual | 1 |

Rows marked ❌ **should have been auto-resolved** but were not, due to **Root Cause 1**
described below.

---

## Root Causes

### Root Cause 1: QFX MEMO Field Is Ignored During Payee Parsing

**Location:** `FinancialInstitution.convertQfxToTransaction()` (lines 366–398)

```java
// CURRENT CODE (only uses NAME, ignores MEMO):
String payee = qfxTxn.getName();
```

In Wells Fargo QFX files, the `<NAME>` element contains only a generic transfer label
(e.g., `ONLINE TRANSFER FROM HIXON D`). The `<MEMO>` element contains the critical
disambiguation data—including masked account numbers in the format `XXXXXX####`:

```
NAME: ONLINE TRANSFER FROM HIXON D
MEMO: EVERYDAY CHECKING XXXXXX7394 REF #IB0Y2MCFXJ ON 05/11/26
```

`WellsFargoBank.parseMerchantPayee()` already has logic to extract `XXXXXX####` patterns
(`MASKED_ACCOUNT_PATTERN`) and match them to registers via `Register.getByLastFourDigits()`.
It just never sees the MEMO because `convertQfxToTransaction` discards it.

**Impact:** 4 of 11 manual transfer questions would be eliminated automatically by this
single fix (import IDs: 202605114, 202605116, 202605117, 2026051815).

---

### Root Cause 2: Memo Keywords Not Leveraged for Ambiguous Transfers

For transfers whose memos contain **no masked account number**, the text after the REF#
code often carries meaningful hints—specifically, a **user-supplied description** that was
entered when the transfer was created at the bank (e.g., `DAVE CREDIT CARD`,
`MOVERS REIMBURSE`, `SPENDING MONEY`, `OVERDRAFT`).

The `RegisterController.resolveUnmatchedAccount()` method already has a full-text search
step (lines 598–712), but it depends on finding *past transactions* with matching
user descriptions. For **first-time transfers**, there is no history. The text in the
memo could, however, be matched against **register nicknames** or a **stored memo→register
mapping table**.

**Examples from the session:**
- `EVERYDAY CHECKING DAVE CREDIT CARD` → register nickname `BPW` (Bill Pay Dave)
- `EVERYDAY CHECKING MOVERS REIMBURSE` → Danni's Spending
- `EVERYDAY CHECKING JUSTIN GRADUATION PICTURES` → Danni's Spending
- `WELLS FARGO CLEAR ACCESS BA JUSTIN DOCTOR` → Justin's Spending

---

### Root Cause 3: No Session-Level Transfer Cache

Multiple transfers on 05-11-2026 and 05-18-2026 all went to `Danni's Spending Account`.
After the first one was manually resolved, the system prompted again for each subsequent
transfer with the same payee pattern from the same session, even though the pattern
(`ONLINE TRANSFER FROM HIXON D`, `EVERYDAY CHECKING`, no masked account) was identical.

---

### Root Cause 4: Merchant Confirmation for Register-Transfer Payees

When a transfer transaction results in a new payee string (e.g.,
`Transfer to Danni's Spending Account from Bill Pay Danni`), the system asks:

> `Use merchant 'Danni' for payee 'Transfer to Danni's Spending Account from Bill Pay Danni'? (y/n): [y]:`

Since the merchant is **derived directly from the resolved register name**, this
confirmation is never needed — the answer is always "yes."

---

### Root Cause 5: No Default Budget Item Per Merchant

Four newly imported transactions each required a multi-step budget item selection
interaction. The user selected `Other (Miscellaneous)` each time. Had a **default budget
item** been configured per merchant, these interactions would be skipped entirely.

---

### Root Cause 6: Fixed "Old Transaction" Warning Threshold

The system issued a warning when the earliest transaction in the file (`05-04-2026`) was
considered "old." The threshold is hardcoded. A configurable threshold — or one tied to
the last import date for the register — would eliminate this question for normal use.

---

## Proposed Enhancements

### Enhancement 1: Include QFX MEMO in Payee String for Transfer Parsing

**Files to change:**
- `FinancialInstitution.java` — `convertQfxToTransaction()`

**Design:**

When the QFX transaction type is `XFER`, `PAYMENT`, or `DIRECTDEBIT`/`DIRECTDEP`, or
when `qfxTxn.getName()` starts with a transfer keyword (`ONLINE TRANSFER`, `RECURRING
TRANSFER`, etc.), combine `NAME` and `MEMO` into a single payee string:

```
payee = NAME + " " + MEMO
```

This matches exactly the format that `WellsFargoBank.parseMerchantPayee()` already
handles for CSV imports, where the entire bank description is a single payee string.

For non-transfer transactions, continue using only `NAME` as the payee to avoid
polluting merchant name parsing with irrelevant memo text.

**Estimated savings:** Eliminates **4 manual transfer questions** per session.

---

### Enhancement 2: Memo-to-Register Mapping Table

**New database table:** `transfer_memo_mapping`

| Column | Type | Description |
|--------|------|-------------|
| `id` | INT PK | |
| `payee_pattern` | VARCHAR | e.g., `ONLINE TRANSFER FROM HIXON D` |
| `memo_keywords` | VARCHAR | Comma-separated keywords from memo |
| `id_register` | INT FK → register | The resolved register |
| `confidence` | FLOAT | Score: higher = more reliable |
| `usage_count` | INT | Times this mapping has been used |
| `last_used` | DATE | For staleness detection |

**Behavior:**

When `resolveUnmatchedAccount()` is about to prompt the user:
1. Extract memo keywords (words that survive the stopword filter).
2. Query `transfer_memo_mapping` for the best match on (payee_pattern, memo_keywords).
3. If a high-confidence mapping exists (confidence ≥ 0.85), auto-resolve without prompting.
4. If confidence is moderate (0.60–0.84), show the suggestion as a "Found potential match"
   message and auto-accept (same as the current single-register auto-resolution path).
5. After every manual selection, insert or update the mapping table.

**Estimated savings:** Eliminates **4–5 manual transfer questions** in future sessions for
transfers whose memos were previously mapped (all the non-XXXXXX transfers).

---

### Enhancement 3: Session-Level Transfer Resolution Cache

**Location:** `RegisterController` (or `ImportController`)

**Design:**

Introduce a `Map<String, Register> sessionTransferCache` that is initialized at the start
of each import session and keyed on:

```
key = payee_name_normalized + "|" + account_type + "|" + last4_or_empty + "|" + amount
```

The **amount is a required part of the key** because the same person name (e.g.,
`HIXON D`) can transfer different amounts to different registers. For example:

| Payee | Amount | Register |
|-------|--------|----------|
| ONLINE TRANSFER FROM HIXON D | -2,000.00 | Danni's Spending |
| ONLINE TRANSFER FROM HIXON D | -500.00 | Bill Pay Envelopes |

Without the amount in the key, the second transfer would incorrectly reuse the first
register. With the amount included, each distinct (payee, amount) combination is cached
independently — the user answers once per pair, and subsequent identical pairs are
auto-resolved.

> **Edge case:** If the same person transfers the exact same amount to two *different*
> registers within a single import file, the cache will give the wrong answer for the
> second occurrence. This is expected to be extremely rare; if it happens, the user can
> correct the transaction via re-processing after import.

When `resolveUnmatchedAccount()` resolves a register (either automatically or via user
selection), store the result in the cache. On the next call with the same key,
return the cached register without prompting.

The cache is scoped to the import session only — it does not persist to the database
(Enhancement 2 handles persistence).

**Estimated savings:** Eliminates **3 additional manual questions** within a single session
where multiple same-pattern transfers occur (e.g., the 05-11 and 05-18 batch of Danni
transfers).

---

### Enhancement 4: Auto-Confirm Merchant Name for Register Transfers

**Location:** `ImportController` — wherever the "Use merchant X for payee Y?" prompt is
issued.

**Design:**

When the transaction is a transfer and the merchant name was **derived directly from a
resolved register name** (i.e., `merchant.getName().equals(transferRegister.getName())`),
skip the confirmation prompt and auto-accept.

The confirmation should only be shown when:
- The transaction is NOT a transfer (i.e., it's a debit/credit to an external merchant),
  AND
- The proposed merchant name was not previously mapped to this payee string.

**Estimated savings:** Eliminates **3–4 merchant confirmation questions** per session.

---

### Enhancement 5: Default Budget Item Per Merchant

The `merchant` table already has an `askAlways` column (mapped to `Merchant.askAlways`,
default `true`). The semantics are:

| `askAlways` | Meaning | Example |
|-------------|---------|---------|
| `false` | Fixed, single-category — auto-assign silently, never prompt | Car payment, Netflix |
| `true` | Multi-category or growing — always prompt the user | Target, Amazon |

**No new column is needed on `merchant`.** The existing `askAlways` field covers both cases.

**Behavior during import:**

**When `askAlways = false` (single-category merchant, budget item already assigned):**
1. Look up the merchant's assigned budget item(s) from `budgetitem_merchant`.
2. Auto-assign the transaction without prompting.
3. Display a confirmation line only (e.g., `▸ Auto-assigned to: Car Payment (Honda)`).
4. **Memo:** Default to the budget item's `memo` field. The user is not prompted for a
   memo since this transaction type never changes.

**When `askAlways = true` (multi-category merchant, or no budget item assigned yet):**
1. Show the existing budget item selection prompt.
2. **Memo:** Pre-fill the input field with the matching budget item's `memo` value as the
   default. If the user presses Enter, the budget item's memo is used. If the user types
   something different, their input overrides the default memo.
   ```
   Memo [Car insurance payment]:   ← user presses Enter to accept, or types to override
   ```
3. After the user selects a budget item (and optionally overrides the memo), ask:
   ```
   Set this as the default for [Merchant Name]?
     y - yes, auto-assign next time (sets askAlways = false)
     n - no, always ask (keeps askAlways = true)
   [n]:
   ```
   This gives the user an explicit path to "lock in" a merchant as single-category once
   they're confident it will never change.

**How to add a new budget item category to a single-category merchant:**  
If a merchant is set to `askAlways = false` and the user wants to assign a *different*
budget item for a specific transaction, they use the existing **re-process transaction**
flow after import. The re-process flow always prompts regardless of `askAlways`, so the
user can change the assignment and optionally reset `askAlways = true` if the merchant
has become multi-category.

**Estimated savings:** Eliminates **4 budget item selection interactions** (the 4 new
transactions in this session once defaults are set). Future imports reduce to:
- 0 keystrokes for `askAlways = false` merchants
- 1 keystroke (Enter for memo, Enter for budget item) for `askAlways = true` merchants
  when the default suggestion is correct

---

### Enhancement 6: Configurable "Old Transaction" Warning Threshold

**Current behavior:** A hardcoded (or fixed) date threshold triggers the warning
"The earliest transaction in the import file seems old."

**Proposed behavior:**
- Store a `last_import_date` per register (already implied by existing import tracking).
- If the earliest transaction in the file is within `N` days of the `last_import_date`,
  suppress the warning entirely.
- Make `N` configurable in application settings (default: 30 days).
- Only show the warning when the gap is more than `N` days (e.g., importing a file that
  is months old).

**Estimated savings:** Eliminates **1 confirmation question** in most normal import
sessions.

---

### Enhancement 7: Auto-Default "Reprocess Skipped Transactions" to Yes

**Current behavior:** Always prompts `"There are skipped transactions in the register.
Do you want to process them now? ('y' or 'n'):"` before reprocessing.

**Proposed behavior:**
- Default this to `y` automatically when running the Daily Update.
- Only prompt if there are a **large number** of skipped transactions (configurable
  threshold, e.g., > 10) or if a setting flag is `false`.

**Estimated savings:** Eliminates **1 confirmation question** per session.

---

## Summary of Estimated Impact

| Enhancement | Questions Eliminated (This Session) | Questions Eliminated (Future Sessions) |
|-------------|-------------------------------------|----------------------------------------|
| 1 — Include QFX MEMO in payee | 4 | 4 per session (repeatable) |
| 2 — Memo-to-register mapping table | 0 (first time, building mappings) | 4–5 per session |
| 3 — Session-level transfer cache | 3 | 3 per session |
| 4 — Auto-confirm merchant for transfers | 3 | 3 per session |
| 5 — Default budget item per merchant | 0 (first time, setting defaults) | 4 per session |
| 6 — Configurable old-transaction threshold | 1 | 1 per session |
| 7 — Auto-default reprocess skipped | 1 | 1 per session |
| **Total** | **~12** | **~20 of ~23** |

After all enhancements are in place, a typical import of a similar QFX file should
require only **3–5 user interactions**:
1. Register selection (unavoidable).
2. Genuinely ambiguous new merchant assignment (e.g., first-ever Radiology visit).
3. Any truly new transfer destination with no prior history and no memo keywords.

---

## Implementation Priority

1. **Enhancement 1** (QFX MEMO) — Highest ROI, single-method change, no schema change.
2. **Enhancement 4** (Auto-confirm merchant for transfers) — Simple conditional check,
   no schema change.
3. **Enhancement 3** (Session cache) — In-memory only, no schema change.
4. **Enhancement 6** (Old transaction threshold) — Minor change, high visibility.
5. **Enhancement 7** (Auto-reprocess skipped) — Minor change.
6. **Enhancement 5** (Default budget item) — Requires schema change and UI addition.
7. **Enhancement 2** (Memo-to-register mapping) — Requires new table and query logic;
   highest long-term value.

---

## Files to Change (by Enhancement)

| Enhancement | File(s) |
|-------------|---------|
| 1 | `FinancialInstitution.java` — `convertQfxToTransaction()` |
| 2 | New table `transfer_memo_mapping`; `RegisterController.java` — `resolveUnmatchedAccount()` |
| 3 | `RegisterController.java` — add session cache field; `ImportController.java` — initialize/clear cache per session |
| 4 | `ImportController.java` — merchant confirmation prompt |
| 5 | `ImportController.java` — budget item assignment phase (uses existing `merchant.askAlways`) |
| 6 | `ImportController.java` — old transaction warning logic |
| 7 | `DailyUpdateController.java` (or equivalent) — reprocess skipped prompt |



