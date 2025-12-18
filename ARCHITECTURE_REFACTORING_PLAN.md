# Architecture Refactoring Plan: Format-Agnostic Financial Institution Interface

**Date**: December 18, 2025  
**Goal**: Decouple file format parsing from financial institution logic

---

## Current Architecture Issues

### Problem 1: Format-Specific Interface
```java
// FinancialInstitutionInt currently has CSV-specific methods:
Transaction createFromCSVRecord(CSVRecord record, String importRecordId);
String getRegisterImportRecordBaseName(CSVRecord record);
```

**Issue**: Interface assumes CSV format, won't work for QFX, JSON, XML, etc.

### Problem 2: File Handling in Wrong Place
- Financial institutions handle file parsing
- Should be: Parsers handle files → Financial institutions convert to Transactions

### Problem 3: DTO Naming
- `ParsedTransaction` is too generic
- Should be `QfxTransaction` to indicate QFX-specific data

---

## New Architecture

### Layer 1: Format Parsers (File → DTO)
```
CsvParser → CsvTransaction (one record at a time)
QfxParser → QfxTransaction (one transaction at a time)
```

### Layer 2: Financial Institutions (DTO → Transaction)
```
WellsFargoBank → converts CsvTransaction → Transaction
BarclaysBank → converts QfxTransaction → Transaction
```

### Layer 3: Import Controller
```
ImportController → uses parsers + financial institutions
```

---

## Implementation Steps

### Step 1: Rename QFX Classes ✅
- [x] `ParsedTransaction` → `QfxTransaction`
- [x] `ParsedStatement` → `QfxStatement`
- [x] Update `QfxParser` to use new names
- [x] Update `QfxParserTest` to use new names

### Step 2: Create Transaction DTO Interface
```java
public interface TransactionData {
    LocalDate getPostDate();
    LocalDate getAuthorizationDate();
    double getAmount();
    String getPayee();
    boolean isCleared();
    String getImportRecordId();
}
```

### Step 3: Make QfxTransaction Implement Interface
```java
public class QfxTransaction implements TransactionData {
    // Existing fields plus interface methods
}
```

### Step 4: Create CsvTransaction DTO
```java
public class CsvTransaction implements TransactionData {
    // Similar to QfxTransaction but for CSV data
}
```

### Step 5: Create Parser Interface
```java
public interface TransactionParser<T extends TransactionData> {
    void open(InputStream input) throws Exception;
    boolean hasNext() throws Exception;
    T getNext() throws Exception;
    void close() throws Exception;
}
```

### Step 6: Update QfxParser to Implement Interface
```java
public class QfxParser implements TransactionParser<QfxTransaction> {
    private Iterator<Transaction> transactionIterator;
    
    @Override
    public void open(InputStream input) throws Exception {
        // Parse QFX, create iterator
    }
    
    @Override
    public boolean hasNext() {
        return transactionIterator.hasNext();
    }
    
    @Override
    public QfxTransaction getNext() {
        // Convert ofx4j Transaction → QfxTransaction
    }
}
```

### Step 7: Create CsvParser
```java
public class CsvParser implements TransactionParser<CsvTransaction> {
    private CSVParser csvParser;
    private Iterator<CSVRecord> iterator;
    
    // Similar structure to QfxParser
}
```

### Step 8: Redesign FinancialInstitutionInt
```java
public interface FinancialInstitutionInt<T extends TransactionData> {
    
    // Convert format-specific DTO to Transaction
    Transaction createTransaction(T transactionData) throws Exception;
    
    // Parse merchant/payee (institution-specific)
    String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception;
    
    // Reconcile provisional transactions
    boolean reconcileProvisionalTransaction(
        Transaction clearedTransaction,
        Transaction provisionalTransaction,
        Register register,
        List<TransactionSplit> splits) throws Exception;
    
    // Extract user description
    String extractUserDescription(String payee);
    
    // Extract users
    List<User> extractUsers(String payee);
    
    // Extract account type
    String extractAccountType(String payee);
}
```

