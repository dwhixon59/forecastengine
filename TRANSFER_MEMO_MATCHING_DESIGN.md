# Transfer Memo in Auto-Matching — Design

**Status:** proposed. No code written. Every number below was measured against `ForecastDatabase`
on 2026-08-26; the queries are in the appendix.

## Goal

Wells Fargo lets the user attach a memo when they make a transfer. The memo is the one place the
user says *why* they moved the money, and the application currently throws it away for
categorization purposes. If the user types `RENT`, auto-matching should lean toward the rent budget
item.

The application already knows this is the missing signal. From
`TransactionSplitsController.autoAssignExactPerTransactionAmount`:

> An earlier version of this shortcut fired on a dominant *relevancy score* instead. It was removed
> after real data showed it would have silently assigned a $150 rent transfer to Groceries — scoring
> 80.0 against a runner-up of 25.0, a wider lead than the case it was built for. No threshold
> separates those two; **the distinguishing signal was the word "RENT" in the payee, which relevancy
> scoring never sees.**

This design makes relevancy scoring see it.

---

## 1. What the memo actually is

Wells Fargo does not give the memo its own field. It appends it to the transaction description,
after the reference code and the counterparty account-type name:

```
ONLINE TRANSFER FROM RYBICKI C REF #IB0Z8W2598 EVERYDAY CHECKING RENT
                               └─ ref code ──┘ └─ account type ─┘ └ memo
ONLINE TRANSFER FROM HIXON D REF #IB0YLLMD9Q WAY2SAVE SAVINGS GROCERY
ONLINE TRANSFER TO HIXON J REF #IB0YZ8KPC9 WELLS FARGO CLEAR ACCESS BA PATIO
```

When there is no memo, the description ends in the account number and posting date instead, and
carries no user text at all:

```
ONLINE TRANSFER TO HIXON D EVERYDAY CHECKING XXXXXX7018 REF #IB0YLYCRJ2 ON 06/21/26
```

### The memo is only on cleared transactions

Confirmed in the data. The one provisional transfer currently in the register:

```
cleared=0  importRecordId=P202608251
ONLINE TRANSFER FROM HIXON D REF #IB0ZHVQXST WA
```

47 characters, cut mid-word through `WAY2SAVE`. The pending feed truncates the description, and the
truncation lands before the memo every time. Older rows show the same thing at other widths
(`ONLINE TRANSFER FROM HIXON D REF`, `ONLINE TRANSFER FROM HIXON D`). **A memo can never be read
from a provisional transaction.** Section 6 deals with what that costs.

### Extraction already exists

`FinancialInstitutionInt.extractUserDescription(String payee)` is on the institution interface and
implemented by `WellsFargoBank`, `CitiBank`, `BarclaysBank` and `GenericBank`. It takes everything
after the last `REF #`, drops the reference code, drops a stopword list and any masked account
number, and rejects a result that is only a date. `RegisterController.resolveUnmatchedAccount`
already calls it — to match memo tokens against *register nicknames*, which is a different question
from the one this design asks.

Running the real `WellsFargoBank.extractUserDescription` over every transfer since 2024
(1,986 transactions) produced a memo for **967 of them (48.7%)**. The top results:

```
 45  RENT                     14  GAS                8  DOCTOR
 43  COVER OVERDRAFTS         14  CHRISTMAS          8  FUND ENVELOPES BPE
 35  SPENDING MONEY           12  DAVES SPENDING MONEY 2ND HALF
 34  MICHELE ALIMONY          11  GAS DWH            7  VISITATION
 33  SPENDING MONEY JSA       10  MAKEUP             6  PAY BACK
 27  WEEKLY SPENDING          10  FUND ENVELOPES     5  DANNI HAIR
 26  MEDICAL                   9  NAILS              5  GROOM
 21  GROCERY                                         4  COSTCO
 31  WELLS FARGO CLEAR ACCESS JUSTIN SPENDING MONEY
```

Coverage is falling, which matters for sizing the work:

