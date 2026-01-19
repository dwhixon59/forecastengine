# Explanation: Why Register References Matter for File Import

## Your Question

"The register, not the financial institution, contains the name of the import file. How can not refreshing the financial institution cause the wrong filename to be used?"

## You Were Right to Question This!

You correctly identified that the **Register** object contains the import file path, not the FinancialInstitution. However, the bug was more subtle than I initially explained.

## The Actual Problem

### The Hidden Issue: Cached Register Reference

When a `FinancialInstitution` object is created, it makes a **copy of the register reference**:

```java
// FinancialInstitution constructor (BEFORE the fix)
protected FinancialInstitution(SessionController sessionController) {
    this.sessionController = sessionController;
    this.register = sessionController.getRegister();  // ⚠️ Copies the reference!
    // ...
}
```

Later, when the FinancialInstitution needs to import a file:

```java
// In importRegisterTrxFile()
String fullPath = register.getTrxImportFilePath();  // ⚠️ Uses the CACHED register!
```

### The Bug Flow:

1. **User imports for Bill Pay Danni:**
   - SessionController.register = Bill Pay Danni
   - FinancialInstitution is created
   - FinancialInstitution.register = Bill Pay Danni (reference copied)
   - Import runs successfully from Danni's file

2. **User switches to Bill Pay Dave:**
   - SessionController.setRegister(Bill Pay Dave) is called
   - SessionController.register = Bill Pay Dave ✅ (updated)
   - **BUT:** FinancialInstitution.register = Bill Pay Danni ❌ (still old reference!)

3. **Import tries to run:**
   - `importRegisterTrxFile()` calls `register.getTrxImportFilePath()`
   - Uses the **cached** register (still Danni!)
   - Gets Danni's import file path
   - Opens and imports Danni's file
   - But the transaction says it's going into Dave's register!

## Why My Initial Fix Worked (For a Different Reason)

My initial fix to recreate the FinancialInstitution when the register changes worked because:

1. New FinancialInstitution object created
2. New object gets fresh register reference from SessionController
3. New register reference points to Dave
4. Import uses correct file

**But this wasn't the best solution** because it creates unnecessary objects.

## The Better Fix (What I Just Implemented)

Instead of caching the register reference, use the SessionController's current register:

```java
// FinancialInstitution (AFTER the fix)
protected FinancialInstitution(SessionController sessionController) {
    this.sessionController = sessionController;
    // Don't cache register - removed this line!
    // ...
}

// Add a getter that always uses current register
protected Register getRegister() {
    return sessionController.getRegister();
}
```

Now when importing:

```java
// In importRegisterTrxFile()
String fullPath = getRegister().getTrxImportFilePath();  // ✅ Always uses current register!
```

## Why This Is Better

### Before (Caching Register):
- ❌ FinancialInstitution has stale register reference
- ❌ Need to recreate FinancialInstitution when register changes
- ❌ Extra object creation overhead
- ❌ Two places to keep in sync (SessionController and FinancialInstitution)

### After (Using getRegister()):
- ✅ FinancialInstitution always uses current register
- ✅ No need to recreate FinancialInstitution
- ✅ Single source of truth (SessionController)
- ✅ Simpler, more maintainable code

## The Real Root Cause

The root cause was **caching mutable state**. The `register` can change during a session (when the user switches registers), but the `FinancialInstitution` was caching a reference to the old register.

### General Principle:

**Don't cache references to mutable objects that can change.** Instead, use a getter to always fetch the current value.

## Files Modified

1. **FinancialInstitution.java:**
   - Removed `register` field
   - Added `getRegister()` method that returns `sessionController.getRegister()`
   - Updated all `register.` references to `getRegister().`

2. **WellsFargoBank.java:**
   - Updated all `register.` references to `getRegister().`

3. **SessionController.java:**
   - Added custom `setRegister()` method (this is still useful for recreating FinancialInstitution)
   - Added `setFinancialInstitution()` method

## Why Both Fixes Are Needed

1. **getRegister() method:** Ensures FinancialInstitution always uses current register
2. **Recreating FinancialInstitution:** Ensures the FinancialInstitution is properly initialized for the new register's financial institution type

Together, these fixes ensure:
- The correct register is always used
- The correct financial institution logic is always used
- The correct import file is always opened

## Testing

To verify the fix:

1. Import transactions for Bill Pay Danni
2. Switch to Bill Pay Dave
3. Import transactions
4. **Verify:** Dave's file is opened (not Danni's)
5. **Verify:** Correct transactions are imported

## Should Other Code Be Changed?

### ImportController

You asked: "Should the code that is getting the register from the financial institution be changed to get it from the session instead?"

**Answer:** The `ImportController` also caches the register reference in its constructor:

```java
ImportController(SessionController sessionController) {
    this.register = sessionController.getRegister();  // Also caches!
    // ...
}
```

**However, this is acceptable** because:

1. **ImportController is recreated for each operation** - In `DailyUpdateController.run()`, a new `ImportController` is created at the start:
   ```java
   ImportController importController = new ImportController(sessionController);
   ```

2. **Short-lived object** - The ImportController is used for one import operation and then discarded

3. **No register switching mid-operation** - The user can't switch registers while an import is in progress

**In contrast, FinancialInstitution:**
- Is created once and stored in SessionController
- Persists across multiple operations  
- Lives through register switches
- **That's why it needed to be fixed**

### The Principle

**Caching is acceptable when:**
- ✅ The object is short-lived (like ImportController)
- ✅ The cached value won't change during the object's lifetime
- ✅ The object is recreated when the cached value needs to change

**Caching is problematic when:**
- ❌ The object persists across operations (like FinancialInstitution in SessionController)
- ❌ The cached value can change while the object still exists
- ❌ The object is reused when it should be seeing new values

### Architectural Consistency

For maximum consistency and maintainability, you **could** change ImportController to use `sessionController.getRegister()` instead of caching. This would:

**Pros:**
- ✅ Consistent pattern across all controllers
- ✅ Single source of truth
- ✅ Future-proof if ImportController lifetime changes

**Cons:**
- ❌ Extra method calls (minor performance impact)
- ❌ Requires changing many lines of code
- ❌ Low benefit since current pattern is safe

**Recommendation:** The current ImportController caching is safe and doesn't need to be changed unless you want to enforce a consistent pattern across all controllers.

## Summary

You were absolutely right that the register contains the filename, not the financial institution. The bug was that the financial institution was **caching the wrong register object**, so when it asked for the import file path, it asked the **wrong register**!

The fix ensures the financial institution always uses the **current** register from the SessionController, not a stale cached copy.

