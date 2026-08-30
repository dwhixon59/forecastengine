# Transfer Memo in Auto-Matching — Design

**Status:** phases 1, 2 and 2.1 shipped; phases 3 and 4 outstanding.  Every number below was measured
against `ForecastDatabase`, re-measured on 2026-08-28 after the phase-1 backfill; the queries are in
the appendix. Where a number here differs from the version of this document written on 2026-08-26,
the 2026-08-28 figure is the one taken against the shipped code.

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

Confirmed in the data. The one provisional transfer in the register when this was written:

```
cleared=0  importRecordId=P202608251
ONLINE TRANSFER FROM HIXON D REF #IB0ZHVQXST WA
```

47 characters, cut mid-word through `WAY2SAVE`. The pending feed truncates the description, and the
truncation lands before the memo every time. Older rows show the same thing at other widths
(`ONLINE TRANSFER FROM HIXON D REF`, `ONLINE TRANSFER FROM HIXON D`). **A memo can never be read
from a provisional transaction.** Section 6 deals with what that costs.

### Extraction — fixed in phase 1

`FinancialInstitutionInt.extractUserDescription(String payee)` is on the institution interface and
implemented by `WellsFargoBank`, `CitiBank`, `BarclaysBank` and `GenericBank`. It takes everything
after the last `REF #`, drops the reference code, drops the account type, drops any masked account
number, and rejects a result that is only a date. `RegisterController.resolveUnmatchedAccount`
already calls it — to match memo tokens against *register nicknames*, which is a different question
from the one this design asks.

**The defect this design called out has been fixed.** `WellsFargoBank.STOPWORDS` used to filter word
by word: it contained `BANKING`, `BA`, `ACCOUNT`, `CHECKING`, `SAVINGS`, `JOINT`, `WAY2SAVE`,
`EVERYDAY` — but not `WELLS`, `FARGO`, `CLEAR` or `ACCESS`. So 50-odd rows came out as
`WELLS FARGO CLEAR ACCESS JUSTIN SPENDING MONEY`, with the account type still attached. Word-wise
filtering also had the opposite failure: `JOINT` and `SAVINGS` were stripped as noise even though
`Joint Spending Money` and `Savings` are real budget item names, so `JOINT SPENDING MONEY JSA`
arrived as `SPENDING MONEY JSA`.

Both were the same mistake. The account type is a **phrase anchored at the front of the tail**, not
a bag of words. `skipAccountTypePhrase` now strips it as a phrase (`EVERYDAY CHECKING`,
`WAY2SAVE SAVINGS`, `WELLS FARGO CLEAR ACCESS BANKING`, `WELLS FARGO AT WORK CHECKING`, …), longest
match wins, tolerating the bank's fixed-width truncation of the account-type field — then keeps
every remaining word. The register-nickname path inherited the improvement.

The fix is visible in the stored data. Compare the top memos before and after:

```
before                                            after
 31  WELLS FARGO CLEAR ACCESS JUSTIN SPENDING…     31  JUSTIN SPENDING MONEY
 33  SPENDING MONEY JSA                            33  JOINT SPENDING MONEY JSA
```

The current top of the file, over the 1,054 memo-bearing transactions since 2024:

```
 45  RENT                 27  WEEKLY SPENDING      13  JDH EXPENSES
 43  COVER OVERDRAFTS     26  JOINT SPENDING MONEY 12  DAVES SPENDING MONEY 2ND HALF
 34  MICHELE ALIMONY      25  GROCERY              11  DAVES SPENDING 1ST HALF
 33  JOINT SPENDING MONEY JSA                      11  GAS DWH
 31  JUSTIN SPENDING MONEY                         10  MAKEUP
 28  MEDICAL              19  MICHELE ALIMONY DWH  10  FUND ENVELOPES
 27  GAS                  19  JUSTIN SPENDING MONEY JDH   9  NAILS
                          18  SPENDING              9  PAY BACK
                          14  CHRISTMAS
```

Coverage is falling, which matters for sizing the work. These are the stored `user_description`
values now, and they match what the extraction-over-payees estimate predicted — the backfill and the
import agree:

| Year | Transfers | With a memo | |
|---|---|---|---|
| 2023 | 386 | 295 | 76% |
| 2024 | 839 | 465 | 55% |
| 2025 | 794 | 398 | 50% |
| 2026 | 354 | 105 | 30% |

---

## 2. How much signal is in the memo

