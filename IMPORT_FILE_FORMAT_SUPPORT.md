# Import File Format Support

## Overview
The application now supports multiple file formats for importing both cleared and provisional transactions. The import process automatically detects the file format based on the file extension.

## Supported Scenarios

### Wells Fargo Bank
- **Cleared Transactions**: CSV format (`.csv`)
  - Contains all posted/cleared transactions
  - Imported via `ImportController.importRegisterTransactionFile()`
  
- **Provisional Transactions**: TSV format (`.tsv`)
  - Contains pending/uncleared transactions (e.g., weekend transactions not yet posted)
  - Imported via `ImportController.importProvisionalTransactionFile()`
  - **Note**: Wells Fargo does NOT include provisional transactions in their download file, so a separate TSV file must be obtained

### Barclays Bank
- **Cleared Transactions**: QFX format (`.qfx`)
  - Contains all transactions (already cleared)
  - Imported via `ImportController.importRegisterTransactionFile()`
  
- **Provisional Transactions**: Not applicable
  - Barclays does not separate provisional transactions
  - All transactions in QFX file are already cleared
  - Register fields for provisional file should be `NULL`

## Register Configuration

The `register` table in the database has the following relevant columns:

### Cleared Transaction Configuration
- `trxImportFileName` - Filename for cleared transactions (e.g., "Checking1.csv", "qdlYYYYMMDD.qfx")
- `trxImportFileDirectory` - Directory path where cleared transaction file is located

### Provisional Transaction Configuration  
- `provisionalTrxFileName` - Filename for provisional transactions (e.g., "BillPayAccountProvTrx.tsv")
  - Set to `NULL` for institutions that don't support provisional transactions (like Barclays)
- `provisionalTrxFileDirectory` - Directory path where provisional transaction file is located
  - Set to `NULL` for institutions that don't support provisional transactions

## Supported File Formats

### Cleared Transactions
The `importRegisterTransactionFile()` method supports:
- **CSV** (`.csv`) - Comma-separated values
- **TSV** (`.tsv`) - Tab-separated values  
- **QFX** (`.qfx`) - Quicken Financial Exchange format
- **OFX** (`.ofx`) - Open Financial Exchange format

### Provisional Transactions
The `importProvisionalTransactionFile()` method supports:
- **CSV** (`.csv`) - Comma-separated values
- **TSV** (`.tsv`) - Tab-separated values

**Note**: QFX/OFX formats are not supported for provisional transactions because:
1. These formats don't have a concept of "provisional" vs "cleared" - all transactions are considered cleared
2. Institutions that use QFX/OFX (like Barclays) don't provide separate provisional transaction files

## How It Works

### Daily Update Process
During the daily update (`DailyUpdateController.run()`):

1. **Import Cleared Transactions**
   - Calls `importController.importRegisterTransactionFile()`
   - Reads `register.trxImportFileName` and `register.trxImportFileDirectory`
   - Detects file format from extension
   - Uses appropriate parser (CSV, TSV, QFX, or OFX)

2. **Import Provisional Transactions** (if configured)
   - Checks if `register.provisionalTrxFileName` is NOT NULL and not empty
   - If configured, calls `importController.importProvisionalTransactionFile()`
   - Reads `register.provisionalTrxFileName` and `register.provisionalTrxFileDirectory`  
   - Detects file format from extension
   - Uses appropriate parser (CSV or TSV only)

3. **Reconciliation**
   - Cleared transactions are matched against provisional transactions
   - If match found, provisional transaction is updated to cleared status
   - Prevents duplicate transactions

### Format Detection Logic

Both import methods use the same pattern:
```java
// Extract file extension
String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();

// Route to appropriate handler
switch (extension) {
    case "csv", "tsv" -> handleCsvFormat();
    case "qfx", "ofx" -> handleQfxFormat();  // Cleared only
    default -> throw new IllegalArgumentException("Unsupported format");
}
```

## Example Register Configurations

### Wells Fargo - Checking Account
```sql
INSERT INTO register (
    name,
    trxImportFileName,
    trxImportFileDirectory,
    provisionalTrxFileName,
    provisionalTrxFileDirectory,
    financialInstitution
) VALUES (
    'Bill Pay Account',
    'Checking1.csv',
    'C:\\Users\\dwhix\\Downloads',
    'BillPayAccountProvTrx.tsv',
    'C:\\Users\\dwhix\\Downloads',
    'Wells Fargo Bank'
);
```

### Barclays - Credit Card
```sql
INSERT INTO register (
    name,
    trxImportFileName,
    trxImportFileDirectory,
    provisionalTrxFileName,
    provisionalTrxFileDirectory,
    financialInstitution
) VALUES (
    'AAdvantage Aviator Mastercard',
    'qdlYYYYMMDD.qfx',
    'C:\\Users\\dwhix\\Downloads',
    NULL,  -- No provisional transactions
    NULL,  -- No provisional transactions
    'Barclays Bank'
);
```

## Migration Notes

### Old Code (Deprecated)
```java
// Cleared transactions - format-specific method
importController.importCsvRegisterTransactionFile();

// Provisional transactions - hardcoded to CSV only  
importController.importCsvProvisionalTransactionFile();
```

### New Code (Current)
```java
// Cleared transactions - auto-detects format
importController.importRegisterTransactionFile();

// Provisional transactions - auto-detects CSV/TSV format
importController.importProvisionalTransactionFile();
```

## Architecture Benefits

1. **Format Agnostic**: Both cleared and provisional imports now support multiple formats
2. **Extensible**: Easy to add support for new file formats (just add new case to switch statement)
3. **Institution Specific**: Each financial institution can use different formats
4. **Backward Compatible**: Old CSV-specific methods still work (marked as deprecated)
5. **Consistent**: Same pattern used for both cleared and provisional transactions

## Future Enhancements

Potential additions:
- **OFX/QFX support for provisional transactions** (if any institution provides it)
- **JSON format support** for modern APIs
- **XML format support** for some European banks
- **Direct API integration** (eliminate file download step entirely)

## Testing

To test the import functionality:

1. **Wells Fargo Test**:
   - Place `Checking1.csv` in downloads folder (cleared transactions)
   - Place `BillPayAccountProvTrx.tsv` in downloads folder (provisional transactions)
   - Run daily update
   - Verify both files are imported and reconciled correctly

2. **Barclays Test**:
   - Place `qdl20251215.qfx` in downloads folder (all transactions, already cleared)
   - Run daily update
   - Verify QFX file is imported
   - Confirm no provisional transaction import occurs (fields are NULL)

3. **Mixed Test**:
   - Import Wells Fargo register (CSV + TSV)
   - Import Barclays register (QFX only)
   - Verify both work in same daily update session

