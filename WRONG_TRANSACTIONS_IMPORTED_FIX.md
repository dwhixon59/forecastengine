# Fixed Wrong Transactions Imported From Wrong File

## Date: January 16, 2026

## Problem Description

When running the daily update for **Bill Pay Dave** (register #3), the system was importing transactions from **Bill Pay Danni** (register #2):

### Expected Transactions (from Checking1.qfx - Dave's file):
```
1. JPMORGAN CHASE B PAYROLL DD - $4,280.32 (David's paycheck)
2. TO HIXON D REF - $-1,625.00 (Michele alimony - DWH)
3. FORTH-CLARITY - $-369.50 (Debt payment)
```

### Actual Transactions Imported (from Danni's previous session):
```
1. McConnaughhay, Coonrod, Pope, Weaver & Stern P.A. - $3,639.22 (Danni's paycheck)
2. Amazon - $37.34 (Cast iron skillet)
3. Amazon - $9.94 (Mosquito dunks)
```

The system reported:
```
Successfully imported 3 cleared transactions into the register: Bill Pay Dave 
from file C:\Users\dwhix\Downloads\Checking1.qfx.
```

But it imported Danni's transactions instead of Dave's!

## Root Cause

**File:** `SessionController.java`  
**Issue:** The `financialInstitution` object was not being recreated when switching registers

### The Bug Flow:

1. **User imports for Bill Pay Danni (register #2):**
   - `SessionController.getRegisterBudgetForecast()` is called
   - `register` is null, so it creates a new financial institution:
     ```java
     financialInstitution = FinancialInstitutionFactory.create(this);
     ```
   - Financial institution opens Danni's file and loads her transactions

2. **User switches to Bill Pay Dave (register #3):**
   - `SessionController.setRegister(billPayDave)` is called (via Lombok's generated setter)
   - **PROBLEM:** Lombok's auto-generated setter ONLY does:
     ```java
     this.register = billPayDave;  // Sets the register
     // financialInstitution is NOT updated!
     ```

3. **ImportController is created:**
   ```java
   this.register = sessionController.getRegister();  // ✅ Gets Bill Pay Dave
   this.financialInstitution = sessionController.getFinancialInstitution();  // ❌ Gets Danni's financial institution!
   ```

4. **Import process runs:**
   - `register` says "Bill Pay Dave"
   - `financialInstitution` still points to **Danni's file** and transactions
   - Imports Danni's transactions into Dave's register!

### Why This Happened:

The `@Setter` annotation on the `SessionController` class (line 32) generated automatic setters for ALL fields, including `register`. When `setRegister()` was called, it only updated the register field without updating the dependent `financialInstitution` object.

## The Fix

### Part 1: Custom `setRegister()` Method

Added a custom `setRegister()` method that recreates the financial institution when the register changes:

```java
public void setRegister(Register register) {
    this.register = register;
    // Recreate the financial institution when the register changes
    if (register != null) {
        try {
            this.financialInstitution = FinancialInstitutionFactory.create(this);
        } catch (Exception e) {
            System.err.println("Error creating financial institution for register " + 
                register.getName() + ": " + e.getMessage());
            this.financialInstitution = null;
        }
    } else {
        this.financialInstitution = null;
    }
}
```

### Part 2: Removed Class-Level `@Setter`

Removed the `@Setter` annotation from the class level and added it to individual fields:

```java
@Getter  // Keep @Getter at class level
public class SessionController {
    
    private Register register = null;  // No @Setter - uses custom setter
    @Setter
    private Budget budget = null;
    @Setter
    private Forecast forecast = null;
    private FinancialInstitutionInt financialInstitution = null;  // Managed by setRegister()
    @Setter
    private ViewInt view;
    // ... etc
}
```

## Impact

### Before Fix (BROKEN):
```
User selects: Bill Pay Dave
System says: Importing from Checking1.qfx (Dave's file)
System does: ❌ Imports Danni's transactions (from previous session)
Result: Wrong transactions in wrong register!
```

### After Fix (CORRECT):
```
User selects: Bill Pay Dave
System says: Importing from Checking1.qfx (Dave's file)  
System does: ✅ Creates new financial institution for Dave's register
           ✅ Opens Dave's file
           ✅ Imports Dave's transactions
Result: Correct transactions in correct register!
```

## What Gets Fixed:

✅ **Register switching** - Each register gets its own financial institution  
✅ **File isolation** - Dave's file only imports Dave's transactions  
✅ **No cross-contamination** - Danni's data stays in Danni's register  
✅ **Consistent behavior** - Same transactions shown and imported  

## Related Issues Fixed:

This is the same root cause as the earlier issue where Bill Pay Dave saw Bill Pay Danni's provisional transactions. Both were caused by the `financialInstitution` object not being updated when switching registers.

## Testing Recommendations:

1. **Import for Bill Pay Danni:**
   - Run daily update
   - Import Danni's transactions
   - Verify Danni's transactions imported correctly

2. **Switch to Bill Pay Dave:**
   - Select Bill Pay Dave for daily update
   - Import Dave's transactions
   - **Verify:** Should see Dave's transactions, NOT Danni's

3. **Switch back to Bill Pay Danni:**
   - Select Bill Pay Danni again
   - **Verify:** Should still work correctly

4. **Multiple switches:**
   - Switch between several registers in one session
   - **Verify:** Each register gets its own correct transactions

## Technical Details:

### Lombok `@Setter` Behavior:

The `@Setter` annotation generates simple setter methods:

```java
// Lombok-generated setter (before fix)
public void setRegister(Register register) {
    this.register = register;  // Only sets the field
}
```

This doesn't handle dependent objects that need to be updated when a field changes.

### Custom Setter Pattern:

When you need additional logic when setting a field, you must:
1. Remove `@Setter` from that field
2. Write a custom setter with the needed logic
3. Keep `@Setter` on other fields that don't need custom logic

## Files Modified:

- **SessionController.java**
  - Removed `@Setter` from class level
  - Added `@Setter` to individual fields (except `register` and `financialInstitution`)
  - Added custom `setRegister()` method
  - Custom setter recreates financial institution when register changes

## Lessons Learned:

1. **Be careful with Lombok annotations** - They generate simple code that may not handle complex relationships
2. **Session state management** - When one object depends on another, both must be updated together
3. **Test register switching** - Always test switching between registers in the same session
4. **Review auto-generated code** - Sometimes you need custom logic instead of generated setters

## Prevention:

Consider:
1. **Adding tests** for register switching scenarios
2. **Documenting dependencies** between session objects
3. **Adding assertions** to verify financial institution matches current register
4. **Logging register/institution IDs** during import for debugging

This fix ensures that each register maintains its own financial institution context, preventing cross-contamination of transaction data between different accounts.