Two candidate readings of a memo, measured over the single-split transfers since 2024 that have both
a memo and a budget item. Phase 1's backfill grew that set from 960 to **1,044**.

### Reading A — lexical: the memo names the budget item

`RENT` → `Room rental`. `NAILS` → `Danni's Nails`. `MAKEUP` → `Danni's Skin Care and Makeup`.

Measured: the first word of the memo appears anywhere in the chosen budget item's name in
**256 of 1,044 cases (24.5%)** — worse than the 29.7% the earlier draft measured, because phase 1's
extraction fix recovered memos that are *more* specific than the budget's vocabulary, not less
(`JOINT SPENDING MONEY JSA` where the item is `Joint Spending Money`, and the first word is now
`JOINT` rather than `SPENDING`).

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
in the file. **Not implemented, and not worth implementing on these numbers.**

### Reading B — historical: the memo names what the user chose before

This is what ships. Two sub-readings were measured head to head on the identical corpus — the same
518 transfers that have a prior within 2024+ history:

| Reading | Had a prior | Correct | |
|---|---|---|---|
| **Plurality** — the item this memo has named most often, ties to the most recent | 518 | 437 | **84.4%** |
| Most recent — the item the single most recent earlier transfer named | 518 | 427 | 82.4% |

**Plurality wins, and is what `MemoBudgetItemHistory` implements.** The earlier draft of this
document specified the most-recent reading and measured it at 83.5%; measured directly against each
other, counting beats recency by two points, and it also yields the prior count that §3.1 needs to
weight the suggestion and show the user the evidence.

Register and direction scoping is load-bearing, not incidental. Dropping both takes plurality from
**84.4% to 74.4%** — ten points, and it makes 6 fewer correct suggestions while making 61 more
suggestions overall. The reason is that **both sides of one transfer carry the same memo and belong
to different budget items**: `RENT` is `Room rental` in Dave's budget and `Danni's contribution` in
Danni's, so an unscoped vote has every transfer arguing against itself.

### How much history to consult

Every number above restricts the history to 2024 and later. The first cut of phase 2 did not restrict
it at all, and that turned out to matter more than anything else in this section. Evaluated on the
same 2024+ transfers, varying only how far back the history is allowed to reach:

| History window | Suggestions | Correct | Accuracy |
|---|---|---|---|
| 6 months | 514 | 433 | 84.2% |
| 12 months | 551 | 446 | 80.9% |
| **18 months** *(shipped)* | **559** | **447** | **80.0%** |
| 24 months | 566 | 445 | 78.6% |
| 36 months | 571 | 435 | 76.2% |
| unbounded | 574 | 432 | 75.3% |

Unbounded is **dominated**: an 18-month window makes 15 more correct suggestions *and* is five points
more accurate. Reaching further back does not merely dilute the average — it actively loses correct
answers, because a memo that meant one item in 2023 and a different one now can be outvoted by its
own history. This is the same effect the earlier draft noticed as "2026 is worse than 2025 (73.3% vs
85.8%)" and attributed to budget re-creation; the real mechanism is stale votes, and a window is the
fix.

`HISTORY_WINDOW_MONTHS = 18` is that window. See §10 for how it was found and what it bought.

### Conclusion, which sets the shape of everything below

84% is excellent for **ordering a list** and unacceptable for **choosing silently** — one in six
would be wrong, and a wrong split is worse than a question, because nobody looks at it again. Hence
the rule the rest of this design obeys:

> **The memo prefers a budget item. It never selects one, and never rules one out.**

Deliberately weaker than the sibling rule already in `ForecastTransactionMatcher`, where the bank
reference *does* gate ("the reference confirms a match, it never gates one"). A reference is an
identity; a memo is an opinion.

---

## 3. Where the memo gets consulted

Three sites, in descending order of value.

### 3.1 Phase 4 — the ranked budget item list (the main event) — **shipped**

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
(0–20), clamped to 0–100. The memo is now a fourth component, applied *after* the existing clamp so
it cannot be swallowed by it, raising the effective ceiling to 130:

```
+30   MEMO_BONUS                 the memo's historical item for (memo, register, direction) is this item
+15   MEMO_BONUS_SINGLE_PRIOR    ... and that rests on exactly one earlier transfer
```

**Order of operations, which is the safety property.** The memo is consulted *after*
`autoAssignExactPerTransactionAmount` has already had its chance and declined:

```java
if (autoAssignExactPerTransactionAmount(transaction, splits, merchant, budgetItemsForMerchant)) {
    return;                       // reads a decision the user recorded in advance — untouched
}
MemoBudgetItemHistory.Suggestion memoSuggestion = lookUpMemoSuggestion(transaction, budget);
final BudgetItemMerchant memoExtraRow =
        appendMemoSuggestedItem(budgetItemsForMerchant, merchant, memoSuggestion);