### Step 9: Update WellsFargoBank
```java
public class WellsFargoBank extends FinancialInstitution 
                            implements FinancialInstitutionInt<CsvTransaction> {
    
    @Override
    public Transaction createTransaction(CsvTransaction csvData) throws Exception {
        // Convert CsvTransaction → Transaction
        Calendar postDate = Utility.localDateToCalendarDate(csvData.getPostDate());
        return new Transaction(register, postDate, csvData.getPayee(), 
                              csvData.getAmount(), csvData.isCleared(), 
                              csvData.getCheckNumber(), csvData.getImportRecordId());
    }
}
```

### Step 10: Create BarclaysBank
```java
public class BarclaysBank extends FinancialInstitution 
                          implements FinancialInstitutionInt<QfxTransaction> {
    
    @Override
    public Transaction createTransaction(QfxTransaction qfxData) throws Exception {
        // Convert QfxTransaction → Transaction
        Calendar postDate = Utility.localDateToCalendarDate(qfxData.getPostedDate());
        return new Transaction(register, postDate, qfxData.getName(), 
                              qfxData.getAmount(), true, // QFX always cleared
                              0, qfxData.getFitId());
    }
}
```

### Step 11: Update ImportController
```java
// Old way (format-specific):
FinancialInstitution fi = ...;
Transaction t = fi.createFromCSVRecord(csvRecord, importId);

// New way (format-agnostic):
TransactionParser<QfxTransaction> parser = new QfxParser();
FinancialInstitutionInt<QfxTransaction> fi = new BarclaysBank(...);

parser.open(inputStream);
while (parser.hasNext()) {
    QfxTransaction qfxTxn = parser.getNext();
    Transaction transaction = fi.createTransaction(qfxTxn);
    // Process transaction...
}
parser.close();
```

---

## Benefits

✅ **Format Agnostic**: Add new formats (JSON, XML, API) without changing interface  
✅ **Separation of Concerns**: Parsers parse, institutions convert, controllers orchestrate  
✅ **Clear Naming**: `QfxTransaction` clearly indicates QFX-specific data  
✅ **Testability**: Can test parsers and institutions independently  
✅ **Extensibility**: Easy to add new banks or new formats  
✅ **Reusability**: Multiple banks could use same format (e.g., multiple banks using OFX)  

---

## Migration Strategy

### Phase A: Rename (No Breaking Changes)
1. Rename `ParsedTransaction` → `QfxTransaction`
2. Rename `ParsedStatement` → `QfxStatement`
3. Update all references

### Phase B: Create New Interfaces (Additive)
1. Create `TransactionData` interface
2. Create `TransactionParser` interface
3. Make QfxTransaction implement TransactionData
4. Update QfxParser to implement TransactionParser

### Phase C: Implement for Barclays (New Code)
1. Create BarclaysBank class
2. Implement using new architecture
3. Test thoroughly

### Phase D: Refactor Wells Fargo (Breaking Changes)
1. Create CsvTransaction DTO
2. Create CsvParser
3. Update WellsFargoBank to use new interface
4. Update ImportController
5. Test existing functionality

### Phase E: Clean Up (Remove Old Code)
1. Remove old CSV-specific methods from interface
2. Update documentation
3. Celebrate! 🎉

---

## Current Status

We are at: **Phase A - Rename**

**Next Action**: Rename ParsedTransaction → QfxTransaction and related classes

---

## Questions Resolved

**Q**: Should ParsedTransaction merge with Transaction?  
**A**: No - keep separate as DTO (see previous discussion)

**Q**: Should it be named ParsedTransaction?  
**A**: No - rename to QfxTransaction (format-specific)

**Q**: How to handle multiple formats?  
**A**: Create parser interface + format-specific DTOs

**Q**: Where does file handling go?  
**A**: In parsers (CsvParser, QfxParser), not in financial institutions

---

This refactoring aligns with SOLID principles:
- **S**ingle Responsibility: Parsers parse, institutions convert, controllers orchestrate
- **O**pen/Closed: Easy to add new formats without modifying existing code
- **L**iskov Substitution: All parsers/institutions interchangeable through interfaces
- **I**nterface Segregation: Clean, focused interfaces
- **D**ependency Inversion: Depend on abstractions (interfaces), not concrete classes

