# Automated Transaction Download – Design & Implementation Plan

## Table of Contents
1. [Tool Selection](#1-tool-selection)
2. [Architecture Overview](#2-architecture-overview)
3. [Database Schema Changes](#3-database-schema-changes)
4. [New & Modified Classes](#4-new--modified-classes)
5. [Plaid Link Flow (Account Connection)](#5-plaid-link-flow-account-connection)
6. [Automated Download Flow](#6-automated-download-flow)
7. [Security Considerations](#7-security-considerations)
8. [Implementation Phases](#8-implementation-phases)
9. [Maven Dependency](#9-maven-dependency)
10. [Testing Strategy](#10-testing-strategy)
11. [Known Trade-offs & Limitations](#11-known-trade-offs--limitations)

---

## 1. Tool Selection

### Plaid (recommended)

| Criterion | Plaid | Yodlee | Finicity (Mastercard) | MX |
|---|---|---|---|---|
| US bank coverage | 12,000+ institutions | 17,000+ | 10,000+ | 16,000+ |
| Java SDK | ✅ Official | ❌ REST only | ❌ REST only | ❌ REST only |
| Free developer tier | ✅ Up to 100 Items | ❌ Paid only | ❌ Paid only | ❌ Paid only |
| Transaction history depth | 24 months | 24 months | 24 months | 24 months |
| Real-time webhooks | ✅ | ✅ | ✅ | ✅ |
| Personal-use licensing | ✅ | ❌ Enterprise | ❌ Enterprise | ❌ Enterprise |
| OFX/QFX compatibility | N/A – JSON API | N/A | N/A | N/A |

**Decision: Plaid** is the right choice. It has an official Java SDK (`plaid-java`), a free developer sandbox, and is purpose-built for personal-finance applications at this scale. Yodlee/Finicity/MX are enterprise products with no free tier and no official Java client.

---

## 2. Architecture Overview

The integration must fit the existing MVC architecture without the controller layer directly interacting with the user. The diagram below shows where the new components plug in.

```
┌─────────────────────────────────────────────────────────────────┐
│  VIEW LAYER (ViewInt implementations)                           │
│  CmdLineView  |  ExcelView  |  WebView (future)                 │
│                                                                 │
│  NEW: PlaidLinkView – renders Plaid Link URL or launches        │
│       browser for OAuth flow                                    │
└────────────────────────┬────────────────────────────────────────┘
                         │ calls view methods only
┌────────────────────────▼────────────────────────────────────────┐
│  CONTROLLER LAYER                                               │
│                                                                 │
│  NEW: PlaidController  – manages item (account) connections,    │
│       token exchange, and scheduled/manual sync triggers        │
│                                                                 │
│  MODIFIED: ImportController – adds PlaidImportStrategy as a     │
│       third import path alongside QFX and CSV                   │
│                                                                 │
│  MODIFIED: FinancialInstitutionFactory – adds "Plaid" case      │
└────────────────────────┬────────────────────────────────────────┘
                         │ calls model methods only
┌────────────────────────▼────────────────────────────────────────┐
│  MODEL LAYER                                                    │
│                                                                 │
│  NEW: PlaidService         – wraps plaid-java API calls         │
│  NEW: PlaidBank            – FinancialInstitutionInt for Plaid  │
│  NEW: PlaidItemEntity      – persisted Plaid item/access token  │
│  NEW: PlaidAccountEntity   – maps Plaid account_id → Register   │
│                                                                 │
│  MODIFIED: Register        – adds plaidAccountId field          │
└────────────────────────┬────────────────────────────────────────┘
                         │
              ┌──────────▼──────────┐
              │  MySQL Database      │
              │  (new tables below)  │
              └─────────────────────┘
```

### Key Design Principles Preserved
- **Controllers never interact with the user directly.** All prompts (link URL display, confirmation) go through `ViewInt`.
- **`FinancialInstitutionInt` iterator contract is unchanged.** `PlaidBank.hasNext()` / `PlaidBank.next()` provide the same interface as `BarclaysBank` and `WellsFargoBank`. `ImportController` requires zero logic changes to process Plaid transactions.
- **`FinancialInstitutionFactory` remains the single creation point.** The factory gains one new `case "plaid"` branch.

---

## 3. Database Schema Changes

### 3.1 New Table: `plaid_item`

Stores one row per connected financial institution (a "Plaid Item" = one login at one bank).

```sql
CREATE TABLE plaid_item (
    idPlaidItem       BINARY(16)   NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    accessToken       VARCHAR(512) NOT NULL,          -- AES-256 encrypted at rest
    itemId            VARCHAR(100) NOT NULL UNIQUE,   -- Plaid's item_id
    institutionId     VARCHAR(50)  NOT NULL,          -- Plaid's institution_id  (e.g. "ins_3")
    institutionName   VARCHAR(200) NOT NULL,
    consentExpiration DATETIME     NULL,              -- NULL = no expiry
    lastSyncCursor    VARCHAR(512) NULL,              -- cursor for /transactions/sync
    lastSyncDate      DATETIME     NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'active',  -- active | error | revoked
    createdAt         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (idPlaidItem)
);
```

### 3.2 New Table: `plaid_account`

Maps each Plaid account (checking, savings, credit) to an existing `Register`.

```sql
CREATE TABLE plaid_account (
    idPlaidAccount  BINARY(16)   NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    plaidItem       BINARY(16)   NOT NULL,
    accountId       VARCHAR(100) NOT NULL UNIQUE,  -- Plaid's account_id
    Register_idRegister BINARY(16) NOT NULL,
    accountName     VARCHAR(200) NOT NULL,
    accountType     VARCHAR(50)  NOT NULL,          -- checking, savings, credit, etc.
    accountMask     VARCHAR(10)  NULL,              -- last 4 digits
    PRIMARY KEY (idPlaidAccount),
    FOREIGN KEY (plaidItem)             REFERENCES plaid_item(idPlaidItem),
    FOREIGN KEY (Register_idRegister)   REFERENCES Register(idRegister)
);
```

### 3.3 Modified Table: `Register`

Add one nullable column to identify when a register is Plaid-linked.

```sql
ALTER TABLE Register
    ADD COLUMN plaidAccountId VARCHAR(100) NULL
        COMMENT 'Plaid account_id if this register is linked via Plaid; NULL otherwise';
```

### 3.4 New Table: `plaid_webhook_event` (optional, Phase 3)

Audit log of incoming Plaid webhook events for replay/debugging.

```sql
CREATE TABLE plaid_webhook_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    webhookType     VARCHAR(50)  NOT NULL,
    webhookCode     VARCHAR(100) NOT NULL,
    itemId          VARCHAR(100) NOT NULL,
    payload         JSON         NOT NULL,
    processedAt     DATETIME     NULL,
    createdAt       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
```

---

## 4. New & Modified Classes

### 4.1 `PlaidService` (new – model layer)
**Package:** `com.hixon.financialApp.model.plaid`

Thin wrapper around the official `plaid-java` SDK. All raw Plaid API calls live here; nothing else in the codebase touches the SDK directly.

```
PlaidService
  + createLinkToken(userId: String) : String
  + exchangePublicToken(publicToken: String) : PlaidItemCredentials
  + syncTransactions(accessToken: String, cursor: String) : PlaidSyncResult
  + getAccounts(accessToken: String) : List<PlaidAccountInfo>
  + revokeItem(accessToken: String) : void
```

`PlaidItemCredentials` is a simple record:
```java
record PlaidItemCredentials(String accessToken, String itemId) {}
```

`PlaidSyncResult` is a simple record:
```java
record PlaidSyncResult(
    List<PlaidTransactionData> added,
    List<PlaidTransactionData> modified,
    List<String>               removed,   // transaction_ids
    String                     nextCursor,
    boolean                    hasMore
) {}
```

`PlaidTransactionData` mirrors the fields from Plaid's `Transaction` object that the app cares about:
- `transactionId`, `accountId`, `date`, `authorizedDate`, `amount`, `name`, `merchantName`, `pending`, `pendingTransactionId`

### 4.2 `PlaidItemEntity` (new – model layer)
**Package:** `com.hixon.financialApp.model.plaid`

Extends `IndependentEntity`. Handles persistence to `plaid_item`.

```
PlaidItemEntity
  - accessToken : String         (decrypted in memory, stored encrypted)
  - itemId : String
  - institutionId : String
  - institutionName : String
  - lastSyncCursor : String
  - lastSyncDate : Calendar
  - status : String
  + save() : void
  + static getById(uuid) : PlaidItemEntity
  + static getByItemId(itemId) : PlaidItemEntity
  + static getAll() : List<PlaidItemEntity>
```

### 4.3 `PlaidAccountEntity` (new – model layer)
**Package:** `com.hixon.financialApp.model.plaid`

Maps Plaid accounts to existing `Register` rows.

```
PlaidAccountEntity
  - accountId : String            (Plaid's account_id)
  - plaidItemId : UUID
  - registerId : UUID
  - accountName : String
  - accountType : String
  - accountMask : String
  + save() : void
  + static getByAccountId(accountId) : PlaidAccountEntity
  + static getByRegisterId(registerId) : PlaidAccountEntity
```

### 4.4 `PlaidBank` (new – model layer, financial institution)
**Package:** `com.hixon.financialApp.model.financialinstitution`

Extends `FinancialInstitution`, implements `FinancialInstitutionInt`. Instead of reading a file, it calls `PlaidService.syncTransactions()` to populate the internal transaction list.

```
PlaidBank extends FinancialInstitution
  - plaidService : PlaidService
  - plaidItem : PlaidItemEntity
  
  + importRegisterTrxFile() throws Exception
      // Calls PlaidService.syncTransactions() using stored cursor.
      // Converts PlaidTransactionData → Transaction objects.
      // Updates lastSyncCursor and lastSyncDate on PlaidItemEntity.
      
  + createFromCSVRecord(...)  // not used; throws UnsupportedOperationException
  + parseMerchantPayee(...)   // uses merchantName from Plaid if available
  + getImportedLedgerBalance(): Double   // from Plaid account balance
```

Because `PlaidBank` implements the same `FinancialInstitutionInt` iterator interface, the **existing `ImportController` loop works with zero changes**:

```java
// ImportController.java – this existing loop already works for Plaid:
FinancialInstitutionInt institution = sessionController.getFinancialInstitution();
institution.importRegisterTrxFile();   // ← calls Plaid API for PlaidBank
while (institution.hasNext()) {
    Transaction t = institution.next();
    // ... existing processing logic
}
```

### 4.5 `PlaidController` (new – controller layer)
**Package:** `com.hixon.financialApp.controller`

Manages all Plaid item lifecycle operations. Never touches the user directly; delegates all I/O to `ViewInt`.

```
PlaidController
  - sessionController : SessionController
  - plaidService : PlaidService
  
  + connectNewAccount() throws Exception
      // 1. Call PlaidService.createLinkToken()
      // 2. Call view.displayPlaidLinkUrl(linkToken) to show user the URL
      // 3. Receive public token via view.getPlaidPublicToken()
      // 4. Call PlaidService.exchangePublicToken()
      // 5. Fetch accounts, let user map each to a Register
      // 6. Persist PlaidItemEntity and PlaidAccountEntity rows
      
  + syncAllAccounts() throws Exception
      // Iterates all active PlaidItemEntity rows, calls ImportController for each
      
  + listConnectedAccounts() : List<PlaidAccountEntity>
  + disconnectAccount(plaidItemId: UUID) throws Exception
```

### 4.6 `ViewInt` additions (new methods on existing interface)

```java
// In ViewInt.java – new methods for Plaid Link flow:

/**
 * Displays a Plaid Link URL and instructs the user to open it to authorize
 * their bank account. For CLI: prints the URL. For web: redirects.
 */
void displayPlaidLinkUrl(String linkUrl);

/**
 * Prompts the user to paste back the public_token returned by Plaid Link
 * after they have completed the authorization flow.
 * For CLI: reads from stdin. For web: receives via callback URL.
 */
String getPlaidPublicToken() throws CancelException, QuitException;
```

### 4.7 `FinancialInstitutionFactory` modification

Add one case to the existing switch:

```java
case "plaid" -> new PlaidBank(sessionController);
```

### 4.8 `Register` modification

Add `plaidAccountId` field with `@Getter @Setter`, and include it in `save()` / `load()` SQL.

---

## 5. Plaid Link Flow (Account Connection)

Plaid uses OAuth-style "Link" to get user consent. The flow differs slightly by view implementation but the controller logic is identical.

```
PlaidController.connectNewAccount()
        │
        ▼
PlaidService.createLinkToken(userId)
        │  returns: link_token
        ▼
view.displayPlaidLinkUrl(linkUrl)
        │  CLI: "Please open this URL: https://link.plaid.com/?token=..."
        │  Web: redirect browser to Plaid Hosted Link
        ▼
[User authorizes at Plaid Link — outside the app]
        │
        ▼
view.getPlaidPublicToken()
        │  CLI: "Paste the token Plaid gave you:"
        │  Web: Plaid redirects to /plaid/callback?public_token=...
        ▼
PlaidService.exchangePublicToken(publicToken)
        │  returns: PlaidItemCredentials(accessToken, itemId)
        ▼
PlaidService.getAccounts(accessToken)
        │  returns: List<PlaidAccountInfo>
        ▼
For each Plaid account:
  view.selectByNameFromList("Map Plaid account to Register", registers, ...)
        │
        ▼
  Persist PlaidItemEntity (encrypted accessToken)
  Persist PlaidAccountEntity (accountId → Register UUID)
  Register.plaidAccountId = accountId
  Register.financialInstitution = "Plaid"
  Register.save()
```

---

## 6. Automated Download Flow

Once accounts are connected, the download flow reuses the existing `ImportController` machinery:

```
PlaidController.syncAllAccounts()
  for each PlaidItemEntity (status == "active"):
    │
    ├── sessionController.setRegister(register for this item's first account)
    │
    ├── ImportController.importFromExternalSource()
    │       │
    │       └── PlaidBank.importRegisterTrxFile()
    │               │
    │               ├── PlaidService.syncTransactions(accessToken, cursor)
    │               │       Uses /transactions/sync endpoint (cursor-based, no gaps)
    │               │
    │               ├── Convert added transactions → Transaction objects
    │               ├── Handle modified transactions (update existing DB rows)
    │               ├── Handle removed transactions (soft-delete in DB)
    │               └── Update lastSyncCursor, lastSyncDate
    │
    └── ImportController processes each Transaction through the existing
        merchant ID / budget assignment / forecast reconciliation pipeline
```

### Handling "pending" Plaid Transactions

Plaid provides pending (provisional) transactions before they clear. These map directly to the existing "provisional transaction" concept in the app:

| Plaid field | App concept | Handling |
|---|---|---|
| `pending = true` | Provisional transaction | Import as `cleared = false` |
| `pending = false` | Cleared transaction | Import as `cleared = true`; look for matching provisional via `pendingTransactionId` |
| `pendingTransactionId` | Match key | Use in `getMatchingProvisionalTransaction()` to replace provisional |

`PlaidBank.createFromPlaidTransaction()` will set `importRecordId = plaidTransactionId` to enable duplicate detection via the existing `INSERT ON DUPLICATE SKIP` logic.

---

## 7. Security Considerations

### 7.1 Access Token Encryption

Plaid access tokens grant ongoing bank access and **must** be encrypted at rest.

- Use **AES-256-GCM** via `javax.crypto`.
- Store encryption key in an environment variable (`PLAID_TOKEN_KEY`) or a local keystore file outside the project directory. Never in source control.
- `PlaidItemEntity.save()` encrypts before writing; `PlaidItemEntity.load()` decrypts after reading.
- Provide a `TokenEncryptionService` class so the encryption algorithm is isolated and testable.

```
TokenEncryptionService
  + encrypt(plaintext: String, key: byte[]) : String   (Base64-encoded ciphertext + IV)
  + decrypt(ciphertext: String, key: byte[]) : String
```

### 7.2 Plaid API Keys

- Store `PLAID_CLIENT_ID` and `PLAID_SECRET` in environment variables, not in `application.properties` or any committed file.
- `PlaidService` reads them via `System.getenv()`.
- Use `PLAID_ENV = sandbox` for development, `development` for testing against real banks (limited to 100 items), `production` when ready.

### 7.3 Token Revocation

- `PlaidController.disconnectAccount()` calls `PlaidService.revokeItem()` which hits Plaid's `/item/remove` endpoint before deleting the local `plaid_item` row.
- This ensures the bank also revokes access, not just the local record.

### 7.4 HTTPS

- Plaid webhooks (Phase 3) must be received on an HTTPS endpoint. The embedded web server (when built) must use TLS.

---

## 8. Implementation Phases

### Phase 1 – Foundation (minimum viable)
**Goal:** Manual sync via command line; no webhooks.

| # | Task | Files |
|---|---|---|
| 1.1 | Add `plaid-java` Maven dependency | `pom.xml` |
| 1.2 | Create `plaid_item` and `plaid_account` SQL migration | new SQL file |
| 1.3 | `ALTER TABLE Register ADD plaidAccountId` migration | new SQL file |
| 1.4 | `TokenEncryptionService` | new class |
| 1.5 | `PlaidService` wrapper | new class |
| 1.6 | `PlaidItemEntity` and `PlaidAccountEntity` model classes | new classes |
| 1.7 | `PlaidBank` implementing `FinancialInstitutionInt` | new class |
| 1.8 | `FinancialInstitutionFactory` – add Plaid case | modify existing |
| 1.9 | `ViewInt` – add `displayPlaidLinkUrl()` and `getPlaidPublicToken()` | modify existing |
| 1.10 | `CmdLineView` – implement the two new ViewInt methods | modify existing |
| 1.11 | `PlaidController` – `connectNewAccount()` and `syncAllAccounts()` | new class |
| 1.12 | Wire `PlaidController` into `MainController` menu | modify existing |
| 1.13 | Unit tests for `PlaidService`, `TokenEncryptionService`, `PlaidBank` | new tests |

### Phase 2 – Polish & Robustness
**Goal:** Production-ready sync, error recovery, scheduling.

| # | Task |
|---|---|
| 2.1 | Error handling: Plaid `ITEM_LOGIN_REQUIRED` → notify user to re-authenticate |
| 2.2 | Re-authentication flow (update access token without disconnecting accounts) |
| 2.3 | Rate limit handling with exponential backoff in `PlaidService` |
| 2.4 | Scheduled sync: add a background thread / cron job that calls `PlaidController.syncAllAccounts()` at a configurable interval (e.g., every 6 hours) |
| 2.5 | `Register.lastImportDate` updated after successful Plaid sync |
| 2.6 | Excel/SpreadsheetXml view implementations for `displayPlaidLinkUrl()` |
| 2.7 | Integration tests using Plaid Sandbox test credentials |

### Phase 3 – Webhooks (future)
**Goal:** Real-time push notifications from Plaid instead of polling.

| # | Task |
|---|---|
| 3.1 | Embedded HTTP server (e.g., Undertow or Sun's `com.sun.net.httpserver`) to receive webhooks |
| 3.2 | Webhook signature verification (Plaid signs payloads with JWT) |
| 3.3 | `plaid_webhook_event` table for audit/replay |
| 3.4 | Process `TRANSACTIONS_SYNC` webhook events asynchronously via existing `NotificationServiceInt` |

---

## 9. Maven Dependency

Add to `pom.xml` inside `<dependencies>`:

```xml
<!-- Plaid Java SDK -->
<dependency>
    <groupId>com.plaid</groupId>
    <artifactId>plaid-java</artifactId>
    <version>28.2.0</version>
</dependency>

<!-- Retrofit (required by plaid-java) -->
<dependency>
    <groupId>com.squareup.retrofit2</groupId>
    <artifactId>retrofit</artifactId>
    <version>2.11.0</version>
</dependency>
<dependency>
    <groupId>com.squareup.retrofit2</groupId>
    <artifactId>converter-gson</artifactId>
    <version>2.11.0</version>
</dependency>
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>logging-interceptor</artifactId>
    <version>4.12.0</version>
</dependency>

<!-- Gson (required by plaid-java) -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.11.0</version>
</dependency>
```

> **Note:** Verify the latest `plaid-java` version at https://github.com/plaid/plaid-java before starting implementation. As of June 2026, `28.x` tracks Plaid's 2024-02-15 API version.

---

## 10. Testing Strategy

### Unit Tests

| Class under test | Test class | Key scenarios |
|---|---|---|
| `TokenEncryptionService` | `TokenEncryptionServiceTest` | Encrypt/decrypt roundtrip; wrong key throws |
| `PlaidService` | `PlaidServiceTest` | Mock Retrofit client; verify request bodies; verify cursor is passed |
| `PlaidBank` | `PlaidBankTest` | Uses mock `PlaidService`; verify iterator returns correct `Transaction` objects; pending → `cleared=false`; cleared with `pendingTransactionId` matches provisional |
| `PlaidController` | `PlaidControllerTest` | Mock `ViewInt` and `PlaidService`; verify `connectNewAccount()` calls view methods in correct order |
| `PlaidItemEntity` | `PlaidItemEntityTest` | Save/load roundtrip with in-memory mock DB (or H2) |

### Integration Tests (Plaid Sandbox)

- Use Plaid's Sandbox environment with test credentials (`user_good` / `pass_good`).
- `PlaidIntegrationTest` – connects a sandbox account, performs a sync, verifies transactions are returned.
- Add to a separate Maven profile (`-P plaid-integration`) so they don't run on every `mvn test`.

---

## 11. Known Trade-offs & Limitations

| Area | Trade-off / Limitation |
|---|---|
| **Plaid free tier** | Developer tier allows up to 100 Items (bank logins). For a personal app this is more than enough. Production pricing is per Item per month. |
| **Plaid Link for CLI** | Plaid Link is a web UI. For the CLI view, the user must open a URL in their browser and paste back the token. This is less seamless than the web experience but fully functional. |
| **Transaction history on first sync** | Plaid's `/transactions/sync` returns up to 24 months of history on the first call. This may result in a large initial import that the user will need to categorize. Consider adding a configurable "import from date" filter. |
| **Coverage gaps** | A small number of institutions (credit unions, smaller banks) may not be on Plaid. File-based imports (CSV/QFX) remain as a fallback. |
| **Plaid terms of service** | Plaid's developer TOS prohibits storing raw credentials. The app stores only the access token (which is what TOS requires), not username/password. |
| **No direct balance sync** | Plaid returns balances via the `/accounts/balance/get` endpoint (a separate call). This can be added to `PlaidBank.getImportedLedgerBalance()` to maintain the existing balance-verification feature. |
| **Scheduled sync reliability** | The in-process scheduler (Phase 2) will miss syncs when the app is not running. A true cron job or system service would be more robust for unattended operation. |