if (memoSuggestion != null) {
    relevancyScores = calculateRelevancyScores(budgetItemsForMerchant, transaction);
    applyMemoBonus(budgetItemsForMerchant, relevancyScores, memoSuggestion);
    sortByRelevancyScore(budgetItemsForMerchant, relevancyScores);
}
```

That ordering makes "the memo cannot cause a silent assignment" structural rather than a rule to be
remembered — there is no code path on which the shortcut can see a memo-adjusted score. `lookUpMemoSuggestion`
swallows every exception and returns null: a broken history lookup degrades to the behaviour that
shipped before this feature, never to an abandoned import.

**Calibration.** 30 and 15 are the design's numbers, and `MemoRankingBacktest` (§8) now prices them
rather than leaving them to intuition. Essentially the same transfers benefit at every setting
(223–224, ~21.5%) — the constant only decides how far each one moves — and the curve has no knee:

| bonus | reached top of list | buried the right answer |
|---|---|---|
| 20 | 80 (7.7%) | 44 (4.2%) |
| **30** *(shipped)* | **104 (10.0%)** | **54 (5.2%)** |
| 45 | 153 (14.7%) | 58 (5.6%) |
| 60 | 186 (17.8%) | 65 (6.2%) |
| 80 | 211 (20.2%) | 78 (7.5%) |

Measured with the §10 window in place; every row of this table improved when that landed, which is
why it is worth re-running the sweep after any change to the history scoping rather than treating
these as fixed.

So the data prices a value; it does not pick one. 30 was kept because amount similarity is worth
0–60, the largest single input, and a memo worth as much as the strongest factual signal has stopped
*preferring* an item and started *choosing* it. Raising toward 45 buys roughly ten more top-of-list
placements per five points at a cost of two more burials; going past 60 gives up the rule in §2.

**Display.** `BudgetController.showBudgetItemsForMerchant` takes the suggestion and names the
evidence on the one row it applies to:

```
   1.  Room rental (Household, $-750 Monthly), Relevancy Score: 92.3  ← memo "RENT" (42 priors)
```

Naming the evidence is what makes a 16%-wrong suggestion safe: the user can see *why* it is first.
The three-argument overload still exists and delegates, so every other caller is unchanged.

**When the suggested item is not in the list.** `budgetItemsForMerchant` contains only items already
associated with this merchant, so the memo's item may not be present at all. It is appended as an
ordinary, selectable row labelled:

```
   1.  Room rental (Household, $-750 Monthly), Relevancy Score: 80.0  ← memo "RENT" (42 priors), not yet assigned to this merchant
```

The row is a **transient, unsaved `BudgetItemMerchant`** — the same mechanism the existing budget-item
search already uses for one-time-use items. This is a deliberate departure from the earlier draft,
which said to route it through `BudgetItemMerchantController.addBudgetItemToMerchant`: that method is
private and interactive, and asks its own questions. Instead `settleMemoSuggestedItem` runs once the
user has answered:

- if a split was assigned to the row, it offers *"Permanently associate 'Room rental' with merchant
  'HIXON D'? (y/n) [y]"* and saves the association on yes, so next time the item is in the list on its
  own merit;
- if the row was ignored, it is removed again, leaving the caller's list exactly as it was found.

Two smaller consequences of appending a row, both handled:

- `allFixed` — which decides whether the prompt offers "just return to accept displayed amounts" —
  now excludes the memo row. A suggested item has no configured amount, and must not turn a
  merchant with pre-established amounts into a manual one.
- the single-item `askAlways` shortcut is skipped whenever a row was appended, since the list is no
  longer of size one. That is correct: the whole point is that the user sees both candidates.

**What the memo must not touch.** `autoAssignExactPerTransactionAmount` reads a decision the user
recorded in advance. It is not a guess, and the memo has no business in it. Left alone, and guarded
by both the ordering above and a test asserting an appended row can never satisfy
`hasExactPerTransactionAmount` (it carries amount 0 and percentage 0 by construction).

### 3.2 Phase 2.5 — forecast transaction scoring (tie-break only) — **not yet built**

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
and runs a full-text search over `user_description`. No change here, but it inherited the extraction
fix from §1, and §5 finally gave its full-text search something to search.

---

## 4. Where the memo→budget-item association comes from

**Derived from transaction history at query time. There is no learned mapping table.**

Two reasons — one historical, one structural.

The historical one: `e6253c8` deleted exactly such a table. `transfer_memo_mapping` keyed a
normalized payee string to a register, and `TransferBudgetItemPair`'s class comment records why it
was removed — "it was keyed on a payee string such as `HIXON D`, which is ambiguous across accounts,
and so fixed an ambiguous payee to a single register." The failure was not *learning*; it was
choosing a key carrying less information than the question needed. The key here — **(normalized
memo, register, direction)** — is the one §2 measured at 84.4%, and dropping either scope term
measurably degrades it.

The structural one, which is the stronger argument: a mapping table has to be invalidated when the
user recategorizes a transaction, and nothing would do that. Derived history is correct by
construction — recategorize a transfer and the next suggestion changes, with no `learn()` call, no
staleness, and no unlearn path to build. `TransferBudgetItemPair` earns its table because it records
something *no transaction shows* (which item on the far side corresponds to this one). A memo
association is fully visible in the transactions themselves.

### The shipped query

`MemoBudgetItemHistory.buildCandidateQuery`, per candidate transaction:

```sql
select bin_to_uuid(bi.idBudgetItem) as 'mh.idBudgetItem', bi.payee as 'mh.payee',
       count(*) as 'mh.priors'
