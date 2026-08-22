# Linked Account Transfers — Design

**Status:** draft for review. Nothing implemented yet.

## Goal

A transfer between two registers is one movement of money and should be dealt with once. Today it is
handled twice — once from each account's own statement, each time asking for a merchant and a budget
item.

After this change, processing a transfer in one register records the expected other side. When the
second account's statement is imported, that transaction auto-matches, reports where it came from,
and is passed over without questions.

## How big is this actually

Phase 2.5 matches an imported transaction against **forecast** transactions, and on-demand budget
items never produce any (`generateForecastItems` skips them outright). So the budget item a transfer
was assigned to tells us whether auto-matching was even possible.

For the 334 transfer transactions in 2026:

| Could Phase 2.5 have auto-matched it? | Transactions |
|---|---|
| No — assigned to an on-demand / unplanned item | **231** |
| Yes — assigned to a scheduled item | 103 |

And the 231 are overwhelmingly the ad-hoc transfers:

```
Other                   68  +  32     (one copy per budget)
Reimbursement           52  +  28
Short term borrowing    18  +  14
                     ------------
                       212 of 231
```

Two conclusions:

1. **Budgeted transfers already work.** The rent transfer has a real budget item on both sides
   (`Danni's contribution` / `Room rental and utilities`), so the forecast already contains a planned
   transaction and Phase 2.5 already matches it. **This design deliberately leaves those alone** —
   if an item is planned there is already a forecast transaction for it and a second one would only
   compete with it.
2. **The effort is in the ad-hoc transfers**, which have no planned forecast transaction by
   definition. That is what this design targets. The paired counts above are the two budgets' copies
   of the same transfers, so roughly half of those 231 are second sides that would stop needing
   attention.

## Approach

**When a transfer is processed in one register, create a forecast transaction for the expected other
side in the counterparty register's forecast. Then let Phase 2.5 do what it already does.**

Phase 2.5 auto-matches at a score of 70 or better, and a forecast transaction created at the actual
date and amount scores far above that. On a match it takes the budget item from the matched forecast
item, builds the split, and everything downstream is skipped:

```java
/*
 * Phase 3:  Get the assigned budget items for this merchant:
 */
// If there was a provisional transaction with assigned splits, then the splits are already assigned.
// If that is not the case then we need to assign the splits now.
if (splits == null) {
```

This is deliberately the smallest possible change, and it is worth being explicit about what it
avoids compared with creating a compensating *transaction* in the other register:

- **No new matching code.** `ForecastTransactionMatcher` already does it.
- **No balance handling.** A forecast transaction does not touch `register.balance`, so there is no
  double-count risk and no ordering problem with the balance update in Phase 2.
- **No interaction with the pending sweep.** That sweep only reads `cleared = false` *transactions*.
- **Almost no lifecycle work.** A forecast transaction is already a thing the app creates, edits,
  regenerates and deletes.

It is also the honest model. "This transfer should turn up in Dave's account" is an expectation, and
this application already represents expectations as forecast transactions. Creating them on the fly
is an established pattern — `ForecastController.reconcile` builds a forecast item and transaction
when no applicable one exists, and the fallen-off cleanup already knows about "UNPLANNED forecast
transactions that were created solely to reconcile this provisional transaction's splits".

### On balance accuracy

A register balance is only true immediately after that register is imported, so mirroring a
*transaction* into a register that is never imported was buying a correctness that does not exist.
Real-time balances need real-time importing, which is a separate problem.

## The convention that resolves feedless registers

The awkward case in every earlier draft was the six registers with no import feed: anything written
there is never matched, and sits forever as a stale expectation.

The rule:

> **If the counterparty register has no forecast, do not create a forecast transaction for it.**
>
> **And do not create a forecast for a register with no import feed.**

The important property is that **the code contains no active/inactive test**. It asks a data
question — "does this register have a forecast?" — and the answer encodes the intent. Whether a
register has a feed is a decision made once, by hand, when the forecast is or is not created. Nothing
infers it.

