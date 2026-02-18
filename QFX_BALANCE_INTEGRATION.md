# QFX Balance Integration Enhancement

## Overview
Enhanced the daily update process to automatically use the bank account balance from QFX import files during the "Verify Register Balance" step.

## Problem Solved
Previously, when importing transactions from QFX files, the user had to manually enter the account balance even though the QFX file already contains the ledger balance. This enhancement automatically extracts and offers the QFX balance to the user.

## Changes Made

### 1. FinancialInstitutionInt Interface
**File:** `FinancialInstitutionInt.java`

Added new method:
```java
/**
 * Gets the ledger balance from the imported transaction file (if available).
 * This is only available for QFX/OFX files after calling importRegisterTrxFile().
 * 
 * @return the ledger balance from the import file, or null if not available
 *         (e.g., for CSV files or before import)
 */
Double getImportedLedgerBalance();
```

### 2. FinancialInstitution Base Class
**File:** `FinancialInstitution.java`

- Added import for `QfxStatement`
- Implemented `getImportedLedgerBalance()` method:
  - Returns the ledger balance from QFX files after import
  - Returns `null` for CSV files or before import
  - Safely casts the parser and handles exceptions

### 3. GenericClassifer Class
**File:** `GenericClassifer.java`

- Implemented `getImportedLedgerBalance()` to return `null` (doesn't support file import)

### 4. RegisterController
**File:** `RegisterController.java`

Enhanced `verifyRegisterBalance()` method to:

**When QFX balance is available:**
1. Display current database balance
2. Display balance from QFX file
3. If balances differ, offer three choices:
   - Use QFX balance
   - Keep database balance
   - Enter a different balance manually

**When QFX balance is NOT available (CSV files):**
- Works as before - prompts for manual balance entry

## User Experience

### Before Enhancement
```
VERIFY REGISTER BALANCE
Current balance of AAdvantage Aviator Mastercard: $-5,355.74
Enter new balance (or press Enter to keep current balance): [user must type]
```

### After Enhancement (QFX files)

**Scenario 1: Balances Match**
```
VERIFY REGISTER BALANCE
Current balance of AAdvantage Aviator Mastercard: $-5,355.74
Downloaded balance: $-5,355.74
Balance matches QFX file. No update needed.
```

**Scenario 2: Balances Differ**
```
VERIFY REGISTER BALANCE
Current balance of AAdvantage Aviator Mastercard: $-5,300.00
Downloaded balance: $-5,355.74

The balances differ!

What would you like to do?
  1 - Use balance from QFX file ($-5,355.74)
  2 - Keep database balance ($-5,300.00)
  3 - Enter a different balance
Enter your choice (1-3): 1

[Balance updated to $-5,355.74]
```

**Scenario 3: CSV Files (No QFX balance)**
```
VERIFY REGISTER BALANCE
Current balance of Checking Account: $2,500.00
Enter new balance (or press Enter to keep current balance): [works as before]
```

## Technical Details

### How It Works

1. **During Import:** When `importRegisterTrxFile()` is called for a QFX file:
   - `QfxParser` parses the file and extracts the ledger balance
   - Balance is stored in `QfxStatement` object
   - Parser remains open until `close()` is called

2. **During Balance Verification:** When `verifyRegisterBalance()` is called:
   - Calls `financialInstitution.getImportedLedgerBalance()`
   - If QFX balance exists, displays it and offers options
   - If no QFX balance (CSV or before import), uses traditional prompt

3. **Balance Extraction:** The QFX parser extracts balance from:
   - Banking message sets (checking/savings accounts)
   - Credit card message sets (credit cards)
   - Uses the ofx4j library's `getLedgerBalance()` method

### File Format Support

| Format | Balance Available | Behavior |
|--------|------------------|----------|
| QFX/OFX | ✅ Yes | Shows QFX balance with 3-choice menu |
| CSV/TSV | ❌ No | Traditional manual entry prompt |

## Benefits

1. **Accuracy:** Uses the official balance from the financial institution
2. **Convenience:** No need to manually type balance for QFX files
3. **Transparency:** Shows both balances when they differ
4. **Flexibility:** Still allows manual entry if needed
5. **Backward Compatible:** Works seamlessly with CSV files

## Common Use Cases

### Use Case 1: Weekend Import with No Cleared Transactions
- User downloads QFX on weekend
- No new transactions (non-business day)
- QFX still contains current balance
- User can verify/update balance even without new transactions

### Use Case 2: Reconciliation After Multiple Transactions
- User imports QFX with multiple transactions
- Balance may have changed during processing
- QFX balance provides authoritative ending balance
- Easy one-click acceptance of correct balance

### Use Case 3: Detecting Import Issues
- QFX balance differs significantly from database
- Alerts user to potential missed transactions or errors
- User can investigate before accepting balance

## Testing Recommendations

1. Test with QFX file containing balance
2. Test with CSV file (should use traditional prompt)
3. Test when balances match (should show match message)
4. Test when balances differ (should show 3 choices)
5. Test each choice option (1, 2, 3)
6. Test invalid choice input (should keep current balance)

## Future Enhancements

Potential improvements for future versions:

1. **Available Balance:** Also show available balance if present in QFX
2. **Balance History:** Track balance changes over time
3. **Discrepancy Reporting:** Log significant balance differences
4. **Auto-Accept Threshold:** Auto-accept if difference is within $0.01
5. **Statement Date:** Show the statement date from QFX file