from   transaction t
join   transaction_split ts on ts.Transaction_idTransaction = t.idTransaction
join   budget_item bi       on bi.idBudgetItem = ts.BudgetItem_idBudgetItem
where  t.user_description    = '<normalized memo>'
  and  t.Register_idRegister = uuid_to_bin('<this register>')
  and  sign(t.amount)        = <sign of this transaction>
  and  (select count(*) from transaction_split s
        where s.Transaction_idTransaction = t.idTransaction) = 1
  and  t.idTransaction      <> uuid_to_bin('<this transaction>')
  and  t.postDate           >= '<this transaction's post date, less 18 months>'
group by bi.idBudgetItem, bi.payee
order by count(*) desc, max(t.postDate) desc
```

Four differences from the draft version of this query, all deliberate:

- **The 18-month window**, `HISTORY_WINDOW_MONTHS`, anchored on the transaction rather than on today.
  §10 has the measurement; the short version is that unbounded history is dominated on both accuracy
  and correct-suggestion count.

- **No `LIMIT 1`.** The full ranked candidate list comes back and `lookup` takes the first. The
  losing candidates are what make `COVER OVERDRAFTS` explicable rather than merely wrong, and having
  them in hand costs nothing.
- **`max(t.postDate)` appears only in the `order by`,** not the select list — the tie-break needs it,
  nothing else does.
- **An optional `and t.postDate < …` cutoff**, set through `MemoBudgetItemHistory.asOf(Calendar)`.
  Null in the application, which asks the question as of now. Only `MemoRankingBacktest` sets it,
  because measuring a suggestion against history that contains the answer measures nothing.

Three rules the measurements dictate, all implemented:

- **Multi-split transactions are excluded.** `FUND ENVELOPES` is a bulk envelope-funding transfer
  that splits across ten different budget items; counting each split as a vote for the memo is noise.
  That is the `= 1` subquery above.
- **Budget items are per-budget and get re-created.** `resolveIntoBudget` matches on `idBudgetItem`
  first; if the winner belongs to a budget that is not the current one — or has been deleted
  outright, so the load throws — it falls back to `BudgetItem.getUnexpiredByPayee` in the current
  budget. An ambiguous name (two unexpired items with the same payee) yields **no suggestion** rather
  than a guess.
- **A memo seen once is a weaker suggestion than one seen forty times.** `priors` is carried through
  to the display and to the bonus, as `priors == 1 ? 15 : 30`.

### Testing seams

The class is a plain object, not an entity, and its three database touches are `protected` so the
logic above them can be exercised without a database:

| Seam | Overridden in tests by |
|---|---|
| `fetchCandidates(memo, register, sign, transaction)` | an in-memory history table that filters exactly as the SQL does |
| `loadById(idBudgetItem)` | a fixed set of items, or a throw, for the deleted-item case |
| `loadUnexpiredByPayee(budget, payee)` | a fixed set, for the stale-budget and ambiguous-name cases |

`buildCandidateQuery` is package-visible and static so the scoping that the accuracy depends on is
assertable directly as SQL text — the register clause, the sign clause, the single-split subquery and
the self-exclusion are each worth measured accuracy, so each has an assertion.

---

## 5. The one persistence change: actually write `user_description` — **shipped in phase 1**

`transaction.user_description VARCHAR(64)` existed. It had a FULLTEXT index
(`user_descripton_idx`). `com.hixon.utilities.BackfillUserDescriptions` existed to populate it. And
`TransactionUtilities.getByUserDescriptionFullText` read it.

**Nothing in the application ever wrote it.** `Transaction` had no such field; the column was absent
from `selectColumns`, `insertQuery`, `getInsertOnDuplicateUpdateQuery` and `getUpdateByIdQuery`.
Every 2026 row was NULL — the historical values came from the one-off backfill and stopped when it
stopped being run.

So the whole of §4 needed one change, and no migration:

1. `Transaction` gained a `userDescription` field with getter/setter (the setter sets the dirty flag
   and truncates to `USER_DESCRIPTION_MAX_LENGTH`, per the entity convention), and the column was
   added to `selectColumns`, `insertQuery`, `getInsertOnDuplicateUpdateQuery`, `getUpdateByIdQuery`
   and the `ResultSet` constructor.
2. It is populated where the payee is parsed. `FinancialInstitution.convertQfxToTransaction` and
   `WellsFargoBank.loadFromCsvRecord` both already called `parseMerchantPayee`; they now call
   `extractUserDescription(payee)` alongside it.
3. `BackfillUserDescriptions` was re-run once for history. It used to hardcode `root` and a literal
   password; it now reads `db.properties`, and it delegates extraction to
   `WellsFargoBank.extractMemoFromPayee` instead of keeping a private copy that fixes never reached.
   The re-run rebuilt 1,851 memos (from 1,396), cleared 57 rows still carrying account-type text, and
   populated 2026, which had none.

Adjacent bug found while reading this path, **also fixed in phase 1**:
`getByUserDescriptionFullText` computed its `relevance` column as
`MATCH(user_description) AGAINST ('ALIMONY' …)`, a hardcoded literal, while the `WHERE` clause used
the real search term. The `ORDER BY relevance DESC` therefore ranked by similarity to the word
"ALIMONY" regardless of what was searched. It now scores against the term actually searched for.

---

## 6. The pending/cleared problem — **not yet addressed**

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
the value in §2 is available without touching the reconcile path at all. **This is where the code
stands today.**

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

| # | Work | Ships | Status |
|---|---|---|---|
| 1 | Fix `extractUserDescription` phrase-stripping (§1); add `userDescription` to `Transaction` and write it at import (§5); re-run the backfill | Better register-nickname matching immediately; the data §2–§4 need | **shipped** — `219a234` |
| 2 | `MemoBudgetItemHistory` lookup (§4) + Phase 4 ranking bonus and display (§3.1) | **The feature as asked for** | **shipped** |
| 2.1 | Bound the history window to 18 months (§2, §10) | 447 correct suggestions instead of 432, and 54 burials instead of 69 | **shipped** |
| 3 | Phase 2.5 tie-break with the memo-free threshold invariant (§3.2) | Planned transfers | outstanding |
| 4 | Late-memo confirmation at reconcile (§6b) | Pending-first transfers; protects the history | outstanding |

Phase 1 had to land and be backfilled before Phase 2 could be measured honestly, and it was.

### What phase 2 added

| File | |
|---|---|
| `model/budget/MemoBudgetItemHistory.java` | new — the lookup, `HISTORY_WINDOW_MONTHS`, its records `Candidate` and `Suggestion`, and the three testing seams |
| `controller/TransactionSplitsController.java` | `MEMO_BONUS`, `MEMO_BONUS_SINGLE_PRIOR`, `lookUpMemoSuggestion`, `appendMemoSuggestedItem`, `applyMemoBonus`, `memoBonus`, `settleMemoSuggestedItem`; `calculateRelevancyScores` and `sortByRelevancyScore` made `static` |
| `controller/BudgetController.java` | five-argument `showBudgetItemsForMerchant` overload and `memoAnnotation` |
| `utilities/MemoRankingBacktest.java` | new — the bulk measurement harness |

`calculateRelevancyScores`, `sortByRelevancyScore`, `applyMemoBonus` and `appendMemoSuggestedItem`
are `public static` rather than private, so the backtest in `com.hixon.utilities` can replay the real
ranking instead of reimplementing it. That is the mistake `BackfillUserDescriptions` used to make
with the extraction logic, and it is not worth repeating for the scorer.

---

## 8. Testing

Per `CLAUDE.md`, JUnit with mocks. None of these need a database. 33 tests across two classes; the
full suite is 384 and green.

**Extraction — `WellsFargoBankMemoExtractionTest`** (9 tests, phase 1), table-driven over real payee
strings:

| Input | Expected |
|---|---|
| `… REF #IB0Z8W2598 EVERYDAY CHECKING RENT` | `RENT` |
| `… REF #IB0YZ8KPC9 WELLS FARGO CLEAR ACCESS BA PATIO` | `PATIO` |
| `… REF #IB0Y89FF7K WAY2SAVE SAVINGS DOCTOR` | `DOCTOR` |
| `… EVERYDAY CHECKING XXXXXX7018 REF #IB0YLYCRJ2 ON 06/21/26` | `null` |
| `ONLINE TRANSFER FROM HIXON D REF` | `null` |
| `ONLINE TRANSFER FROM HIXON D REF #IB0ZHVQXST WA` *(truncated pending)* | `null` |
| `… REF #IB0ZGXJP6L EVERYDAY CHECKING JOINT SPENDING MONEY JSA` | `JOINT SPENDING MONEY JSA` |

