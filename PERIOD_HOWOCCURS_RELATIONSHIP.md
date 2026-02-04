# Period and HowOccurs Field Relationship

## Database Design Issue Identified

**Date:** February 4, 2026  
**Issue:** Semantic inconsistency between `Period` and `HowOccurs` fields

## The Problem

The `budget_item` and `forecast_item` tables contain two fields that have overlapping semantics:

1. **`Period`** (PeriodType enum): Defines the frequency/schedule of an item
   - Values: ON_DEMAND, DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, etc.
   
2. **`HowOccurs`** (HowOccurs enum): Describes the occurrence pattern relative to the period
   - Values: PERIODIC, VARIABLE_PERIODIC, COLLECTION, ENVELOPE, UNPLANNED

### The Semantic Overlap

- `Period = ON_DEMAND` means "no regular schedule"
- `HowOccurs = UNPLANNED` means "no budget period, happens randomly"

These two concepts overlap, creating the possibility of **inconsistent states**:

#### Invalid Combinations
- ❌ `Period = WEEKLY` + `HowOccurs = UNPLANNED`  
  *Contradiction: Can't be unplanned AND weekly*
  
- ❌ `Period = ON_DEMAND` + `HowOccurs = PERIODIC`  
  *Contradiction: Can't be periodic without a schedule*
  
- ❌ `Period = ON_DEMAND` + `HowOccurs = COLLECTION`  
  *Contradiction: Can't collect multiple times in an undefined period*

#### Valid Combinations
- ✅ `Period = ON_DEMAND` + `HowOccurs = UNPLANNED`  
  *Consistent: Truly unplanned expenses*
  
- ✅ `Period = ON_DEMAND` + `HowOccurs = ENVELOPE`  
  *Allowed: Saving for something with no set schedule (e.g., vacation fund)*
  
- ✅ `Period = WEEKLY` + `HowOccurs = PERIODIC`  
  *Consistent: Regular weekly expense*
  
- ✅ `Period = MONTHLY` + `HowOccurs = VARIABLE_PERIODIC`  
  *Consistent: Monthly bill with varying amounts (e.g., electric bill)*

## Was the Database Designed Wrong?

**Short answer:** Not entirely, but there is redundancy.

### The Redundancy

The design has **two orthogonal concepts that partially overlap**:

1. **Period** answers: *"How often does this occur?"*
2. **HowOccurs** answers: *"How does it occur relative to the budget period?"*

For most items, these work together well:
- A monthly electric bill: `Period = MONTHLY`, `HowOccurs = VARIABLE_PERIODIC`
- Weekly groceries: `Period = WEEKLY`, `HowOccurs = COLLECTION`
- Random medical expenses: `Period = ON_DEMAND`, `HowOccurs = UNPLANNED`

However, the overlap between `ON_DEMAND` and `UNPLANNED` creates the potential for contradictions.

### Alternative Design Options

#### Option 1: Merge the Fields (NOT RECOMMENDED)
Create a single combined enum with values like:
- `WEEKLY_PERIODIC`
- `MONTHLY_VARIABLE`
- `UNPLANNED`

**Why not:** Would create an explosion of combinations and lose semantic clarity.

#### Option 2: Make Period Mandatory, HowOccurs Conditional (NOT RECOMMENDED)
Remove `UNPLANNED` from HowOccurs, use only `Period = ON_DEMAND` for unplanned items.

**Why not:** HowOccurs provides valuable information about how items behave within their periods (COLLECTION vs ENVELOPE vs PERIODIC).

#### Option 3: Add Validation Rules (IMPLEMENTED ✓)
Keep both fields but enforce consistency rules.

**Why this works:** 
- Preserves the semantic richness of both fields
- Prevents invalid combinations
- Minimal code changes required
- Clear documentation of the rules

## Solution Implemented

### 1. Documentation Added to Item.java

A comprehensive JavaDoc comment explaining the relationship and rules:

```java
/**
 * IMPORTANT: Relationship between Period and HowOccurs
 * =====================================================
 *
 * There is a semantic dependency between the Period and HowOccurs fields:
 *
 * 1. If HowOccurs = UNPLANNED:
 *    - Period MUST be ON_DEMAND
 *
 * 2. If Period = ON_DEMAND:
 *    - HowOccurs SHOULD typically be UNPLANNED
 *    - Exception: Could be ENVELOPE if saving for something with no set schedule
 *
 * 3. If HowOccurs = PERIODIC, VARIABLE_PERIODIC, COLLECTION, or ENVELOPE:
 *    - Period MUST NOT be ON_DEMAND
 */
```

### 2. Validation Method Added to Item.java

```java
public void validatePeriodHowOccursConsistency() throws BudgetException {
    if (period == null || howOccurs == null) {
        return; // Can't validate if fields aren't set yet
    }

    // Rule 1: UNPLANNED items must have ON_DEMAND period
    if (howOccurs == UNPLANNED && period != ON_DEMAND) {
        throw new BudgetException(...);
    }

    // Rule 2: Scheduled occurrences cannot have ON_DEMAND period
    if (period == ON_DEMAND && (howOccurs == PERIODIC || 
                                howOccurs == VARIABLE_PERIODIC || 
                                howOccurs == COLLECTION)) {
        throw new BudgetException(...);
    }
}
```

### 3. Validation Called Before Save/Update

**BudgetItem.java:**
- `save()` method: Calls validation before saving
- `update()` method: Calls validation before updating

This ensures that no invalid combinations can be persisted to the database.

## Impact Analysis

### Existing Data
**Action Required:** Run a query to check for existing invalid combinations:

```sql
SELECT 
    bin_to_uuid(idBudgetItem) as id,
    payee,
    category,
    period,
    howOccurs
FROM budget_item
WHERE 
    (howOccurs = 'U' AND period != 'OD') OR
    (period = 'OD' AND howOccurs IN ('P', 'VP', 'C'));
```

If any records are found, they should be corrected before the validation enforcement goes live.

### Future Development
**Best Practice:** When creating or updating budget items through the UI or API:
1. The validation will automatically prevent invalid combinations
2. Error messages will guide users to correct combinations
3. The relationship is now explicitly documented in the code

## Recommendations

### Immediate Actions
1. ✅ Validation code has been added
2. ✅ Documentation has been added
3. ⚠️ **TODO:** Check existing database for invalid combinations
4. ⚠️ **TODO:** Add UI hints when users select Period/HowOccurs combinations

### Long-term Considerations
1. Consider adding a database CHECK constraint (if MySQL version supports it):
   ```sql
   ALTER TABLE budget_item ADD CONSTRAINT chk_period_howoccurs
   CHECK (
       (howOccurs != 'U' OR period = 'OD') AND
       (period != 'OD' OR howOccurs NOT IN ('P', 'VP', 'C'))
   );
   ```

2. Add unit tests to verify validation rules work correctly

3. Update user documentation to explain the Period/HowOccurs relationship

## Conclusion

The database design is **fundamentally sound** but had an **undocumented semantic dependency** between two fields. The solution implemented:

- ✅ Preserves the flexibility of the current design
- ✅ Prevents invalid data from being created
- ✅ Documents the rules clearly
- ✅ Minimal code changes required
- ✅ No schema changes needed

This is a common pattern in database design where normalized tables maintain orthogonal concerns that have subtle interdependencies. The key is to **document and enforce** those dependencies, which has now been done.
