package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Calendar;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ImportSummaryController}.
 *
 * <p>The database is fully avoided:
 * <ul>
 *   <li>{@link TransactionSplit#getSplitsForTransaction} is mocked as a static call.</li>
 *   <li>{@link TransactionController#recategorizeTransaction} is injected via the
 *       {@link TestableImportSummaryController} subclass that overrides
 *       {@link ImportSummaryController#createTransactionController()}.</li>
 * </ul>
 * </p>
 */
@DisplayName("ImportSummaryController Tests")
public class ImportSummaryControllerTest {

    private ViewInt mockView;
    private Register mockRegister;
    private SessionController sessionController;
    private ImportLog importLog;
    private TransactionController mockTransactionController;
    private TestableImportSummaryController summaryController;

    @BeforeEach
    void setUp() {
        mockView = mock(ViewInt.class);
        mockRegister = mock(Register.class);
        when(mockRegister.getName()).thenReturn("Test Register");

        sessionController = new SessionController(
                mockRegister, mock(Budget.class), mock(Forecast.class), mockView, null);

        importLog = new ImportLog();
        mockTransactionController = mock(TransactionController.class);
        summaryController = new TestableImportSummaryController(
                sessionController, importLog, mockTransactionController);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Transaction buildMockTransaction(boolean cleared, double amount) throws Exception {
        Transaction txn = mock(Transaction.class);
        Merchant merchant = mock(Merchant.class);
        when(merchant.getName()).thenReturn("Test Merchant");
        when(txn.getMerchant()).thenReturn(merchant);
        when(txn.isCleared()).thenReturn(cleared);
        when(txn.getAmount()).thenReturn(amount);
        when(txn.getAuthorizationDate()).thenReturn(null);
        when(txn.getPostDate()).thenReturn(Calendar.getInstance());
        return txn;
    }

    private ImportLog.ImportRecord addNewRecord(Transaction txn) {
        ImportLog.ImportRecord record = new ImportLog.ImportRecord(
                txn, ImportLog.ImportRecord.Status.NEWLY_IMPORTED);
        importLog.getImportRecords().add(record);
        return record;
    }

    // ── Empty log ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Empty log prints 'no transactions' message and returns false")
    void emptyLog_printsMessageAndReturnsFalse() throws Exception {
        boolean result = summaryController.showSummaryAndRecategorize();

        assertFalse(result);
        verify(mockView).say("No transactions were processed this session.");
    }

    // ── Loop exit conditions ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Loop exit conditions")
    class LoopExit {

        @Test
        @DisplayName("Pressing Enter (empty input) exits loop and returns false")
        void emptyInput_exitsLoop_returnsFalse() throws Exception {
            addNewRecord(buildMockTransaction(true, -10.00));

            try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
                ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                        .thenReturn(Collections.emptyList());
                when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean())).thenReturn("");

                boolean result = summaryController.showSummaryAndRecategorize();

                assertFalse(result);
            }
        }

        @Test
        @DisplayName("QuitException propagates out of the loop")
        void quitException_propagates() throws Exception {
            addNewRecord(buildMockTransaction(true, -10.00));

            try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
                ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                        .thenReturn(Collections.emptyList());
                when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean()))
                        .thenThrow(new QuitException("user quit"));

                assertThrows(QuitException.class,
                        () -> summaryController.showSummaryAndRecategorize());
            }
        }
    }

    // ── Input validation ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("Non-numeric text shows error, loop continues until Enter")
        void nonNumericText_showsError() throws Exception {
            addNewRecord(buildMockTransaction(true, -10.00));

            try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
                ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                        .thenReturn(Collections.emptyList());
                when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean()))
                        .thenReturn("abc", "");

                summaryController.showSummaryAndRecategorize();

                verify(mockView).say(
                        "Invalid selection. Enter a transaction number or press Enter to continue.");
            }
        }

        @Test
        @DisplayName("Zero (below range) shows range error")
        void zeroSelection_showsRangeError() throws Exception {
            addNewRecord(buildMockTransaction(true, -10.00));

            try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
                ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                        .thenReturn(Collections.emptyList());
                when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean()))
                        .thenReturn("0", "");

                summaryController.showSummaryAndRecategorize();

                verify(mockView).say(contains("Enter a number between 1 and 1"));
            }
        }

        @Test
        @DisplayName("Number above list size shows range error")
        void aboveRangeSelection_showsRangeError() throws Exception {
            addNewRecord(buildMockTransaction(true, -10.00));

            try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
                ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                        .thenReturn(Collections.emptyList());
                when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean()))
                        .thenReturn("99", "");

                summaryController.showSummaryAndRecategorize();

                verify(mockView).say(contains("Enter a number between 1 and 1"));
            }
        }
    }

    // ── Status guards ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Status-based guards")
    class StatusGuards {

        @Test
        @DisplayName("Selecting ALREADY_IMPORTED record shows informational message")
        void alreadyImported_showsInfoMessage() throws Exception {
            Transaction txn = buildMockTransaction(true, -10.00);
            importLog.getImportRecords().add(new ImportLog.ImportRecord(
                    txn, ImportLog.ImportRecord.Status.ALREADY_IMPORTED));

            try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
                ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                        .thenReturn(Collections.emptyList());
                when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean()))
                        .thenReturn("1", "");

                summaryController.showSummaryAndRecategorize();

                verify(mockView).say(contains("already imported in a previous session"));
                verifyNoInteractions(mockTransactionController);
            }
        }

        @Test
        @DisplayName("Selecting SKIPPED_BY_USER record shows informational message")
        void skippedByUser_showsInfoMessage() throws Exception {
            Transaction txn = buildMockTransaction(true, -10.00);
            importLog.getImportRecords().add(new ImportLog.ImportRecord(
                    txn, ImportLog.ImportRecord.Status.SKIPPED_BY_USER));

            try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
                ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                        .thenReturn(Collections.emptyList());
                when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean()))
                        .thenReturn("1", "");

                summaryController.showSummaryAndRecategorize();

                verify(mockView).say(contains("Reprocess Transaction"));
                verifyNoInteractions(mockTransactionController);
            }
        }
    }

    // ── Recategorization ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Recategorization")
    class Recategorization {

        @Test
        @DisplayName("Successful recategorization returns true and marks record")
        void successfulRecategorize_returnsTrueAndMarksRecord() throws Exception {
            Transaction txn = buildMockTransaction(true, -25.00);
            ImportLog.ImportRecord record = addNewRecord(txn);

            try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
                ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                        .thenReturn(Collections.emptyList());
                doNothing().when(mockTransactionController)
                        .recategorizeTransaction(any(Transaction.class));

                when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean()))
                        .thenReturn("1", "");

                boolean result = summaryController.showSummaryAndRecategorize();

                assertTrue(result, "Should return true when forecast was changed");
                assertTrue(record.isRecategorizedThisSession(),
                        "Record should be flagged as recategorized this session");
                verify(mockTransactionController).recategorizeTransaction(txn);
            }
        }

        @Test
        @DisplayName("Cancelled recategorization (CancelException) returns false, record unchanged")
        void cancelledRecategorize_returnsFalseAndLeavesRecord() throws Exception {
            Transaction txn = buildMockTransaction(true, -25.00);
            ImportLog.ImportRecord record = addNewRecord(txn);

            try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
                ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                        .thenReturn(Collections.emptyList());
                doThrow(new CancelException("cancelled"))
                        .when(mockTransactionController)
                        .recategorizeTransaction(any(Transaction.class));

                when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean()))
                        .thenReturn("1", "");

                boolean result = summaryController.showSummaryAndRecategorize();

                assertFalse(result, "Should return false when recategorization was cancelled");
                assertFalse(record.isRecategorizedThisSession(),
                        "Record should not be flagged when recategorization was cancelled");
            }
        }

        @Test
        @DisplayName("With two transactions, only the selected one is recategorized")
        void multipleTransactions_onlySelectedOneIsRecategorized() throws Exception {
            Transaction txn1 = buildMockTransaction(true, -10.00);
            Transaction txn2 = buildMockTransaction(true, -20.00);
            ImportLog.ImportRecord record1 = addNewRecord(txn1);
            ImportLog.ImportRecord record2 = addNewRecord(txn2);

            try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
                ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                        .thenReturn(Collections.emptyList());
                doNothing().when(mockTransactionController)
                        .recategorizeTransaction(any(Transaction.class));

                when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean()))
                        .thenReturn("2", "");

                summaryController.showSummaryAndRecategorize();

                verify(mockTransactionController).recategorizeTransaction(txn2);
                verify(mockTransactionController, never()).recategorizeTransaction(txn1);
                assertFalse(record1.isRecategorizedThisSession());
                assertTrue(record2.isRecategorizedThisSession());
            }
        }
    }

    // ── Cleared vs provisional display ────────────────────────────────────────

    @Test
    @DisplayName("Provisional divider line is printed before the first non-cleared transaction")
    void provisionalDivider_printedBeforeFirstProvisional() throws Exception {
        addNewRecord(buildMockTransaction(true, -10.00));   // cleared first
        addNewRecord(buildMockTransaction(false, -20.00));  // provisional second

        try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
            ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                    .thenReturn(Collections.emptyList());
            when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn("");

            summaryController.showSummaryAndRecategorize();

            verify(mockView).say(contains("Provisional"));
        }
    }

    @Test
    @DisplayName("No provisional divider when all transactions are cleared")
    void noDivider_whenAllCleared() throws Exception {
        addNewRecord(buildMockTransaction(true, -10.00));
        addNewRecord(buildMockTransaction(true, -20.00));

        try (MockedStatic<TransactionSplit> ts = Mockito.mockStatic(TransactionSplit.class)) {
            ts.when(() -> TransactionSplit.getSplitsForTransaction(any()))
                    .thenReturn(Collections.emptyList());
            when(mockView.getResponseStringMenuSelection(anyString(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn("");

            summaryController.showSummaryAndRecategorize();

            verify(mockView, never()).say(contains("Provisional"));
        }
    }

    // ── Testable subclass ─────────────────────────────────────────────────────

    /**
     * Overrides {@code createTransactionController()} to return an injected mock,
     * preventing any database access during tests.
     */
    static class TestableImportSummaryController extends ImportSummaryController {

        private final TransactionController mockTxnController;

        TestableImportSummaryController(SessionController sessionController,
                                        ImportLog importLog,
                                        TransactionController mockTxnController) {
            super(sessionController, importLog);
            this.mockTxnController = mockTxnController;
        }

        @Override
        protected TransactionController createTransactionController() {
            return mockTxnController;
        }
    }
}