**History lookup — `MemoBudgetItemHistoryTest`** (19 tests), against an in-memory history table whose
filtering mirrors the SQL clause for clause, so that a test asserting "the opposite direction is not
consulted" asserts something about the lookup rather than about the fake:

- same memo, same register, same direction → returns the item, with `priors`
- same memo, **opposite direction** → no suggestion *(the `RENT` both-sides case)*
- same memo, **different register** → no suggestion
- memo on a multi-split transaction → excluded from the vote *(`FUND ENVELOPES`)*
- three-way disagreement → the plurality wins, with its count *(`COVER OVERDRAFTS`)*
- winner in a stale budget → falls back to the same-payee item in the current budget
- winner deleted outright → same fallback, via the thrown load
- two unexpired items of the same name → no suggestion, rather than a guess
- memo present, no priors → no suggestion, no exception
- no memo at all, blank memo, null transaction, null budget → no suggestion
- normalization: case, surrounding and internal whitespace
- the generated SQL carries the register, sign, single-split and self-exclusion clauses
- a memo containing an apostrophe is escaped *(`DANNI'S HAIR` is a real memo, and these queries are
  concatenated rather than prepared)*
- `describe()` reads correctly in singular and plural

**Phase 4 ranking — `TransactionSplitsControllerMemoRankingTest`** (14 tests):

