# Missing Forecast Transactions in Long-Term Forecast Rendering Fix

## Issue
When rendering the long-term forecast for Bill Pay Dave (or any register), the system showed:
```
No forecast transactions found in the forecast period.
The starting balance is: $363
```

However, when searching for forecast transactions through the "Manage Data" feature, all 100+ forecast transactions were present and accessible.

## Root Cause
The method `Forecast.getFirstNonZeroTransactionDate()` was **not filtering by forecast ID**. 

### The Buggy Query (Line 439-442):
```sql
select MIN(plannedDate) as 'ft.plannedDate' 
from forecast_transaction ft 
inner join forecast_item on ForecastItem_idForecastItem = idForecastItem 
where remainingAmount <>0 and amount <>0
```

This query was finding the minimum planned date across **ALL forecasts** in the database, not just the specific forecast being rendered.

### The Problem:
1. Bill Pay Danni's forecast has transactions starting from January 15, 2026 (today)
2. Bill Pay Dave's forecast has transactions starting from January 15, 2026 (today)
3. But Bill Pay Danni might have been processed first and has an earlier date
4. When rendering Bill Pay Dave's forecast, the query would return Bill Pay Danni's earliest date
5. The system would then try to find Bill Pay Dave's transactions starting from that date
6. Since Bill Pay Dave's forecast doesn't have transactions on that date, it would find nothing

## Solution
Added a filter to the query to only look at transactions for the specific forecast being rendered:

### The Fixed Query:
```sql
select MIN(plannedDate) as 'ft.plannedDate' 
from forecast_transaction ft 
inner join forecast_item fi on ft.ForecastItem_idForecastItem = fi.idForecastItem 
where ft.remainingAmount <>0 
  and fi.amount <>0 
  and fi.Forecast_idForecast = uuid_to_bin('[forecast_id]')
```

### Changes Made:
1. Added table alias `fi` for `forecast_item` in the JOIN
2. Added `WHERE` clause filter: `fi.Forecast_idForecast = uuid_to_bin('[forecast_id]')`
3. Qualified the column references with table aliases for clarity

## Impact

### Before Fix
- Long-term forecast rendering would fail to find transactions for some forecasts
- The system would use the wrong start date (from a different forecast)
- Users would see "No forecast transactions found" even though data existed

### After Fix
- Each forecast uses its own earliest transaction date
- Long-term forecast rendering correctly finds and displays all transactions
- The system is properly isolated by forecast

## Files Modified
- `Forecast.java` - Method `getFirstNonZeroTransactionDate()` (lines 438-450)

## Testing
After applying this fix:
1. Render the long-term forecast for Bill Pay Dave
2. Verify it shows all expected transactions
3. Render the long-term forecast for Bill Pay Danni
4. Verify it also shows all expected transactions
5. Confirm that each forecast uses its own start date

## Related Issues
This is related to the cross-register contamination issue that was fixed earlier. Both issues stem from queries not properly filtering by the specific entity (register or forecast) being processed.

## Date
January 16, 2026
