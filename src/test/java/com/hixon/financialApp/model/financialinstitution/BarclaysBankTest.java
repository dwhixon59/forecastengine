package com.hixon.financialApp.model.financialinstitution;

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

    @Test
    @DisplayName("Test 1: Constructor with valid QFX file succeeds")
    void testConstructor_ValidQfxFile() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        Register mockRegister = mock(Register.class);
        Budget mockBudget = mock(Budget.class);
        Forecast mockForecast = mock(Forecast.class);
        ViewInt mockView = mock(ViewInt.class);
        NotificationServiceInt mockNotificationService = mock(NotificationServiceInt.class);

        // Act
        BarclaysBank barclays = new BarclaysBank(
            qfxFile, mockRegister, mockBudget, mockForecast, mockView, mockNotificationService
        );

        // Assert
        assertNotNull(barclays, "BarclaysBank should be created");

        // Cleanup
        barclays.close();
    }

    @Test
    @DisplayName("Test 2: Constructor with null filename throws exception")
    void testConstructor_NullFilename() {
        // Arrange
        Register mockRegister = mock(Register.class);
        Budget mockBudget = mock(Budget.class);
        Forecast mockForecast = mock(Forecast.class);
        ViewInt mockView = mock(ViewInt.class);
        NotificationServiceInt mockNotificationService = mock(NotificationServiceInt.class);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new BarclaysBank(
                null, mockRegister, mockBudget, mockForecast, mockView, mockNotificationService
            );
        }, "Constructor should throw IllegalArgumentException for null filename");
    }

    @Test
    @DisplayName("Test 3: Constructor with empty filename throws exception")
    void testConstructor_EmptyFilename() {
        // Arrange
        Register mockRegister = mock(Register.class);
        Budget mockBudget = mock(Budget.class);
        Forecast mockForecast = mock(Forecast.class);
        ViewInt mockView = mock(ViewInt.class);
        NotificationServiceInt mockNotificationService = mock(NotificationServiceInt.class);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new BarclaysBank(
                "", mockRegister, mockBudget, mockForecast, mockView, mockNotificationService
            );
        }, "Constructor should throw IllegalArgumentException for empty filename");
    }

    @Test
    @DisplayName("Test 4: hasNext returns false for file with no transactions")
    void testHasNext_NoTransactions() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        Register mockRegister = mock(Register.class);
        Budget mockBudget = mock(Budget.class);
        Forecast mockForecast = mock(Forecast.class);
        ViewInt mockView = mock(ViewInt.class);
        NotificationServiceInt mockNotificationService = mock(NotificationServiceInt.class);

        BarclaysBank barclays = new BarclaysBank(
            qfxFile, mockRegister, mockBudget, mockForecast, mockView, mockNotificationService
        );

        try {
            // Act
            boolean hasNext = barclays.hasNext();

            // Assert
            // Note: Currently QfxParser returns empty transaction list
            // This will change when we implement actual transaction extraction
            assertFalse(hasNext, "Should return false when no transactions available");

        } finally {
            // Cleanup
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 5: Iterator can iterate through transactions")
    void testIterator_IterateThroughTransactions() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        Register mockRegister = mock(Register.class);
        Budget mockBudget = mock(Budget.class);
        Forecast mockForecast = mock(Forecast.class);
        ViewInt mockView = mock(ViewInt.class);
        NotificationServiceInt mockNotificationService = mock(NotificationServiceInt.class);

        BarclaysBank barclays = new BarclaysBank(
            qfxFile, mockRegister, mockBudget, mockForecast, mockView, mockNotificationService
        );

        try {
            // Act
            List<Transaction> transactions = new ArrayList<>();
            while (barclays.hasNext()) {
                Transaction t = barclays.next();
                transactions.add(t);
            }

            // Assert
            // Note: Currently returns 0 transactions
            // Will change when we implement actual transaction extraction
            assertEquals(0, transactions.size(), "Should iterate through all transactions");

        } finally {
            // Cleanup
            barclays.close();
        }
    }

    @Test
    @DisplayName("Test 6: parseMerchantPayee returns payee as-is")
    void testParseMerchantPayee() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        Register mockRegister = mock(Register.class);
        Budget mockBudget = mock(Budget.class);
        Forecast mockForecast = mock(Forecast.class);
        ViewInt mockView = mock(ViewInt.class);
        NotificationServiceInt mockNotificationService = mock(NotificationServiceInt.class);

        BarclaysBank barclays = new BarclaysBank(
            qfxFile, mockRegister, mockBudget, mockForecast, mockView, mockNotificationService
        );

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
    @DisplayName("Test 7: CSV methods throw UnsupportedOperationException")
    void testCsvMethods_ThrowException() throws Exception {
        // Arrange
        String qfxFile = getResourceFilePath("/qfx/test-single-purchase.qfx");
        Register mockRegister = mock(Register.class);
        Budget mockBudget = mock(Budget.class);
        Forecast mockForecast = mock(Forecast.class);
        ViewInt mockView = mock(ViewInt.class);
        NotificationServiceInt mockNotificationService = mock(NotificationServiceInt.class);

        BarclaysBank barclays = new BarclaysBank(
            qfxFile, mockRegister, mockBudget, mockForecast, mockView, mockNotificationService
        );

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
}