- the bonus goes to the memo's item and to nothing else
- `priors == 1` is worth half
- the bonus lands **after** the clamp — asserted by showing the total exceeds 100
- the memo's item sorts first, in the case the design was written for
- the memo **cannot** overturn an exact amount match
- a single-prior memo does not displace a strong amount match
- no memo → scores byte-identical to today *(the regression guard for the ~50% with no memo)*
- a suggestion for an item not in the list disturbs no score
- an absent item is appended; a present one is not appended twice; no memo appends nothing
- an appended row can never satisfy `hasExactPerTransactionAmount`
- the annotation labels the memo's row, only that row, and says when the item is not yet associated

**Phase 2.5 — `ForecastTransactionMatcherMemoTest`.** Not yet written; phase 3. The invariant is the
point:

- memo agreement reorders two candidates that both clear 70
- **a candidate whose memo-free score is 69 is not auto-matched, whatever the memo says**
- `ReferenceVerdict.CERTAIN` still short-circuits before any memo is consulted
- `RULED_OUT` still wins over memo agreement

**Backtest harness — `com.hixon.utilities.MemoRankingBacktest`.** Ranking changes are only honestly
judged in bulk, so this is a `main()`-style tool that hits the real database, like the others in that
package. It replays every single-split memo-bearing transfer from a given year, rebuilding the list
the import would have shown — the merchant's real associations, scored by the real scorer — and
reports where the item the user actually chose ended up. Each transfer asks the history *as of its
own post date*, so no suggestion is informed by the answer it is being scored against.

```
mvn -o dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java --enable-preview -cp "target/classes;$(cat target/cp.txt)" \
     com.hixon.utilities.MemoRankingBacktest 2024
```

As shipped, over 1,044 transfers:

