wells # Barclays Bank Implementation Plan (QFX Format)
## Test-Driven Development Approach

**Project**: Add support for Barclays Aviator Mastercard credit card tracking  
**Import Format**: QFX (Quicken 2010 or later format)  
**Development Methodology**: Test-Driven Development (TDD)  
**Created**: December 15, 2025

---

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites & Questions](#prerequisites--questions)
3. [Technical Background](#technical-background)
4. [Project Structure](#project-structure)
5. [Implementation Phases](#implementation-phases)
6. [Testing Strategy](#testing-strategy)
7. [Acceptance Criteria](#acceptance-criteria)
8. [Appendices](#appendices)

---

## Overview

### Goals
- Implement Barclays Bank as a new FinancialInstitution
- Support QFX file format for transaction imports
- Follow TDD principles throughout development
- Maintain consistency with existing Wells Fargo implementation patterns
- Enable tracking of credit card transactions (purchases, payments, fees, interest, rewards)

### Non-Goals (Initial Release)
- ~~Automatic transaction downloads (manual file downloads only)~~ - **SEE BELOW**
- Support for other Barclays account types (checking, savings)
- Historical data migration (start tracking from implementation date forward)

### Automatic Transaction Downloads - Analysis

**Question**: Can we automatically download transactions from Barclays Bank?

**Short Answer**: Yes, but with significant complexity and requirements.

#### Options for Automatic Downloads

##### Option 1: Barclays Open Banking API (Best Option for UK)
- **Status**: Available in UK only (not US Barclays)
- **Access**: Free via UK Open Banking regulations
- **Requirements**:
  - Register as Third Party Provider (TPP)
  - Obtain FCA authorization or use registered TPP
  - Implement OAuth 2.0 authentication
  - Handle Strong Customer Authentication (SCA)
- **Data Format**: JSON (not QFX)
- **Availability**: Real-time transaction access
- **Complexity**: High (requires API registration, security, compliance)
- **Cost**: Free for regulated access, but registration process has costs

##### Option 2: Barclays US Web Scraping
- **Status**: Technically possible but not officially supported
- **Method**: Programmatically log into Barclays online banking and download QFX
- **Requirements**:
  - Selenium/Playwright for browser automation
  - User credentials storage (security concern)
  - Handle 2FA/MFA challenges
  - Maintain script as website changes
- **Data Format**: QFX files (same as manual)
- **Complexity**: High (brittle, maintenance-heavy)
- **Legality**: May violate Terms of Service
- **Recommendation**: ❌ **NOT RECOMMENDED** - against most bank TOS

##### Option 3: Third-Party Aggregation Services
- **Providers**: Plaid, Yodlee, MX, Finicity
- **Status**: Widely used, officially supported by many banks
- **How it works**:
  - User connects account via aggregator
  - Aggregator maintains bank connections
  - Your app queries aggregator API
- **Data Format**: JSON via REST API (not QFX)
- **Barclays Support**: 
  - **Plaid**: Yes (Barclays US supported)
  - **Yodlee**: Yes (Barclays supported)
  - **MX**: Yes (most US banks including Barclays)
- **Complexity**: Medium (integrate API, handle OAuth)
- **Cost**: 
  - Plaid: $0.30-$0.50 per linked account per month
  - Yodlee: Variable, typically $0.10-$0.25 per user per month
  - MX: Enterprise pricing (contact sales)
- **Pros**:
  - Officially supported
  - Handles authentication, MFA, bank changes
  - Legal and compliant
  - Supports 10,000+ financial institutions
- **Cons**:
  - Monthly costs scale with users
  - Requires code changes (JSON instead of QFX)
  - Data privacy considerations
  - Ongoing subscription

##### Option 4: OFX Direct Connect Protocol
- **Status**: Legacy protocol, declining support
- **Barclays Support**: ❌ **NOT SUPPORTED** by Barclays US
- **How it works**: Direct OFX protocol connection to bank servers
- **Used by**: Quicken, Microsoft Money (older versions)
- **Availability**: Barclays discontinued OFX Direct Connect support
- **Recommendation**: Not viable

#### Recommendation for Your Application

**Phase 1 Implementation**: ✅ **Manual QFX Downloads** (Current Plan)
- Simplest to implement
- No ongoing costs
- No third-party dependencies
- Works with existing QFX format
- User maintains full control
- Completes in 15-22 hours

**Future Enhancement**: 🔄 **Add Plaid Integration** (Phase 2)
- Most popular aggregator in US
- Good developer documentation
- Reasonable pricing for personal use
- JSON API is straightforward
- Supports Barclays and 12,000+ other institutions

#### Implementation Estimate for Plaid Integration

If you decide to add automatic downloads later:

**Additional Work Required**:
1. **Plaid Account Setup** (1 hour)
   - Register developer account
   - Configure application
   - Get API keys (sandbox + production)

2. **Plaid Link Integration** (3-4 hours)
   - Add Plaid Link UI component
   - Implement OAuth flow
   - Store access tokens securely
   - Handle token refresh

3. **Transaction Sync** (4-5 hours)
   - Call Plaid Transactions API
   - Map Plaid JSON to internal Transaction model
   - Deduplicate with existing QFX imports
   - Schedule periodic syncs (daily/weekly)

4. **Testing** (2-3 hours)
   - Test with sandbox accounts
   - Test with real Barclays connection
   - Test deduplication logic

**Total Additional Time**: 10-13 hours  
**Monthly Cost**: ~$0.30-$0.50 per connected account  
**Annual Cost (1 account)**: ~$4-$6/year

#### Recommendation Matrix

| Approach | Complexity | Cost | Legality | Maintenance | Recommendation |
|----------|-----------|------|----------|-------------|----------------|
| Manual QFX | Low | $0 | ✅ Legal | None | ✅ **Start Here** |
| Plaid API | Medium | ~$5/year | ✅ Legal | Low | ✅ **Future Phase** |
| Yodlee API | Medium | ~$3/year | ✅ Legal | Low | ✅ Alternative |
| Web Scraping | High | $0 | ❌ TOS Violation | High | ❌ Avoid |
| OFX Direct | N/A | $0 | ✅ Legal | N/A | ❌ Not Supported |
| UK Open Banking | Very High | Varies | ✅ Legal | Medium | ❌ UK Only |

#### Updated Phasing

**Current Plan (Phase 1-5)**: Manual QFX Import
- Implement as designed
- User downloads QFX from Barclays website
- Import via daily update process
- **Status**: ✅ Ready to implement

**Future Enhancement (Phase 6)**: Automatic Downloads
- Add Plaid integration
- Optional feature (manual import still works)
- Scheduled daily/weekly syncs
- **Status**: 🔮 Future consideration

#### Decision Point

**For Now**: Proceed with manual QFX import implementation. This:
- ✅ Meets immediate need (tracking transactions)
- ✅ Zero ongoing costs
- ✅ Completes quickly (15-22 hours)
- ✅ No privacy/security concerns with third parties
- ✅ Full control over data

**For Later**: If you want automatic downloads after Phase 5:
- 🔄 Evaluate if manual process is burdensome
- 🔄 Consider Plaid integration (~10-13 additional hours)
- 🔄 Budget for ~$5/year ongoing costs
- 🔄 Review data privacy implications

**My Recommendation**: 
1. **Complete manual QFX implementation first** (current plan)
2. **Use it for 1-2 months** to understand workflow
3. **Then decide** if automatic downloads justify the effort/cost
4. **If yes**, add Plaid as enhancement

The manual approach is perfectly viable for a single user tracking one credit card. Many personal finance apps use manual imports successfully.

---

## Prerequisites & Questions

### Questions to Resolve Before Starting

#### 1. **Sample QFX File** ✅ RESOLVED
- [x] Obtain a sample QFX file from Barclays (with sensitive data redacted)
- [x] Document the file structure and field mappings
- [x] Identify Barclays-specific quirks or variations

**Resolution**: Sample file `qdl20251215.qfx` obtained with year-to-date transactions. 
**Key Findings**:
- File format is **SGML-based OFX 1.02** (not XML!)
- Uses `<CREDITCARDMSGSRSV1>` wrapper for credit card transactions
- Account ID: XXXXXXXXXXXX2925
- Contains all transaction types: DEBIT, CREDIT
- `<NAME>` tag contains merchant/payee information
- `<FITID>` provides unique transaction identifiers
- No `<MEMO>` tag in most transactions (only NAME)
- **CRITICAL**: This is SGML format, NOT XML - requires special parsing

#### 2. **Register Setup** ✅ RESOLVED
- [ ] Does the Aviator Mastercard register already exist in the database?
- [x] If not, should we include register creation steps in this plan?
- [ ] What should the register be named? (e.g., "Aviator Mastercard", "Barclays Credit Card")

**Resolution**: Include register creation steps in Phase 0.

#### 3. **Transaction Types** ✅ RESOLVED
For credit cards, we should track:
- [x] Purchases (debits/charges) - **Confirmed in QFX**
- [x] Payments (credits) - **Confirmed in QFX** ("PAYMENT RECV'D CHECKFREE", "Payment Received")
- [x] Fees (annual fee, late payment, etc.) - **Confirmed in QFX** ("PRIMARY ANNUAL FEE")
- [x] Interest charges - **Confirmed in QFX** ("INTEREST CHARGE-PURCHASES")
- [x] Rewards/cashback (if shown in QFX) - **Confirmed in QFX** ("FLIGHT CENTS", "AA 25% INFLIGHT CREDIT", "AA WIFI CREDIT")
- [x] Cash advances - **Likely supported but not in sample**
- [x] Refunds/returns - **Likely supported but not in sample**

**Resolution**: Track all of the above. QFX file contains comprehensive transaction data.

#### 4. **Merchant Identification** ✅ RESOLVED
- [x] How does Barclays format merchant/payee information in QFX?
- [x] Is it similar to Wells Fargo's format, or completely different?
- [x] Are there merchant category codes (MCC) in the QFX file?

**Resolution**: 
- Barclays uses `<NAME>` tag for merchant/payee (e.g., "NETFLIX.COM", "LA FITNESS", "Spectrum Mobile")
- Format is very similar to Wells Fargo CSV payee format - can reuse most parsing logic
- No MCC (Merchant Category Code) found in the sample QFX file
- Some transactions have detailed merchant names (e.g., "PUBLIX #1553", "7-ELEVEN 38991")
- Payment transactions have standardized names ("PAYMENT RECV'D CHECKFREE")

#### 5. **QFX Parser Library** ✅ RESOLVED
- [x] Use existing library for OFX/SGML parsing

**Resolution**: Use a library (recommended: `ofx4j`) because:
- QFX is **SGML-based**, not XML (SGML is more complex to parse)
- SGML format doesn't have closing tags like XML
- Professional OFX libraries handle both SGML (OFX 1.x) and XML (OFX 2.x) formats
- Saves significant development time and handles edge cases
- **Library Choice**: `ofx4j` (Apache License, actively maintained, supports OFX 1.x SGML and OFX 2.x XML)

#### 6. **Provisional Transactions** ✅ RESOLVED
- [x] Does Barclays QFX export include pending/provisional transactions?
- [x] Or only posted/cleared transactions?
- [x] If both, how are they distinguished in the file?

**Resolution**: 
- Sample QFX file contains only **posted/cleared transactions** (all have `<DTPOSTED>` dates)
- `<DTUSER>` field present (user-initiated date, often same as or 1-2 days before posted date)
- No provisional transaction handling needed for QFX imports
- Provisional transactions would come from a separate source (if Barclays provides them)

#### 7. **Testing Data** ✅ RESOLVED
- [x] Do you have real (redacted) QFX files we can use for test cases?
- [x] Should we create synthetic QFX test data?

**Resolution**: 
- Real QFX file available: `src/test/resources/qdl20251215.qfx`
- Will create smaller synthetic test files for unit tests
- Will extract specific transaction examples for edge case testing

---

## Technical Background

### QFX/OFX File Format

**IMPORTANT**: Barclays exports QFX files in **SGML format (OFX 1.02)**, NOT XML format!

#### SGML vs. XML
- **OFX 1.x (SGML)**: Original format, no closing tags, more compact
- **OFX 2.x (XML)**: Modern XML-based format with closing tags

Barclays uses OFX 1.02 SGML format. Here's the actual structure from the sample file:

```sgml
OFXHEADER:100
DATA:OFXSGML
VERSION:102
SECURITY:NONE
ENCODING:USASCII
CHARSET:1252
COMPRESSION:NONE
OLDFILEUID:NONE
NEWFILEUID:NONE

<OFX>
  <SIGNONMSGSRSV1>
    <SONRS>
      <STATUS><CODE>0<SEVERITY>INFO</STATUS>
      <DTSERVER>20251215172640.276
      <LANGUAGE>eng
      <FI><ORG>Barclays Bank Delaware<FID>4351</FI>
      <INTU.BID>4351
      <INTU.USERID>dwhixon
    </SONRS>
  </SIGNONMSGSRSV1>
  
  <CREDITCARDMSGSRSV1>
    <CCSTMTTRNRS>
      <TRNUID>0
      <STATUS><CODE>0<SEVERITY>INFO</STATUS>
      <CCSTMTRS>
        <CURDEF>USD
        <CCACCTFROM>
          <ACCTID>XXXXXXXXXXXX2925
        </CCACCTFROM>
        
        <BANKTRANLIST>
          <DTSTART>20250101000000.000
          <DTEND>20251215000000.000
          
          <!-- Sample Purchase Transaction -->
          <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20251210050000.000
            <DTUSER>20251210050000.000
            <TRNAMT>-2.90
            <FITID>75140215344000935945073101
            <NAME>FLIGHT CENTS
          </STMTTRN>
          
          <!-- Sample Payment Transaction -->
          <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20251119050000.000
            <DTUSER>20251119050000.000
            <TRNAMT>2219.00
            <FITID>75140215323111925069629108
            <NAME>PAYMENT RECV'D CHECKFREE
          </STMTTRN>
          
          <!-- Sample Fee Transaction -->
          <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20251130050000.000
            <DTUSER>20251130050000.000
            <TRNAMT>-99.00
            <FITID>00005000020251130
            <NAME>PRIMARY ANNUAL FEE
          </STMTTRN>
          
          <!-- Sample Interest Charge -->
          <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20251210050000.000
            <DTUSER>20251210050000.000
            <TRNAMT>-237.23
            <FITID>00005000020251210
            <NAME>INTEREST CHARGE-PURCHASES
          </STMTTRN>
          
          <!-- Sample Reward Credit -->
          <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20251005040000.000
            <DTUSER>20251004040000.000
            <TRNAMT>5.00
            <FITID>751402152780000000005727239
            <NAME>AA 25% INFLIGHT CREDIT
          </STMTTRN>
          
          <!-- More transactions... -->
        </BANKTRANLIST>
        
        <LEDGERBAL>
          <BALAMT>-10327.21
          <DTASOF>20251215172640.276
        </LEDGERBAL>
      </CCSTMTRS>
    </CCSTMTTRNRS>
  </CREDITCARDMSGSRSV1>
</OFX>
```

#### Key OFX Elements for Barclays Credit Card Transactions

| Element | Description | Example | Required |
|---------|-------------|---------|----------|
| `TRNTYPE` | Transaction type | `DEBIT`, `CREDIT` | Yes |
| `DTPOSTED` | Posted date (YYYYMMDDHHMMSS.mmm) | `20251210050000.000` | Yes |
| `DTUSER` | User-initiated date | `20251210050000.000` | No |
| `TRNAMT` | Amount (negative=charge, positive=payment) | `-237.23`, `2219.00` | Yes |
| `FITID` | Unique transaction ID | `75140215344000935945073101` | Yes |
| `NAME` | Merchant/payee name | `NETFLIX.COM`, `LA FITNESS` | Yes |
| `MEMO` | Additional description | (rarely used by Barclays) | No |
| `CHECKNUM` | Check number | (not used for credit cards) | No |

#### Merchant Name Patterns in Barclays QFX

Based on the sample file analysis:

1. **Subscription Services**: Clean names - `NETFLIX.COM`, `Spotify P3CA3D8014`, `GOOGLE *YouTube TV`
2. **Utilities**: Descriptive - `PEACE RIVER ELECTRIC`, `Spectrum Mobile`, `ADT SECURITY*320925392`
3. **Retail with Location**: `PUBLIX #1553`, `7-ELEVEN 38991`, `WAWA 5185`
4. **Online Services**: `AMAZON MKTPL*H87MO7QJ3`, `WP*JETPACK UBKKUTL1X`
5. **Payments**: Standardized - `PAYMENT RECV'D CHECKFREE`, `Payment Received`
6. **Fees**: Standardized - `PRIMARY ANNUAL FEE`, `INTEREST CHARGE-PURCHASES`
7. **Rewards**: Specific - `FLIGHT CENTS`, `AA 25% INFLIGHT CREDIT`, `AA WIFI CREDIT`
8. **Loans/Insurance**: `MORI Loan Re`, `STATE FARM  INSURANCE`
9. **Travel**: `icelandairCB59ZC`, `BRITISH A 1252203142408`, `LOS SUENOS MARRIOTT OC`

**Parsing Strategy**: Very similar to Wells Fargo format - can reuse tokenization and cleaning logic.

#### Detailed Merchant Name Analysis

From analyzing the 230+ transactions in the sample file, here are key observations:

**Exact Matches to Wells Fargo Patterns**:
- Transfers: `MORI Loan Re` (similar to Wells Fargo "Transfer to/from" patterns)
- Online services: `AMAZON MKTPL*H87MO7QJ3` (same asterisk pattern as Wells Fargo)
- Utilities: `PEACE RIVER ELECTRIC`, `Spectrum Mobile` (clean names)

**Special Patterns Unique to Barclays**:
- Airline rewards: `FLIGHT CENTS`, `AA 25% INFLIGHT CREDIT`, `AA WIFI CREDIT`
- Subscription identifiers: `Spotify P3CA3D8014` (Spotify + transaction ID)
- Insurance/loans: `STATE FARM  INSURANCE` (double space preserved)
- Fee standardization: `PRIMARY ANNUAL FEE`, `INTEREST CHARGE-PURCHASES`

**Merchant Name Cleaning Required**:
1. **Asterisk-separated serial numbers**: `AMAZON MKTPL*H87MO7QJ3` → `AMAZON MKTPL`
2. **Store numbers**: `PUBLIX #1553` → Keep as-is (identifies location)
3. **Alphanumeric suffixes**: `Spotify P3CA3D8014` → Needs evaluation (P3... might be order ID)
4. **Security codes**: `WP*JETPACK UBKKUTL1X` → `WP*JETPACK` or `JETPACK`
5. **Airline codes**: `BRITISH A 1252203142408` → `BRITISH A` (strip booking number)

**Recommendation for Phase 2**:
- Start with Wells Fargo's `makePayeeFromTokens()` logic
- Add Barclays-specific rules for:
  - Airline booking number removal
  - Reward credit identification
  - Fee/interest standardization
- Test against sample transactions to verify merchant extraction accuracy

### OFX Parsing Library: ofx4j

**Selected Library**: [ofx4j](https://github.com/stoicflame/ofx4j) 

**Why ofx4j?**
- Apache License 2.0 (compatible with project)
- Handles both SGML (OFX 1.x) and XML (OFX 2.x) formats
- Mature and well-tested library
- Active community support
- Clean API for reading OFX files

**Maven Dependency**:
```xml
<dependency>
    <groupId>com.webcohesion.ofx4j</groupId>
    <artifactId>ofx4j</artifactId>
    <version>1.36</version>
</dependency>
```

**Basic Usage Example**:
```java
import com.webcohesion.ofx4j.io.AggregateUnmarshaller;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardStatementResponseTransaction;

// Parse OFX file
AggregateUnmarshaller unmarshaller = new AggregateUnmarshaller<>(ResponseEnvelope.class);
ResponseEnvelope envelope = (ResponseEnvelope) unmarshaller.unmarshal(new FileReader("file.qfx"));

// Access credit card transactions
CreditCardStatementResponseTransaction ccStmt = envelope.getCreditCardResponseMessageSet()
    .getStatementResponses().get(0);
List<BankTransaction> transactions = ccStmt.getMessage().getTransactionList().getTransactions();
```

---

## Project Structure

### New Files to Create

```
src/main/java/com/hixon/financialApp/
├── model/
│   └── financialinstitution/
│       └── BarclaysBank.java                    (NEW - main implementation)
│
└── utility/
    └── QfxParser.java                           (NEW - QFX/OFX parsing utility)

src/test/java/com/hixon/financialApp/
├── model/
│   └── financialinstitution/
│       └── BarclayansBankTest.java              (NEW - main test class)
│
└── utility/
    └── QfxParserTest.java                       (NEW - parser test class)

src/test/resources/
└── qfx/
    ├── barclays-sample-full.qfx                 (NEW - complete sample file)
    ├── barclays-sample-single-purchase.qfx      (NEW - minimal test case)
    ├── barclays-sample-with-payment.qfx         (NEW - payment test case)
    └── barclays-sample-malformed.qfx            (NEW - error handling test)
```

### Files to Modify

```
src/main/java/com/hixon/financialApp/
├── controller/
│   ├── ImportController.java                    (MODIFY - add QFX import handling)
│   └── FinancialInstitutionController.java      (MODIFY - if exists, add Barclays)
│
└── model/
    └── financialinstitution/
        └── FinancialInstitution.java            (REVIEW - ensure base class supports QFX)
```

---

## Implementation Phases

### Phase 0: Setup & Preparation (1-2 hours)
**Status**: 🟡 In Progress (50% Complete)

#### Tasks
1. **Gather Requirements** ✅ COMPLETE
   - [x] Get sample QFX file from Barclays - **File obtained: qdl20251215.qfx**
   - [x] Answer all questions in [Prerequisites section](#prerequisites--questions) - **All resolved**
   - [ ] Review Wells Fargo implementation thoroughly

2. **Create Test Data** ✅ COMPLETE
   - [x] Create `src/test/resources/qfx/` directory
   - [x] Extract minimal QFX test files from sample (1-3 transactions each)
   - [x] Create edge case test files:
     - test-single-purchase.qfx (Netflix)
     - test-single-payment.qfx (CheckFree payment)
     - test-annual-fee.qfx (annual fee)
     - test-interest-charge.qfx (interest charge)
     - test-reward-credit.qfx (airline reward)
   - [x] Document the structure of each test file

3. **Setup Database - Create Register**
   - [ ] Determine register name (suggested: "Barclays Aviator Mastercard")
   - [ ] Determine account number (use last 4 digits: 2925)
   - [ ] Create register in database:
     ```sql
     -- Example SQL (adjust as needed for your schema)
     INSERT INTO register (idRegister, name, accountNumber, financialInstitution, registerType, user_idUser)
     VALUES (UUID(), 'Barclays Aviator Mastercard', 'XXXXXXXXXXXX2925', 'Barclays Bank Delaware', 'CREDIT_CARD', <user_id>);
     ```
   - [ ] Verify register appears in application
   - [ ] Set initial balance (if needed)

4. **Setup Database - Budget Assignments**
   - [ ] Review existing budget categories for credit card expenses
   - [ ] Create any missing categories:
     - Credit Card Interest (if not exists)
     - Credit Card Fees (if not exists)
     - Credit Card Rewards (if not exists)
   - [ ] Document category names for test assertions

5. **Create Test Database Backup**
   - [ ] Backup current database state
   - [ ] Document backup location and restore procedure
   - [ ] Consider creating a dedicated test database

6. **Add ofx4j Dependency** ✅ COMPLETE
   - [x] Add ofx4j to pom.xml:
     ```xml
     <dependency>
         <groupId>com.webcohesion.ofx4j</groupId>
         <artifactId>ofx4j</artifactId>
         <version>1.36</version>
     </dependency>
     ```
   - [x] Run `mvn clean install` to download dependency
   - [x] Verify ofx4j classes are available in IDE

7. **Review Existing Code**
   - [ ] Study `WellsFargoBank.java` implementation
     - Focus on `parseMerchantPayee()` method
     - Focus on `makePayeeFromTokens()` method
     - Focus on `cleanPayeeTokenList()` method
   - [ ] Study `FinancialInstitutionInt.java` interface
   - [ ] Study `FinancialInstitution.java` abstract class
   - [ ] Study `ImportController.java` CSV import flow
   - [ ] Identify methods to move from WellsFargoBank to abstract class (if applicable)
   - [ ] Document key patterns to follow

8. **Refactor Common Code** (If Applicable)
   - [ ] Review WellsFargoBank for generally applicable methods
   - [ ] Consider moving to FinancialInstitution abstract class:
     - Token cleaning utilities?
     - City/state removal logic?
     - Generic payee parsing helpers?
   - [ ] Ensure changes don't break Wells Fargo implementation
   - [ ] Run existing tests to verify no regressions

**Deliverables**:
- [x] Sample QFX files in test resources
- [ ] Database ready with register and budget categories
- [ ] ofx4j library integrated
- [ ] Understanding of existing patterns documented
- [ ] (Optional) Common code refactored to abstract class

**Estimated Time**: 1-2 hours

---

### Phase 1: QFX Parser Wrapper (TDD) (3-4 hours)
**Status**: 🔴 Not Started

This phase builds a wrapper around the ofx4j library to simplify QFX file parsing for our application.

#### Test Cases to Write FIRST

**Test Class**: `QfxParserTest.java`

1. **Test: Parse Empty QFX File**
   ```java
   @Test
   @DisplayName("Parse empty QFX file should return empty transaction list")
   void testParseEmptyQfxFile()
   ```
   - Given: QFX file with no transactions (valid header, empty BANKTRANLIST)
   - Expected: Empty transaction list, no errors

2. **Test: Parse Single Purchase Transaction**
   ```java
   @Test
   @DisplayName("Parse QFX with single purchase transaction")
   void testParseSinglePurchase()
   ```
   - Given: QFX with one DEBIT transaction
   - Expected: One transaction with correct date, amount, merchant, FITID

3. **Test: Parse Single Payment Transaction**
   ```java
   @Test
   @DisplayName("Parse QFX with single payment transaction")
   void testParseSinglePayment()
   ```
   - Given: QFX with one CREDIT transaction (payment)
   - Expected: One transaction with positive amount, payment merchant name

4. **Test: Parse Multiple Transactions**
   ```java
   @Test
   @DisplayName("Parse QFX with multiple transactions")
   void testParseMultipleTransactions()
   ```
   - Given: QFX with 5+ transactions of various types
   - Expected: All transactions parsed correctly in order

5. **Test: Parse Transaction Types**
   ```java
   @Test
   @DisplayName("Correctly identify DEBIT vs CREDIT transactions")
   void testParseTransactionTypes()
   ```
   - Given: QFX with both DEBIT and CREDIT transactions
   - Expected: Correct signs on amounts (negative for DEBIT, positive for CREDIT)

6. **Test: Parse Date Formats**
   ```java
   @Test
   @DisplayName("Parse OFX SGML date formats")
   void testParseDateFormats()
   ```
   - Given: Dates in format: YYYYMMDDHHMMSS.mmm
   - Expected: Correct Calendar objects

7. **Test: Handle Malformed File**
   ```java
   @Test
   @DisplayName("Throw exception for malformed QFX file")
   void testMalformedQfx()
   ```
   - Given: Invalid SGML structure
   - Expected: Appropriate exception thrown with clear message

8. **Test: Handle Missing Required Fields**
   ```java
   @Test
   @DisplayName("Handle transaction with missing required fields")
   void testMissingRequiredFields()
   ```
   - Given: Transaction missing TRNAMT or DTPOSTED
   - Expected: Skip transaction with warning or throw clear error

9. **Test: Extract Account Information**
   ```java
   @Test
   @DisplayName("Extract account number from QFX")
   void testExtractAccountInfo()
   ```
   - Given: QFX with CCACCTFROM section
   - Expected: Account ID extracted correctly ("XXXXXXXXXXXX2925")

10. **Test: Parse Statement Balance**
    ```java
    @Test
    @DisplayName("Parse statement balance from QFX")
    void testParseStatementBalance()
    ```
    - Given: QFX with LEDGERBAL section
    - Expected: Balance and date extracted (-10327.21 in sample)

11. **Test: Parse Statement Date Range**
    ```java
    @Test
    @DisplayName("Extract statement start and end dates")
    void testParseStatementDateRange()
    ```
    - Given: QFX with DTSTART and DTEND
    - Expected: Correct date range extracted

12. **Test: Handle Special Characters in Merchant Names**
    ```java
    @Test
    @DisplayName("Handle special characters in merchant names")
    void testSpecialCharactersInMerchantNames()
    ```
    - Given: Merchants with asterisks, slashes, etc. ("WP*JETPACK", "PY *PRODIGY")
    - Expected: Special characters preserved in merchant name

#### Implementation Steps (TDD Cycle)

For each test above:
1. **RED**: Write the failing test first
2. **GREEN**: Write minimal code to make it pass
3. **REFACTOR**: Clean up code while keeping tests green
4. Commit after each test passes

#### Implementation Tasks

- [ ] Create `QfxParser.java` wrapper class
- [ ] Add ofx4j imports and basic file reading
- [ ] Implement transaction extraction using ofx4j:
  ```java
  // Pseudocode structure
  public class QfxParser {
      public List<QfxTransaction> parseQfxFile(File qfxFile) {
          // Use ofx4j to unmarshal
          // Extract credit card statement
          // Convert ofx4j transactions to our QfxTransaction DTOs
      }
      
      public QfxStatement getStatementInfo(File qfxFile) {
          // Extract account, balance, date range
      }
  }
  ```
- [ ] Create `QfxTransaction` DTO class:
  ```java
  public class QfxTransaction {
      private String fitid;           // FITID
      private Calendar postedDate;    // DTPOSTED
      private Calendar userDate;      // DTUSER
      private double amount;          // TRNAMT
      private String transactionType; // TRNTYPE
      private String merchantName;    // NAME
      private String memo;            // MEMO (if present)
      // getters, setters, constructors
  }
  ```
- [ ] Create `QfxStatement` DTO class:
  ```java
  public class QfxStatement {
      private String accountId;
      private Calendar startDate;
      private Calendar endDate;
      private double ledgerBalance;
      private Calendar balanceDate;
      // getters, setters, constructors
  }
  ```
- [ ] Implement date parsing (ofx4j handles this, but wrap for our needs)
- [ ] Implement error handling and logging
- [ ] Add comprehensive JavaDoc documentation

**Key Implementation Notes**:
- Use ofx4j's `AggregateUnmarshaller` to parse the SGML file
- Access transactions via `CreditCardStatementResponseTransaction`
- Map ofx4j's `TransactionType` enum to our application's concepts
- Handle timezone conversions (OFX dates are in institution timezone)

**Deliverables**:
- `QfxParser.java` with full test coverage
- `QfxTransaction.java` DTO
- `QfxStatement.java` DTO
- All tests passing (green)
- Code reviewed and refactored

**Estimated Time**: 3-4 hours (reduced from 4-6 because using library)

---

### Phase 2: Barclays Bank Implementation - Core (TDD) (6-8 hours)
**Status**: 🔴 Not Started

This phase implements the `BarclaysBank` class following the `FinancialInstitutionInt` interface.

#### Test Cases to Write FIRST

**Test Class**: `BarclayansBankTest.java`

1. **Test: Constructor**
   ```java
   @Test
   @DisplayName("Create BarclaysBank instance")
   void testConstructor()
   ```

2. **Test: Get CSV Headers Class**
   ```java
   @Test
   @DisplayName("Return null for CSV headers (QFX doesn't use CSV)")
   void testGetCsvHeadersClass()
   ```
   - Expected: Return null or throw UnsupportedOperationException

3. **Test: Create Transaction from QFX Data**
   ```java
   @Test
   @DisplayName("Create transaction from parsed QFX data")
   void testCreateFromQfxData()
   ```
   - Given: Parsed QFX transaction data
   - Expected: Transaction object with all fields populated

4. **Test: Parse Merchant/Payee - Simple Purchase**
   ```java
   @Test
   @DisplayName("Parse simple merchant name from QFX NAME field")
   void testParseMerchantSimple()
   ```
   - Given: "AMAZON.COM"
   - Expected: Merchant identified or payee extracted

5. **Test: Parse Merchant/Payee - With Location**
   ```java
   @Test
   @DisplayName("Parse merchant with location info")
   void testParseMerchantWithLocation()
   ```
   - Given: "STARBUCKS #12345 NEW YORK NY"
   - Expected: Extract "STARBUCKS", ignore location

6. **Test: Parse Merchant/Payee - Payment**
   ```java
   @Test
   @DisplayName("Parse payment transaction payee")
   void testParseMerchantPayment()
   ```
   - Given: "ONLINE PAYMENT - THANK YOU"
   - Expected: Identify as payment, extract appropriate payee

7. **Test: Get Register Import Record Base Name**
   ```java
   @Test
   @DisplayName("Generate import record ID from QFX transaction")
   void testGetRegisterImportRecordBaseName()
   ```
   - Given: QFX transaction with FITID
   - Expected: Unique import record ID generated

8. **Test: Handle Credit Card Fees**
   ```java
   @Test
   @DisplayName("Identify and parse fee transactions")
   void testHandleFees()
   ```
   - Given: "ANNUAL MEMBERSHIP FEE"
   - Expected: Proper categorization and parsing

9. **Test: Handle Interest Charges**
   ```java
   @Test
   @DisplayName("Identify and parse interest charges")
   void testHandleInterest()
   ```
   - Given: "INTEREST CHARGE - PURCHASES"
   - Expected: Proper categorization

10. **Test: Handle Refunds**
    ```java
    @Test
    @DisplayName("Parse refund/return transactions")
    void testHandleRefunds()
    ```
    - Given: Positive amount on purchase transaction
    - Expected: Identified as refund

11. **Test: Import Record ID Uniqueness**
    ```java
    @Test
    @DisplayName("Ensure import record IDs are unique")
    void testImportRecordIdUniqueness()
    ```
    - Given: Multiple transactions on same day
    - Expected: Each has unique import record ID

12. **Test: Provisional Transaction Matching**
    ```java
    @Test
    @DisplayName("Match cleared QFX transaction to provisional")
    void testProvisionalMatching()
    ```
    - This may be N/A if Barclays QFX only includes posted transactions

#### Implementation Tasks

- [ ] Create `BarclaysBank.java` class extending `FinancialInstitution`
- [ ] Implement all methods from `FinancialInstitutionInt` interface
- [ ] Integrate with `QfxParser`
- [ ] Implement merchant/payee parsing logic specific to Barclays
- [ ] Implement import record ID generation
- [ ] Add logging (using existing log4j2 setup)
- [ ] Add comprehensive JavaDoc

**Key Implementation Decisions**:
1. **Import Record ID Format**: Suggest using FITID from QFX + date for uniqueness
2. **Merchant Parsing**: Study actual Barclays QFX merchant format first
3. **Transaction Type Mapping**: Map OFX TRNTYPE to application concepts

**Deliverables**:
- `BarclaysBank.java` with full test coverage
- All tests passing
- Code reviewed and refactored

---

### Phase 3: Import Controller Integration (TDD) (3-4 hours)
**Status**: 🔴 Not Started

Integrate Barclays QFX import into the existing `ImportController`.

#### Test Cases to Write FIRST

**Test Class**: `ImportControllerTest.java` (or new `QfxImportTest.java`)

1. **Test: Detect QFX File Format**
   ```java
   @Test
   @DisplayName("Detect QFX file by extension and content")
   void testDetectQfxFormat()
   ```

2. **Test: Import Single Transaction**
   ```java
   @Test
   @DisplayName("Import single transaction from QFX file")
   void testImportSingleTransaction()
   ```

3. **Test: Import Multiple Transactions**
   ```java
   @Test
   @DisplayName("Import multiple transactions from QFX file")
   void testImportMultipleTransactions()
   ```

4. **Test: Duplicate Transaction Detection**
   ```java
   @Test
   @DisplayName("Detect and skip duplicate transactions")
   void testDuplicateDetection()
   ```
   - Given: Same transaction imported twice
   - Expected: Second import skipped with appropriate message

5. **Test: Invalid File Handling**
   ```java
   @Test
   @DisplayName("Handle invalid QFX file gracefully")
   void testInvalidFileHandling()
   ```

6. **Test: Register Balance Update**
   ```java
   @Test
   @DisplayName("Update register balance after import")
   void testRegisterBalanceUpdate()
   ```

7. **Test: Transaction Ordering**
   ```java
   @Test
   @DisplayName("Import transactions in correct date order")
   void testTransactionOrdering()
   ```

#### Implementation Tasks

- [ ] Add QFX file detection to `ImportController`
- [ ] Create `importQfxRegisterTransactionFile()` method
- [ ] Integrate with `BarclaysBank` class
- [ ] Add error handling and user feedback
- [ ] Update register balance logic for credit cards
- [ ] Add logging for import process

**Deliverables**:
- Import controller integration complete
- All tests passing
- Error handling robust

---

### Phase 4: End-to-End Testing (2-3 hours)
**Status**: 🔴 Not Started

Test the complete flow from QFX file to database.

#### Integration Test Cases

1. **Test: Complete Import Flow**
   - Given: Real/realistic QFX file
   - Steps: Import → Assign merchants → Assign budget items → Reconcile
   - Expected: All transactions properly stored and categorized

2. **Test: Daily Update Integration**
   - Given: QFX file with new transactions
   - Steps: Run daily update process
   - Expected: New transactions imported, old ones skipped

3. **Test: Merchant Assignment**
   - Given: Transactions with known merchants
   - Expected: Auto-assignment works correctly

4. **Test: Budget Item Assignment**
   - Given: Transactions matching forecast items
   - Expected: Auto-matching works correctly

5. **Test: Register Balance Verification**
   - Given: Imported transactions
   - Expected: Register balance matches QFX statement balance

#### Manual Testing Checklist

- [ ] Import a real QFX file from Barclays
- [ ] Verify all transactions appear correctly
- [ ] Test merchant assignment UI
- [ ] Test budget item assignment UI
- [ ] Verify forecast transaction matching
- [ ] Test duplicate import (should skip)
- [ ] Verify database integrity
- [ ] Test with edge cases (large files, unusual merchants)

**Deliverables**:
- All integration tests passing
- Manual testing completed and documented
- Any bugs found are fixed

---

### Phase 5: Documentation & Cleanup (1-2 hours)
**Status**: 🔴 Not Started

#### Tasks

- [ ] Add JavaDoc to all new classes and methods
- [ ] Update README.md with Barclays support information
- [ ] Create user guide section for Barclays QFX import
- [ ] Document any Barclays-specific quirks or limitations
- [ ] Update this implementation plan with "Lessons Learned"
- [ ] Code review and final refactoring
- [ ] Update .gitignore if needed (for QFX test files)

**Deliverables**:
- Complete documentation
- Clean, well-commented code
- User guide updated

---

### Phase 6: Automatic Downloads with Plaid (FUTURE ENHANCEMENT)
**Status**: 🔮 Future Consideration (Optional)

**Note**: This phase is NOT part of the initial implementation. Complete Phases 0-5 first, use the manual QFX import for 1-2 months, then evaluate if automatic downloads are needed.

#### Prerequisites
- [ ] Phases 0-5 completed and tested
- [ ] Manual QFX import proven to work
- [ ] User experience evaluated (is manual process burdensome?)
- [ ] Budget approved for ~$5/year ongoing costs
- [ ] Privacy implications reviewed

#### Phase 6A: Plaid Setup & Integration (4-5 hours)

**Tasks**:
1. **Create Plaid Developer Account**
   - [ ] Sign up at https://plaid.com/
   - [ ] Complete developer registration
   - [ ] Get sandbox API keys
   - [ ] Get production API keys (after testing)

2. **Add Plaid Dependencies**
   - [ ] Add Plaid Java library to pom.xml:
   ```xml
   <dependency>
       <groupId>com.plaid</groupId>
       <artifactId>plaid-java</artifactId>
       <version>17.0.0</version>
   </dependency>
   ```

3. **Implement Plaid Link UI**
   - [ ] Add Plaid Link to view layer
   - [ ] Implement OAuth flow for account connection
   - [ ] Store access tokens securely (encrypted in database)
   - [ ] Handle token expiration and refresh

4. **Create PlaidService Class**
   - [ ] Implement getTransactions() method
   - [ ] Map Plaid JSON to Transaction objects
   - [ ] Handle pagination (Plaid returns max 500 transactions per call)
   - [ ] Implement error handling and retry logic

5. **Add Configuration**
   - [ ] Add Plaid settings to application configuration
   - [ ] Add toggle for auto-sync vs. manual import
   - [ ] Configure sync schedule (daily/weekly)

**Deliverables**:
- Plaid integration functional
- User can connect Barclays account
- Transactions sync automatically

#### Phase 6B: Deduplication & Sync Logic (3-4 hours)

**Tasks**:
1. **Implement Transaction Deduplication**
   - [ ] Create unique transaction identifier (date + amount + merchant + last 4 of account)
   - [ ] Check for duplicates before inserting Plaid transactions
   - [ ] Handle partial matches (amount differs by tip)
   - [ ] Log duplicate detection for user review

2. **Implement Sync Scheduler**
   - [ ] Create scheduled job (use existing scheduler if available)
   - [ ] Run daily at configured time
   - [ ] Handle sync errors gracefully
   - [ ] Send notifications on sync success/failure

3. **Handle Historical Transactions**
   - [ ] On first sync, only import new transactions (not historical)
   - [ ] Provide option to backfill if needed
   - [ ] Prevent duplicate imports of QFX vs. Plaid data

4. **Add Sync Status Tracking**
   - [ ] Store last sync timestamp
   - [ ] Track sync success/failure
   - [ ] Show sync status in UI

**Deliverables**:
- No duplicate transactions
- Reliable scheduled syncs
- User visibility into sync status

#### Phase 6C: Testing & Production (2-3 hours)

**Tasks**:
1. **Test with Plaid Sandbox**
   - [ ] Test connection flow
   - [ ] Test transaction retrieval
   - [ ] Test error scenarios

2. **Test with Real Barclays Account**
   - [ ] Connect real account (production Plaid API)
   - [ ] Verify transactions match Barclays statement
   - [ ] Test deduplication with existing QFX imports

3. **Monitor Production Usage**
   - [ ] Set up monitoring/alerting for sync failures
   - [ ] Review Plaid usage/costs monthly
   - [ ] Gather user feedback

**Deliverables**:
- Production-ready Plaid integration
- Tested with real account
- Monitoring in place

#### Phase 6 Estimates
- **Total Time**: 10-13 hours
- **Ongoing Cost**: ~$0.30-$0.50/month per connected account
- **Benefits**: 
  - No manual downloads needed
  - Daily automatic updates
  - Works with any Plaid-supported bank (12,000+)
- **Tradeoffs**:
  - Ongoing subscription cost
  - Third-party dependency
  - Data shared with Plaid

#### Alternative: Yodlee Integration
If Plaid pricing is too high, Yodlee is similar but slightly cheaper (~$0.10-$0.25/month). Implementation effort is comparable.

---

## Testing Strategy

### TDD Principles
1. **Red-Green-Refactor Cycle**
   - Write failing test first (RED)
   - Write minimal code to pass (GREEN)
   - Refactor while keeping tests green (REFACTOR)

2. **Test Coverage Goals**
   - Aim for 80%+ code coverage
   - 100% coverage for critical paths (transaction parsing, merchant identification)

3. **Test Pyramid**
   - Many unit tests (fast, isolated)
   - Some integration tests (test component interactions)
   - Few end-to-end tests (test complete flows)

### Testing Tools
- **JUnit 5**: Test framework (already in pom.xml)
- **Mockito**: Mocking framework (already in pom.xml)
- **Maven Surefire**: Test runner (already in pom.xml)

### Test Data Strategy
1. **Synthetic Test Data**: Create minimal QFX files for unit tests
2. **Realistic Test Data**: Use real (redacted) QFX files for integration tests
3. **Edge Cases**: Create QFX files with unusual scenarios

### Running Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=BarclayansBankTest

# Run with coverage report (if jacoco added)
mvn clean test jacoco:report
```

---

## Acceptance Criteria

### Must Have (Phase 1)
- [ ] Can import QFX file from Barclays Aviator Mastercard
- [ ] All transaction fields correctly parsed (date, amount, merchant)
- [ ] Duplicate transactions detected and skipped
- [ ] Register balance updates correctly
- [ ] Transactions appear in application UI
- [ ] All unit tests passing
- [ ] All integration tests passing

### Should Have (Phase 2)
- [ ] Merchant auto-identification works for common merchants
- [ ] Budget item auto-matching works for recurring purchases
- [ ] Forecast transaction matching works
- [ ] User can manually assign merchants for unknown payees
- [ ] User can manually assign budget items
- [ ] Import log created with import summary

### Nice to Have (Phase 6 - Future Enhancement)
- [ ] Automatic transaction downloads via Plaid API (~10-13 hours additional work)
- [ ] Support for provisional/pending transactions (if Barclays provides them separately)
- [ ] Support for other Barclays account types (checking, savings)
- [ ] Credit card rewards tracking dashboard
- [ ] Payment due date tracking and alerts
- [ ] Transaction categorization suggestions using machine learning

---

## Appendices

### Appendix A: QFX/OFX Resources

**OFX Format Documentation**:
- [OFX Specification](https://www.ofx.net/downloads.html)
- [OFX Tag Reference](https://www.ofx.net/downloads/OFX%202.2.pdf)
- [ofx4j Library Documentation](https://github.com/stoicflame/ofx4j)

**Financial Aggregation APIs** (for future automatic downloads):
- [Plaid Developer Portal](https://plaid.com/docs/) - Recommended for US banks
- [Yodlee Documentation](https://developer.yodlee.com/)
- [MX Platform](https://www.mx.com/products/platform/)
- [Finicity by Mastercard](https://www.finicity.com/)

**Barclays-Specific**:
- [Barclays UK Open Banking API](https://developer.barclays.com/) - UK only, not applicable for US accounts
- Barclays US does not provide a public API for transaction downloads

### Appendix B: Estimated Timeline

| Phase | Estimated Time | Cumulative |
|-------|---------------|------------|
| Phase 0: Setup | 1-2 hours | 2 hours |
| Phase 1: QFX Parser Wrapper (TDD) | 3-4 hours | 6 hours |
| Phase 2: Barclays Bank (TDD) | 5-7 hours | 13 hours |
| Phase 3: Import Integration | 3-4 hours | 17 hours |
| Phase 4: E2E Testing | 2-3 hours | 20 hours |
| Phase 5: Documentation | 1-2 hours | 22 hours |
| **Total** | **15-22 hours** | **~2-4 days** |

*Note: Timeline assumes focused work sessions and no major blockers. Time reduced from original estimate due to using ofx4j library instead of manual SGML parsing.*

### Appendix C: TDD Learning Resources
- [Test Driven Development by Kent Beck](https://www.amazon.com/Test-Driven-Development-Kent-Beck/dp/0321146530)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [TDD Best Practices](https://martinfowler.com/bliki/TestDrivenDevelopment.html)

### Appendix D: Decision Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2025-12-15 | Use QFX format instead of CSV | Barclays supports QFX download (Quicken 2010+ format) |
| 2025-12-15 | Use ofx4j library for parsing | QFX is SGML-based (OFX 1.02), not XML - complex to parse manually. ofx4j handles both SGML and XML formats professionally |
| 2025-12-15 | Follow TDD for entire implementation | Learning goal + better code quality |
| 2025-12-15 | Reuse Wells Fargo merchant parsing logic | Barclays NAME tag format very similar to Wells Fargo CSV payee format |
| 2025-12-15 | Use FITID as import record ID base | FITID is unique identifier provided by Barclays in QFX |
| 2025-12-15 | No provisional transaction handling needed | Barclays QFX only contains posted/cleared transactions |
| 2025-12-15 | Track all transaction types | QFX contains purchases, payments, fees, interest, rewards |
| 2025-12-15 | Include register creation in plan | Register for Aviator Mastercard doesn't exist yet |
| 2025-12-15 | Store last 4 digits of account (2925) | Full account number masked, only last 4 available |
| 2025-12-16 | Start with manual QFX imports | Simplest, zero cost, no dependencies. Evaluate automatic downloads after 1-2 months of use |
| 2025-12-16 | Reserve Plaid integration for Phase 6 | Optional future enhancement (~10-13 hours, ~$5/year). Allows proving manual workflow first |

### Appendix E: Risks & Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| QFX format varies from spec | High | Medium | Test with real files early, build flexible parser |
| No sample QFX file available | High | Low | Create synthetic test data from OFX spec |
| Barclays merchant names too varied | Medium | Medium | Build robust fuzzy matching, allow manual override |
| Database schema changes needed | Medium | Low | Review schema early in Phase 0 |
| QFX parsing performance issues | Low | Low | Optimize only if proven necessary |

### Appendix F: Collaboration Notes

**How to Use This Plan**:
1. Start with Phase 0 - answer all questions
2. Work through phases sequentially
3. Update status checkboxes as you progress
4. Add notes and observations in each phase
5. Update Decision Log as decisions are made
6. Mark completion dates when phases finish

**Communication**:
- Update plan after each work session
- Note any blockers or questions
- Document decisions and rationale
- Add "Lessons Learned" at the end

---

## Next Steps

1. ~~**Answer all questions in the Prerequisites section**~~ ✅ COMPLETE
2. ~~**Obtain sample QFX file from Barclays**~~ ✅ COMPLETE (qdl20251215.qfx)
3. ~~**Review and approve this plan**~~ - **READY FOR YOUR APPROVAL**
4. **Begin Phase 0: Setup & Preparation** - **READY TO START**

---

*This plan is a living document. Update it as you progress through implementation.*

**Plan Status**: 🟢 READY TO BEGIN  
**Prerequisites**: ✅ ALL RESOLVED  
**Current Phase**: Phase 0 (Setup & Preparation)  
**Last Updated**: December 15, 2025

---

## Summary of Key Findings

### QFX File Analysis
- **Format**: SGML-based OFX 1.02 (NOT XML)
- **Account**: Barclays Aviator Mastercard (last 4: 2925)
- **Transactions**: 230+ transactions year-to-date
- **Transaction Types**: Purchases, payments, fees, interest, rewards all present
- **Merchant Format**: Very similar to Wells Fargo CSV format

### Implementation Approach
- **Parser**: Use ofx4j library (handles SGML OFX 1.x and XML OFX 2.x)
- **Merchant Parsing**: Reuse Wells Fargo tokenization and cleaning logic
- **Import ID**: Use FITID as base for import record ID
- **Provisional Transactions**: Not applicable (QFX only has posted transactions)
- **Testing**: Full TDD approach with JUnit 5 and Mockito

### Estimated Effort
- **Total Time**: 15-22 hours (~2-4 days of focused work)
- **Complexity**: Medium (leveraging existing patterns and library)
- **Risk Level**: Low (clear requirements, sample data available)


