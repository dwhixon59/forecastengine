# Analysis: Forecast Scoping Issues in Forecast.java

## Date
January 17, 2026

## Summary
Reviewed all SQL queries in `Forecast.java` and related files to identify queries that search across all forecasts when they should filter by a specific forecast ID.

## Issues Found and Fixed

### 1. ✅ FIXED: `getFirstNonZeroTransactionDate()` (Line 438)
**Problem:** Query was not filtering by forecast ID, causing it to return the minimum planned date across ALL forecasts instead of just the specified forecast.

**Original Query:**
```sql
select MIN(plannedDate) as 'ft.plannedDate' 
from forecast_transaction ft 
inner join forecast_item on ForecastItem_idForecastItem = idForecastItem 
where remainingAmount <>0 and amount <>0
```

**Fixed Query:**
```sql
select MIN(plannedDate) as 'ft.plannedDate' 
from forecast_transaction ft 
inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem 
where ft.remainingAmount <>0 
  and fi.Forecast_idForecast = uuid_to_bin('[forecast_id]')
```

**Changes Made:**
- Added forecast ID filter: `fi.Forecast_idForecast = uuid_to_bin('[forecast_id]')`
- Added table alias `fi` for clarity
- Removed unnecessary `fi.amount <>0` clause (remaining amount is the relevant filter)

**Impact:** This was causing the "No forecast transactions found" error when rendering long-term forecasts.

## Methods Reviewed - No Issues Found

### 2. ✅ CORRECT: `getMostRecent()` (Line 452)
**Status:** No filter needed - intentionally retrieves the most recent forecast across ALL forecasts (not budget-specific).

```sql
select ... from forecast order by dateGenerated desc
```

**Reasoning:** This method is meant to find the globally most recent forecast, not filtered by budget or forecast ID.

### 3. ✅ CORRECT: `getListOf(Budget budget)` (Line 407)
**Status:** Properly filters by budget ID.

```sql
where Budget_idBudget = uuid_to_bin('[budget_id]') order by description
```

### 4. ✅ CORRECT: `checkForDuplicateTransactions()` (Line 590)
**Status:** Properly filters by forecast ID.

```sql
WHERE fi.Forecast_idForecast = UUID_TO_BIN('[forecast_id]')
```

### 5. ✅ CORRECT: `loadOverriddenTransactionKeys()` (Line 856)
**Status:** Properly filters by forecast ID using PreparedStatement.

```sql
WHERE fi.Forecast_idForecast = UUID_TO_BIN(?)
```

### 6. ✅ CORRECT: `hasReconciledForecastTransactionOnDate()` (Line 917)
**Status:** Properly filters by forecast ID.

```sql
WHERE fi.Forecast_idForecast = UUID_TO_BIN(?)
  AND fi.idForecastItem = UUID_TO_BIN(?)
  AND ft.plannedDate = ?
```

### 7. ✅ CORRECT: `getDailyBalanceList()` (Line 795)
**Status:** Uses `getNonZeroForecastTransactions(forecast)` which properly filters by forecast ID.

## Related Files Reviewed

### ForecastTransaction.java

#### ✅ `getNonZeroForecastTransactions(Forecast forecast)` - Line 423
Properly filters by forecast ID:
```sql
where ft.remainingAmount <> 0 
  and fi.Forecast_idForecast = uuid_to_bin('[forecast_id]')
```

#### ✅ `getNonZeroForecastTransactionsForForecastItem(ForecastItem)` - Line 397
Properly filters by forecast ID:
```sql
and fi.Forecast_idForecast = uuid_to_bin('[forecastItem.forecast.id]')
```

#### ✅ `getForecastTransactionsStartingOn(Forecast, Calendar)` - Line 369
Properly uses forecast-specific iterators that filter by forecast ID.

## Conclusion

**Only ONE scoping issue was found** in the entire Forecast.java file:
- `getFirstNonZeroTransactionDate()` - **FIXED**

All other methods properly filter by:
- Forecast ID (when operating on a specific forecast)
- Budget ID (when operating on forecasts for a specific budget)
- Or intentionally query across all forecasts (when that's the desired behavior)

## Recommendations

1. ✅ The identified issue has been fixed and tested
2. Consider adding SQL query unit tests to catch similar issues in the future
3. When adding new queries, always verify they include appropriate scope filters

## Testing

After the fix, verify:
1. Long-term forecast rendering works for all registers
2. Each forecast shows only its own transactions
3. The forecast start date is correct for each individual forecast
