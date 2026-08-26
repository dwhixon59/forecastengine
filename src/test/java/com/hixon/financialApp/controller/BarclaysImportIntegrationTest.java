package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.financialinstitution.BarclaysBank;
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
 * Integration tests for Barclays Bank QFX import using ImportController.
 *
 * <p>These tests verify that the complete import flow works correctly,
 * from QFX file parsing through to transaction objects ready for database insertion.
 */
@DisplayName("Barclays Import Integration Tests")
class BarclaysImportIntegrationTest {

    /**
     * Helper method to get file path from resource.
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
    @DisplayName("Test 1: Import QFX file and iterate through all transactions")
    void testImportQfxFileFullIteration() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            // Act - Simulate ImportController flow
            barclays.importRegisterTrxFile();

            List<Transaction> importedTransactions = new ArrayList<>();
            while (barclays.hasNext()) {
                Transaction t = nextAsTheImportWould(barclays);
                importedTransactions.add(t);
            }

            // Assert
            assertEquals(1, importedTransactions.size(), "Should import 1 transaction");

            Transaction txn = importedTransactions.get(0);
            assertNotNull(txn, "Transaction should not be null");
            assertEquals("NETFLIX.COM", txn.getMerchantPayee(), "Merchant should match");
            assertEquals(-28.20, txn.getAmount(), 0.01, "Amount should match");
            assertTrue(txn.isCleared(), "Should be cleared");
            assertNotNull(txn.getImportRecordId(), "Should have import record ID");

        } finally {
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 2: Verify transaction ready for database insertion")
    void testTransactionReadyForDatabase() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-payment.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            barclays.importRegisterTrxFile();

            // Act
            Transaction txn = nextAsTheImportWould(barclays);

            // Assert - Verify all required fields are populated
            assertNotNull(txn.getDate(), "Transaction date should be set");
            assertNotNull(txn.getMerchantPayee(), "Merchant payee should be set");
            assertNotEquals(0.0, txn.getAmount(), "Amount should not be zero");
            assertTrue(txn.isCleared(), "Should be marked as cleared");
            assertNotNull(txn.getImportRecordId(), "Import record ID should be set");
            assertEquals(0, txn.getCheckNumber(), "Credit cards don't have check numbers");

        } finally {
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 3: Multiple transaction types in sequence")
    void testMultipleTransactionTypes() throws Exception {
        // This test would use a file with multiple transactions
        // For now, we can test multiple individual files

        String[] testFiles = {
            "/qfx/test-single-purchase.qfx",
            "/qfx/test-single-payment.qfx",
            "/qfx/test-annual-fee.qfx",
            "/qfx/test-interest-charge.qfx",
            "/qfx/test-reward-credit.qfx"
        };

        for (String testFile : testFiles) {
            String qfxFile = getResourceFilePath(testFile);
            SessionController sessionController = createMockSessionController(qfxFile);
            BarclaysBank barclays = new BarclaysBank(sessionController);

            try {
                // Act
                barclays.importRegisterTrxFile();

                assertTrue(barclays.hasNext(), "Should have at least one transaction: " + testFile);
                Transaction txn = nextAsTheImportWould(barclays);

                // Assert
                assertNotNull(txn, "Transaction should not be null: " + testFile);
                assertNotNull(txn.getMerchantPayee(), "Should have merchant: " + testFile);
                assertTrue(txn.isCleared(), "Should be cleared: " + testFile);

            } finally {
                barclays.close();
            }
        }
    }

    @Test
    @DisplayName("Test 4: Verify iterator closes properly after completion")
    void testIteratorClosesCleanly() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank barclays = new BarclaysBank(sessionController);

        try {
            barclays.importRegisterTrxFile();

            // Act - Consume all transactions
            while (barclays.hasNext()) {
                barclays.next();
            }

            // Assert
            assertFalse(barclays.hasNext(), "Iterator should be exhausted");

        } finally {
            // Should close without throwing exception
            assertDoesNotThrow(() -> barclays.close());
        }
    }

    @Test
    @DisplayName("Test 5: ImportController pattern compatibility")
    void testImportControllerPattern() throws Exception {
        // This test simulates exactly what ImportController does

        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        SessionController sessionController = createMockSessionController(qfxFile);
        BarclaysBank financialInstitution = new BarclaysBank(sessionController);

        try {
            // Act - Exactly as ImportController.importRegisterTransactionFile() does
            financialInstitution.importRegisterTrxFile();

            int transactionCount = 0;
            while (financialInstitution.hasNext()) {
                Transaction t = nextAsTheImportWould(financialInstitution);
                // In real ImportController, transaction would be processed here
                transactionCount++;

                // Verify transaction is valid
                assertNotNull(t);
                assertNotNull(t.getDate());
                assertNotNull(t.getMerchantPayee());
            }

            // Assert
            assertEquals(1, transactionCount, "Should process exactly 1 transaction");

        } finally {
            financialInstitution.close();
        }
    }

    /**
     * A transaction as the import actually ends up with it.
     *
     * <p>{@code next()} deliberately leaves the merchant payee unset:  parsing it can ask the user a
     * question, and at that point nobody yet knows whether the row was already imported on an
     * earlier run, so {@code ImportController} defers the parse until after its import-record-id
     * lookup.  See {@code FinancialInstitution.convertQfxToTransaction}.  These tests mirror that
     * second step so they assert on the finished article rather than the raw row.
     */
    private static Transaction nextAsTheImportWould(BarclaysBank bank) throws Exception {
        Transaction transaction = bank.next();
        transaction.setMerchantPayee(bank.parseMerchantPayee(
                transaction.getDate(), transaction.getAmount(), transaction.getPayee()));
        return transaction;
    }

}

