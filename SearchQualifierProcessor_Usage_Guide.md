# SearchQualifierProcessor Implementation Guide

## Overview

The SearchQualifierProcessor pattern has been successfully implemented in MatchQuery to enable flexible, domain-specific search qualifiers without polluting the core search logic.

## Architecture

### Core Interfaces & Classes

1. **SearchQualifierProcessor** (interface)
   - Functional interface for processing search qualifiers
   - Single method: `SearchContext process(String searchTerm, String baseQuery)`
   - Includes `IDENTITY` constant for no-op processing

2. **SearchContext** (class)
   - Encapsulates cleaned search term and modified query
   - Includes optional metadata map for display hints
   - Fluent API with `withMetadata()` for chaining

3. **MatchQuery** (enhanced)
   - New 5-parameter constructor accepts SearchQualifierProcessor
   - Processes qualifiers BEFORE search mode prefixes (n:, b:, e:, l:, s:)
   - Backward compatible with existing code

### Implementations Provided

1. **BudgetSearchQualifierProcessor**
   - Handles budget-specific qualifiers
   - Supports `budget:all:term`, `ba:term`, `budget:uuid:term`

2. **CompositeSearchQualifierProcessor**
   - Chains multiple processors together
   - Enables complex multi-qualifier searches

## Usage Examples

### Example 1: Basic Budget Search (Search All Budgets)

```java
// In BudgetController or similar
UUID currentBudgetId = currentBudget.getId();

// Create the processor
SearchQualifierProcessor processor = new BudgetSearchQualifierProcessor(
    currentBudgetId,
    "bi.Budget_idBudget"
);

// Create MatchQuery with processor
MatchQuery matchQuery = new MatchQuery(
    "SELECT bi.* FROM budget_item bi " +
    "WHERE bi.Budget_idBudget = uuid_to_bin('" + currentBudgetId + "') AND ",
    "bi.payee",
    "bi.category, bi.payee, bi.memo",
    "ORDER BY bi.payee ASC",
    processor
);

// User searches with "ba:hyundai" or "budget:all:hyundai"
String userInput = view.getSearchTerm();
String sqlQuery = matchQuery.getQuery(userInput);

// Execute query and display results
List<BudgetItem> results = dao.search(sqlQuery);

// Check for display hints
SearchContext context = processor.process(userInput, baseQuery);
if (context.hasMetadata("searchAllBudgets")) {
    view.say("Searching across all budgets...");
}
```

### Example 2: No Qualifier Processing (Existing Behavior)

```java
// Existing code continues to work - uses IDENTITY processor by default
MatchQuery matchQuery = new MatchQuery(
    "SELECT m.* FROM merchant m WHERE ",
    "m.name",
    "m.name",
    "ORDER BY m.name ASC"
);

// No qualifiers processed - just standard search
String query = matchQuery.getQuery("amazon");
```

### Example 3: Custom Qualifier Processor (Lambda)

```java
// Simple inline processor using lambda
SearchQualifierProcessor customProcessor = (searchTerm, baseQuery) -> {
    if (searchTerm.startsWith("active:")) {
        String cleaned = searchTerm.substring(7);
        String modified = baseQuery + " AND bi.active = true AND ";
        return new SearchContext(cleaned, modified)
            .withMetadata("filterActive", true);
    }
    return new SearchContext(searchTerm, baseQuery);
};

MatchQuery matchQuery = new MatchQuery(
    baseQuery,
    nameColumn,
    matchColumns,
    orderBy,
    customProcessor
);

// User can search: "active:groceries" to find only active items
```

### Example 4: Composite Processor (Multiple Qualifiers)

```java
// Chain multiple processors for complex filtering
SearchQualifierProcessor combined = new CompositeSearchQualifierProcessor(
    new BudgetSearchQualifierProcessor(currentBudget, "bi.Budget_idBudget"),
    (term, query) -> {
        // Category filter: "category:expense:term"
        if (term.startsWith("category:")) {
            int colonPos = term.indexOf(':', 9);
            String category = term.substring(9, colonPos);
            String cleaned = term.substring(colonPos + 1);
            String modified = query + " AND bi.category = '" + category + "' AND ";
            return new SearchContext(cleaned, modified);
        }
        return new SearchContext(term, query);
    }
);

// User can now search: "budget:all:category:expense:groceries"
// This searches all budgets for expense category items matching "groceries"
```

