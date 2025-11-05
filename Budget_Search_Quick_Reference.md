# Budget Search Quick Reference Guide

## How to Use Cross-Budget Searching in the UI

### ✅ IMPLEMENTATION COMPLETE - Simple Syntax!

Cross-budget searching with **qualifiers after search term** is now enabled.

---

## Your Search Options

### Simple Rule: Search Term First, Qualifiers After

**Format:**
```
[search-term] [qualifiers]
```

**Examples:**
```
groceries category:Food and Beverage
car payment budget:all
hyundai budget:all category:Automotive
```

---

## Qualifiers

| Qualifier | Purpose | Example |
|-----------|---------|---------|
| `budget:all` | Search all budgets | `car budget:all` |
| `category:Name` | Filter by category | `groceries category:Food and Beverage` |

**Key Point:** Qualifiers go AFTER your search term and can be in any order

---

## Real-World Examples

### Example 1: "Where's my car payment?"
```
car payment budget:all
```

**Result:**
```
Found 2 items:
  1. Car payment - Bertha
  2. Car payment - Honda
```

---

### Example 2: "Show me all grocery items"
```
groceries budget:all category:Food and Beverage
```

---

### Example 3: "Find automotive expenses"
```
budget:all category:Automotive
```

---

### Example 4: "Category with spaces"
```
groceries category:Food and Beverage
payment category:Debt - Unsecured
```

**Spaces work naturally!** Just type the category name after `category:`

---

## Syntax Rules

### ✅ Correct

```
groceries category:Food and Beverage
car payment budget:all
hyundai budget:all category:Automotive
payment category:Housing budget:all
```

### ❌ Incorrect

```
category:Food and Beverage groceries     ← Qualifiers before search term
budget:all car                           ← Qualifiers before search term
```

### Exception: No Search Term

If you're just filtering (no search term), qualifiers can be alone:
```
category:Food and Beverage
budget:all category:Housing
```

---

## Search Modes

You can combine search mode prefixes with qualifiers:

```
n:mortgage category:Housing budget:all     (natural language)
b:+car -insurance category:Automotive      (boolean)
e:Utilities category:Housing               (exact match)
```

Available modes:
- `n:` - Natural language
- `b:` - Boolean (+must -not)
- `e:` - Exact match
- `l:` - LIKE pattern
- `s:` - Simple (default)

---

## Common Use Cases

| Use Case | Search Command |
|----------|----------------|
| Cross-budget search | `car payment budget:all` |
| Category filter | `groceries category:Food and Beverage` |
| Both | `groceries category:Food budget:all` |
| Category only | `category:Automotive` |
| All items all budgets | `budget:all` |

---

## Getting Help

Type `?` at any search prompt:
```
Search for budget item: ?
```

Shows complete help including all qualifiers and examples.

---

### 3️⃣ **Combine Multiple Qualifiers**

Mix and match qualifiers with search modes:

```
Search for budget item: n:groceries budget:all category:Food
```

This searches:
- **Natural language mode** (`n:`)
- **Across all budgets** (`budget:all`)
- **In Food category only** (`category:Food`)
- For the term **"groceries"**

---

## Real-World Examples

### Scenario 1: "Where's my car payment?"
**Problem:** You know you have a "Car Payment" item but don't remember which budget.

**Solution (multiple ways that work):**
```
budget:all car payment
car payment budget:all
```

**Result:**
```
Found 2 items:
  1. [Bill Pay Account] Car payment - Bertha
  2. [Personal Budget] Car payment - Honda
```

---

### Scenario 2: "Show me all grocery items"
**Problem:** You want to see all grocery-related budget items across all budgets.

**Solution:**
```
budget:all category:Food
category:Food budget:all groceries
```

**Result:**
```
Found 3 items:
  1. [Bill Pay Envelopes] Groceries - Weekly
  2. [Joint Spending] Grocery Store
  3. [Personal] Costco - Groceries
```

---

### Scenario 3: "Find automotive expenses across all budgets"
**Problem:** You want all car-related expenses from any budget.

**Solution:**
```
category:Automotive budget:all
budget:all category:Automotive car
n:car budget:all category:Automotive
```

---

### Scenario 4: "Complex search with multiple filters"
**Problem:** Natural language search for "payment" in Housing category, all budgets.

**Solution:**
```
n:payment budget:all category:Housing
```

**What happens:**
1. `budget:all` → Removes budget filter from SQL
2. `category:Housing` → Adds `WHERE category = 'Housing'`
3. `n:payment` → Natural language search on remaining text
4. Results ranked by relevance

---

## Supported Qualifiers

