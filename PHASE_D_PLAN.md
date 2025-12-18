# Phase D Implementation Plan

**Date**: December 18, 2025  
**Phase**: D - Make System Fully Format-Agnostic  
**Goal**: Refactor Wells Fargo to use same architecture as Barclays

---

## Overview

Currently:
- ✅ Barclays uses new architecture (QfxParser → QfxTransaction → BarclaysBank → Transaction)
- ❌ Wells Fargo uses old architecture (direct CSV parsing in methods)

After Phase D:
- ✅ Both use same pattern (Parser → DTO → Institution → Transaction)
- ✅ ImportController is completely format-agnostic
- ✅ Easy to add new banks/formats

---

## Step-by-Step Plan

### Step 1: Create CsvTransaction DTO ✅
Create a DTO for CSV transaction data that implements TransactionData interface.

**File**: `src/main/java/com/hixon/financialApp/model/csv/CsvTransaction.java`

**Fields**:
- postDate (LocalDate)
- amount (double)
- payee (String)
- cleared (boolean)
- checkNumber (int)
- importRecordId (String)

### Step 2: Create CsvParser ✅
Create a CSV parser that implements TransactionParser<CsvTransaction>.

**File**: `src/main/java/com/hixon/financialApp/model/csv/CsvParser.java`

**Methods**:
- open(InputStream) - opens CSV file
- hasNext() - checks for more records
- getNext() - returns next CsvTransaction
- close() - closes resources

**Note**: Will need to be configurable for different CSV formats (Wells Fargo, other banks)

### Step 3: Refactor WellsFargoBank ✅
Update WellsFargoBank to use the new architecture.

**Changes**:
- Add Iterator<Transaction> implementation
- Use CsvParser internally
- Convert CsvTransaction → Transaction
- Keep Wells Fargo-specific merchant parsing

### Step 4: Create FinancialInstitutionFactory ✅
Factory to create appropriate institution based on type.

**File**: `src/main/java/com/hixon/financialApp/model/financialinstitution/FinancialInstitutionFactory.java`

**Method**:
```java
public static FinancialInstitution create(
    String institutionType,
    String filename,
    Register register,
    Budget budget,
    Forecast forecast,
    ViewInt view,
    NotificationServiceInt notificationService
) throws Exception
```

### Step 5: Update FinancialInstitutionInt Interface ✅
Make interface format-agnostic.

**Add**:
- Extend Iterator<Transaction>
- Add close() method

**Keep** (institution-specific):
- parseMerchantPayee()
- extractUserDescription()
- extractUsers()
- extractAccountType()

**Remove or Deprecate** (format-specific):
- getCsvHeadersClass()
- getRegisterImportRecordBaseName()
- createFromCSVRecord()
- loadProvisionalTransactionFromCSV()

### Step 6: Update Tests ✅
- Create CsvParserTest
- Update WellsFargoBankTest to use new architecture
- Create FinancialInstitutionFactoryTest

### Step 7: Integration Testing ✅
Test the complete flow with actual data files.

---

## Implementation Order

1. **CsvTransaction** (simple DTO)
2. **CsvParser** (more complex, needs CSV library configuration)
3. **Tests for CsvTransaction and CsvParser**
4. **Refactor WellsFargoBank** (use CsvParser)
5. **FinancialInstitutionFactory** (simple factory pattern)
6. **Update/deprecate FinancialInstitutionInt** (interface changes)
7. **Integration tests**

---

## Challenges to Address

### Challenge 1: CSV Format Variations
Wells Fargo CSV has specific format. Need flexible CsvParser.

**Solution**: CsvParser takes a format specification in constructor or via builder pattern.

### Challenge 2: Provisional Transactions
Wells Fargo supports provisional transactions (pending), Barclays doesn't.

**Solution**: Keep provisional transaction handling in WellsFargoBank-specific code.

### Challenge 3: Backward Compatibility
Existing ImportController code uses old methods.

**Solution**: 
- Phase D: Keep old methods marked @Deprecated
- Phase E: Update ImportController to use new architecture
- Later: Remove deprecated methods

### Challenge 4: Register Integration
Register needs to know institution type and filename.

**Solution**: Add fields to Register entity (can be done in parallel).

---

## Success Criteria

✅ CsvTransaction implements TransactionData  
✅ CsvParser implements TransactionParser<CsvTransaction>  
✅ WellsFargoBank uses CsvParser internally  
✅ WellsFargoBank implements Iterator<Transaction>  
✅ FinancialInstitutionFactory creates both Barclays and Wells Fargo  
✅ All existing tests still pass  
✅ New tests for CSV components pass  

---

## Timeline Estimate

- Step 1-2: CsvTransaction + CsvParser (30-45 min)
- Step 3: Refactor WellsFargoBank (45-60 min)
- Step 4-5: Factory + Interface updates (30 min)
- Step 6-7: Testing (30-45 min)

**Total**: ~3 hours

---

Let's begin with Step 1!