This also means turning on a feed for `Dave's Spending Account` later needs no code change: create a
forecast for it and transfers start landing there.

### Prerequisite: a forecast belongs to one register

The convention cannot be expressed today, because a forecast belongs to a **budget**, and six
registers share the Bill Pay Dave budget:

```
Bill Pay Dave, Dave's Spending Account, Justin's Spending, Justin's Savings,
Christian's Checking, Christian's Savings   ->   Bill Pay Account - Dave Forecast
```

So "Dave's Spending Account has no forecast" is not currently a statement that can be made — its
budget has one. Per your note, a forecast should belong to **one and only one register**, with a
register free to have several.

This is a real change, and it should be its own piece of work before the transfer feature:

```sql
ALTER TABLE forecast ADD COLUMN Register_idRegister BINARY(16) NULL;
```

Roughly 65 call sites across 23 files navigate budget↔forecast↔register today. Most keep working —
the budget association stays, since forecast items still reference budget items — but forecast
selection (`Forecast.getListOf(budget)`, `getMostRecent(budget)`,
`SessionController.getRegisterBudgetForecast()`) becomes register-scoped.

It also fixes an existing wart. `AbstractForecastView.renderLongTermForecast` currently does:

```java
// Get the starting balance.  Take if from the first register associated with the budget for now:
List<Register> registers = forecast.getBudget().getRegisters();
double startingBalance = registers.get(0).getBalance();
```

`registers.get(0)` is arbitrary among six. With the forecast owning a register, it becomes correct
rather than a "for now".

**Decided: do this first, as its own change**, before any of the transfer work.

The forecast that had no register at all, `Joint Spending Account`, has been deleted. It left no
orphaned forecast items or forecast transactions behind. The four that remain each own exactly one
register:

| Forecast | Register |
|---|---|
| Bill Pay Account - Danni Forecast | Bill Pay Danni |
| Bill Pay Account - Dave Forecast | Bill Pay Dave |
| Bill Pay Envelopes Forecast | Bill Pay Envelopes |
| Citi AAdvantage Mastercard Forecast | Citi AAdvantage Mastercard |

In every case the forecast belongs to the register that carries the money, so the seven registers
that share a budget with one of these — `Danni's Spending Account`, `Dave's Spending Account`, both
of Justin's, both of Christian's, and `Joint Savings Account` — end up with **no forecast**. That is
the intended outcome, not an omission: it is precisely what makes the convention above decide not to
create counterpart expectations for them.

The migration is therefore deterministic and can be written by name rather than by hand:

```sql
ALTER TABLE forecast ADD COLUMN Register_idRegister BINARY(16) NULL;

UPDATE forecast f
  JOIN register r ON r.Name = CASE f.description
        WHEN 'Bill Pay Account - Danni Forecast' THEN 'Bill Pay Danni'
        WHEN 'Bill Pay Account - Dave Forecast'  THEN 'Bill Pay Dave'
        WHEN 'Bill Pay Envelopes Forecast'       THEN 'Bill Pay Envelopes'
        WHEN 'Citi AAdvantage Mastercard Forecast'       THEN 'Citi AAdvantage Mastercard'
      END
  SET f.Register_idRegister = r.idRegister;
```

The column stays nullable so a forecast with no register is representable rather than an error, but
after this migration every forecast has one and new forecasts should require it.

Your instinct that multiple forecasts per register might be useful for modelling alternative budgets
is worth keeping in mind but not designing for now — one nullable column supports it whenever you
want it.

## What gets built

### 1. Create the counterpart expectation

A new phase in `ImportController`, after the transaction, merchant and splits are settled for the
transaction being imported:

> **Phase 5.5: Record the other side of a transfer**

```
if the transaction is not a transfer                        -> done
if the counterparty register cannot be determined           -> done
if the counterparty register has no forecast                -> done
if a counterpart forecast transaction already exists        -> done

for each split on this transaction:
        if the split's budget item is planned (not on-demand/unplanned) -> skip this split
        find the counterpart budget item in the counterparty forecast's budget (section 2)
        create a forecast transaction on the same date for the negated split amount
```

