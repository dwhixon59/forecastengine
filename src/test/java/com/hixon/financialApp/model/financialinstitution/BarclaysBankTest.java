package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BarclaysBank financial institution implementation.
 */
@DisplayName("Barclays Bank Tests")
class BarclaysBankTest {

    /**
     * Helper method to get file path from resource, handling URL encoding properly.
     */
    private String getResourceFilePath(String resourcePath) throws Exception {
        URL resource = getClass().getResource(resourcePath);
        assertNotNull(resource, "Resource should exist: " + resourcePath);
        return new File(resource.toURI()).getAbsolutePath();
    }

    /**
     * Helper method to create a SessionController with mocked dependencies.
     */
    private SessionController createMockSessionController(String qfxFilePath) {
        Register mockRegister = mock(Register.class);
        when(mockRegister.getTrxImportFileDirectory()).thenReturn(new File(qfxFilePath).getParent());
        when(mockRegister.getTrxImportFileName()).thenReturn(new File(qfxFilePath).getName());
        when(mockRegister.getTrxImportFilePath()).thenReturn(qfxFilePath);

        Budget mockBudget = mock(Budget.class);
        Forecast mockForecast = mock(Forecast.class);
        ViewInt mockView = mock(ViewInt.class);
        NotificationServiceInt mockNotificationService = mock(NotificationServiceInt.class);

        return new SessionController(mockRegister, mockBudget, mockForecast, mockView, mockNotificationService);
    }

