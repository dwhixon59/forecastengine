# Semester Budget Items — Design

> **Status:** Implemented on 2026-09-15 on `dev`.
>
> **Goal:** One budget item that recurs once a month, for a set number of months, during the fall and
> spring semesters only. Justin's college meal plan is the first use.
>
> **The UF meal plan:** billed on the **15th** of **August, September, October, November** (fall) and
> **January, February, March, April** (spring). There is no summer plan.

---

## 1. How the meal plan is modeled today

No existing period means "monthly, but only during semesters", so Bill Pay Danni's budget holds three
separate items:

| Budget item | Period | Amount | Start | End | Memo |
|---|---|---|---|---|---|
| Meal plan for Justin | Monthly | -700.00 | 2026-09-15 | 2026-11-15 | Four even payments |
| Justin's Meal Plan 2026-2027 | Monthly | -700.00 | 2026-10-15 | 2027-04-15 | UF Blue Unlimited plan |
| Justin's Meal Plan 2028-2029 | Monthly | -700.00 | 2028-09-15 | 2029-04-15 | UF Blue Unlimited plan |

The Bill Pay Danni forecast holds the 2026-2027 occurrences on the 15th of October through March. That
modeling has three problems:

- **December is charged.** A monthly item with a start and end date cannot skip a month, so the forecast
  charges a payment UF never bills.
- **The months are wrong.** October–March is neither the August–November fall nor the January–April spring.
- **Summer is handled by expiry.** Every school year needs a new item with new dates.

The existing `SCHOOL_YEAR_SEMIMONTHLY` period cannot express this either. It pays twice a month and
only skips July through September.

---

## 2. The `SEMESTERS` period

### 2.1 What it means

An item with `period = SEMESTERS` occurs **once a month, on its start date's day of the month**, but only
in its payment months:

- **Fall:** the first fall month, plus the following months up to the payments-per-semester count.
- **Spring:** the first spring month, plus the following months, with the same count.

Justin's meal plan is *fall starts August, spring starts January, 4 payments per semester, start date on
the 15th*. That gives payment months Aug, Sep, Oct, Nov, Jan, Feb, Mar and Apr.

The start date says when the plan begins and the end date when it stops, so one item can cover all of
college. December and May through July simply have no occurrence.

### 2.2 Storing it

`FIXED_DAYS` is the precedent: its day count lives inside the period string (`Every-25-Days`), so it
travels everywhere the period already goes. That includes the raw SQL that copies `bi.period` into
`fi.period`. `SEMESTERS` does the same:

```
Semesters-<first fall month>-<first spring month>-<payments per semester>
Semesters-Aug-Jan-4
```

**In memory**, the schedule is packed into the item's existing `periodDays` field:
`fall * 10000 + spring * 100 + payments`, so Aug-Jan-4 is `80104`.

- **Why pack it:** `periodDays` already travels between budget items, forecast items, copies and the
  database:
  - `BudgetItem` and `ForecastItem` copy it;
  - `Forecast` writes it back through `generatePeriodType`;
  - `BudgetController` copies and edits it.

  So a `SEMESTERS` item needs no new field, no new column, and no change to any of those places.
- **Helpers:** `Item.packSemesterSchedule`, `fallStartMonthOf`, `springStartMonthOf` and
  `paymentsPerSemesterOf` pack and unpack it.
- **Parsing:** `Item.parsePeriodType` recognises `Semesters-…` as `SEMESTERS`, and `Item.parsePeriodDays`
  returns the packed schedule.
- **Writing:** `Item.generatePeriodType(SEMESTERS, schedule)` writes the string.
- **Invalid schedules:** a bad month, a count outside 1–6, or overlapping semesters is rejected when read
  and when written.

### 2.3 Validation

`validatePeriodHowOccursConsistency` gains one rule. A `SEMESTERS` item must have a valid schedule:

- both first months are months of the year;
- 1 to 6 payments per semester;
- fall and spring months do not overlap.

The existing rules still apply:

- `SEMESTERS` + `UNPLANNED` is invalid, because `UNPLANNED` requires `ON_DEMAND`.
- Rule 4 now allows a non-zero `periodDays` for `SEMESTERS` as well as `FIXED_DAYS`.

### 2.4 Occurrence dates

Everything the forecast engine needs goes through three `Item` methods, and `ForecastEngine` itself is
unchanged. Each method delegates to a static helper that is unit-tested directly. The payment day is the
start date's day of the month, clamped to shorter months the way `MONTHLY` already does it (the 31st
becomes the 30th in September).

| Method | Helper | Rule |
|---|---|---|
| `getFirstDateOnOrAfter(d)` | `semesterPaymentOnOrAfter` | The payment day in `d`'s month. If that is before `d`, the next month. Then move forward to a payment month. Never before the start date. |
| `getNextDateOfOccurrence(p)` | `semesterPaymentAfter` | One month after `p`, then forward to a payment month. |
| `getPreviousDateOfOccurrence(p)` | `semesterPaymentBefore` | One month before `p`, then back to a payment month. |