The counterparty register is **already resolved during import**. `WellsFargoBank` reads the last four
digits out of the payee, and falls back to `RegisterController.resolveUnmatchedAccount(...)`, which
narrows by account type and user, asks only when still ambiguous, and caches the answer for the
session. This design consumes a decision the import already makes and adds no account-identification
prompt.

Working per split rather than per transaction matters: a transfer covering two things on the source
side should arrive as two expectations, not one lump. It also means a transfer that is part planned
and part ad-hoc produces an expectation only for the ad-hoc part.

**Only ad-hoc splits produce a counterpart.** A split against a planned item already has a forecast
transaction waiting on the far side, which Phase 2.5 will match on its own; creating a second one on
the same date would give the matcher two candidates for the same money and risk it picking the new
one, leaving the planned transaction stranded and the forecast overstated.

> **Residual risk.** This assumes that when the source side is planned, the target side is too. If
> you have a transfer that is budgeted on one side only, its far import will keep asking. Worth
> watching for once this is running; the fix would be to gate on the *target* item's existence rather
> than the source item's type.

### 2. The counterpart budget item

This is inherent to the goal, not a property of this approach. A forecast transaction needs a
forecast item, which needs a budget item — so something has to know that Danni's
`Danni's contribution` corresponds to Dave's `Room rental and utilities`.

The pairing is stable, so ask once and remember it:

```sql
CREATE TABLE transfer_budget_item_pair (
  idTransferBudgetItemPair BINARY(16) NOT NULL PRIMARY KEY,
  sourceBudgetItem         BINARY(16) NOT NULL,
  targetBudget             BINARY(16) NOT NULL,
  targetBudgetItem         BINARY(16) NOT NULL,
  UNIQUE KEY uk_source_target (sourceBudgetItem, targetBudget)
);
```

Same shape as the existing `budgetitem_merchant` mapping.

> **Caution.** This is close in spirit to the `TransferMemoMapping` removed in `e6253c8` for
> "incorrectly fixing an ambiguous payee (e.g. HIXON D) to a single register". The difference is the
> key: that mapping was keyed on a *payee string*, ambiguous across accounts, whereas this is keyed
> on a *budget item plus target budget*, which already carries the semantics. You hit that problem
> first-hand, so you should be the judge.

### 2a. The first time a pairing is not known

**Decided: never interrupt the source import to ask.** The counterpart is created regardless, and the
far import's existing logic assigns the budget item — including creating a new one if it needs to.
The pairing is then learned from what was chosen, so the question is asked once ever, in the place
the application already asks it.

This needs one piece of care. A forecast transaction cannot be created "with no splits" the way a
transaction can — it needs a forecast item, which needs a budget item, and that is exactly what
Phase 2.5 reads:

```java
UUID idBudgetItem = matchedForecast.getForecastItem().getIdBudgetItem();
splits = new ArrayList<>();
splits.add(new TransactionSplit(currentTransaction.getAmount(), idBudgetItem, ...));
```

A counterpart created against a placeholder item would therefore auto-match, assign the *placeholder*
as the budget item, and set `splits` non-null — which skips Phases 3 and 4 and suppresses the very
logic that should be asking. Silently wrong, rather than a question.

So the counterpart carries a flag saying whether its budget item is trusted:

```
pairing known    -> create against the paired budget item
                    Phase 2.5 auto-matches, assigns it, no questions       [the steady state]

pairing unknown  -> create against an unplanned item, flagged unpaired
                    Phase 2.5 recognises the flag and does NOT assign a split;
                    it reports "this is the other side of <source transaction>"
                    and falls through to Phases 3 and 4, which ask as they do today
                    and may create a new budget item
                    afterwards, record the pairing from whatever was chosen,
                    and drop the placeholder forecast transaction
```

The counterpart still earns its place in the unpaired case: it is what tells the far import that this
transaction is the other side of a transfer already dealt with, and it carries `sourceTransaction`,
which is what makes learning the pairing possible at all. Without it there is nothing linking the two
sides and no way to know which source budget item to pair with.

