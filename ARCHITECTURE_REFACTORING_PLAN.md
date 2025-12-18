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

### Design Principle: ImportController is Parser-Agnostic

**Import Flow**:
1. User selects register to import to
2. Register provides: FinancialInstitution type + import filename
3. ImportController instantiates FinancialInstitution with filename
4. FinancialInstitution:
   - Determines file format (single format or check extension)
   - Instantiates appropriate parser
   - Provides iterator interface to ImportController
5. ImportController iterates through Transactions (format-agnostic!)

### Layer 1: Format Parsers (File → DTO)
```
CsvParser(filename) → iterates CsvTransaction objects
QfxParser(filename) → iterates QfxTransaction objects
JsonParser(filename) → iterates JsonTransaction objects
```

**Parser Responsibilities**:
- Open file
- Parse format-specific data
- Provide iterator interface
- Close file

### Layer 2: Financial Institutions (DTO → Transaction)
```
WellsFargoBank(filename)
  ├─ Instantiates CsvParser (Wells Fargo uses CSV only)
  ├─ Implements Iterator<Transaction>
  └─ Converts CsvTransaction → Transaction

BarclaysBank(filename)
  ├─ Instantiates QfxParser (Barclays uses QFX only)
  ├─ Implements Iterator<Transaction>
  └─ Converts QfxTransaction → Transaction

FlexibleBank(filename)
  ├─ Checks file extension (.csv, .qfx, .json)
  ├─ Instantiates appropriate parser
  ├─ Implements Iterator<Transaction>
  └─ Converts appropriate DTO → Transaction
```

**FinancialInstitution Responsibilities**:
- Determine supported formats
- Instantiate correct parser based on filename
- Convert format-specific DTOs to Transaction objects
- Provide Transaction iterator to ImportController

### Layer 3: Import Controller (Format-Agnostic!)
```
ImportController
  ├─ User selects Register
  ├─ Register → FinancialInstitution type + filename
  ├─ Instantiates: FinancialInstitution fi = factory.create(type, filename)
  ├─ Iterates: while (fi.hasNext()) { Transaction t = fi.next(); ... }
  └─ Processes Transactions (no knowledge of CSV, QFX, JSON!)
```

**ImportController Responsibilities**:
- Get register from user
- Instantiate correct FinancialInstitution
- Iterate through Transactions
- Apply business logic (assign splits, reconcile, etc.)
- **NOT responsible for**: File formats, parsing, DTO conversion

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
public interface FinancialInstitutionInt extends Iterator<Transaction> {
    
    // Initialize with import file (constructor handles parser instantiation)
    // Constructor: FinancialInstitution(String filename, Register register, ...)
    
    // Iterator methods - ImportController uses these
    @Override
    boolean hasNext();
    
    @Override
    Transaction next() throws Exception;
    
    // Institutional-specific logic (not format-specific!)
    String parseMerchantPayee(Calendar date, double amount, String payee) throws Exception;
    
    boolean reconcileProvisionalTransaction(
        Transaction clearedTransaction,
        Transaction provisionalTransaction,
        Register register,
        List<TransactionSplit> splits) throws Exception;
    
    String extractUserDescription(String payee);
    List<User> extractUsers(String payee);
    String extractAccountType(String payee);
    
