package com.hixon.financialApp.model.qfx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test-Driven Development tests for QfxParser.
 *
 * This test class follows TDD principles:
 * 1. Write a failing test
 * 2. Write minimal code to pass the test
 * 3. Refactor
 *
 * Tests are written BEFORE implementation.
 */
@DisplayName("QFX Parser Tests")
class QfxParserTest {

    private QfxParser parser;

    @BeforeEach
    void setUp() {
        parser = new QfxParser();
    }

    // ========================================
    // Phase 1: Basic Parsing Tests
    // ========================================

    @Test
    @DisplayName("Test 1: Parse valid QFX file returns non-null QfxStatement")
    void testParseValidQfxFile_ReturnsNonNull() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-purchase.qfx");
        assertNotNull(input, "Test file should exist");

        // Act
        QfxStatement statement = parser.parse(input);

        // Assert
        assertNotNull(statement, "Parsed statement should not be null");
    }

    @Test
    @DisplayName("Test 2: Parse single purchase transaction - verify transaction count")
    void testParseSinglePurchase_VerifyTransactionCount() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-purchase.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        List<QfxTransaction> transactions = statement.getTransactions();

        // Assert
        assertNotNull(transactions, "Transactions list should not be null");
        assertEquals(1, transactions.size(), "Should have exactly 1 transaction");
    }

    @Test
    @DisplayName("Test 3: Parse single purchase - verify transaction type is DEBIT")
    void testParseSinglePurchase_VerifyType() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-purchase.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertEquals(TransactionType.DEBIT, transaction.getType(),
                "Purchase should be DEBIT type");
    }

    @Test
    @DisplayName("Test 4: Parse single purchase - verify amount")
    void testParseSinglePurchase_VerifyAmount() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-purchase.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertEquals(-28.20, transaction.getAmount(), 0.01,
                "Purchase amount should be -28.20");
    }

    @Test
    @DisplayName("Test 5: Parse single purchase - verify posted date")
    void testParseSinglePurchase_VerifyPostedDate() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-purchase.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertEquals(LocalDate.of(2025, 12, 10), transaction.getPostedDate(),
                "Posted date should be 2025-12-10");
    }

    @Test
    @DisplayName("Test 6: Parse single purchase - verify payee name")
    void testParseSinglePurchase_VerifyPayee() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-purchase.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertEquals("NETFLIX.COM", transaction.getName(),
                "Payee name should be NETFLIX.COM");
    }

    @Test
    @DisplayName("Test 7: Parse single purchase - verify FITID")
    void testParseSinglePurchase_VerifyFitId() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-purchase.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertEquals("554328650712053126673293001", transaction.getFitId(),
                "FITID should match");
    }

    // ========================================
    // Phase 2: Payment Transaction Tests
    // ========================================

    @Test
    @DisplayName("Test 8: Parse single payment - verify type is CREDIT")
    void testParseSinglePayment_VerifyType() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-payment.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertEquals(TransactionType.CREDIT, transaction.getType(),
                "Payment should be CREDIT type");
    }

    @Test
    @DisplayName("Test 9: Parse single payment - verify positive amount")
    void testParseSinglePayment_VerifyPositiveAmount() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-payment.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertEquals(2219.00, transaction.getAmount(), 0.01,
                "Payment amount should be positive 2219.00");
    }

    @Test
    @DisplayName("Test 10: Parse single payment - verify payee")
    void testParseSinglePayment_VerifyPayee() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-payment.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertEquals("PAYMENT RECV'D CHECKFREE", transaction.getName(),
                "Payment payee should match");
    }

    // ========================================
    // Phase 3: Account Information Tests
    // ========================================

    @Test
    @DisplayName("Test 11: Parse statement - verify account number")
    void testParseStatement_VerifyAccountNumber() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-purchase.qfx");

        // Act
        QfxStatement statement = parser.parse(input);

        // Assert
        assertEquals("XXXXXXXXXXXX2925", statement.getAccountNumber(),
                "Account number should match");
    }

    @Test
    @DisplayName("Test 12: Parse statement - verify currency")
    void testParseStatement_VerifyCurrency() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-purchase.qfx");

        // Act
        QfxStatement statement = parser.parse(input);

        // Assert
        assertEquals("USD", statement.getCurrency(),
                "Currency should be USD");
    }

    @Test
    @DisplayName("Test 13: Parse statement - verify ledger balance")
    void testParseStatement_VerifyLedgerBalance() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-single-purchase.qfx");

        // Act
        QfxStatement statement = parser.parse(input);

        // Assert
        assertEquals(-28.20, statement.getLedgerBalance(), 0.01,
                "Ledger balance should match");
    }

    // ========================================
    // Phase 4: Edge Cases and Error Handling
    // ========================================

    @Test
    @DisplayName("Test 14: Parse null input throws exception")
    void testParseNullInput_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            parser.parse(null);
        }, "Parsing null input should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Test 15: Parse empty file throws exception")
    void testParseEmptyFile_ThrowsException(@TempDir Path tempDir) throws Exception {
        // Arrange
        File emptyFile = tempDir.resolve("empty.qfx").toFile();
        emptyFile.createNewFile();

        // Act & Assert
        assertThrows(QfxParseException.class, () -> {
            try (FileInputStream fis = new FileInputStream(emptyFile)) {
                parser.parse(fis);
            }
        }, "Parsing empty file should throw QfxParseException");
    }

    @Test
    @DisplayName("Test 16: Parse malformed QFX throws exception")
    void testParseMalformedQfx_ThrowsException(@TempDir Path tempDir) throws Exception {
        // Arrange
        File malformedFile = tempDir.resolve("malformed.qfx").toFile();
        java.nio.file.Files.writeString(malformedFile.toPath(), "This is not valid QFX data");

        // Act & Assert
        assertThrows(QfxParseException.class, () -> {
            try (FileInputStream fis = new FileInputStream(malformedFile)) {
                parser.parse(fis);
            }
        }, "Parsing malformed QFX should throw QfxParseException");
    }

    // ========================================
    // Phase 5: All Transaction Types
    // ========================================

    @Test
    @DisplayName("Test 17: Parse annual fee transaction")
    void testParseAnnualFee() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-annual-fee.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertAll("Annual fee transaction",
            () -> assertEquals(TransactionType.DEBIT, transaction.getType()),
            () -> assertEquals(-99.00, transaction.getAmount(), 0.01),
            () -> assertEquals("PRIMARY ANNUAL FEE", transaction.getName())
        );
    }

    @Test
    @DisplayName("Test 18: Parse interest charge transaction")
    void testParseInterestCharge() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-interest-charge.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertAll("Interest charge transaction",
            () -> assertEquals(TransactionType.DEBIT, transaction.getType()),
            () -> assertEquals(-237.23, transaction.getAmount(), 0.01),
            () -> assertEquals("INTEREST CHARGE-PURCHASES", transaction.getName())
        );
    }

    @Test
    @DisplayName("Test 19: Parse reward credit transaction")
    void testParseRewardCredit() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-reward-credit.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertAll("Reward credit transaction",
            () -> assertEquals(TransactionType.CREDIT, transaction.getType()),
            () -> assertEquals(5.00, transaction.getAmount(), 0.01),
            () -> assertEquals("AA 25% INFLIGHT CREDIT", transaction.getName())
        );
    }

    @Test
    @DisplayName("Test 20: Parse transaction with user date")
    void testParseTransaction_WithUserDate() throws Exception {
        // Arrange
        InputStream input = getClass().getResourceAsStream("/qfx/test-reward-credit.qfx");

        // Act
        QfxStatement statement = parser.parse(input);
        QfxTransaction transaction = statement.getTransactions().get(0);

        // Assert
        assertNotNull(transaction.getUserDate(), "User date should not be null");
        assertEquals(LocalDate.of(2025, 10, 4), transaction.getUserDate(),
                "User date should be 2025-10-04");
    }
}