```
Memo produced a suggestion            553  (53.0%)
  ... and it was what the user chose  447  (80.8%)
  ... resting on a single prior        81  (14.6%)
Chosen item was absent from the list   10   (1.0%)
  ... and the memo put it there         2   (0.2%)

Chosen item moved up                  224  (21.5%)
Chosen item moved down                 54   (5.2%)
Chosen item did not move              766  (73.4%)
Reached the top of the list           104  (10.0%)
Was already at the top                350  (33.5%)
```

The memo lifts the right answer four times as often as it buries it. **Moved down is the number to
watch** — the memo is allowed to be wrong, but not to bury the right answer for the transfers it says
nothing useful about. It is also the number that found §10: every unit test passed while that figure
was 69, because no single behaviour was wrong, only the aggregate.

---

## 9. Risks

- **Roughly one in five memo suggestions is wrong** (80.8% correct as shipped). Mitigated
  structurally by §2's rule (never selects) and §3.1's labelling (the user sees the evidence). Not
  mitigated by any threshold, because none exists that separates the good cases from the bad — which
  is precisely what killed the earlier dominant-relevancy-score shortcut.
- **Genuinely ambiguous memos.** `COVER OVERDRAFTS` maps to `Short term borrowing` 19×,
  `Joint Spending Money` 13× and `Other` 7×. The design surfaces the plurality and the count; it
  cannot do better, because the user themselves is not consistent here.
- **Stale history actively hurts.** Bounded to 18 months in phase 2.1 (§10). The window is a
  constant, and the right value drifts with how often budget items are re-created — re-run the
  backtest before assuming 18 is still right a year from now.
- **Coverage is falling** (76% → 30% since 2023). The feature quietly rewards writing memos and does
  nothing for the growing majority without one. Worth knowing before weighting the effort.
- **Silent regression for the ~50% with no memo** was the main correctness risk, and is covered by
  the byte-identical-scores test above.
- **Multi-split transfers** would poison the history if counted; excluded in §4.
- **The list can now contain a row the merchant is not associated with.** Handled at every branch of
  the split-entry loop (`allFixed`, the `askAlways` shortcut, `d`-delete, and the settle step), but it
  is the part of phase 2 with the widest blast radius if a branch was missed.

---

## 10. The history window — **shipped in phase 2.1**

`MemoBudgetItemHistory` originally consulted every transfer the register had ever recorded. §2
measures that as **strictly worse** than an 18-month window — 432 correct suggestions at 75.3%
against 447 at 80.0%. Reaching further back does not merely dilute the average; it loses correct
answers outright, because a memo that meant one item two years ago can outvote what it has meant
since.

`HISTORY_WINDOW_MONTHS = 18` now bounds it, as one clause in `buildCandidateQuery` alongside the
`postedBefore` cutoff that was already there for the backtest:

```sql
and t.postDate >= <this transaction's post date> - interval 18 month
```

18 months is the maximum of the measured curve on both axes at once. 12 months is within one
suggestion of it and half a point more accurate, so anything in 12–18 is defensible; 6 months is the
most accurate setting but gives up 13 correct suggestions to get there.

**The window is anchored on the transaction being categorized, not on today.** Categorizing an old
transaction — or replaying one in the backtest — therefore asks the question the way it would have
been asked at the time. `historyWindowStart` clones the calendar before subtracting, since
`Calendar.add` mutates in place and the argument is the transaction's own post date.

This was found by `MemoRankingBacktest` after the rest of phase 2 was written, which is the case for
having built the harness at all: it is not a defect any unit test would have surfaced, because every
individual behaviour was correct. Only the aggregate was wrong.

### Measured effect of the fix

The same 1,044 transfers, before and after:

| | unbounded | 18-month window |
|---|---|---|
| Suggestions made | 566 (54.2%) | 553 (53.0%) |
| ... correct | 432 (**76.3%**) | 447 (**80.8%**) |
| Chosen item moved up | 211 (20.2%) | **224 (21.5%)** |
| Chosen item moved **down** | 69 (6.6%) | **54 (5.2%)** |
| Places gained, in total | 1,459 | **1,606** |
| Lift-to-bury ratio | 3.1 : 1 | **4.1 : 1** |

Every axis improved, including the one that matters most — the memo now buries the right answer 15
fewer times while lifting it 13 more.

---

## Appendix — reproducing the numbers

All queries below were run on 2026-08-28 against `ForecastDatabase`, after phase 1's backfill.

**Memo coverage by year**, from the stored column rather than from re-running extraction:

