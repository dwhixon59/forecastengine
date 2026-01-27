# Forecast Update - Found Flag Correction

## Issue Corrected

The `found` flag was incorrectly being used in the forecast update process to determine which transactions to preserve. This has been corrected.

## The `found` Flag - Correct Purpose

The `found` flag has **ONE AND ONLY ONE** purpose:
- **Used ONLY in "Update from External Source"** (Excel/CSV import)
- Marks which forecast transactions exist in the external source
- Transactions NOT found in the external source are zeroed out
- **Has nothing to do with reconciliation**

## Reconciliation Definition

A forecast transaction is **reconciled** if and only if:
- It has **forecast_transaction_split** records linking it to actual imported transactions
- The `found` flag is NOT used to determine reconciliation status

## Changes Made

### 1. ForecastController.updateForecast()
**Before (INCORRECT):**
```java
"and not found " +  // Don't delete reconciled transactions
"and not exists (select 1 from forecast_transaction_split ...)"
```

**After (CORRECT):**
```java
"and not exists (select 1 from forecast_transaction_split ...)"
```

Now only checks for splits to determine if reconciled. The `found` flag is no longer checked.

### 2. Forecast.hasReconciledForecastTransactionOnDate()
**Before (INCORRECT):**
```java
"AND (ft.found = TRUE OR EXISTS (SELECT 1 FROM forecast_transaction_split ...))"
```

**After (CORRECT):**
```java
"AND EXISTS (SELECT 1 FROM forecast_transaction_split ...)"
```

Only checks for the existence of splits, not the `found` flag.

### 3. Removed Diagnostic Code
- Removed diagnostic logging from `ForecastController.updateForecast()`
- Removed diagnostic logging from `ForecastItem.updateForecastItemsFromBudgetItems()`

## Transaction Preservation Logic

During forecast update, transactions are **preserved** (not deleted/regenerated) if they are:
1. **Overridden** - User manually modified the transaction
2. **Reconciled** - Has forecast_transaction_split records (matched to actual imports)

Transactions are **deleted and regenerated** if they are:
- Not overridden
- Not reconciled (no splits)
- Occur on or after the update start date

## The `found` Flag Workflow (External Source Update Only)

1. User exports forecast to Excel/CSV
2. User modifies transactions in Excel
3. User imports the modified forecast:
   - All forecast transactions marked `found = false`
   - Each transaction in the import file is marked `found = true`
   - Transactions with `found = false` after import are zeroed out (user deleted them from Excel)

## Summary

The `found` flag is for the **"Update from External Source"** feature only. It tracks which forecast transactions exist in an external source (Excel/CSV) so that transactions deleted from the external source can be zeroed out in the forecast.

Reconciliation is determined solely by the existence of **forecast_transaction_split** records, which link forecast transactions to actual imported bank transactions.

## Files Modified
- `ForecastController.java` - Removed `found` flag check from delete query
- `Forecast.java` - Removed `found` flag check from `hasReconciledForecastTransactionOnDate()`
- `ForecastItem.java` - Removed diagnostic code

## Testing Required
Test the forecast update process to ensure:
1. Unreconciled transactions are properly deleted and regenerated
2. Reconciled transactions (with splits) are preserved
3. Overridden transactions are preserved
4. The `found` flag is not interfering with the update process
