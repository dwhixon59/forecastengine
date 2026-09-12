# Import Impact Analysis — Design

**Status:** first pass, for discussion. Nothing here is built yet.
**Date:** 2026-09-12

---

## Goal

After an import, tell the user in a few sentences what their recent behaviour did to the
forecast — and specifically to their goals.

The forecast already gets a long, capable summary at render time (lowest balance, required
float, runway, recommendations — `AbstractForecastView.renderLongTermForecast`, lines
~560–1100). That summary answers **"where do I stand?"**. It cannot answer **"what did I just
do?"**, because it has no memory of where the user stood twenty minutes ago.

Impact analysis is the delta report. Its single organising question:

> Of everything that changed in the forecast during this import, which changes matter, and
> what behaviour caused them?

The headline case, in the user's words: an envelope goal to buy a bedroom set for $2,000 on a
certain date, which is now underfunded because of overspending on dining out. Today the app
will happily show both facts on different reports and never connect them.

### Illustrative output

Numbers below are invented, to fix the shape of the thing:

```
IMPORT IMPACT

14 transactions imported, $1,842 out, $0 in.

Your goals:
  ! Bedroom set  $2,000 on 11-15-2026 — now short $340 (was fully funded)
      Furniture envelope reaches $1,660 by then, down from $2,010.
      Cause: $210 more than planned on Dining Out, $140 unplanned on Amazon.
      Fix: $170/month more into Furniture, or move the date to 12-15-2026.
  · Vacation  $3,500 on 06-01-2027 — still on track ($120 ahead)

Your balance:
  Lowest balance falls to $310 on 10-28-2026, down from $655. Still positive.

Nothing else moved enough to mention.
```

Three sections, hard-capped. **Concision is a requirement, not a nicety** — the daily update
already prints a great deal and this report competes with all of it for attention. If nothing
crossed a threshold, the report should be one line: *"This import did not change any goal or
balance materially."*

---

## 1. Capturing the "before" state

The suggestion in the brief was to render the forecast to a temp file before importing and
diff against it. That gets the timing right but the medium wrong, and it is worth being
explicit about why, because the timing insight is the valuable half.

**Why not diff the rendered file.** The rendered forecast is a presentation artifact. A diff
of it is dominated by noise — row shifts, reformatting, running-balance recalculation on every
line below any change. Extracting "the bedroom set is short $340" from that diff means parsing
our own spreadsheet back into semantics we already had in memory when we wrote it. It also
couples the analysis to the Excel view, breaking the one hard rule in `CLAUDE.md`: the
analysis would stop working on the CSV or text views.

**Instead: a structured snapshot.** Compute a small value object holding exactly the facts the
analysis needs, and serialise it. Same timing, but the "diff" becomes field-by-field
arithmetic on typed data. It is testable without a database, it is the natural payload for the
AI narrator (§7), and it is view-independent.

Snapshots go to `<user personal file system>/snapshots/` as JSON, named
`forecast-snapshot-<registerName>-<yyyyMMdd-HHmmss>.json`, keeping the last N (10?) per
register. Keeping them on disk rather than only in memory buys three things: the analysis
survives a crash mid-import, "what changed this week?" becomes possible later by comparing
non-adjacent snapshots, and a bad analysis can be debugged after the fact from the two inputs.

> **Open question 1.** JSON needs a serialiser. The project has no Jackson/Gson dependency
> today — `pom.xml` has POI, commons-csv, ofx4j, Lombok. Options: (a) add Jackson, (b)
> hand-roll writer/reader for one flat record type, (c) reuse the existing CSV machinery.
> Leaning (a) — the snapshot will grow, and hand-rolled parsers rot. But it is a new
> dependency for one feature.

### 1.1 Where exactly is "before"?

Harder than it looks, because `DailyUpdateController.run()` mutates the forecast in several
places before the import proper:

| Step | Mutates forecast? |
|---|---|
| Check for external forecast changes → `updateFromExternalSource()` | **yes** — user edits in Excel |
| Reprocess skipped transactions | **yes** — old transactions, not this session's behaviour |
| Import cleared transactions | yes — *this is the behaviour we want to attribute* |
| Import provisional transactions | yes — same |
| Review / recategorise | yes — same |
| Verify register balance | yes — balance correction |
| `forecastController.updateForecast()` | yes — regeneration |

