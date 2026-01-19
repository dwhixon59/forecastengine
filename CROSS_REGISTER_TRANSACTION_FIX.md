# Cross-Register Transaction Contamination Fix

## Issue
Transactions from different registers (e.g., Bill Pay Danni and Bill Pay Dave) were being cross-contaminated during the import process. When importing transactions, the system would incorrectly retrieve transactions from other registers if they had the same Financial Institution Transaction ID (FITID).

## Root Cause
There were TWO issues causing this problem:

### 1. Code Issue: Non-Register-Specific Lookup
The `Transaction.getByImportRecordId()` method was only filtering by the import record ID without considering which register the transaction belonged to. This meant that if two different registers had transactions with the same FITID, the import process would retrieve the wrong transaction.

### 2. Database Issue: Incorrect Unique Constraint
The database had a UNIQUE constraint on `importRecordId` alone:
```sql
UNIQUE KEY `importRecord_UNIQUE` (`importRecordId`)
```

This prevented the same FITID from existing in different registers, which is incorrect. Different bank accounts (registers) can legitimately have transactions with the same FITID.

For example:
- Bill Pay Danni imports a paycheck transaction with FITID "202601151"
- Bill Pay Dave tries to import his own paycheck transaction with FITID "202601151"
- The database constraint would reject Dave's transaction as a duplicate, even though it's a completely different transaction in a different account

## Solution

### Part 1: Code Changes

#### Enhanced Transaction.getByImportRecordId()
- **File**: `Transaction.java`
- **Changes**: 
  - Created a new overloaded method `getByImportRecordId(String importRecordId, UUID registerId)` that filters by both import record ID and register ID
  - Marked the old method as `@Deprecated` to encourage migration to the safer version
  - The new method adds a WHERE clause to filter by `Register_idRegister`

#### Updated ImportController
- **File**: `ImportController.java`
- **Changes**:
  - Line ~332: Updated QFX import to use `Transaction.getByImportRecordId(importRecordId, register.getId())`
  - Line ~801: Updated CSV import to use `Transaction.getByImportRecordId(importRecordId, register.getId())`

### Part 2: Database Schema Changes

#### Fix the Unique Constraint
The database constraint needs to be changed from a single-column constraint to a composite constraint:

**OLD (Incorrect):**
```sql
UNIQUE KEY `importRecord_UNIQUE` (`importRecordId`)
```

**NEW (Correct):**
```sql
UNIQUE KEY `importRecord_Register_UNIQUE` (`importRecordId`, `Register_idRegister`)
```

#### How to Apply the Database Fix

1. **Check for existing duplicate data:**
   ```bash
   mysql -u root -p forecastengine < check_duplicate_importrecords.sql
   ```
   
2. **Apply the fix:**
   ```bash
   mysql -u root -p forecastengine < apply_importrecord_fix.sql
   ```

The fix script will:
- Report any existing duplicate import record IDs across registers
- Drop the old single-column unique constraint
- Add the new composite unique constraint
- Verify the changes

## Impact

### Before Fix
- **Code**: Transactions could be incorrectly matched across different registers
- **Database**: Same FITID could not exist in different registers (prevented legitimate imports)
- **Result**: Cross-contamination and failed imports

### After Fix
- **Code**: Each register's transactions are properly isolated
- **Database**: Same FITID can exist in different registers but not within the same register
- **Result**: Correct transaction isolation and successful imports

## Testing Recommendations
1. Import transactions for multiple registers with overlapping FITIDs
2. Verify that each register only shows its own transactions
3. Confirm that duplicate detection works within each register but not across registers
4. Verify that re-importing the same transaction file doesn't create duplicates

## Files Created
- `CROSS_REGISTER_TRANSACTION_FIX.md` - This documentation
- `check_duplicate_importrecords.sql` - Query to check for existing cross-contaminated data
- `fix_importrecord_unique_constraint.sql` - Simple migration script
- `apply_importrecord_fix.sql` - Comprehensive fix script with reporting

## Date
January 16, 2026
