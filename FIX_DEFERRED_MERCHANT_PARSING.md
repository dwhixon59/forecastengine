# Fix: Defer Merchant Payee Parsing for Provisional Transactions

**Date**: December 16, 2025  
**Issue**: User prompted to identify transfer accounts for duplicate provisional transactions  
**Solution**: Defer merchant payee parsing until after duplicate checking

---

## Problem Description

### Original Flow (Problematic)
1. `importCsvProvisionalTransactionFile` reads CSV file
2. For each line → calls `loadProvisionalTransactionFromCSV`
3. `loadProvisionalTransactionFromCSV` → calls `parseMerchantPayee`
4. `parseMerchantPayee` → calls `resolveUnmatchedAccount` for transfers without account numbers
5. **User prompted IMMEDIATELY for every transaction in CSV**
6. Later → matches with existing provisional transactions in database

### The Problem
For transfer transactions without account numbers in the payee string, the user is asked to identify the account for **EVERY provisional transaction import**, even if:
- That exact transaction already exists in the database
- The merchant was already identified in a previous import
- The transaction will be matched as a duplicate and skipped

This results in **unnecessary user prompts** for duplicate transactions.

---

## Solution Implemented

### New Flow (Fixed)
1. `importCsvProvisionalTransactionFile` reads CSV file
2. For each line → calls `loadProvisionalTransactionFromCSV`
3. `loadProvisionalTransactionFromCSV` → stores RAW payee (no parsing yet)
4. Match with existing provisional transactions in database
5. **Only for NEW transactions** → call `parseMerchantPayee`
6. `parseMerchantPayee` → calls `resolveUnmatchedAccount` if needed
7. User prompted ONLY for truly new transactions

### Benefits
✅ **No duplicate prompts** - Existing transactions are matched before parsing  
✅ **Better user experience** - Only asked about new transactions  
✅ **Cleaner separation** - Loading vs. processing are separate phases  
✅ **More efficient** - Skip expensive parsing for duplicates  
✅ **Minimal changes** - Small, focused changes to two files

---

## Code Changes

### File 1: `WellsFargoBank.java`

**Method**: `loadProvisionalTransactionFromCSV`

**Change**: Remove the call to `parseMerchantPayee` during CSV loading

**Before**:
```java
// Figure out which merchant the transaction is associated with:
String merchantPayee = parseMerchantPayee(postDate, amount, tokens[1 + iOffset]);

// Create a transaction based on the provisional record:
return new Transaction(register, tokens[iOffset], tokens[1 + iOffset], amount, merchantPayee);
```

**After**:
```java
// Don't parse merchant payee yet - defer until after duplicate checking in ImportController.
// This avoids prompting the user for transfers without account numbers when the provisional
// transaction already exists in the database with merchant already identified.
// The merchant payee will be parsed later for new transactions only.

// Create a transaction based on the provisional record (merchantPayee will be set to raw payee):
return new Transaction(register, tokens[iOffset], tokens[1 + iOffset], amount, tokens[1 + iOffset]);
```

**Impact**: Provisional transactions now load with raw payee as merchant payee (will be parsed later).

---

### File 2: `ImportController.java`

**Method**: `importCsvProvisionalTransactionFile`

**Change**: Add merchant payee parsing AFTER duplicate checking, only for new transactions

**Before**:
```java
// If the key to the provisional transaction is less than the key to the register transaction:
if (comparison < 0) {
    /*
     * then this is a new provisional transaction, so add this transaction to the database:
     */

    // Display basic transaction info so user knows what we're processing
    view.say("\nProcessing provisional transaction:");
    view.say("  Date: " + calendarDateToStringDate(provisionalTransactions.get(provTrxIndex).getPostDate()));
    // ... more display code
```

**After**:
```java
// If the key to the provisional transaction is less than the key to the register transaction:
if (comparison < 0) {
    /*
     * then this is a new provisional transaction, so add this transaction to the database:
     */

    // Parse the merchant payee for this NEW transaction (deferred from loadProvisionalTransactionFromCSV).
    // This avoids prompting user for transfers without account numbers when the transaction
    // already exists in the database.
    String merchantPayee = financialInstitution.parseMerchantPayee(
            provisionalTransactions.get(provTrxIndex).getPostDate(),
            provisionalTransactions.get(provTrxIndex).getAmount(),
            provisionalTransactions.get(provTrxIndex).getPayee());
    provisionalTransactions.get(provTrxIndex).setMerchantPayee(merchantPayee);

    // Display basic transaction info so user knows what we're processing
    view.say("\nProcessing provisional transaction:");
    view.say("  Date: " + calendarDateToStringDate(provisionalTransactions.get(provTrxIndex).getPostDate()));
    view.say("  Amount: " + formatDollarAmount(provisionalTransactions.get(provTrxIndex).getAmount()));
    view.say("  Payee: " + provisionalTransactions.get(provTrxIndex).getPayee());
    view.say("  Merchant Payee: " + merchantPayee);
    // ... more display code
```

**Impact**: 
- Merchant payee parsing now happens inside the "new transaction" block
- User prompts for `resolveUnmatchedAccount` only occur for new transactions
- Duplicate transactions skip this entire block

---

## Testing Recommendations

### Test Case 1: New Transfer Without Account Number
**Setup**: Import provisional transaction file with transfer that has no account number  
**Expected**: User is prompted to identify the account  
**Result**: ✅ Should work (parseMerchantPayee called for new transactions)

### Test Case 2: Duplicate Transfer Import
**Setup**: 
1. Import provisional transaction file with transfer (user identifies account)
2. Re-import the same file without clearing provisional transactions

