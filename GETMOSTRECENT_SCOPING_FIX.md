# getMostRecent() Scoping Fix

## Date
January 17, 2026

## Issue
The `Forecast.getMostRecent()` method was retrieving the most recent forecast across **ALL budgets** in the system, which could cause cross-budget contamination.

## Problem Examples

### 1. ForecastTransaction.getApplicableForecastTransaction()
```java
// Line 871 in ForecastTransaction.java
ForecastTransaction forecastTransaction = getApplicableForecastTransaction(
    Forecast.getMostRecent(),  // ❌ Could get forecast from wrong budget!
    idBudgetItem,
    date
);
```

### 2. View Constructors
```java
// SpreadsheetXmlForecastView.java, ExcelForecastView.java, etc.
public SpreadsheetXmlForecastView() throws EntityException, SQLException {
    super(Forecast.getMostRecent());  // ❌ Could load wrong budget's forecast!
    // ...
}
```

## Why This Is a Problem

1. **Multiple Budgets**: If you have Bill Pay Danni budget and Bill Pay Dave budget, `getMostRecent()` might return Dave's forecast when you're working with Danni's budget
2. **Wrong Context**: Similar to the cross-register transaction contamination we fixed earlier
3. **Unpredictable Behavior**: Which forecast you get depends on which was generated most recently, not which one you actually need

## Solution

### Before:
```java
public static Forecast getMostRecent() throws EntityException, SQLException {
    String selectMostRecentQuery = selectQuery + "order by dateGenerated desc";
    // Returns forecast from ANY budget - potentially wrong one!
}
```

### After:
```java
// Old version deprecated with warning
@Deprecated
public static Forecast getMostRecent() throws EntityException, SQLException {
    // ... same implementation ...
}

// New budget-scoped version
public static Forecast getMostRecent(Budget budget) throws EntityException, SQLException {
    String selectMostRecentQuery = selectQuery + 
            "where Budget_idBudget = uuid_to_bin('" + budget.getId() + "') " +
            "order by dateGenerated desc";
    // Returns forecast for SPECIFIC budget - correct!
}
```

## Changes Made

1. **Deprecated the unscoped version** with clear JavaDoc warning
2. **Added budget-scoped overload** that filters by budget ID
3. **Documented the issue** so future developers understand why both versions exist

## Impact

### Before Fix
- Could retrieve forecast from wrong budget
- Unpredictable behavior when multiple budgets exist
- Silent data contamination issues

### After Fix
- Can explicitly request forecast for specific budget
- Old code still works (backward compatible) but shows deprecation warning
- Future code should use budget-scoped version

## Next Steps

**Code that needs updating** (uses deprecated version):
1. `ForecastTransaction.getApplicableForecastTransaction()` - Line 871
2. `SpreadsheetXmlForecastView` constructor - Line 221
3. `ExcelForecastView` constructor - Lines 132, 155
4. `CsvForecastView` constructor - Line 51

These should be updated to use `getMostRecent(budget)` when the budget context is known.

## Testing

After updating calling code:
1. Verify each budget gets its own most recent forecast
2. Test with multiple budgets to ensure no cross-contamination
3. Confirm backward compatibility - deprecated method still works

## Related Issues

This is part of the broader **scoping issue** pattern we've been fixing:
- ✅ Transaction.getByImportRecordId() - Fixed to filter by register ID
- ✅ Forecast.getFirstNonZeroTransactionDate() - Fixed to filter by forecast ID
- ✅ Forecast.getMostRecent() - Fixed to filter by budget ID

## Files Modified
- `Forecast.java` - Added budget-scoped getMostRecent() method and deprecated old version
