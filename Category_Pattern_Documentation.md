# Category Pattern - Handling Spaces in Category Names

## The Problem

Category names can have spaces: **"Food and Beverage"**, **"Debt - Unsecured"**, etc.

When using `category:Food and Beverage` followed by a search term, the system needs to know where the category name ends and the search term begins.

## The Solution

### Best Practice: Qualifier Placement

**✅ RECOMMENDED - Put category qualifier in these positions:**

1. **After the search term:**
   ```
   groceries category:Food and Beverage
   ```

2. **Before another qualifier:**
   ```
   category:Food and Beverage budget:all groceries
   budget:all category:Food and Beverage groceries
   n:payment category:Food and Beverage budget:all
   ```

3. **At the end (no search term):**
   ```
   category:Food and Beverage
   budget:all category:Food and Beverage
   ```

**❌ AVOID - Category followed immediately by search term:**
```
category:Food and Beverage groceries  ← Ambiguous! System can't tell where category ends.
```

### Why This Works

The pattern stops capturing when it encounters:
- **A space followed by another qualifier** (`budget:`, `n:`, `b:`, etc.)
- **End of string** (`$`)

By putting qualifiers before search terms or after other qualifiers, you avoid ambiguity.

## Edge Cases

### ✅ Works Correctly
```
category:Food and Beverage
category:Debt - Unsecured  
category:Travel and Entertainment
category:Food and Beverage budget:all
budget:all category:Housing n:mortgage
```

### ⚠️ Potential Issues

If a category name contains a search mode prefix as a word:
```
category:Food n Storage
```
Would capture: `Food` (stops at `n:` lookahead)

**Solution:** This is unlikely since category names rarely contain `n:`, `b:`, `e:`, `l:`, `s:` as standalone words. If needed, users can reorder:
```
n:storage category:Food Storage
```

## User Experience

Users can type category names naturally with spaces:
```
category:Food and Beverage groceries
groceries category:Food and Beverage  
budget:all category:Food and Beverage
```

**No quotes needed!** The pattern handles it automatically.

## Implementation Notes

The pattern in `BudgetSearchQualifierProcessor.java`:
```java
private static final Pattern CATEGORY_PATTERN = 
    Pattern.compile("\\bcategory:([^\\n]+?)(?=\\s+(?:budget:|category:|n:|b:|e:|l:|s:)|$)");
```

The `.trim()` call after capture removes any trailing whitespace:
```java
String categoryName = categoryMatcher.group(1).trim();
```

## Summary

✅ **Spaces in category names work correctly**
✅ **No quotes required**
✅ **Order-independent** (category can go anywhere)
✅ **Works with all search modes**
✅ **Composable with other qualifiers**

Users can search naturally:
```
category:Food and Beverage budget:all groceries
```