From the second occurrence of that pairing onward, the far import is silent.

### 3. Reporting the match

Phase 2.5 already announces its match:

```java
view.sayH3("Auto-matched to forecast transaction: " + matchedForecast.toStringConcise());
```

Mark forecast transactions created this way so the message can instead read
`Taken from the corresponding transfer in Bill Pay Danni on 08-03-2026`, making it obvious why no
questions were asked. A nullable `sourceTransaction` column on `forecast_transaction` is enough, and
doubles as the "already created" check in Phase 5.5 and as the audit trail.

### 4. Lifecycle

| Event | Behaviour |
|---|---|
| Counterpart matched and reconciled | Normal Phase 2.5 path; nothing special. |
| Source transaction deleted before the far import | Delete the counterpart forecast transaction. |
| Source amount or date edited | Update the counterpart forecast transaction. |
| Forecast regenerated before the far import | The counterpart must survive. `updateForecast` preserves transactions that are `overridden` or have splits, so create it **overridden**. |
| Counterpart never matched | It ages in the forecast like any unreconciled planned transaction. |

The regeneration point is easy to miss and would silently undo the feature.

### 5. The bank reference number as a confirmation signal

Wells Fargo issues its own reference for a transfer and writes **the same string into both sides**:

```
Bill Pay Dave    -30.00  ONLINE TRANSFER TO   HIXON D ... REF #IB0ZBFJRYR ON 08/11/26
Bill Pay Danni   +30.00  ONLINE TRANSFER FROM HIXON D ... REF #IB0ZBFJRYR ON 08/11/26
```

Opposite signs, same date, different registers, one reference. Where both sides carry it, this is an
*exact* identity for "these two rows are the same movement of money" — no scoring involved.

It is tempting to build the whole feature on that. The measurements say otherwise.

#### What the data actually shows

For 2026:

| | Count |
|---|---|
| Transfer transactions | 334  (Bill Pay Danni 236, Bill Pay Dave 98) |
| …carrying a `REF #` at all | **143  (43%)** |
| …forming a true cross-register pair | **15** |

All time, the same shape: 2,639 transactions carry a reference, but of 2,492 distinct references
**2,408 occur exactly once**. Only 83 occur twice, and only 47 of those are genuine cross-register,
opposite-sign pairs.

The reason is structural rather than a parsing defect. References live almost entirely in one
register:

```
Bill Pay Dave        2,443
Bill Pay Danni         130
Bill Pay Envelopes      66
```

Most transfers point at a feedless register, so the other side is never imported and there is nothing
for the reference to match. This is the same fact the forecast-per-register convention already
encodes, seen from a different angle.

Two further limits. The reference is not universally present even within one payee format — 186 of
the 2026 `ONLINE TRANSFER TO/FROM` transactions carry none. And it exists only inside the `payee`
varchar; there is no column, so any use of it means parsing.

#### Why it cannot replace the counterpart

Deeper than coverage: **a reference tells you two transactions are the same money. It does not tell
you which budget item the far side should get.** That is the question this design exists to answer,
and the counterpart forecast transaction is what carries the answer across — it exists *before* the
far transaction arrives, which a reference on that transaction cannot. `transfer_budget_item_pair`
and section 1 stand unchanged.

#### The rule

> **The reference confirms a match. It never gates one.**
>
> When both sides carry the same reference, a Phase 2.5 match is upgraded from "score ≥ 70" to
> certain. When either side lacks one, nothing changes and the existing score decides.

No code path may require a reference to be present, and no counterpart may be suppressed for want of
one. Coverage is 43% and outside our control; anything conditional on presence would silently do
nothing for the majority.

| Situation | Behaviour |
|---|---|
| Both sides carry the same reference | Match is certain. Skip the score threshold and take it. |
| Both carry references, but different ones | Not the same movement. Suppress the match regardless of score. |
| Either side carries none | Unchanged — `ForecastTransactionMatcher` scores as it does today. |