**Before fix**: User prompted again to identify account  
**After fix**: No prompt - transaction matched as duplicate  
**Result**: ✅ Should work (parseMerchantPayee skipped for duplicates)

### Test Case 3: Transfer With Account Number
**Setup**: Import provisional transaction with transfer that includes account number  
**Expected**: No user prompt (account automatically identified from payee)  
**Result**: ✅ Should work (no change to this code path)

### Test Case 4: Non-Transfer Transaction
**Setup**: Import regular purchase transaction (not a transfer)  
**Expected**: Merchant identified from payee, no account number needed  
**Result**: ✅ Should work (parseMerchantPayee handles this normally)

### Test Case 5: Mixed File
**Setup**: Import file with:
- New transfer without account number (should prompt)
- Duplicate transfer (should not prompt)
- New purchase (should not prompt)

**Expected**: Only one prompt (for new transfer)  
**Result**: ✅ Should work (selective parsing based on comparison logic)

---

## Edge Cases Considered

### Edge Case 1: Transaction Constructor with merchantPayee = payee
**Question**: Does the Transaction constructor handle merchantPayee being the same as payee?  
**Answer**: Yes - the constructor accepts this and sets merchantPayee field appropriately

### Edge Case 2: What if parseMerchantPayee throws exception?
**Question**: Is exception handling still correct?  
**Answer**: Yes - the try-catch in importCsvProvisionalTransactionFile wraps the new code

### Edge Case 3: Performance with large files?
**Question**: Does deferring parsing improve or hurt performance?  
**Answer**: Improves - skip parsing for all duplicate transactions

### Edge Case 4: Transaction ordering
**Question**: Does the sorting comparator work with raw payee?  
**Answer**: Yes - sorts by payee + amount, raw payee is fine for sorting

---

## Alternative Solutions Considered

### Alternative 1: Remove resolveUnmatchedAccount from parseMerchantPayee
**Pros**: Would make parseMerchantPayee non-interactive  
**Cons**: 
- ❌ Would need to handle transfers separately everywhere
- ❌ Would break cleared transaction imports that also use parseMerchantPayee
- ❌ Larger code changes required

**Verdict**: ❌ Not recommended - too invasive

### Alternative 2: Don't ask user in resolveUnmatchedAccount
**Pros**: Would make all imports non-interactive  
**Cons**: 
- ❌ Would lose ability to identify transfers automatically
- ❌ User would have to manually fix all transfers later
- ❌ Defeats purpose of the feature

**Verdict**: ❌ Not recommended - reduces functionality

### Alternative 3: Remove parseMerchantPayee entirely from loadProvisionalTransactionFromCSV ✅
**Pros**: 
- ✅ Defers expensive operations until needed
- ✅ Minimal code changes
- ✅ Clean separation of loading vs. processing
- ✅ Fixes the duplicate prompt issue

**Cons**: 
- ⚠️ Need to remember to parse in the right place

**Verdict**: ✅ **Implemented - Best solution**

---

## Backward Compatibility

### Existing Behavior Preserved
✅ **Cleared transaction imports** - Still use parseMerchantPayee normally  
✅ **User prompts** - Still occur when needed (just not for duplicates)  
✅ **Transfer identification** - Still works the same way  
✅ **Database schema** - No changes required  
✅ **Transaction matching** - No changes to comparison logic

### No Breaking Changes
✅ **API signatures** - No changes to FinancialInstitutionInt interface  
✅ **Transaction class** - No changes to constructors or fields  
✅ **Import process** - Same overall flow, just deferred parsing  

---

## Documentation Updates

### WellsFargoBank.java
✅ Added comment explaining why parseMerchantPayee is not called  
✅ Notes that merchantPayee will be raw payee initially  
✅ References where parsing happens instead

### ImportController.java
✅ Added comment explaining when merchant payee is parsed  
✅ Notes this is deferred from loadProvisionalTransactionFromCSV  
✅ Explains the benefit (no duplicate prompts)

---

## Verification

### Compilation
✅ **Status**: Successful  
✅ **Command**: `mvn compile -q`  
✅ **Result**: No errors, no new warnings

### Code Review Checklist
✅ Logic is correct (parsing deferred to right place)  
✅ Exception handling preserved  
✅ Comments added explaining the change  
✅ No breaking changes to API  
✅ Minimal code changes (focused fix)  
✅ Backward compatible

---

## Next Steps

1. **Test with real data**:
   - Import a provisional transaction file with transfers
   - Re-import the same file
   - Verify user is only prompted once

2. **Monitor for issues**:
   - Watch for any transactions with incorrect merchant payee
   - Check that transfer identification still works
   - Verify forecast matching still works with deferred parsing

3. **Document in user guide** (if applicable):
   - Note that merchant identification happens after duplicate checking
   - This is normal and expected behavior

4. **Consider future enhancements**:
   - Could cache merchant payee parsing results
   - Could add progress indicator for parsing phase
   - Could batch prompts if multiple new transfers need identification

---

## Summary

**Problem**: User prompted for every provisional transaction import, including duplicates  
**Root Cause**: Merchant payee parsed too early (during CSV loading)  
**Solution**: Defer parsing until after duplicate checking  
**Result**: User only prompted for NEW transactions  
**Impact**: Better user experience, more efficient processing  
**Risk**: Low - minimal changes, backward compatible  
**Testing**: Compile successful, manual testing recommended

**Status**: ✅ **COMPLETE** - Ready for testing

