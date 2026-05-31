package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ImportLog} and its inner {@link ImportLog.ImportRecord} class.
 *
 * <p>Database calls are avoided entirely — {@code getSplits()} is lazy and not exercised here.
 * The only external dependency is {@link Utility#getView()}, which is satisfied by
 * calling {@link Utility#setView(ViewInt)} in setup.</p>
 */
@DisplayName("ImportLog Tests")
public class ImportLogTest {

    private ViewInt mockView;
    private Transaction mockTransaction;
    private Merchant mockMerchant;
    private ImportLog importLog;

    @BeforeEach
    void setUp() throws Exception {
        mockView = mock(ViewInt.class);
        Utility.setView(mockView);

        mockMerchant = mock(Merchant.class);
        when(mockMerchant.getName()).thenReturn("Starbucks");

        mockTransaction = mock(Transaction.class);
        when(mockTransaction.getPayee()).thenReturn("STARBUCKS #12345");
        when(mockTransaction.getAmount()).thenReturn(-5.75);
        when(mockTransaction.getMerchant()).thenReturn(mockMerchant);
        when(mockTransaction.getAuthorizationDate()).thenReturn(null);
        Calendar postDate = Calendar.getInstance();
        when(mockTransaction.getPostDate()).thenReturn(postDate);
        when(mockTransaction.getImportRecordId()).thenReturn("FITID-001");
        when(mockTransaction.hasTipInfo()).thenReturn(false);

        importLog = new ImportLog();
    }

    // ── logImportEvent boolean overload ───────────────────────────────────────

    @Nested
    @DisplayName("Boolean overload")
    class BooleanOverload {

        @Test
        @DisplayName("true flag creates NEWLY_IMPORTED record")
        void trueFlag_createsNewlyImportedRecord() throws Exception {
            importLog.logImportEvent(mockTransaction, true);

            assertEquals(1, importLog.getImportRecords().size());
            ImportLog.ImportRecord record = importLog.getImportRecords().get(0);
            assertEquals(ImportLog.ImportRecord.Status.NEWLY_IMPORTED, record.getStatus());
            assertSame(mockTransaction, record.getTransaction());
        }

        @Test
        @DisplayName("false flag creates ALREADY_IMPORTED record")
        void falseFlag_createsAlreadyImportedRecord() throws Exception {
            importLog.logImportEvent(mockTransaction, false);

            assertEquals(1, importLog.getImportRecords().size());
            ImportLog.ImportRecord record = importLog.getImportRecords().get(0);
            assertEquals(ImportLog.ImportRecord.Status.ALREADY_IMPORTED, record.getStatus());
        }

        @Test
        @DisplayName("also adds transaction to legacy importedTransactions list")
        void alsoAddsToLegacyList() throws Exception {
            importLog.logImportEvent(mockTransaction, true);

            assertEquals(1, importLog.getImportedTransactions().size());
            assertSame(mockTransaction, importLog.getImportedTransactions().get(0));
        }
    }

    // ── logImportEvent Status overload ────────────────────────────────────────

    @Nested
    @DisplayName("Status overload")
    class StatusOverload {

        @Test
        @DisplayName("SKIPPED_BY_USER status recorded correctly")
        void skippedStatus_recordedCorrectly() throws Exception {
            importLog.logImportEvent(mockTransaction, ImportLog.ImportRecord.Status.SKIPPED_BY_USER);

            assertEquals(1, importLog.getImportRecords().size());
            assertEquals(ImportLog.ImportRecord.Status.SKIPPED_BY_USER,
                    importLog.getImportRecords().get(0).getStatus());
        }

        @Test
        @DisplayName("Multiple events accumulate in order")
        void multipleEvents_accumulateInOrder() throws Exception {
            Transaction t2 = mock(Transaction.class);
            Merchant m2 = mock(Merchant.class);
            when(m2.getName()).thenReturn("Amazon");
            when(t2.getPayee()).thenReturn("AMAZON");
            when(t2.getAmount()).thenReturn(-42.00);
            when(t2.getMerchant()).thenReturn(m2);
            when(t2.getAuthorizationDate()).thenReturn(null);
            when(t2.getPostDate()).thenReturn(Calendar.getInstance());
            when(t2.getImportRecordId()).thenReturn("FITID-002");
            when(t2.hasTipInfo()).thenReturn(false);

            importLog.logImportEvent(mockTransaction, ImportLog.ImportRecord.Status.NEWLY_IMPORTED);
            importLog.logImportEvent(t2, ImportLog.ImportRecord.Status.ALREADY_IMPORTED);

            assertEquals(2, importLog.getImportRecords().size());
            assertEquals(2, importLog.getImportedTransactions().size());
            assertEquals(ImportLog.ImportRecord.Status.NEWLY_IMPORTED,
                    importLog.getImportRecords().get(0).getStatus());
            assertEquals(ImportLog.ImportRecord.Status.ALREADY_IMPORTED,
                    importLog.getImportRecords().get(1).getStatus());
        }
    }

    // ── ImportRecord state ────────────────────────────────────────────────────

    @Nested
    @DisplayName("ImportRecord state")
    class ImportRecordState {

        @Test
        @DisplayName("recategorizedThisSession defaults to false on new record")
        void recategorizedThisSession_defaultsFalse() {
            ImportLog.ImportRecord record =
                    new ImportLog.ImportRecord(mockTransaction, ImportLog.ImportRecord.Status.NEWLY_IMPORTED);
            assertFalse(record.isRecategorizedThisSession());
        }

        @Test
        @DisplayName("setRecategorizedThisSession(true) updates flag")
        void setRecategorizedThisSession_updatesFlag() {
            ImportLog.ImportRecord record =
                    new ImportLog.ImportRecord(mockTransaction, ImportLog.ImportRecord.Status.NEWLY_IMPORTED);
            record.setRecategorizedThisSession(true);
            assertTrue(record.isRecategorizedThisSession());
        }

        @Test
        @DisplayName("setStatus() updates the status")
        void setStatus_updatesStatus() {
            ImportLog.ImportRecord record =
                    new ImportLog.ImportRecord(mockTransaction, ImportLog.ImportRecord.Status.NEWLY_IMPORTED);
            record.setStatus(ImportLog.ImportRecord.Status.SKIPPED_BY_USER);
            assertEquals(ImportLog.ImportRecord.Status.SKIPPED_BY_USER, record.getStatus());
        }

        @Test
        @DisplayName("getTransaction() returns the transaction passed to constructor")
        void getTransaction_returnsConstructorTransaction() {
            ImportLog.ImportRecord record =
                    new ImportLog.ImportRecord(mockTransaction, ImportLog.ImportRecord.Status.NEWLY_IMPORTED);
            assertSame(mockTransaction, record.getTransaction());
        }
    }

    // ── Tip info output ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Tip info line is printed when hasTipInfo returns true")
    void logImportEvent_withTipInfo_printsTipLine() throws Exception {
        when(mockTransaction.hasTipInfo()).thenReturn(true);
        when(mockTransaction.getTipAmount()).thenReturn(1.25);
        when(mockTransaction.getProvisionalAmount()).thenReturn(-4.50);

        importLog.logImportEvent(mockTransaction, true);

        // The "Tip detected:" say() call should have been made
        verify(mockView, atLeastOnce()).say(contains("Tip detected:"));
    }
}