    // File/resource management
    void close() throws Exception;
}
```

**Key Changes**:
- ✅ Implements Iterator<Transaction> - ImportController just iterates
- ✅ Constructor takes filename - institution determines parser
- ✅ No format-specific methods (no CSV, QFX references!)
- ✅ Parser instantiation hidden inside institution

### Step 9: Update WellsFargoBank
```java
public class WellsFargoBank extends FinancialInstitution 
                            implements FinancialInstitutionInt {
    
    private TransactionParser<CsvTransaction> parser;
    
    public WellsFargoBank(String filename, Register register, Budget budget, 
                         Forecast forecast, ViewInt view, 
                         NotificationServiceInt notificationService) throws Exception {
        super(register, budget, forecast, view, notificationService);
        
        // Wells Fargo only supports CSV
        this.parser = new CsvParser();
        this.parser.open(new FileInputStream(filename));
    }
    
    @Override
    public boolean hasNext() {
        return parser.hasNext();
    }
    
    @Override
    public Transaction next() throws Exception {
        CsvTransaction csvData = parser.getNext();
        
        // Convert CsvTransaction → Transaction
        Calendar postDate = Utility.localDateToCalendarDate(csvData.getPostDate());
        Transaction transaction = new Transaction(
            register, postDate, csvData.getPayee(), 
            csvData.getAmount(), csvData.isCleared(), 
            csvData.getCheckNumber(), csvData.getImportRecordId()
        );
        
        // Apply Wells Fargo-specific merchant parsing
        String merchantPayee = parseMerchantPayee(postDate, csvData.getAmount(), csvData.getPayee());
        transaction.setMerchantPayee(merchantPayee);
        
        return transaction;
    }
    
    @Override
    public void close() throws Exception {
        if (parser != null) {
            parser.close();
        }
    }
}
```

### Step 10: Create BarclaysBank
```java
public class BarclaysBank extends FinancialInstitution 
                          implements FinancialInstitutionInt {
    
    private TransactionParser<QfxTransaction> parser;
    
    public BarclaysBank(String filename, Register register, Budget budget, 
                       Forecast forecast, ViewInt view, 
                       NotificationServiceInt notificationService) throws Exception {
        super(register, budget, forecast, view, notificationService);
        
        // Barclays only supports QFX
        this.parser = new QfxParser();
        this.parser.open(new FileInputStream(filename));
    }
    
    @Override
    public boolean hasNext() {
        return parser.hasNext();
    }
    
    @Override
    public Transaction next() throws Exception {
        QfxTransaction qfxData = parser.getNext();
        
        // Convert QfxTransaction → Transaction
        Calendar postDate = Utility.localDateToCalendarDate(qfxData.getPostedDate());
        Transaction transaction = new Transaction(
            register, postDate, qfxData.getName(), 
            qfxData.getAmount(), 
            true, // QFX transactions are always cleared
            0,    // Credit cards don't have check numbers
            qfxData.getFitId()
        );
        
        // Apply Barclays-specific merchant parsing (if needed)
        String merchantPayee = parseMerchantPayee(postDate, qfxData.getAmount(), qfxData.getName());
        transaction.setMerchantPayee(merchantPayee);
        
        return transaction;
    }
    
    @Override
    public void close() throws Exception {
        if (parser != null) {
            parser.close();
        }
    }
}
```

### Step 10.5: Create FinancialInstitutionFactory
```java
public class FinancialInstitutionFactory {
    
    public static FinancialInstitutionInt create(
            String institutionType,
            String filename,
            Register register,
            Budget budget,
            Forecast forecast,
            ViewInt view,
            NotificationServiceInt notificationService) throws Exception {
        
        return switch (institutionType.toUpperCase()) {
            case "WELLSFARGO" -> new WellsFargoBank(
                filename, register, budget, forecast, view, notificationService);
            
            case "BARCLAYS" -> new BarclaysBank(
                filename, register, budget, forecast, view, notificationService);
            
            case "JPMORGAN" -> new JPMorganChase(
                filename, register, budget, forecast, view, notificationService);
            
            // Add more institutions here...
            
            default -> throw new IllegalArgumentException(
                "Unsupported financial institution: " + institutionType);
        };
    }
}
```

**Factory Benefits**:
- ✅ Centralized institution creation
- ✅ Easy to add new institutions (just add case)
- ✅ ImportController doesn't know about concrete classes
- ✅ Can add configuration/caching later if needed

### Step 11: Update ImportController
```java
// Old way (format-specific, tightly coupled):
CSVParser csvParser = ...;
for (CSVRecord record : csvParser) {
    Transaction t = financialInstitution.createFromCSVRecord(record, importId);
    // Process transaction...
}

// New way (format-agnostic, loosely coupled):
// 1. Get register from user
Register register = view.selectRegister();

// 2. Register provides institution type and filename
String institutionType = register.getFinancialInstitution().getType();
String filename = register.getImportFilename();

// 3. Factory creates appropriate institution with filename
FinancialInstitutionInt financialInstitution = 
    FinancialInstitutionFactory.create(
        institutionType,  // "WellsFargo", "Barclays", etc.
        filename,         // "/downloads/checking.csv" or "statement.qfx"
        register,
        budget,
        forecast,
        view,
        notificationService
    );

// 4. Iterate through transactions (ImportController doesn't know format!)
try {
    while (financialInstitution.hasNext()) {
        Transaction transaction = financialInstitution.next();
        
        // Process transaction (format-agnostic!)
        assignMerchant(transaction);
        assignSplits(transaction);
        reconcile(transaction);
        transaction.save();
    }
} finally {
    financialInstitution.close();
}
```

**Benefits**:
- ✅ ImportController has **zero** knowledge of file formats
- ✅ Adding new format = add parser + update institution (no controller changes!)
- ✅ Institution handles complexity of format detection
- ✅ Clean iterator pattern - easy to test and maintain

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

