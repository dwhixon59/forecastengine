# Cross-Account Transaction Contamination - Fixed

## Date: January 15, 2026

## Problem Description

During the daily update process for the Bill Pay Dave account, a transaction from the Bill Pay Danni account appeared:

```
Posted = 01-14-2026, Authorized = 01-14-2026, Merchant = not assigned yet, 
Amount = $3,639.22, Original Payee = MCCONNAUGHHAY DU PAYROLL 021254 JDL000000003
This provisional transaction has disappeared from the list of provisional transactions, 
but it does not appear as a cleared transaction.
It has likely been invalidated.  Do you want to remove it? ('y' or 'n'):
```

This transaction (MCCONNAUGHHAY DU PAYROLL - Danni's paycheck) belongs to Bill Pay Danni, not Bill Pay Dave.

## Root Cause

**File:** `ImportController.java`  
**Method:** `importProvisionalRegisterTransactionFile()`  
**Line:** 1382-1383

### The Bug:

```java
ResultSet rs = EntityInt.getRS(Transaction.getSelectQuery() + " where tr.cleared = false",
    "attempting to retrieve a list of provisional transactions.");
```

This query retrieves **ALL** provisional (uncleared) transactions from **ALL registers**, not just the current register being processed.

### Why This Caused Cross-Contamination:

1. Daily update processes **Bill Pay Dave** account
2. Query loads provisional transactions with `cleared = false` (no register filter)
3. Gets transactions from:
   - ✅ Bill Pay Dave (correct)
   - ❌ Bill Pay Danni (wrong!)
   - ❌ All other registers with provisional transactions (wrong!)
4. Code compares these against the newly imported transactions
5. Finds Danni's paycheck transaction in the list
6. Asks user if they want to delete it (because it didn't come from Dave's import file)

## The Fix

Added register filtering to the query:

```java
ResultSet rs = EntityInt.getRS(Transaction.getSelectQuery() + 
    " where tr.cleared = false AND tr.Register_idRegister = uuid_to_bin('" + register.getId() + "')",
    "attempting to retrieve a list of provisional transactions.");
```

### What Changed:
- **Before:** `where tr.cleared = false`
- **After:** `where tr.cleared = false AND tr.Register_idRegister = uuid_to_bin('{current register ID}')`

### Effect:
Now only retrieves provisional transactions that belong to the current register being processed.

## Impact

### Before Fix (BROKEN):
- ❌ Bill Pay Dave import sees Bill Pay Danni's transactions
- ❌ User gets confusing prompts about transactions from other accounts
- ❌ Risk of accidentally deleting valid transactions from other registers
- ❌ Cross-contamination between all accounts

### After Fix (CORRECT):
- ✅ Each register only sees its own provisional transactions
- ✅ No cross-contamination between accounts
- ✅ User only prompted about transactions relevant to the current account
- ✅ Safe - can't accidentally affect other accounts

## Testing Recommendations

1. **Run daily update for Bill Pay Dave**
   - Should ONLY see Dave's provisional transactions
   - Should NOT see any Danni transactions

2. **Run daily update for Bill Pay Danni**
   - Should ONLY see Danni's provisional transactions
   - Should NOT see any Dave transactions

3. **Have provisional transactions in multiple accounts**
   - Each account should only process its own
   - No cross-contamination warnings

## Files Modified

- **ImportController.java** (line 1383-1384)
  - Added `AND tr.Register_idRegister = uuid_to_bin('{register.getId()}')` to query

## Additional Notes

This was a critical bug that could have resulted in:
- Data integrity issues (deleting transactions from wrong accounts)
- User confusion (seeing transactions from other accounts)
- Incorrect reconciliation (matching transactions to wrong registers)

The fix ensures proper register isolation during the import process.

## Related Issue

This is similar to the issue fixed earlier in `DataManagerController` where the register wasn't being properly set in the sessionController. Both issues stem from incomplete register context management.

Consider reviewing other places where transactions are queried to ensure they're properly filtered by register.