```sql
SELECT YEAR(postDate) AS yr, COUNT(*) AS transfers,
       SUM(user_description IS NOT NULL AND user_description <> '') AS with_memo
FROM   transaction
WHERE  UPPER(payee) LIKE '%TRANSFER%' AND YEAR(postDate) >= 2023
GROUP BY YEAR(postDate);
```

**The two readings of a memo, head to head.** The `ev` CTE is the corpus: single-split,
memo-bearing, 2024 onwards. Swap the correlated subquery for the other reading; the `WHERE pred IS
NOT NULL` at the end is what makes both share the same 518-row denominator.

```sql
WITH ev AS (
  SELECT t.user_description AS memo, t.postDate AS pd,
         t.Register_idRegister AS reg, SIGN(t.amount) AS sgn,
         ts.BudgetItem_idBudgetItem AS item
  FROM   transaction t
  JOIN   transaction_split ts ON ts.Transaction_idTransaction = t.idTransaction
  WHERE  t.user_description IS NOT NULL AND t.user_description <> ''
    AND  YEAR(t.postDate) >= 2024
    AND  (SELECT COUNT(*) FROM transaction_split s
          WHERE s.Transaction_idTransaction = t.idTransaction) = 1
)
SELECT COUNT(*) AS had_a_prior, SUM(pred = item) AS correct
FROM (
  SELECT e.item,
    -- plurality (shipped):
    (SELECT p.item FROM ev p
      WHERE p.memo = e.memo AND p.reg = e.reg AND p.sgn = e.sgn AND p.pd < e.pd
      GROUP BY p.item ORDER BY COUNT(*) DESC, MAX(p.pd) DESC LIMIT 1) AS pred
    -- most recent (the earlier draft's reading):
    -- (SELECT p.item FROM ev p
    --   WHERE p.memo = e.memo AND p.reg = e.reg AND p.sgn = e.sgn AND p.pd < e.pd
    --   ORDER BY p.pd DESC LIMIT 1) AS pred
  FROM ev e
) x WHERE pred IS NOT NULL;

→ plurality      518 had a prior   437 correct   84.4%
→ most recent    518 had a prior   427 correct   82.4%
```

**The value of register/direction scoping.** Same query, with `p.reg = e.reg AND p.sgn = e.sgn`
removed from the subquery. Note that the denominator grows — dropping the scope finds *more* priors,
and they are worse than useless:

```
→ scoped         518 had a prior   437 correct   84.4%
→ unscoped       579 had a prior   431 correct   74.4%
```

**The lexical reading (§2, reading A)**, for completeness — it is measured but not implemented:

```sql
SELECT COUNT(*), SUM(UPPER(bi.payee) LIKE CONCAT('%', SUBSTRING_INDEX(t.user_description,' ',1), '%'))
FROM   transaction t
JOIN   transaction_split ts ON ts.Transaction_idTransaction = t.idTransaction
JOIN   budget_item bi       ON bi.idBudgetItem = ts.BudgetItem_idBudgetItem
WHERE  t.user_description IS NOT NULL AND t.user_description <> ''
  AND  YEAR(t.postDate) >= 2024
  AND  (SELECT COUNT(*) FROM transaction_split s
        WHERE s.Transaction_idTransaction = t.idTransaction) = 1;

→ 1044 rows   256 lexical hits   24.5%
```

**The history window (§10).** Same shape, but `hist` drops the year filter so the history reaches as
far back as the data goes, while the evaluated set stays at 2024+ — which is what the application
actually does. Add `AND p.pd >= e.pd - INTERVAL n MONTH` to the subquery to bound it:

```
 6 months    514 suggested   433 correct   84.2%
12 months    551 suggested   446 correct   80.9%
18 months    559 suggested   447 correct   80.0%
24 months    566 suggested   445 correct   78.6%
36 months    571 suggested   435 correct   76.2%
unbounded    574 suggested   432 correct   75.3%
```

These SQL figures and the harness's differ by a handful of suggestions in each row, always in the
same direction and for the same reason: `resolveIntoBudget` declines to guess when a winning item's
name is ambiguous or expired in the current budget, so the harness makes slightly fewer suggestions
than raw id-matching would. The unbounded row is the clearest case — SQL 574/432 against the
harness's 566/432, the same correct answers with eight fewer suggestions. At the shipped 18-month
window the harness reports **553 suggestions, 447 correct (80.8%)** against this table's 559/447.

**Ranking movement and the bonus sweep** come from `MemoRankingBacktest` itself; see §8 for the
invocation. The sweep in §3.1 was produced by rebuilding with `MEMO_BONUS` set to each value in turn.
