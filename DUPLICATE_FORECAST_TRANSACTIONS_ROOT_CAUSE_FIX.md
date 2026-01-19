# Forecast Transaction Duplication - Root Cause Analysis and Fix

## Date: January 14, 2026

## Your Diagnosis Was Correct! 

You identified the exact root cause of the duplicate forecast transactions issue. The problem occurs when:

1. A forecast transaction with a future planned date gets reconciled (matched to actual transactions)
2. The user updates the forecast with a start date that falls between the reconciliation date and the planned date
3. The old reconciled transaction is preserved while a new duplicate is generated

## The Problem in Detail

### Scenario Example:
- **Jan 7**: User reconciles transactions, matching a transaction to a forecast transaction with plannedDate = Jan 10
- **Jan 9**: User updates the forecast starting from Jan 9
- **Result**: TWO forecast transactions for Jan 10 (one reconciled, one newly generated)

### Why This Happened:

**In ForecastController.updateForecast() (lines 926-945):**
```java
// Delete query ONLY deletes transactions that:
// - Have plannedDate >= updateStartDate (Jan 9)
// - Are NOT overridden
// - Have NO splits (i.e., not reconciled)
String deleteQuery = ForecastTransaction.getDeleteQuery() +
    "where plannedDate >= " + updateStartDate + " " +
    "and not overridden " +
    "and not exists (SELECT 1 FROM forecast_transaction_split...)"
```

**Problem:** The Jan 10 reconciled transaction has splits, so it **doesn't get deleted**!

**In ForecastEngine.generateForecastTransactions() (lines 143-149):**
```java
// Only skips if transaction is OVERRIDDEN
if (forecast.hasOverriddenForecastTransactionOnDate(forecastItem, nextDate)) {
    continue; // Skip it
}
// Otherwise, ADD IT
forecast.addTransactionOnDate(forecastItem, startDate, nextDate, firstOccurrence);
```

**Problem:** The Jan 10 transaction is reconciled but NOT marked as overridden, so it **doesn't get skipped**!

**Result:** Jan 10 transaction exists (reconciled) AND a new one is generated = **DUPLICATE**

## The Fix

### 1. Updated Delete Query (ForecastController.java)

**Added:** `"and not found "` to the delete conditions

```java
String deleteQuery = ForecastTransaction.getDeleteQuery() +
    "where " +
        "ForecastItem_idForecastItem in (...) " +
        "and plannedDate >= " + updateStartDate + " " +
        "and not overridden " +
        "and not found " +  // NEW: Don't delete reconciled transactions
        "and not exists (SELECT 1 FROM forecast_transaction_split...)";
```

**Effect:** Now deletes ONLY truly unreconciled forecast transactions

### 2. Updated Forecast Generation Logic (ForecastEngine.java)

**Added:** Check for reconciled transactions in addition to overridden ones

```java
// Skip if overridden OR reconciled
if (forecast.hasOverriddenForecastTransactionOnDate(forecastItem, nextDate) ||
    forecast.hasReconciledForecastTransactionOnDate(forecastItem, nextDate)) {
    firstOccurrence = false;
    nextDate = forecastItem.getNextDateOfOccurrence(nextDate);
    continue; // Skip generating this transaction
}
```

**Effect:** Now skips generating transactions that are already reconciled

### 3. New Method Added (Forecast.java)

**Method:** `hasReconciledForecastTransactionOnDate(ForecastItem, Calendar)`

Checks if a transaction is reconciled by verifying:
- `found = TRUE` (matched during reconciliation), OR
- Has splits assigned (matched to actual transaction splits)

```java
public boolean hasReconciledForecastTransactionOnDate(ForecastItem forecastItem, Calendar date) {
    String sql = "SELECT COUNT(*) as count " +
        "FROM forecast_transaction ft " +
        "WHERE ft.plannedDate = ? " +
        "  AND (ft.found = TRUE OR EXISTS (" +
        "    SELECT 1 FROM forecast_transaction_split fts " +
        "    WHERE fts.ForecastTransaction_idForecastTransaction = ft.idForecastTransaction))";
    // Execute query and return true if count > 0
}
```

## Files Modified

1. **ForecastController.java** (line 938)
   - Added `"and not found "` to delete query

2. **ForecastEngine.java** (lines 144-145)
   - Added check for reconciled transactions in generation loop

3. **Forecast.java** (lines 901-950)
   - Added `hasReconciledForecastTransactionOnDate()` method

## What This Fixes

✅ **Prevents duplicates** when updating forecasts after reconciliation  
✅ **Preserves reconciled data** - doesn't delete transactions that have been matched  
✅ **Skips regeneration** - doesn't create new transactions for dates already reconciled  
✅ **Works with both markers** - checks both `found` flag and split existence  

## Testing Recommendations

1. **Create a scenario:**
   - Generate a forecast for January with transactions on Jan 10, 15, 20
   - On Jan 8, import and reconcile transactions (including one for Jan 10)
   - On Jan 9, update the forecast starting from Jan 9

2. **Expected behavior (AFTER fix):**
   - Jan 10 reconciled transaction: PRESERVED
   - Jan 10 duplicate: NOT CREATED
   - Jan 15, 20 transactions: REGENERATED as normal

3. **Verify with duplicate check:**
   - Run "Update Forecast"
   - Check the output from `checkForDuplicateTransactions()`
   - Should show ZERO duplicates

## Cleaning Up Existing Duplicates

The existing duplicates in your database still need to be cleaned up using the SQL script:

```bash
mysql -u [username] -p ForecastDatabase < cleanup_duplicate_forecast_transactions.sql
```

This will:
1. Remove existing duplicates (keeping the most recent version)
2. Add a UNIQUE constraint to prevent future duplicates at the database level

## Summary

Your diagnosis was spot-on! The issue was indeed that reconciled forecast transactions with planned dates after the forecast update start date were being preserved (because they have splits) but not recognized during generation (because they weren't marked as overridden), resulting in duplicates.

The fix now properly:
- **Preserves** reconciled transactions during deletion
- **Skips** reconciled dates during generation  
- **Prevents** duplicates from being created

Excellent detective work identifying this subtle but critical bug!