Attributing the user's Excel edits or last week's skipped transactions to "your recent
behaviour" would be wrong. The snapshot belongs immediately **before `IMPORT CLEARED
TRANSACTIONS`** and after the two preceding steps, so the window contains only the imported
activity and its consequences.

The "after" snapshot belongs **after `updateForecast()` and after the balance verification**,
so regenerated occurrences and the corrected balance are included. That is late in the run —
after the Excel round-trip — which means the report cannot print until near the end. Probably
fine; it also means it can be the last thing the user sees.

> **Open question 2.** If the user edits the forecast in Excel *during* the run (the
> `updateFromExternalSource` step after review), those edits land inside the window and get
> attributed to the import. Take a third snapshot to fence them off, or accept the
> imprecision? Leaning accept for pass 1, and revisit if it bites.

### 1.2 The regeneration boundary

Per how forecast regeneration works here, `updateForecast()` only regenerates from **next
month forward** — the current month is left alone deliberately. This constrains the analysis
in a way worth writing down, because it will otherwise look like a bug:

- **Current-month impact** shows up only as changed envelope running balances, changed MTD
  spend, and the changed register balance. No occurrences move.
- **Future-month impact** shows up as regenerated occurrences with different dates/amounts.

So a goal dated *this month* can only be reported through envelope balance, and the analysis
must not claim its occurrence "moved". Different code paths, and the report should not pretend
otherwise.

---

## 2. The snapshot data model

Sketch, not final:

```
ForecastSnapshot
  registerName, forecastName, takenAt, snapshotReason (BEFORE_IMPORT | AFTER_IMPORT)
  AccountState account
  List<EnvelopeState> envelopes
  List<GoalState> goals
  Map<String,Double> monthToDateSpendByBudgetItem     // for variance attribution
  Map<String,Double> plannedThisMonthByBudgetItem

AccountState
  currentBalance
  lowestBalance, dateOfLowestBalance
  firstNegativeBalance, dateOfFirstNegativeBalance
  endingBalance, netChangeInBalance
  requiredFloat
  monthlyNet                                          // income − expense, averaged
  isCreditLine

EnvelopeState
  budgetItemId, name
  balance                                             // ForecastItem.runningBalance
  contributionAmount, period
  minimumBalance (buffer target)

GoalState
  forecastTransactionId, budgetItemId, envelopeName
  description, goalDate, goalAmount
  projectedEnvelopeBalanceAtGoalDate                   // §3
  fundedAmount, shortfall                              // derived
  status: FUNDED | AT_RISK | SHORT