## User Experience

### Search Qualifiers Supported

| Qualifier | Short Form | Example | Description |
|-----------|------------|---------|-------------|
| `budget:all:` | `ba:` | `ba:hyundai` | Search across all budgets |
| `budget:uuid:` | - | `budget:123e4567-...:car` | Search specific budget |

### Combined with Search Modes

Users can combine qualifiers with existing search modes:

- `ba:n:natural language search` - All budgets, natural language
- `ba:b:boolean +mode -search` - All budgets, boolean mode
- `ba:e:Exact Match` - All budgets, exact match
- `ba:l:%pattern%` - All budgets, LIKE pattern
- `ba:s:simple` - All budgets, simple wildcard search

### Display Results with Context

```java
SearchContext context = processor.process(userInput, baseQuery);

// Show context-aware message
if (context.hasMetadata("searchAllBudgets")) {
    view.say("═══ Searching All Budgets ═══");
} else if (context.hasMetadata("specificBudgetId")) {
    UUID budgetId = context.getMetadata("specificBudgetId", null);
    view.say("Searching budget: " + budgetId);
}

// Display results with budget name when searching all
for (BudgetItem item : results) {
    if (context.hasMetadata("searchAllBudgets")) {
        view.say("[" + item.getBudget().getName() + "] " + item.getPayee());
    } else {
        view.say(item.getPayee());
    }
}
```

## Benefits

✅ **Clean Architecture** - Domain logic stays in domain-specific processors
✅ **Extensible** - Add new qualifiers without modifying MatchQuery
✅ **Composable** - Chain multiple processors for complex scenarios
✅ **Testable** - Easy to unit test processors independently
✅ **Backward Compatible** - Existing code continues to work unchanged
✅ **Type Safe** - Compile-time checking of processor implementations
✅ **Flexible** - Supports lambdas, classes, or composite patterns

## Testing

```java
@Test
public void testBudgetAllQualifier() {
    UUID budgetId = UUID.randomUUID();
    SearchQualifierProcessor processor = new BudgetSearchQualifierProcessor(
        budgetId, "bi.Budget_idBudget"
    );
    
    String baseQuery = "SELECT * FROM budget_item bi WHERE bi.Budget_idBudget = uuid_to_bin('" + 
                      budgetId + "') AND ";
    
    SearchContext result = processor.process("ba:groceries", baseQuery);
    
    assertEquals("groceries", result.getCleanedSearchTerm());
    assertFalse(result.getModifiedQuery().contains("Budget_idBudget"));
    assertTrue(result.hasMetadata("searchAllBudgets"));
}
```

## Future Extensions

### Potential New Processors:

1. **CategorySearchQualifierProcessor** - `category:expense:term`
2. **DateRangeSearchQualifierProcessor** - `date:2024-01-01:2024-12-31:term`
3. **AmountRangeSearchQualifierProcessor** - `amount:100:500:term`
4. **UserSearchQualifierProcessor** - `user:john:term`
5. **StatusSearchQualifierProcessor** - `status:active:term`

### Implementation Pattern:

```java
public class CategorySearchQualifierProcessor implements SearchQualifierProcessor {
    @Override
    public SearchContext process(String searchTerm, String baseQuery) {
        if (searchTerm.startsWith("category:")) {
            // Extract category and clean term
            // Modify query to add category filter
            // Return SearchContext with metadata
        }
        return new SearchContext(searchTerm, baseQuery);
    }
}
```

## Migration Guide

### For New Code:
Use the 5-parameter constructor with a processor:
```java
new MatchQuery(queryBefore, nameCol, matchCols, queryAfter, processor)
```

### For Existing Code:
No changes needed! The 3 and 4-parameter constructors use `IDENTITY` processor by default.

## Summary

The SearchQualifierProcessor pattern provides a clean, extensible way to add domain-specific search qualifiers to MatchQuery without cluttering the core search logic. It enables powerful cross-context searching (like searching across all budgets) while maintaining backward compatibility and testability.

**Remember to commit these changes to version control!**