| Year | Transfers | With a memo | |
|---|---|---|---|
| 2023 | 386 | 295 | 76% |
| 2024 | 839 | 465 | 55% |
| 2025 | 794 | 398 | 50% |
| 2026 | 353 | 106 | 30% |

**One extraction defect, worth fixing first.** `WellsFargoBank.STOPWORDS` filters word by word and
contains `BANKING`, `BA`, `ACCOUNT`, `CHECKING`, `SAVINGS`, `JOINT`, `WAY2SAVE`, `EVERYDAY` — but
not `WELLS`, `FARGO`, `CLEAR` or `ACCESS`. So 50-odd rows come out as
`WELLS FARGO CLEAR ACCESS JUSTIN SPENDING MONEY`, with the account type still attached. Word-wise
filtering also has the opposite failure: `JOINT` and `SAVINGS` are stripped as noise even though
`Joint Spending Money` and `Savings` are real budget item names, so `JOINT SPENDING MONEY JSA`
arrives as `SPENDING MONEY JSA`.

Both are the same mistake. The account type is a **phrase anchored at the front of the tail**, not a
bag of words. Strip it as a phrase (`EVERYDAY CHECKING`, `WAY2SAVE SAVINGS`,
`WELLS FARGO CLEAR ACCESS BANKING`, `WELLS FARGO AT WORK CHECKING`, …), then keep every remaining
word. This is a change to `extractUserDescription`, and it improves the existing register-nickname
path too.

---

## 2. How much signal is in the memo

Two candidate readings of a memo, measured over the 960 single-split transfers since 2024 that have
both a memo and a budget item.

### Reading A — lexical: the memo names the budget item

`RENT` → `Room rental`. `NAILS` → `Danni's Nails`. `MAKEUP` → `Danni's Skin Care and Makeup`.

Measured: the first word of the memo appears anywhere in the chosen budget item's name in
**285 of 960 cases (29.7%)**.

That is much worse than the examples suggest, because the user's vocabulary is not the budget's
vocabulary:

```
MICHELE ALIMONY        →  David's support payment      34×
JUSTIN SPENDING MONEY  →  Children's allowances        31×
WEEKLY SPENDING        →  Joint Spending Money         25×
MEDICAL                →  Bill Pay Envelopes           20×
COVER OVERDRAFTS       →  Short term borrowing         19×
JDH EXPENSES           →  Justin's Weekly Expenses     13×
VISIBLE                →  Reimbursement                 2×
```

None of those share a word. A purely lexical feature would find nothing for the most frequent memos
in the file.

### Reading B — historical: the memo names what the user chose last time

Backtest: for each transfer with a memo, predict the budget item from the **most recent earlier**
transfer carrying the same memo *in the same register and the same direction*, then compare against
what the user actually chose.

| Scope | With a memo | Had a prior | Predicted correctly | |
|---|---|---|---|---|
| 2024–2026 | 960 | 534 | 446 | **83.5%** |
| 2025 | 394 | 232 | 199 | 85.8% |
| 2026 | 105 | 30 | 22 | 73.3% |