```

`EnvelopeState` and `GoalState` map cleanly onto what already exists — `Envelope`,
`Envelope.getGoals(reportDate)`, and `Goal` in `model/forecast/`. A goal is already defined in
code as *a future negative forecast transaction on an envelope item*, which is exactly the
"$2,000 bedroom set on a date" concept. No new persistence needed for goals.

`AccountState` is the problem child. Every one of those fields is computed today **as a side
effect of rendering** — they are locals inside `renderLongTermForecast`, filled by
`DayEndBalanceTracker`, then printed and discarded.

> **Open question 3.** To snapshot them we must either
> (a) extract the metric computation out of `renderLongTermForecast` into something like
> `ForecastMetrics computeMetrics(Forecast)` that both the renderer and the snapshotter call —
> the right shape, but it is surgery on a 1,400-line class that is currently correct and has
> integration tests over its output; or
> (b) have the snapshotter walk the transactions itself with its own `DayEndBalanceTracker`,
> duplicating ~80 lines of the accumulation loop — safer now, guaranteed to drift later.
>
> Leaning (a), done as its own commit with the existing `ForecastSummaryOutputIntegrationTest`
> as the safety net, *before* any impact-analysis code is written. Worth deciding early since
> it is the largest single piece of work in the feature.

---

## 3. Projecting goal funding

For each goal, we need "will the envelope hold enough on the goal date?" — before and after.

```
periodsRemaining     = whole periods between today and goalDate      (Goal.getMonthsRemaining
                       generalised to the envelope's period type)
scheduledInflow      = contributionAmount × periodsRemaining
interveningOutflow   = Σ |amount| of other goals on the same envelope dated before this one
projectedBalance     = currentEnvelopeBalance + scheduledInflow − interveningOutflow
shortfall            = goalAmount − projectedBalance                 (>0 means short)
```

Points to settle:

- **Ordering.** Goals on one envelope compete for the same pot. Fund them in date order —
  earlier goals consume the balance first. This makes a *later* goal go short when an
  *earlier* one is added or grown, which is correct and non-obvious, so the report should say
  so when it happens.
- **Contributions are not guaranteed.** `scheduledInflow` assumes every future contribution
  actually lands. If the account itself is projected negative before the goal date, the
  contribution is fictional. **Cross-check: a goal cannot be reported FUNDED if the account
  balance goes negative before its date.** Downgrade it to AT_RISK and say why. Without this
  the report will confidently promise money that will not exist.
- **Buffer target.** Envelopes carry `minimumBalance` as a cushion. Does a goal get to spend
  into the cushion? Existing `EnvelopeReport` treats a balance below the buffer as a deficit
  worth flagging, which suggests no.
  > **Open question 4.** Fund goals from `balance` or from `balance − minimumBalance`?
  > Leaning the latter, so we do not tell the user a goal is funded by draining the cushion.

Thresholds for what gets reported at all — all placeholders for tuning:

| Signal | Report when |
|---|---|
| Goal shortfall | crosses $0 in either direction, or moves by > 10% of goal amount |
| Goal status change | any FUNDED ⇄ AT_RISK ⇄ SHORT transition |
| Lowest balance | moves > $250, or crosses $0, or crosses `minimumBalance` |
| First negative date | appears, disappears, or moves > 7 days |
| Envelope balance | drops below `minimumBalance` (newly) |

---

## 4. Attribution — the interesting part

Detecting the change is arithmetic. Naming the behaviour that caused it is the feature.

Two mechanisms, and they need to be kept apart:

### 4.1 Direct draws (easy, exact)

A split against an ENVELOPE budget item reduces that envelope directly —
`Item.updateEnvelopeAmount(ForecastTransactionSplit)`. If the user spent $200 out of the
Furniture envelope, the bedroom set is short $200 and we can say so with certainty. Walk the
`ImportLog.ImportRecord` splits, filter to `HowOccurs == ENVELOPE`, group by budget item.
Exact, no heuristics.

### 4.2 Indirect drain (hard, heuristic)

The headline case is *not* direct. Overspending on Dining Out (a COLLECTION item) does not
touch the Furniture envelope at all. It lowers the account balance, which is the shared pool
that future envelope contributions are drawn from. The causal chain is real but diffuse:

```
overspend on Dining Out  →  lower account balance  →  less headroom
                         →  future contributions less certain  →  goal at risk
```

Attributing a specific dollar of dining-out spend to a specific goal is not accounting truth —
it is an allocation choice. Proposal, kept deliberately simple and explainable:

1. Build a **variance ledger** from this import. For each imported split, compute
   actual − planned using the logic `NewTransactionSummaryReport` already implements per
   `HowOccurs` (PERIODIC → variance vs. item amount; COLLECTION → MTD vs. month's budget;
   UNPLANNED → whole amount is variance; ENVELOPE/VARIABLE_PERIODIC → no per-split
   expectation). Reusing that logic keeps the two reports from disagreeing about what counts
   as "over".
2. Sum the negative variances → `totalOverspend`. This is the honest, defensible number: *"you
   spent $350 more than planned this import"*.
3. Rank the contributing budget items by variance and name the **top two or three** as causes.
4. **Do not divide `totalOverspend` across goals.** State the goal shortfall (computed in §3)
   and the overspend separately, joined by "cause", not by a fabricated allocation:

   > Bedroom set short $340. This import ran $350 over plan, mostly Dining Out ($210) and an
   > unplanned Amazon charge ($140).

   The two numbers being close is informative; claiming $210 of dining out *became* $210 of
   bedroom-set shortfall is a precision we do not have.

> **Open question 5.** Is that the right call? The alternative — proportional allocation
> across at-risk goals — reads more decisively and is what a human advisor would say
> informally. It is also fabricated precision that a sharp user will eventually catch. Leaning
> honest-and-adjacent, but this is a judgement call about the product's voice, not a technical
> one.

### 4.3 The counterfactual we cannot compute

The genuinely correct answer to "what did this import do?" is: re-run the forecast with the
imported transactions excluded and diff. That is a true counterfactual and it would make
attribution exact. It needs a forecast regeneration against a hypothetical register state —
possible in principle (regenerate into a scratch forecast row, read it, delete it) but it
doubles the regeneration cost of every daily update and adds a write path that must never leak
into real data. Noting it as the theoretically right answer and rejecting it for pass 1.

---

## 5. Where it hooks in

Follows existing structure — a controller for orchestration, a report class for output.

**New:** `controller/ImportImpactController`, `model/forecast/ForecastSnapshot` (+ nested
state records), `model/forecast/ImpactAnalysis` (the computed delta),
`view/text/ImportImpactReport extends ForecastReport`.

**`DailyUpdateController.run()`** — two insertion points, both wrapped in the same
`try/askContinue` pattern as every other step, so a failure here never aborts a daily update:

```java
// after REPROCESS SKIPPED TRANSACTIONS, before IMPORT CLEARED TRANSACTIONS:
ForecastSnapshot before = impactController.takeSnapshot(BEFORE_IMPORT);

// ... existing import / review / verify / updateForecast / render steps ...

// after the forecast render, before the Spending Report:
view.sayH2("IMPORT IMPACT");
impactController.analyseAndReport(before, importController.getImportLog());
```

**`MainController`** — a `analyzeImportImpact` goal that loads the most recent stored snapshot
for the register and analyses against current state. Lets the report be re-run without
re-importing, which matters for iterating on the wording.

**Output** — both, following the pattern of the other reports:
- to `ViewInt` via `view.say(...)` for the terminal, and
- to a file through `NotificationServiceInt.sendImportImpactReport(...)`, so it reaches the
  phone alongside the New Transaction Summary and Items of Interest reports.

The phone constraint matters for formatting: `NewTransactionSummaryReport` truncates to ~25–30
characters per line for an iPhone 11. The impact report should respect the same width.

---

## 6. What this does *not* do

Worth bounding, since each of these is a plausible next request:

- Not a spending report — that exists (`renderSpendingReportForMonth`).
- Not a budget-vs-actual for the month — that exists.
- Not a recommendation engine for the whole forecast — `renderLongTermForecast` already emits
  Options A/B/C and runway analysis. Impact analysis recommends only against the goals it
  reports on.
- Not tracking behaviour across imports (no trend, no "third week running"). Tempting, and the
  stored snapshots make it possible later, but out of scope for pass 1.

---

## 7. Where AI fits

Yes, there is a real opportunity, and it is narrower than it first looks.

### 7.1 The split

**Java computes every number. The model never does arithmetic.** Every figure in the report —
shortfalls, projections, variances, dates — comes from §§3–4 and is already correct before any
model is involved. This is not caution for its own sake: a wrong dollar figure in a financial
report is worse than no report, and it would be undetectable in a fluent paragraph.

**What the model is actually good at here**, and what the deterministic version will do
badly:

1. **Selection and ranking.** Twelve envelopes and thirty splits change; three matter. Fixed
   thresholds (§3) are a crude proxy — they cannot tell that a $40 slip on a goal three weeks
   out matters more than a $300 slip on one three years out.
2. **The causal sentence.** Joining "Furniture is short $340" to "Dining Out ran $210 over"
   into one sentence that reads like a person wrote it. A template can produce this; it
   produces the *same* sentence every time, and by week three the user stops reading it.
3. **Recommendations with real alternatives.** "$170/month more into Furniture, or move the
   date to 12-15-2026, or skip about four dining-out trips" — that third option requires
   knowing a dining-out trip is ~$50 around here, which is in the data but not in any template.
4. **Proportionality of tone.** Not alarming the user over a $40 slip, and not burying a
   goal that just became unreachable.

Note the natural fallback: if the model is unavailable, disabled, or slow, the template
narrator produces a plainer report from the same `ImpactAnalysis`. The feature must never
*depend* on the model.

```
interface ImpactNarratorInt {
    String narrate(ImpactAnalysis analysis) throws NarrationException;
}

TemplateImpactNarrator  — deterministic, offline, always available, the default
ClaudeImpactNarrator    — sends the ImpactAnalysis JSON, returns prose
```

This mirrors how `ViewInt` is already used: one interface, swappable implementations, callers
unaware. Configuration in `db.properties` alongside the other environment settings
(`impact.narrator=template|claude`, `impact.apiKeyEnvVar=ANTHROPIC_API_KEY`).

### 7.2 MCP, specifically

The brief asks about passing data to "some MCP" for formatting. Worth separating two things
that get conflated, because they solve different problems:

**MCP is a protocol for exposing tools and data _to_ a model host** (Claude Desktop, Claude
Code, an agent runtime). It is not a formatting service you POST to. So "send the data to an
MCP and get prose back" is not quite a thing — but two adjacent things are, and both are
useful:

**(a) Direct API call from Java — recommended for this feature.** `ClaudeImpactNarrator` makes
one HTTPS request to the Claude API with the `ImpactAnalysis` JSON and a system prompt that
says: *you are given computed facts; select what matters; write at most N sentences; never
alter a number; if a figure is not in the input, do not state it.* One request, ~1–2s, no
extra process to run, no protocol layer. This is the right shape for an automated step inside
a daily update.

Needs an HTTP client (`java.net.http.HttpClient` — JDK built-in, no new dependency) and JSON
(same decision as Open Question 1). Model: `claude-sonnet-5` is the right default — this is a
short, well-specified writing task over pre-computed facts, and Sonnet is faster and cheaper
than Opus for it. Cost per run is fractions of a cent at this payload size.

**(b) A ForecastEngine MCP _server_ — a separate, complementary idea worth its own doc.**
Expose the forecast as MCP tools (`get_forecast_snapshot`, `list_goals`,
`get_envelope_balance`, `query_spending_by_category`) so that Claude Code or Claude Desktop can
interrogate the live financial data conversationally: *"why is the bedroom set short?"*, *"what
if I move it to January?"*, *"which category drifted most this quarter?"*. That is genuinely
valuable — it is ad-hoc analysis the app will never have a menu item for — but it serves
interactive exploration, not the unattended daily update. Different feature, same data model.
The `ForecastSnapshot` work below is the shared foundation for both, which is an argument for
getting its shape right.

> **Open question 6.** Is (b) actually more interesting than (a)? An MCP server over this data
> might deliver more value per hour of work than a nicer paragraph in the daily update — the
> daily update already has a competent deterministic summary, whereas there is currently *no*
> way to ask the data an unanticipated question. Worth weighing before committing to build
> order.

### 7.3 What has to be true before AI ships

- **Data leaves the machine.** The payload is a financial summary — envelope names, goal
  descriptions, dollar figures, dates. No account numbers, no merchant-level transaction
  detail unless we deliberately include it for the causal sentence (and Dining Out / Amazon
  are exactly that detail). This is the user's own data going to a service the user chooses,
  which is ordinary — but it must be **opt-in and off by default**, and the doc should say
  plainly what is sent.
- **Payload minimisation.** Send the computed `ImpactAnalysis`, never the register, never the
  transaction list. Aggregates and the two or three named causes, nothing else.
- **Verification.** After narration, check every dollar figure and date in the returned prose
  against the input facts by regex. Any figure not present in the input → discard the
  narration, fall back to the template, log it. This is cheap and it closes the one failure
  mode that actually matters.
- **Failure is silent and non-blocking.** Timeout (5s?), no key, HTTP error, malformed
  response → template narrator, no stack trace in the user's face. Never abort the daily
  update.
- **Determinism in tests.** `ImpactNarratorInt` is stubbed in every unit test.
  `ClaudeImpactNarrator` gets tested against a canned response, never a live call.

---

## 8. Testing

Per `CLAUDE.md`, JUnit with mocks — no live database.

- `GoalFundingProjectionTest` — funded / short / exactly-met; multiple goals competing on one
  envelope in date order; goal inside the current month (no regeneration); goal beyond the
  forecast horizon; account-goes-negative downgrade from FUNDED to AT_RISK.
- `ImpactAnalysisTest` — before/after snapshot pairs as fixtures, asserting on the computed
  deltas. Pure data in, pure data out, no DB.
- `ThresholdTest` — the "nothing moved enough to mention" path, and each threshold boundary.
- `VarianceLedgerTest` — one case per `HowOccurs`, asserting agreement with
  `NewTransactionSummaryReport`'s classification.
- `SnapshotRoundTripTest` — serialise, deserialise, assert equality.
- `ImportImpactReportTest` — rendering against a fixed `ImpactAnalysis` with a stub narrator,
  asserting line width for the phone.
- `ClaudeImpactNarratorTest` — canned response; specifically a response containing a
  **fabricated figure**, asserting fallback to the template.

---

## 9. Suggested build order

Each phase is independently useful and independently committable.

| Phase | Work | Value on its own |
|---|---|---|
| 0 | Extract `ForecastMetrics` from `renderLongTermForecast` (Open Question 3) | Nothing user-visible; unblocks everything and pays down real debt |
| 1 | `ForecastSnapshot` + serialisation + storage; snapshot taken in the daily update | Snapshots accumulate; nothing reported yet |
| 2 | Goal projection (§3) + account deltas + thresholds; `TemplateImpactNarrator`; report to view and file | **The feature works.** Deterministic, offline, shippable |
| 3 | Variance ledger and attribution (§4) | The "why", still deterministic |
| 4 | `ClaudeImpactNarrator` behind a config flag, with verification and fallback | Better prose; strictly optional |
| 5 | MCP server (§7.2b) — separate design doc | Ad-hoc questions of the data |

Phase 2 is the milestone worth aiming at. Phases 4 and 5 should not be started until 2 and 3
have been lived with for a few weeks — the shape of the good report will be much clearer then,
and it is the input to the prompt.

---

## Open questions, collected

1. JSON serialisation — add Jackson, hand-roll, or reuse CSV machinery?
2. Fence off mid-run Excel edits with a third snapshot, or accept the imprecision?
3. Extract `ForecastMetrics` from the renderer (surgery, correct) or duplicate the
   accumulation loop (safe, drifts)?
4. Do goals fund from the envelope balance, or from balance minus the buffer target?
5. Attribution voice — separate the overspend from the shortfall (honest), or allocate across
   goals (decisive, fabricated precision)?
6. Build order — is the MCP server (7.2b) worth more than the narrator (7.2a)?
7. Thresholds in §3 are all guesses. They need real imports to tune against, which argues for
   making them configurable early rather than constants.
8. Envelopes are the stated focus. Should non-envelope future commitments — a large PERIODIC
   item, an installment loan payoff — also count as "goals" for this report?

---

## References in the existing code

| Concept | Location |
|---|---|
| Envelope, goals | `model/forecast/Envelope.java`, `model/forecast/Goal.java` |
| Envelope balance mechanics | `Item.updateEnvelopeAmount(...)`, `model/budget/Item.java` ~257–320 |
| Existing envelope + goal report | `view/text/EnvelopeReport.java` |
| Forecast summary metrics | `view/base/AbstractForecastView.java` ~500–1100 |
| Day-end balance accumulation | `view/base/DayEndBalanceTracker.java` |
| Daily balance series | `Forecast.getDailyBalanceList(...)` |
| Import records and splits | `controller/ImportLog.java` |
| Per-`HowOccurs` variance logic to reuse | `view/text/NewTransactionSummaryReport.java` |
| Orchestration to hook into | `controller/DailyUpdateController.java` |
| Report file delivery | `notification/async/file/fileBasedNotificationService.java` |
