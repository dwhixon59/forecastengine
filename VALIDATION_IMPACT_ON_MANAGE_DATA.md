# Impact of Period/HowOccurs Validation on Manage Data Feature

## Summary

The validation checks will **catch invalid combinations when saving**, providing helpful error messages to guide users. The manage data feature will continue to work as before, but now prevents inconsistent data from being persisted.

---

## How the Validation Works

### When Validation Occurs

The validation is triggered at **save time** in two places:

1. **`BudgetItem.save(SaveMethod method)`** - Called when creating new budget items
2. **`BudgetItem.update()`** - Called when updating existing budget items

Both methods now call `validatePeriodHowOccursConsistency()` before persisting changes to the database.

---

## Impact on Creating Budget Items

### Current Flow (getBudgetItemFromUser)

1. User enters all fields through a wizard:
   - Category, Payee, Memo
   - **Period** (line 843-845)
   - Amount, Running Balance, etc.
   - Item Type, How Important
   - **HowOccurs** (line 906-908)
   - How Paid

2. BudgetItem object is created with all fields (line 916-936)

3. **New:** Validation happens when item is saved

### What Changes

**Before validation:**
- User could create `Period=WEEKLY` + `HowOccurs=UNPLANNED`
- Invalid data would be saved to database
- No error message

**After validation:**
- User creates `Period=WEEKLY` + `HowOccurs=UNPLANNED`
- When the item is saved, validation throws `BudgetException`
- User sees error message:
  ```
  Invalid combination: HowOccurs = UNPLANNED requires Period = ON_DEMAND, 
  but found Period = WEEKLY (Item: [payee], Category: [category])
  ```
- Item is **not saved** to database
- User must correct the combination

### User Experience Impact

**Scenario 1: Creating an Unplanned Expense**
```
User selects:
  Period: MONTHLY
  HowOccurs: UNPLANNED

Result: ❌ Error on save
  "Invalid combination: HowOccurs = UNPLANNED requires Period = ON_DEMAND"

User corrects to:
  Period: ON_DEMAND
  HowOccurs: UNPLANNED

Result: ✅ Saves successfully
```

**Scenario 2: Creating a Periodic Expense**
```
User selects:
  Period: ON_DEMAND
  HowOccurs: PERIODIC

Result: ❌ Error on save
  "Invalid combination: HowOccurs = PERIODIC requires a scheduled Period (not ON_DEMAND)"

User corrects to:
  Period: MONTHLY
  HowOccurs: PERIODIC

Result: ✅ Saves successfully
```

**Scenario 3: Creating an Envelope (Valid Edge Case)**
```
User selects:
  Period: ON_DEMAND
  HowOccurs: ENVELOPE

Result: ✅ Saves successfully
  (This is allowed - saving for something with no set schedule)
```

---

## Impact on Updating Budget Items

### Current Flow (updateBudgetItem)

1. User sees current values (lines 960-977)
2. User selects which field to update from menu
3. User updates **Period** (case "e", lines 1043-1048)
   OR updates **HowOccurs** (case "o", lines 1104-1109)
4. Loop continues until user selects "done - save changes"
5. **New:** Validation happens when `budgetItem.update()` is called (line 1128)

### What Changes

**Before validation:**
- User could update Period to WEEKLY while HowOccurs=UNPLANNED
- OR update HowOccurs to UNPLANNED while Period=MONTHLY
- Invalid combination would be saved

**After validation:**
- User updates one field creating invalid combination
- When selecting "done - save changes", validation throws exception
- User sees error message
- Changes are **not saved**
- User returns to update menu to fix the problem

### User Experience Impact

**Scenario: User Updates Period Creating Invalid State**
```
Current values:
  Period: ON_DEMAND
  HowOccurs: UNPLANNED

User selects: "e - period"
User changes Period to: WEEKLY

Current values (in memory, not saved):
  Period: WEEKLY
  HowOccurs: UNPLANNED

User selects: "- - done - save changes"

Result: ❌ Error thrown
  "Invalid combination: HowOccurs = UNPLANNED requires Period = ON_DEMAND, 
   but found Period = WEEKLY"

User is returned to update menu
User must either:
  Option A: Change Period back to ON_DEMAND
  Option B: Change HowOccurs to PERIODIC (or other valid value)
```

---

## Error Handling Flow

### Exception Chain

```
User saves invalid combination
    ↓
BudgetItem.update() or save()
    ↓
validatePeriodHowOccursConsistency()
    ↓
Detects violation
    ↓
throws BudgetException with descriptive message
    ↓
Exception propagates to controller
    ↓
Controller catches exception
    ↓
User sees error message
    ↓
User corrects the values
    ↓
Tries to save again
```

### Where Exceptions Are Caught

