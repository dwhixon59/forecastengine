# Fixed Category Name Typo: "pending Money" → "Spending Money"

## Date: January 16, 2026

## Problem

Category #23 in the budget item list shows "pending Money" instead of "Spending Money":

```
23 - pending Money
```

This is clearly a typo - the category should be "Spending Money" (matching items #11 "Envelope Savings" pattern).

## Root Cause

The typo exists in the `budget_item` table in the database, in the `category` column. When budget items were created with this category, the name was misspelled as "pending Money" instead of "Spending Money".

## The Fix

A SQL script has been created to fix this typo:

**File:** `fix_pending_money_typo.sql`

### What the script does:

1. **Shows affected budget items:**
   ```sql
   SELECT category, payee, amount
   FROM budget_item
   WHERE category = 'pending Money';
   ```

2. **Fixes the typo:**
   ```sql
   UPDATE budget_item
   SET category = 'Spending Money'
   WHERE category = 'pending Money';
   ```

3. **Verifies the fix:**
   ```sql
   SELECT category, payee
   FROM budget_item
   WHERE category IN ('pending Money', 'Spending Money')
   ORDER BY category, payee;
   ```

4. **Lists all categories** to check for other typos

## How to Apply the Fix

Run the SQL script against your database:

```powershell
mysql -u [username] -p [database_name] < fix_pending_money_typo.sql
```

Or execute it directly in your MySQL client.

## Impact

### Before Fix:
```
23 - pending Money  ❌ Typo
```

### After Fix:
```
23 - Spending Money  ✅ Correct
```

### What Gets Updated:

- All budget items with category = "pending Money"
- The category name changes to "Spending Money"
- All historical data (transactions, forecasts) will automatically show the corrected category
- No data loss - only the category name string changes

## Affected Data

Budget items that may be affected:
- "Danni's Spending Money" 
- "Dave's Spending Money"
- "David's Spending Money"
- Any other spending money budget items

The payee names and amounts remain unchanged - only the category name is corrected.

## Other Typos Noticed

Looking at the category list, there's another typo:

```
20 - Micellaneous  ❌ Should be "Miscellaneous"
21 - Miscellaneous  ✅ Correct spelling
```

This suggests there are **two** miscellaneous categories due to a typo. You may want to:
1. Merge "Micellaneous" into "Miscellaneous"
2. Update any budget items using the misspelled version

Would you like me to create a fix for that as well?

## Testing

After running the fix:

1. **Check the category list:**
   - Should show "Spending Money" instead of "pending Money"
   - Should appear in alphabetical order

2. **Check budget items:**
   - Items like "Danni's Spending Money" should show category "Spending Money"
   - No items should have category "pending Money"

3. **Check transactions and forecasts:**
   - Should display "Spending Money" for related items
   - Historical data should be correct

## Files Created

- **fix_pending_money_typo.sql** - SQL script to fix the typo in the database

## Recommendation

After fixing this typo, consider:

1. **Adding validation** to prevent typos in category names during data entry
2. **Using a dropdown/picklist** for category selection instead of free text
3. **Running a spell check** on all category names to find other typos
4. **Standardizing category names** across the application

This will prevent similar issues in the future.