Each search stops after 12 months. A valid schedule always has a payment month, so a valid item never
hits that limit.

**Examples** (Aug-Jan-4, the 15th):

- Next after 2026-11-15 is **2027-01-15**.
- Next after 2027-04-15 is **2027-08-15**.
- First on or after 2027-05-01 is **2027-08-15**.

`ItemUtilities.getClosestOccurrence`, used to find the occurrence nearest a date, handles `SEMESTERS`
with the same helpers.

### 2.5 Amounts and tolerances

- **Annual amount:** `Item.getForecastAnnualAmount` returns `amount × 2 × paymentsPerSemester`. For the
  meal plan that is 8 × $700 = $5,600 a year.
- **Date tolerance:** `isWithinNormalDateVariance` uses the `MONTHLY` band (±4 days).

### 2.6 Entering one in Manage Data

`BudgetController.askPeriodDays` already asks for the day count of a `FIXED_DAYS` item. When the period is
`SEMESTERS`, it asks for the schedule instead through `askSemesterSchedule`, both when an item is created
and when its period is changed:

```
First month of fall payments [Aug]:
First month of spring payments [Jan]:
Payments per semester (1-6, currently 4):
Paid in Aug-Nov & Jan-Apr.
```

- **Months:** a month can be typed as `8`, `Aug` or `August`.
- **Defaults:** the item's current schedule, or UF's (Aug, Jan, 4) for a new item.
- **Overlaps:** an overlapping schedule is explained and asked for again.

The item then displays as `Justin's Meal Plan (Children, $-700 Semesters-Aug-Jan-4, …)`.

---

## 3. Moving the meal plan onto it

This is a manual step and nothing is migrated automatically:

1. Create **Justin's Meal Plan** with:
   - period *Semesters*, schedule Aug, Jan, 4;
   - amount $-700.00;
   - start date 2026-08-15, so payments fall on the 15th;
   - an end date at the last spring payment of his final year.
2. Give the three existing meal plan items end dates, so they generate no more occurrences. Their past
   transactions stay attached to them.
3. Update the Bill Pay Danni forecast. Per the regeneration cadence, it regenerates from the next month.

---

## 4. Changes by file

| File | Change |
|---|---|
| `Item` | `SEMESTERS` period; storage pattern and pack/unpack helpers; parse and generate; validation; annual amount; date tolerance; the three date methods and their static helpers |
| `ItemUtilities` | `getClosestOccurrence` handles `SEMESTERS` |
| `BudgetController` | `askSemesterSchedule` and `askMonth`, reached through `askPeriodDays` |
| `SemestersPeriodTest` (new) | storage, validation, dates, amounts and tolerance for the UF schedule |

`BudgetItem`, `ForecastItem`, `Forecast` and `ForecastEngine` needed no change.

---

## 5. Tests (`SemestersPeriodTest`)

- **Storage:**
  - `Semesters-Aug-Jan-4` round-trips.
  - Invalid stored schedules are rejected on read and write: a bad month, 0 or 7 payments, overlapping
    semesters.
- **Display and input:**
  - `describeSemesterSchedule` gives `Aug-Nov & Jan-Apr`, and `Aug & Jan` for one payment.
  - `parseMonth` accepts `8`, `aug` and `August`, and rejects `13`, `Au` and `Summer`.
- **Validation:** a valid schedule passes; an overlapping schedule and `UNPLANNED` are rejected.
- **First date:**
  - inside a semester;
  - across winter break (after November → January) and summer (May → August);
  - never before the start date.
- **Next date:**
  - Aug→Sep;
  - Nov→Jan and Apr→Aug;
  - a pay day of the 31st clamped to the 30th and restored to the 31st;
  - one payment per semester (Aug→Jan→Aug);
  - the end date stops payments.
- **Previous date:** Jan→Nov and Aug→Apr.
- **A school year:** 08-01-2026 to 07-31-2027 yields exactly the eight UF payments.
- **Amount:** the annual amount is −$5,600.
- **Tolerance:** ±3 days is within, 5 days is not.

---

## 6. Answered questions

- **Which months does UF bill?** August–November and January–April.
- **Summer plan?** No.
- **Payment day?** Always the 15th, which is the item's start date's day.

---

## 7. Related finding (not changed)

`Item.getForecastAnnualAmount` counts `SCHOOL_YEAR_SEMIMONTHLY` as `amount × 9`. Its dates skip July,
August and September, which leaves nine months at two payments each, **18** a year. The annual amount,
and every per-month average built on it, is therefore half the real figure. The two School lunches for
Justin items use this period. This is worth fixing separately.