The calling code in BudgetController should already have try-catch blocks for `BudgetException` and `SQLException`. If not, we may need to add specific handling.

Let me check the calling code:

---

## Recommendations for Better UX

### Option 1: Proactive Validation (Recommended)

Add **real-time validation hints** when Period or HowOccurs is changed:

```java
case "e":  // period
    Item.PeriodType newPeriod = view.selectByPositionFromList(...);
    budgetItem.setPeriod(newPeriod);
    
    // NEW: Check if this creates invalid state
    try {
        budgetItem.validatePeriodHowOccursConsistency();
    } catch (BudgetException e) {
        view.say("⚠️  Warning: " + e.getMessage());
        view.say("You'll need to update HowOccurs before saving.");
    }
    break;
```

Benefits:
- ✅ User sees warning immediately
- ✅ Can plan to fix before saving
- ✅ Better user experience

### Option 2: Auto-Correction (Advanced)

When user changes Period or HowOccurs, automatically suggest compatible values:

```java
case "e":  // period
    Item.PeriodType newPeriod = view.selectByPositionFromList(...);
    Item.PeriodType oldPeriod = budgetItem.getPeriod();
    budgetItem.setPeriod(newPeriod);
    
    // If changing to/from ON_DEMAND, suggest HowOccurs change
    if (oldPeriod != PeriodType.ON_DEMAND && newPeriod == PeriodType.ON_DEMAND) {
        if (budgetItem.getHowOccurs() == PERIODIC || 
            budgetItem.getHowOccurs() == VARIABLE_PERIODIC ||
            budgetItem.getHowOccurs() == COLLECTION) {
            view.say("Period changed to ON_DEMAND. Consider changing HowOccurs to UNPLANNED or ENVELOPE.");
        }
    }
    break;
```

### Option 3: Smart Defaults

When creating a new budget item, set smart defaults that are always valid:

```java
// In getBudgetItemFromUser():
Item.HowOccurs defaultHowOccurs;
if (selectedPeriodType == PeriodType.ON_DEMAND) {
    defaultHowOccurs = Item.HowOccurs.UNPLANNED;  // Safe default for ON_DEMAND
} else {
    defaultHowOccurs = Item.HowOccurs.PERIODIC;   // Safe default for scheduled periods
}
```

---

## Testing Checklist

Before deployment, test these scenarios:

### Creating Budget Items
- [ ] Create UNPLANNED item with ON_DEMAND period → Should succeed
- [ ] Create UNPLANNED item with WEEKLY period → Should fail with clear error
- [ ] Create PERIODIC item with MONTHLY period → Should succeed
- [ ] Create PERIODIC item with ON_DEMAND period → Should fail with clear error
- [ ] Create ENVELOPE item with ON_DEMAND period → Should succeed

### Updating Budget Items
- [ ] Update Period from MONTHLY to ON_DEMAND (HowOccurs=PERIODIC) → Should fail on save
- [ ] Update HowOccurs from PERIODIC to UNPLANNED (Period=MONTHLY) → Should fail on save
- [ ] Update both Period and HowOccurs to valid combination → Should succeed
- [ ] Cancel update without saving → Should not validate (no error)

### Edge Cases
- [ ] Copy budget item with valid combination → Should succeed
- [ ] Copy budget item, then edit to invalid → Should fail on save
- [ ] Import budget items from external source → Should validate

---

## Existing Data Migration

**IMPORTANT:** Before enabling validation in production:

1. **Run the detection script:**
   ```bash
   mysql -u username -p database < check_period_howoccurs_violations.sql
   ```

2. **Review violations found**

3. **Fix existing violations** using the correction scripts in the SQL file

4. **Deploy validation code** only after database is clean

---

## Conclusion

### Impact Summary

| Aspect | Impact Level | Description |
|--------|-------------|-------------|
| **Creating items** | 🟡 Medium | Validation error shown on save if invalid combination |
| **Updating items** | 🟡 Medium | Validation error shown when clicking "save changes" |
| **User workflow** | 🟢 Low | User can still update all fields, just can't save invalid combinations |
| **Data integrity** | 🟢 High | Prevents corrupt data from being persisted |
| **Error messages** | 🟢 Good | Clear, descriptive error messages guide users |

### User Impact

**Positive:**
- ✅ Prevents creation of semantically invalid budget items
- ✅ Clear error messages explain what's wrong
- ✅ Existing workflows remain the same
- ✅ No surprise behavior changes for valid combinations

**Negative:**
- ❌ Users creating invalid combinations will see errors
- ❌ May require re-entering values to correct mistakes
- ❌ No proactive hints (unless we add Option 1 above)

### Recommendation

**Deploy as-is** for basic protection, then consider adding **Option 1 (Proactive Validation)** in a future iteration for better UX.
