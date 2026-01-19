# Updated Duplicate Detection Logic - Excluding Valid Split Cases

## Date: January 15, 2026

## Important Clarification

You correctly pointed out that if multiple forecast transactions have:
- Same ForecastItem
- Same plannedDate
- BUT are linked to **different transaction splits**

Then they are **NOT duplicates** - they represent different actual transactions that happened to match the same forecast item on the same date.

## Bug Fix - Column Name Correction

**Issue Found:** The initial implementation used the wrong column name `Transaction_idTransaction` instead of the correct `Transaction_Split_idTransaction` from the `forecast_transaction_split` table.

**Error:** `SQLSyntaxErrorException: Unknown column 'fts.Transaction_idTransaction' in 'order clause'`

**Fixed:** All references changed to `Transaction_Split_idTransaction`

## Example of Valid "Non-Duplicate" Case

```
Forecast Item: "Groceries" - budgeted $200 for Jan 15
Actual Transactions on Jan 15:
  - Transaction 1: Walmart $75 → Forecast Transaction A (split to this)
  - Transaction 2: Target $50 → Forecast Transaction B (split to this)
  - Transaction 3: Whole Foods $60 → Forecast Transaction C (split to this)

Result: 3 forecast transactions for "Groceries" on Jan 15
Status: VALID - Each linked to a different actual transaction
```

## Changes Made

### 1. Updated `Forecast.checkForDuplicateTransactions()` Method

**Added logic to exclude valid split cases:**

```sql
SELECT ...
FROM forecast_transaction ft
INNER JOIN forecast_item fi ON ...
LEFT JOIN forecast_transaction_split fts ON ft.idForecastTransaction = fts.ForecastTransaction_idForecastTransaction
WHERE ...
GROUP BY fi.idForecastItem, ft.plannedDate
HAVING COUNT(*) > 1
  -- NEW: Only flag as duplicate if they all point to the same transaction OR have no splits
  AND (COUNT(DISTINCT fts.Transaction_Split_idTransaction) <= 1 
       OR COUNT(DISTINCT fts.Transaction_Split_idTransaction) IS NULL)
```

**Key changes:**
- `LEFT JOIN forecast_transaction_split` - Get split information
- `COUNT(DISTINCT fts.Transaction_Split_idTransaction)` - Count unique actual transactions
- Only reports duplicates if `distinctTransactionCount <= 1 OR NULL` (same or no splits)

### 2. Updated `cleanup_duplicate_forecast_transactions.sql`

**Modified all queries to check for different splits:**

**Step 1 (Identify):**
- Shows `distinct_transaction_count` to help identify valid vs invalid duplicates
- Only shows cases where transactions don't have different splits

**Step 2 & 3 (Delete):**
```sql
DELETE ft1
FROM forecast_transaction ft1
INNER JOIN forecast_transaction ft2 ON ...
LEFT JOIN forecast_transaction_split fts1 ON ft1.idForecastTransaction = ...
LEFT JOIN forecast_transaction_split fts2 ON ft2.idForecastTransaction = ...
WHERE ...
  -- Only delete if splits are NULL or point to the SAME transaction
  AND (fts1.Transaction_Split_idTransaction IS NULL
       OR fts2.Transaction_Split_idTransaction IS NULL
       OR fts1.Transaction_Split_idTransaction = fts2.Transaction_Split_idTransaction)
```

**Effect:** Will NOT delete forecast transactions that point to different actual transactions

**Step 4 (Verify):**
- Uses same logic as Step 1 to verify only true duplicates remain

## What Gets Flagged as Duplicates Now

### ❌ TRUE Duplicates (Will be flagged):
1. Multiple forecast transactions with same item + date, **no splits**
2. Multiple forecast transactions with same item + date, **same split**
3. Multiple forecast transactions with same item + date, **one has split, one doesn't**

### ✅ Valid Cases (Will NOT be flagged):
1. Multiple forecast transactions with same item + date, **different splits**
   - Example: 3 grocery transactions on the same day, each matched to a different store

## UNIQUE Constraint Consideration

**Important:** The proposed UNIQUE constraint on `(ForecastItem_idForecastItem, plannedDate)` will **prevent the valid case** of multiple actual transactions matching the same forecast item on the same date.

### Recommendation:
**DO NOT add the UNIQUE constraint** if you need to support multiple actual transactions matching the same forecast item on the same date.

**Alternative:** Rely on:
1. The improved duplicate detection logic (software-level check)
2. The forecast update prevention logic (won't create duplicates)
3. Regular monitoring via `checkForDuplicateTransactions()`

## Files Modified

1. **Forecast.java** - `checkForDuplicateTransactions()` method
   - Added LEFT JOIN to forecast_transaction_split
   - Added HAVING clause to exclude different splits
   - Added comment explaining the exclusion

2. **cleanup_duplicate_forecast_transactions.sql**
   - Updated all 4 steps to check for different splits
   - Added warnings about the UNIQUE constraint limitation

## Testing

To verify the fix works correctly:

1. **Create a valid multi-split case:**
   - Have 2+ actual transactions on the same day for the same forecast item
   - Reconcile them, creating separate forecast transactions with different splits

2. **Run duplicate check:**
   - Update the forecast
   - Should NOT report these as duplicates

3. **Create an actual duplicate:**
   - Manually create two forecast transactions for same item+date with no splits
   - Run duplicate check
   - SHOULD report these as duplicates

## Summary

The duplicate detection now correctly distinguishes between:
- **Invalid duplicates:** Multiple forecast transactions that serve no purpose (same or no splits)
- **Valid multi-matches:** Multiple forecast transactions representing different actual transactions

This allows proper reconciliation of multiple actual transactions to the same forecast item on the same day while still catching true duplicates caused by bugs in the forecast update process.