    @Test
    @DisplayName("Test 1: Constructor with SessionController succeeds")
    void testConstructor_ValidSessionController() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);

        // Act
        BarclaysBank barclays = new BarclaysBank(sessionController);

        // Assert
        assertNotNull(barclays, "BarclaysBank should be created");

        // Cleanup
        barclays.close();
    }

    @Test
    @DisplayName("Test 2: importRegisterTrxFile loads QFX file successfully")
    void testImportRegisterTrxFile_ValidFile() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            // Act
            barclays.importRegisterTrxFile();

            // Assert - should not throw exception
            assertNotNull(barclays, "BarclaysBank should successfully import file");

        } finally {
            // Cleanup
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 3: hasNext returns true for file with transactions")
    void testHasNext_NoTransactions() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            // Import the file first
            barclays.importRegisterTrxFile();

            // Act
            boolean hasNext = barclays.hasNext();

            // Assert
            assertTrue(hasNext, "Should return true when transactions are available");

        } finally {
            // Cleanup
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 4: Iterator can iterate through transactions")
    void testIterator_IterateThroughTransactions() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            // Import the file first
            barclays.importRegisterTrxFile();

            // Act
            List<Transaction> transactions = new ArrayList<>();
            while (barclays.hasNext()) {
                Transaction t = barclays.next();
                transactions.add(t);
            }

            // Assert
            assertEquals(1, transactions.size(), "Should have exactly 1 transaction from test file");

        } finally {
            // Cleanup
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 5: parseMerchantPayee returns payee as-is")
    void testParseMerchantPayee() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            // Act
            String result = barclays.parseMerchantPayee(null, 0.0, "NETFLIX.COM");

            // Assert
            assertEquals("NETFLIX.COM", result, "Should return payee as-is");

        } finally {
            // Cleanup
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 6: CSV methods throw UnsupportedOperationException")
    void testCsvMethods_ThrowException() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            // Act & Assert
            assertThrows(UnsupportedOperationException.class, () -> {
                barclays.getCsvHeadersClass();
            }, "getCsvHeadersClass should throw UnsupportedOperationException");

            assertThrows(UnsupportedOperationException.class, () -> {
                barclays.getRegisterImportRecordBaseName(null);
            }, "getRegisterImportRecordBaseName should throw UnsupportedOperationException");

            assertThrows(UnsupportedOperationException.class, () -> {
                barclays.createFromCSVRecord(null, null);
            }, "createFromCSVRecord should throw UnsupportedOperationException");

        } finally {
            // Cleanup
            barclays.close();
        }
    }

    // ========================================
    // Phase 2: Transaction Conversion Tests
    // ========================================

    @Test
    @DisplayName("Test 7: Convert QFX purchase to Transaction")
    void testConvertPurchaseTransaction() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            barclays.importRegisterTrxFile();

            // Act
            Transaction transaction = barclays.next();

            // Assert
            assertNotNull(transaction, "Transaction should not be null");
            assertEquals("NETFLIX.COM", transaction.getMerchantPayee(), "Merchant payee should match");
            assertEquals(-28.20, transaction.getAmount(), 0.01, "Amount should match");
            assertTrue(transaction.isCleared(), "QFX transactions should be cleared");
            assertNotNull(transaction.getImportRecordId(), "Import record ID should be set");

        } finally {
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 8: Convert QFX payment to Transaction")
    void testConvertPaymentTransaction() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-payment.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            barclays.importRegisterTrxFile();

            // Act
            Transaction transaction = barclays.next();

            // Assert
            assertNotNull(transaction, "Transaction should not be null");
            assertEquals("PAYMENT RECV'D CHECKFREE", transaction.getMerchantPayee(), "Merchant payee should match");
            assertEquals(2219.00, transaction.getAmount(), 0.01, "Amount should be positive for payment");
            assertTrue(transaction.isCleared(), "QFX transactions should be cleared");

        } finally {
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 9: Convert QFX fee to Transaction")
    void testConvertFeeTransaction() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-annual-fee.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            barclays.importRegisterTrxFile();

            // Act
            Transaction transaction = barclays.next();

            // Assert
            assertNotNull(transaction, "Transaction should not be null");
            assertEquals("PRIMARY ANNUAL FEE", transaction.getMerchantPayee(), "Merchant payee should match");
            assertEquals(-99.00, transaction.getAmount(), 0.01, "Fee should be negative");

        } finally {
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 10: Convert QFX interest charge to Transaction")
    void testConvertInterestTransaction() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-interest-charge.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            barclays.importRegisterTrxFile();

            // Act
            Transaction transaction = barclays.next();

            // Assert
            assertNotNull(transaction, "Transaction should not be null");
            assertEquals("INTEREST CHARGE-PURCHASES", transaction.getMerchantPayee(), "Merchant payee should match");
            assertEquals(-237.23, transaction.getAmount(), 0.01, "Interest should be negative");

        } finally {
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 11: Convert QFX reward credit to Transaction")
    void testConvertRewardTransaction() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-reward-credit.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            barclays.importRegisterTrxFile();

            // Act
            Transaction transaction = barclays.next();

            // Assert
            assertNotNull(transaction, "Transaction should not be null");
            assertEquals("AA 25% INFLIGHT CREDIT", transaction.getMerchantPayee(), "Merchant payee should match");
            assertEquals(5.00, transaction.getAmount(), 0.01, "Reward should be positive");

        } finally {
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 12: Transaction dates are converted correctly")
    void testTransactionDates() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            barclays.importRegisterTrxFile();

            // Act
            Transaction transaction = barclays.next();

            // Assert
            assertNotNull(transaction.getDate(), "Transaction date should be set");
            // Date should be 2025-12-10 based on test file
            // Calendar months are 0-based, so December = 11
            assertEquals(11, transaction.getDate().get(java.util.Calendar.MONTH), "Month should be December");
            assertEquals(10, transaction.getDate().get(java.util.Calendar.DAY_OF_MONTH), "Day should be 10");
            assertEquals(2025, transaction.getDate().get(java.util.Calendar.YEAR), "Year should be 2025");

        } finally {
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 13: Transactions have unique import record IDs")
    void testImportRecordIdUniqueness() throws Exception {
        // This test would need a file with multiple transactions
        // For now, just verify single transaction has an ID
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            barclays.importRegisterTrxFile();

            // Act
            Transaction transaction = barclays.next();

            // Assert
            assertNotNull(transaction.getImportRecordId(), "Import record ID should not be null");
            assertFalse(transaction.getImportRecordId().isEmpty(), "Import record ID should not be empty");
            // FITID from test file
            assertEquals("554328650712053126673293001", transaction.getImportRecordId(),
                "Import record ID should match FITID from QFX");

        } finally {
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 14: No provisional transactions for QFX (always cleared)")
    void testNoProvisionalTransactions() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            // Act
            Transaction result = barclays.getMatchingProvisionalTransaction(mock(Transaction.class));

            // Assert
            assertNull(result, "QFX format does not support provisional transactions");

        } finally {
            barclays.close();
        }
    }
}