Register and direction scoping is load-bearing, not incidental. Without it accuracy drops from 83.5%
to 77.6%, because **both sides of one transfer carry the same memo and belong to different budget
items**: `RENT` is `Room rental` in Dave's budget and `Danni's contribution` in Danni's.

### The two readings are complementary, and A is the smaller half

Of the 426 memos with no prior occurrence, only 75 have a lexical hit. So:

```
history correct                446  ┐
lexical, where no history       75  ┘ 521 of 960 — 54% of memo-bearing transfers
history wrong                   88
neither                        351
```

### Conclusion, which sets the shape of everything below

83.5% is excellent for **ordering a list** and unacceptable for **choosing silently** — one in six
would be wrong, and a wrong split is worse than a question, because nobody looks at it again. Hence
the rule the rest of this design obeys:

> **The memo prefers a budget item. It never selects one, and never rules one out.**

Deliberately weaker than the sibling rule already in `ForecastTransactionMatcher`, where the bank
reference *does* gate ("the reference confirms a match, it never gates one"). A reference is an
identity; a memo is an opinion.

---

## 3. Where the memo gets consulted

Three sites, in descending order of value.

### 3.1 Phase 4 — the ranked budget item list (the main event)

This is where nearly all transfer categorization actually happens.
`LINKED_ACCOUNT_TRANSFERS_DESIGN.md` measured that **231 of 334 transfers in 2026 were assigned to
an on-demand or unplanned budget item**, and on-demand items never generate forecast transactions
(`generateForecastItems` skips them outright). So Phase 2.5 has nothing to match them against, by
construction, and they land here — in `TransactionSplitsController.getSplits`, at the prompt that
looks like this in a real session:

```
▸ Imported a transfer from Dave's Spending Account for $30.00 on 08-24-2026
The assigned budget items and amounts (if specified) for this merchant are:
   1.  Short term borrowing (Debt - Unsecured, $-25 On-Demand), Relevancy Score: 75.0
   2.  Other (Miscellaneous, $-25 On-Demand), Relevancy Score: 65.0
   3.  Dave's work expenses (Spending Money, $-55 Weekly), Relevancy Score: 64.7
   4.  Michael's birthday (Gifts and Holidays, $-50 Annually), Relevancy Score: 51.2
   ...
```

`calculateRelevancyScores` builds those from amount (0–60), date proximity (0–20) and importance
(0–20), clamped to 0–100. The memo is not an input.

**Change.** Add a fourth component, applied *after* the existing clamp so it cannot be swallowed by
it, raising the effective ceiling to 130:

```
+30   the memo's historical budget item for (memo, register, direction) is this item
```

30 points is calibrated against the observed spreads: in the session above it lifts `Other` (17.4 in
a later prompt) clearly above `Short term borrowing` (27.4), and lifts a mid-list item over a
top-of-list one, but does not lift a genuinely implausible item (score near 0) above a strong one.
It is a constant in one place and should be tuned with the backtest in §7, not by intuition.

**Display.** Print the reason, in the style of the existing `[Phase2.5]` instrumentation:

```
   1.  Room rental (Household, $-750 Monthly), Relevancy Score: 92.3  ← memo "RENT" (42 prior)
```

Naming the evidence is what makes a 16%-wrong suggestion safe: the user can see *why* it is first.

**When the suggested item is not in the list.** `budgetItemsForMerchant` contains only items already
associated with this merchant, so the memo's item may not be present at all. Append it as a labelled
extra row rather than dropping it; selecting it creates the association through the existing
`BudgetItemMerchantController.addBudgetItemToMerchant` path. This is the case that turns a
five-prompt interaction into one keystroke, so it is not an edge case to defer.

**What the memo must not touch.** `autoAssignExactPerTransactionAmount` reads a decision the user
recorded in advance. It is not a guess, and the memo has no business in it. Leave it alone.

### 3.2 Phase 2.5 — forecast transaction scoring (tie-break only)

`ForecastTransactionMatcher.calculateMatchScore` gives date 0–40, amount 0–40, merchant 0–20, and
auto-assigns at 70. This path sees only *planned* transfers — roughly 103 of the 334 — where a memo
mostly breaks ties between near-identical candidates (`David's net pay 1` vs `David's net pay 2`).

**Change, and the safety property that comes with it:**

```
+15   the memo's historical budget item is this candidate's budget item
```

…**and the 70-point threshold is evaluated on the memo-free score.** The memo can change *which*
candidate wins; it can never change *whether* one wins. That is §2's rule expressed as a single
testable invariant, and it means no memo, however emphatic, can push a marginal candidate into a
silent auto-assignment.

Where a transfer counterpart already carries a bank reference, `ReferenceVerdict.CERTAIN`
short-circuits before scoring and the memo is never reached. That is correct — the reference is the
stronger fact.

### 3.3 Register resolution (already exists, no change)

`RegisterController.resolveUnmatchedAccount` already matches memo tokens against register nicknames
and runs a full-text search over `user_description`. No change here, but it inherits the extraction
fix from §1, and §5 finally gives its full-text search something to search.

---

## 4. Where the memo→budget-item association comes from

**Derive it from transaction history at query time. Do not build a learned mapping table.**

Two reasons — one historical, one structural.

The historical one: `e6253c8` deleted exactly such a table. `transfer_memo_mapping` keyed a
normalized payee string to a register, and `TransferBudgetItemPair`'s class comment records why it
was removed — "it was keyed on a payee string such as `HIXON D`, which is ambiguous across accounts,
and so fixed an ambiguous payee to a single register." The failure was not *learning*; it was
choosing a key carrying less information than the question needed. The key here — **(normalized
memo, register, direction)** — is the one the backtest in §2 measured at 83.5%, and dropping either
scope term measurably degrades it.

The structural one, which is the stronger argument: a mapping table has to be invalidated when the
user recategorizes a transaction, and nothing would do that. Derived history is correct by
construction — recategorize a transfer and the next suggestion changes, with no `learn()` call, no
staleness, and no unlearn path to build. `TransferBudgetItemPair` earns its table because it records
something *no transaction shows* (which item on the far side corresponds to this one). A memo
association is fully visible in the transactions themselves.

The query, per candidate transaction:

```sql
SELECT bi.idBudgetItem, bi.payee, COUNT(*) AS priors, MAX(t.postDate) AS lastSeen
FROM   transaction t
JOIN   transaction_split ts ON ts.Transaction_idTransaction = t.idTransaction
JOIN   budget_item bi       ON bi.idBudgetItem = ts.BudgetItem_idBudgetItem
WHERE  t.user_description   = '<normalized memo>'
  AND  t.Register_idRegister = uuid_to_bin('<this register>')
  AND  SIGN(t.amount)        = <sign of this transaction>
  AND  t.idTransaction      <> uuid_to_bin('<this transaction>')
