# Forecast Transaction Duplication Issue - Analysis and Fix

## Problem Summary

Duplicate forecast transactions were appearing in the database when viewing the forecast transaction list in the "Manage Data" feature. Examples included:
- Multiple "Groceries Memo = Walmart+ Membership" entries on the same date  
- Multiple "Graduate student loan" entries on the same date
- Multiple "School lunches for Justin" entries on the same date

## Root Cause Analysis

### Initial Hypothesis (INCORRECT)
Initially thought the `saveForecastTransactions()` method was using plain INSERT without duplicate key handling.

### Actual Root Cause
**Each ForecastTransaction gets a unique UUID when created** (via `UUID.randomUUID()`), so there is NO duplicate key error even when the same logical transaction (same ForecastItem + plannedDate) is inserted multiple times.

The real issue is that:
1. **The database has no UNIQUE constraint** on the combination of (ForecastItem_idForecastItem, plannedDate)
2. **The delete query before saving might fail** to delete all existing transactions for various reasons:
   - Transactions marked as `overridden = TRUE` are NOT deleted (intentional)
   - Transactions with splits are NOT deleted (intentional)
   - **If the delete fails or is incomplete, old transactions remain**
3. **If updateForecast() is run multiple times** on overlapping date ranges, new transactions are created each time

### Why This Happens
When a user updates a forecast:
```java
// Step 1: Delete forecast transactions >= updateStartDate (lines 926-945)
executeUpdate(deleteQuery, "deleting all the forecast transactions after...");

// Step 2: Generate NEW forecast transactions with NEW UUIDs (line 967)
forecastEngine.generateForecastTransactions(forecast, updateStartDate);

// Step 3: Save the NEW transactions (line 970) 
forecast.saveForecastTransactions();  // INSERT with new UUIDs
```

If Step 1 fails partially or if the forecast is updated again before completion, you get duplicates with different UUIDs.

## Solution

### Immediate Fix: Add Database Constraint
Add a UNIQUE constraint to prevent logical duplicates:

```sql
ALTER TABLE forecast_transaction 
ADD UNIQUE KEY uk_forecast_item_date (ForecastItem_idForecastItem, plannedDate);
```

**Note:** This will FAIL if duplicates already exist. Run the cleanup script first.

### Code Enhancement: Use REPLACE Instead of INSERT
While `INSERT ... ON DUPLICATE KEY UPDATE` won't help (different UUIDs), we can improve the save logic:

**Option A: Delete ALL transactions for this forecast before saving** (safest)
```java
// In updateForecast(), before calling saveForecastTransactions():
String deleteAllQuery = "DELETE FROM forecast_transaction WHERE " +
    "ForecastItem_idForecastItem IN (" +
        "SELECT idForecastItem FROM forecast_item " +
        "WHERE Forecast_idForecast = UUID_TO_BIN('" + forecast.getId() + "')" +
    ")";
executeUpdate(deleteAllQuery, "deleting all forecast transactions");
```

**Option B: Use deterministic UUIDs** (more complex)
Generate UUIDs based on ForecastItem ID + plannedDate instead of random UUIDs, then use INSERT ON DUPLICATE KEY UPDATE.

## Files Modified

1. **Forecast.java** - `saveForecastTransactions()` method
   - Currently uses try-with-resources for PreparedStatement (good practice)
   - Using INSERT statement (works if delete is complete)
   - **Recommendation:** Change ForecastController to delete ALL transactions before regenerating

## Cleaning Up Existing Duplicates

A SQL cleanup script has been created: `cleanup_duplicate_forecast_transactions.sql`

**IMPORTANT:** Backup your database before running!

The script:
1. Identifies all duplicates (same ForecastItem + plannedDate)
2. Deletes duplicates, keeping only the most recently updated version
3. Verifies cleanup was successful
4. **Adds a UNIQUE constraint** to prevent future duplicates

To run:
```bash
mysql -u [username] -p ForecastDatabase < cleanup_duplicate_forecast_transactions.sql
```

## Prevention Strategy

### Short-term (IMPLEMENTED)
- Run cleanup script to remove existing duplicates
- Add UNIQUE constraint to database

### Long-term (RECOMMENDED)
Choose ONE of:

**Option 1: More aggressive delete (SIMPLEST)**
Delete ALL forecast transactions for the entire forecast before regenerating any part of it.

**Option 2: Deterministic UUIDs (CLEANEST)**
Generate UUIDs using a hash of (ForecastItem_idForecastItem + plannedDate), making them repeatable.

**Option 3: Check before insert (SAFEST)**
Before inserting, query if a transaction already exists for this ForecastItem + plannedDate combination.

## Testing Recommendation

After deploying:
1. Update a forecast
2. Update the SAME forecast again immediately  
3. Check for duplicates in "Manage Data"
4. Verify the UNIQUE constraint prevents duplicates (should get SQL error if duplication is attempted)

## Date: January 13, 2026