The second row is the one that earns its keep. The residual risk noted in section 1 is the matcher
having two plausible candidates for the same money and stranding the planned one; a differing
reference rules a candidate out outright, which scoring alone cannot do.

Where it is worth reading the reference:

- **Phase 5.5 idempotency.** `sourceTransaction` is the primary "already created" check. A reference
  is bank-issued and stable across re-imports, so it still identifies the pair when a source row has
  been deleted and recreated and the UUID has changed.
- **Phase 2.5 confirmation**, per the table above.
- **The audit trail**, alongside `sourceTransaction`, since it is the identifier the bank itself
  would use if you ever had to reconcile by hand.

> **Noticed in passing, and out of scope for this design.** The 36 same-register duplicate pairs above
> are duplicate imports — same reference, amount, date and payee, imported twice. They survived
> because `importRecordId` is the raw import line, and the two copies came from files that format it
> differently (`-1625.00` vs `-1,625.00`, `*` vs `false`, truncated payee). Duplicate detection keyed
> on a reference rather than on the raw line would not have been fooled. Worth its own piece of work.

## Phasing

1. Forecast belongs to a register (prerequisite, own change).
2. `transfer_budget_item_pair`, populated by learning from the far import rather than by prompting.
3. Phase 5.5 creating counterpart forecast transactions, marked overridden and carrying
   `sourceTransaction`.
4. Reporting in Phase 2.5.
5. Lifecycle handling for edit and delete.
6. Reference-number confirmation in Phase 2.5 (section 5).

After step 3 the second import already auto-matches silently; step 4 is only about telling you why.
Step 6 is independent of the rest and can land whenever — the feature is correct without it, only
less certain in the minority of cases where both sides carry a reference.

## Testing

- The claim itself: process an ad-hoc transfer in register A, import the far statement into register
  B, and assert **no** budget-item or split prompt, with the correct budget item assigned.
- No forecast on the counterparty register means nothing is created — the feedless case.
- Multi-split transfers produce one counterpart expectation per split.
- Regeneration of the far forecast between the two imports leaves the counterpart intact.
- The counterpart is not created twice if the source register is re-imported.
- Pairing: the first transfer of a kind falls through to the normal questions and records what was
  chosen; the second is silent; and a pairing never applies to the wrong target budget.
- An unpaired counterpart must **not** auto-match and must **not** assign a placeholder budget item —
  Phases 3 and 4 have to run.
- Reference numbers: matching two sides sharing a reference is certain; two sides carrying
  *different* references do not match however well they score; and a transfer where either side has
  no reference behaves exactly as it does today. The last of these is the one that must not regress —
  it is 57% of transfers.

## Decided

- **A forecast belongs to one and only one register**, and that change lands first, on its own.
  A register may have several forecasts. See the prerequisite section.
- **Do not create a forecast for a register with no import feed**, and do not create a counterpart
  expectation for a register with no forecast. The presence of a forecast is the switch, so no code
  anywhere tests whether a register is active.
- **Ad-hoc splits only.** A split against a planned budget item already has a forecast transaction
  waiting on the far side; a second one would compete with it.
- **Never interrupt the source import to ask for the counterpart budget item.** Create the
  counterpart either way; the far import's existing logic assigns it and may create a new budget
  item, and the pairing is learned from that choice. See section 2a.
- **The pairing is keyed on source budget item plus target budget**, not on a payee string. That is
  the distinction from the `TransferMemoMapping` that was removed in `e6253c8`.
- **The bank reference number confirms a match but never gates one.** It is present on only 43% of
  transfers, so nothing may be conditional on having one. See section 5.

## Open questions

None outstanding. The design is settled and the forecast-to-register mapping is decided, so step 1
can be built as written.

One thing to watch once it is running, noted inline: the ad-hoc gate reads the *source* split's
budget item, so a transfer that is budgeted on one side only will keep asking on the far side. If
that shows up in practice the fix is to gate on whether a target forecast transaction actually
exists rather than on the source item's type.
