# Duplicate Forecast Transactions from Re-Import Bug Fix

## Date
January 17, 2026

## Issue
When cleared transactions were re-imported, duplicate forecast transactions were being created even though the original transaction had already been reconciled to a forecast transaction.

## Root Cause

### The Bug
In `ForecastController.reconcile()` at line 373, the code was calling:

```java
ForecastItem forecastItem = ForecastItem.getByBudgetItemId(split.getIdBudgetItem());
```

This calls the **unscoped version** of `getByBudgetItemId()` which searches across ALL forecasts instead of filtering by the specific forecast being reconciled.

### How It Created Duplicates

**Scenario:**
1. User imports a transaction for Bill Pay Dave register
2. Transaction gets reconciled to Dave's forecast
3. Later, the same transaction file is re-imported (or transaction appears in a new download)
4. The transaction already exists, so it's not new, but it goes through reconciliation again
5. At line 373, `getByBudgetItemId(split.getIdBudgetItem())` is called
6. **BUG**: This returns a ForecastItem from **Bill Pay Danni's forecast** (or any other forecast) if that budget item exists there
7. A new ForecastTransaction is created with Danni's ForecastItem
8. This new ForecastTransaction gets saved to Dave's forecast
9. **Result**: Duplicate forecast transaction in Dave's forecast pointing to Danni's forecast item!

### Why Re-Imports Caused This

The check at line 359-360 should prevent re-reconciliation:
```java
forecastTransactionSplit = ForecastTransactionSplit.getForecastTransactionSplit(forecast, split);
if (forecastTransactionSplit == null) {
    // Only reconcile if not already reconciled
}
```

However, if for any reason this check fails or the forecast transaction split record was lost/deleted, the code would enter the reconciliation block and hit the bug at line 373.

## The Fix

### Before (Wrong):
```java
// Line 373 - Searches ALL forecasts
ForecastItem forecastItem = ForecastItem.getByBudgetItemId(split.getIdBudgetItem());
```

### After (Correct):
```java
// Line 373 - Searches only the specific forecast
ForecastItem forecastItem = ForecastItem.getByBudgetItemId(forecast, split.getIdBudgetItem());
```

## Impact

### Before Fix
- Re-importing transactions could create duplicate forecast transactions
- Forecast transactions could end up in the wrong forecast
- Cross-forecast contamination when budget items have the same UUID across forecasts
- Duplicate forecast transactions with identical forecast_item + planned_date

### After Fix
- ForecastItem lookup is properly scoped to the current forecast
- No cross-forecast contamination
- Re-imports don't create duplicate forecast transactions (assuming the reconciliation check works)
- Each forecast maintains its own set of forecast items and transactions

## Related Issues

This is the **fourth scoping issue** we've fixed in this session:

1. ✅ `Transaction.getByImportRecordId()` - Fixed to filter by register ID
2. ✅ `Forecast.getFirstNonZeroTransactionDate()` - Fixed to filter by forecast ID  
3. ✅ `Forecast.getMostRecent()` - Added budget-scoped version
4. ✅ `ForecastController.reconcile()` - Fixed to use forecast-scoped ForecastItem lookup

All follow the same pattern: **methods that search/retrieve entities must be scoped to the appropriate parent entity** (register, budget, or forecast) to prevent cross-contamination.

## Testing

After this fix:
1. Re-import a previously imported transaction file
2. Verify no duplicate forecast transactions are created
3. Check that forecast transactions remain in their correct forecast
4. Run the duplicate forecast transaction check to verify cleanup

## Files Modified
- `ForecastController.java` - Line 373: Changed `getByBudgetItemId(split.getIdBudgetItem())` to `getByBudgetItemId(forecast, split.getIdBudgetItem())`

## Additional Notes

### Remaining Question
Why does the reconciliation process run again for already-imported transactions? The check at line 359-360 should prevent this, but there may be edge cases where:
- The ForecastTransactionSplit record was deleted
- The transaction was imported but never reconciled (error during initial import)
- Database inconsistency

This is a separate issue to investigate, but the scoping fix prevents the duplicate creation problem regardless.

### Prevention
Consider adding:
1. Additional logging when creating new forecast transactions during reconciliation
2. Validation to ensure forecast items and forecast transactions belong to the same forecast
3. Database constraints to enforce forecast consistency