| Qualifier | Description | Position | Example |
|-----------|-------------|----------|---------|
| `budget:all` | Search all budgets | Anywhere | `budget:all car` |
| `budget:uuid` | Search specific budget | Anywhere | `budget:123e4567-...` |
| `category:Name` | Filter by category | Anywhere | `category:Food` |

**Coming Soon:** `date:`, `amount:`, `user:`, `status:` qualifiers

---

## Search Mode Prefixes

Combine these with qualifiers:

| Prefix | Mode | Example |
|--------|------|---------|
| `n:term` | Natural language | `n:car budget:all` |
| `b:term` | Boolean (+/-) | `b:+car -insurance budget:all` |
| `e:term` | Exact match | `e:Utilities category:Housing` |
| `l:pattern` | LIKE pattern | `l:%car% budget:all` |
| `s:term` | Simple (default) | `s:car budget:all` |

---

## What You'll See

### Normal Search (Current Budget):
```
Search for budget item: car

Found 2 items:
  1. Car Payment
  2. Car Insurance
```

### Cross-Budget Search:
```
Search for budget item: budget:all car

Found 4 items:
  1. [Bill Pay Account] Car Payment - Bertha
  2. [Personal] Car Insurance
  3. [Joint Spending] Car Wash Fund
  4. [Vacation Budget] Car Rental Reserve
```

### Filtered Search:
```
Search for budget item: category:Automotive budget:all

Found 8 items:
  1. [Bill Pay Account] Car payment - Bertha
  2. [Personal] Car Insurance
  3. [Joint Spending] Gas - Regular
  4. [Personal] Oil Change Fund
  ...
```

---

## Syntax Rules

### ✅ Valid Combinations

```
budget:all hyundai
hyundai budget:all
category:Food groceries
groceries category:Food budget:all
n:payment budget:all category:Housing
budget:all n:payment category:Housing
```

**All of these work!** Qualifiers are extracted first, then search happens.

### ❌ Invalid Syntax

```
budget:   (missing value)
category: (missing category name)
budget all hyundai (missing colon)
```

---

## Common Use Cases

| Use Case | Search Command | Why |
|----------|----------------|-----|
| Not sure which budget | `budget:all itemname` | Searches everywhere |
| Category filter | `category:Food` | Narrow by type |
| Both filters | `budget:all category:Auto` | Max flexibility |
| Natural language + filters | `n:payment budget:all category:Housing` | Smart search |
| Boolean + filters | `b:+car -insurance category:Automotive` | Complex logic |

---

## Technical Details

### What Changed:
- `BudgetSearchQualifierProcessor` now uses **regex patterns** to find qualifiers anywhere
- Qualifiers are **order-independent** and **composable**
- Multiple qualifiers are **AND-ed** together
- Backward compatible with direct searches

### How It Works:

When you type: `n:hyundai budget:all category:Automotive`

1. **Qualifier extraction** (order independent):
   - Finds `budget:all` → removes budget filter from SQL
   - Finds `category:Automotive` → adds `WHERE category = 'Automotive'`
   - Removes both qualifiers from search string
   
2. **Cleaned search term**: `n:hyundai`

3. **MatchQuery processes**: Natural language search on "hyundai"

4. **Final SQL**: Searches all budgets, automotive category only, for "hyundai"

---

## Tips & Best Practices

### ✅ DO:
- Put qualifiers anywhere that feels natural
- Combine multiple qualifiers for precise results
- Use `category:` to narrow searches significantly
- Use `budget:all` when browsing or unsure of location

### ❌ DON'T:
- Don't forget the colon after qualifier names
- Don't use quotes around category names (not needed)
- Don't use `budget:all` for every search (slower)

---

## Examples by Complexity

### Simple:
```
budget:all car
```

### Intermediate:
```
category:Food groceries
```

### Advanced:
```
n:payment budget:all category:Housing
```

### Expert:
```
b:+mortgage -insurance budget:all category:Housing
```

---

## Getting Help

Type `?` at any search prompt:
```
Search for budget item: ?
```

Shows complete help including all qualifiers and examples.

---

## Testing Checklist

1. ✅ Normal search: `hyundai`
2. ✅ Budget qualifier: `budget:all hyundai`
3. ✅ Category qualifier: `category:Automotive`
4. ✅ Both qualifiers: `budget:all category:Food`
5. ✅ With search mode: `n:car budget:all category:Automotive`
6. ✅ Any order: `category:Auto n:car budget:all`
7. ✅ Help text: `?`

---

**The composable qualifier system is live!**

Start using flexible, order-independent qualifiers today:
```
n:hyundai budget:all category:Automotive
```