GROUP BY bi.idBudgetItem
ORDER BY priors DESC, lastSeen DESC
LIMIT 1
```

Three rules the measurements dictate:

- **Exclude multi-split transactions.** `FUND ENVELOPES` is a bulk envelope-funding transfer that
  splits across ten different budget items; counting each split as a vote for the memo is noise.
  Add `AND (SELECT COUNT(*) FROM transaction_split s
  WHERE s.Transaction_idTransaction = t.idTransaction) = 1`.
- **Budget items are per-budget and get re-created.** Match on `idBudgetItem` first; if the winner
  belongs to a budget that is not the current one, fall back to the item with the same `payee` in
  the current budget. This accounts for most of the gap between 2025 (85.8%) and 2026 (73.3%).
- **A memo seen once is a weaker suggestion than one seen forty times.** Carry `priors` through to
  the display (§3.1), and do not let a single-prior suggestion outrank an item that already scores
  well on amount. Simplest form: scale the §3.1 bonus as `priors == 1 ? 15 : 30`.

---

## 5. The one persistence change: actually write `user_description`

`transaction.user_description VARCHAR(64)` exists. It has a FULLTEXT index
(`user_descripton_idx`). `com.hixon.utilities.BackfillUserDescriptions` exists to populate it. And
`TransactionUtilities.getByUserDescriptionFullText` reads it.

**Nothing in the application ever writes it.** `Transaction` has no such field; the column is absent
from `selectColumns`, `insertQuery`, `getInsertOnDuplicateUpdateQuery` and `getUpdateByIdQuery`.
Every 2026 row is NULL — the historical values came from the one-off backfill and stopped when it
stopped being run.

So the whole of §4 needs one change, and no migration:

1. `Transaction`: add a `userDescription` field with getter/setter (the setter sets the dirty flag,
   per the entity convention), and add the column to `selectColumns`, `insertQuery`,
   `getInsertOnDuplicateUpdateQuery`, `getUpdateByIdQuery` and the `ResultSet` constructor.
2. Populate it where the payee is parsed. `FinancialInstitution.convertQfxToTransaction` and
   `WellsFargoBank.loadFromCsvRecord` both already call `parseMerchantPayee`; call
   `extractUserDescription(payee)` alongside it. Truncate to 64 characters.
3. Re-run `BackfillUserDescriptions` once for history. It hardcodes `root` and a literal password;
   change it to read `src/main/resources/db.properties` before running it again.

Adjacent bug found while reading this path — not caused by this work and not fixed by it:
`getByUserDescriptionFullText` computes its `relevance` column as
`MATCH(user_description) AGAINST ('ALIMONY' …)`, a hardcoded literal, while the `WHERE` clause uses
the real search term. The `ORDER BY relevance DESC` therefore ranks by similarity to the word
"ALIMONY" regardless of what was searched. This is live in the register-resolution path today.

---

## 6. The pending/cleared problem

Restating it precisely, because it is the one thing that limits this feature.

`ImportController` categorizes a **provisional** transaction when it first appears: it runs Phase 2.5
and, failing that, asks the questions and writes splits. When the cleared version arrives,
`FinancialInstitution.reconcileProvisionalTransaction` copies the provisional's identity onto the
cleared transaction, the cleared payee (memo and all) is saved, and then:

```java
// Phase 2.5: Auto-match with forecast transactions (if enabled)
if (splits == null) {
```

`splits` is not null — they came from the provisional. Phases 2.5, 3 and 4 are all skipped. **The
memo arrives, is stored, and is never read.**

For a transfer that never appears as pending, this costs nothing and everything in §3 works. For one
that does, the memo is late.

Three ways to handle it:

**(a) Accept it.** The memo helps transfers that clear directly; pending-first ones are unchanged.
Zero extra work, zero risk. Worth stating plainly that this is a legitimate stopping point — most of
the value in §2 is available without touching the reconcile path at all.

**(b) Treat the memo as a late-arriving fact — recommended.** At reconcile, if the cleared payee
yields a memo whose historical budget item disagrees with the split already assigned, say so and
offer to change it:

```
▸ This transfer cleared with the memo "RENT", which you have assigned to Room rental
  42 times. It is currently split to Groceries. Change it?  (y/n)
```

Ask only on **disagreement with a memo that has priors** — never on a memo with no history, and
never when the two already agree. Otherwise it becomes a question on every reconciled transfer,
which is the opposite of the point. `IMPORT_SUMMARY_RECATEGORIZE_DESIGN.md` already describes a
recategorization flow; route the change through it rather than writing a second one.

This also protects the data §4 depends on. Without it, provisional-first transfers permanently
record a categorization made in ignorance of the memo, and those rows then become priors that teach
the wrong answer.

**(c) Defer the question for transfers until they clear.** Rejected. It would leave uncategorized
transactions sitting in the register for days, break the forecast reconciliation the pending sweep
depends on, and trade a small accuracy gain for a large behavioural change.

---

## 7. Phases

Each phase is independently useful and independently revertible.

| # | Work | Ships |
|---|---|---|
| 1 | Fix `extractUserDescription` phrase-stripping (§1); add `userDescription` to `Transaction` and write it at import (§5); re-run the backfill | Better register-nickname matching immediately; the data §2–§4 need |
| 2 | `MemoBudgetItemHistory` lookup (§4) + Phase 4 ranking bonus and display (§3.1) | **The feature as asked for** |
| 3 | Phase 2.5 tie-break with the memo-free threshold invariant (§3.2) | Planned transfers |
| 4 | Late-memo confirmation at reconcile (§6b) | Pending-first transfers; protects the history |

Phase 1 must land and be backfilled before Phase 2 can be measured honestly.

---

## 8. Testing

Per `CLAUDE.md`, JUnit with mocks. None of these need a database.

**Extraction — `WellsFargoBankMemoExtractionTest`**, table-driven over real payee strings:

| Input | Expected |
|---|---|
| `… REF #IB0Z8W2598 EVERYDAY CHECKING RENT` | `RENT` |
| `… REF #IB0YZ8KPC9 WELLS FARGO CLEAR ACCESS BA PATIO` | `PATIO` *(fails today: `WELLS FARGO CLEAR ACCESS PATIO`)* |
| `… REF #IB0Y89FF7K WAY2SAVE SAVINGS DOCTOR` | `DOCTOR` |
| `… EVERYDAY CHECKING XXXXXX7018 REF #IB0YLYCRJ2 ON 06/21/26` | `null` |
| `ONLINE TRANSFER FROM HIXON D REF` | `null` |
| `ONLINE TRANSFER FROM HIXON D REF #IB0ZHVQXST WA` *(truncated pending)* | `null` |
| `… REF #IB0ZGXJP6L EVERYDAY CHECKING JOINT SPENDING MONEY JSA` | `JOINT SPENDING MONEY JSA` |

**History lookup — `MemoBudgetItemHistoryTest`**, stubbed rows:

- same memo, same register, same direction → returns the item, with `priors`
- same memo, **opposite direction** → no suggestion *(the `RENT` both-sides case)*
- same memo, **different register** → no suggestion
- memo on a multi-split transaction → excluded from the vote *(`FUND ENVELOPES`)*
- winner in a stale budget → falls back to the same-payee item in the current budget
- memo present, no priors → no suggestion, no exception

**Phase 4 ranking — `TransactionSplitsControllerMemoRankingTest`:**

- memo item present in the list → sorts first, bonus applied after the clamp
- memo item absent → appended as a labelled extra row
- `priors == 1` → smaller bonus, does not displace a strong amount match
- no memo → scores byte-identical to today *(the regression guard for the 51% with no memo)*
- `askAlways` merchant, and `autoAssignExactPerTransactionAmount` → unaffected

**Phase 2.5 — `ForecastTransactionMatcherMemoTest`.** The invariant is the point:

- memo agreement reorders two candidates that both clear 70
- **a candidate whose memo-free score is 69 is not auto-matched, whatever the memo says**
- `ReferenceVerdict.CERTAIN` still short-circuits before any memo is consulted
- `RULED_OUT` still wins over memo agreement

**Backtest harness.** Phases 2–3 change ranking, and ranking changes are only honestly judged in
bulk. Keep the §2 backtest as a runnable utility under `com.hixon.utilities` — not a unit test, since
it needs the real database, like the other `main()`-style tools. Report memo coverage, prediction
accuracy, and the number that actually matters: **how far up the list the correct item moved.** Tune
the 30/15 constants against that, not against intuition.

---

## 9. Risks

- **One in six memo suggestions is wrong.** Mitigated structurally by §2's rule (never selects) and
  §3.1's labelling (the user sees the evidence). Not mitigated by any threshold, because none exists
  that separates the good cases from the bad — which is precisely what killed the earlier
  dominant-relevancy-score shortcut.
- **Genuinely ambiguous memos.** `COVER OVERDRAFTS` maps to `Short term borrowing` 19×,
  `Joint Spending Money` 13× and `Other` 7×. The design surfaces the plurality and the count; it
  cannot do better, because the user themselves is not consistent here.
- **Coverage is falling** (76% → 30% since 2023). The feature quietly rewards writing memos and does
  nothing for the growing majority without one. Worth knowing before weighting the effort.
- **Silent regression for the 51% with no memo** is the main correctness risk, and the reason for the
  byte-identical-scores test above.
- **Multi-split transfers** would poison the history if counted; excluded in §4.

---

## Appendix — reproducing the numbers

Memo extraction over the real corpus, calling the shipped `WellsFargoBank.extractUserDescription`
(the instance is allocated without running its constructor, since the method uses no instance state):

```
SELECT payee FROM transaction
WHERE UPPER(payee) LIKE '%TRANSFER%' AND YEAR(postDate) >= 2024;
→ total=1986  extracted=967
```

The backtest builds a temporary table of `(memo, postDate, idTransaction, budgetItem, register,
sign)` for single-split transfers since 2024, then, for each row, takes the budget item of the most
recent earlier row with the same `(memo, register, sign)`:

```
all 2024+   960 rows   534 had a prior   446 correct   83.5%
2025        394        232               199           85.8%
2026        105         30                22           73.3%
without register/direction scoping                     77.6%
lexical (first memo word inside the item name)   285/960   29.7%
```
