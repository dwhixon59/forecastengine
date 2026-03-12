package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.forecast.*;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.view.base.AbstractForecastView;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the "Update register from external source" feature in ForecastController.
 * <p>
 * Tests functions including:
 * - Changing the date of a forecast transaction
 * - Changing the amount of a forecast transaction
 * - Inserting a new forecast transaction
 * - Deleting a forecast transaction (zeroNotFound)
 * - Changing the category (via forecast item matching)
 * - Payee and memo handling
 * <p>
 * The database and external file I/O are fully mocked using a test subclass that overrides
 * the protected factory/lookup methods in ForecastController.
 */
@DisplayName("Update From External Source Tests")
public class UpdateFromExternalSourceTest {

    // Mocks for core objects:
    private ViewInt mockView;
    private Budget mockBudget;
    private Register mockRegister;
    private Forecast mockForecast;
    private SessionController sessionController;

    // Mocks for collaborators created by factory methods:
    private AbstractForecastView mockExternalSourceView;
    private BudgetController mockBudgetController;
    private ForecastTransactionController mockForecastTransactionController;

    // The controller under test (a testable subclass):
    private TestableForecastController forecastController;

    // Common IDs:
    private final UUID forecastId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private final UUID forecastItemId = UUID.randomUUID();
    private final UUID budgetItemId = UUID.randomUUID();

    // Tracking flags for verifying calls to protected methods:
    private boolean setAllFoundCalled = false;
    private boolean setAllFoundValue = true;
    private final List<ForecastTransaction> updatedTransactions = new ArrayList<>();
    private final List<ForecastTransaction> insertedTransactions = new ArrayList<>();
    private final List<ForecastItem> insertedForecastItems = new ArrayList<>();

    // A map of transaction ID → DB transaction for lookups:
    private final Map<UUID, ForecastTransaction> dbTransactionMap = new HashMap<>();

    // A map of (category|payee) → ForecastItem for lookups:
    private final Map<String, ForecastItem> forecastItemByNameMap = new HashMap<>();

    // A map of payee → List<BudgetItem> for lookups:
    private final Map<String, List<BudgetItem>> budgetItemsByPayeeMap = new HashMap<>();

    // Controls which file extension "exists":
    private String existingFileExtension = ".xlsx";


    /**
     * A test subclass of ForecastController that overrides the protected factory/lookup methods
     * to return mocks and record calls instead of accessing real files and databases.
     */
    class TestableForecastController extends ForecastController {

        TestableForecastController(SessionController sessionController) {
            super(sessionController);
        }

        @Override
        protected boolean fileExists(String filePath) {
            if (existingFileExtension == null) return false;
            return filePath.endsWith(existingFileExtension);
        }

        @Override
        protected AbstractForecastView createExcelForecastView(Forecast forecast) {
            return mockExternalSourceView;
        }

        @Override
        protected AbstractForecastView createCsvForecastView(Forecast forecast) {
            return mockExternalSourceView;
        }

        @Override
        protected BudgetController createBudgetController() {
            return mockBudgetController;
        }

        @Override
        protected ForecastTransactionController createForecastTransactionController() {
            return mockForecastTransactionController;
        }

        @Override
        protected ForecastTransaction lookupForecastTransactionById(UUID id) {
            return dbTransactionMap.get(id);
        }

        @Override
        protected void setAllFound(Forecast forecast, boolean found) {
            setAllFoundCalled = true;
            setAllFoundValue = found;
        }

        @Override
        protected ForecastItem lookupForecastItemByName(UUID idForecast, String category, String payee) {
            return forecastItemByNameMap.get(category + "|" + payee);
        }

        @Override
        protected List<BudgetItem> lookupBudgetItemsByPayee(Budget budget, String payee) {
            return budgetItemsByPayeeMap.getOrDefault(payee, List.of());
        }

        @Override
        protected void updateForecastTransaction(ForecastTransaction ft) {
            updatedTransactions.add(ft);
        }

        @Override
        protected void insertForecastTransaction(ForecastTransaction ft) {
            insertedTransactions.add(ft);
        }

        @Override
        protected void insertForecastItem(ForecastItem fi) {
            insertedForecastItems.add(fi);
        }
    }


    @BeforeEach
    void setUp() throws Exception {
        mockView = mock(ViewInt.class);
        mockBudget = mock(Budget.class);
        mockRegister = mock(Register.class);
        mockForecast = mock(Forecast.class);
        mockExternalSourceView = mock(AbstractForecastView.class);
        mockBudgetController = mock(BudgetController.class);
        mockForecastTransactionController = mock(ForecastTransactionController.class);

        when(mockForecast.getId()).thenReturn(forecastId);
        when(mockForecast.getName()).thenReturn("Test Forecast");
        when(mockForecast.getBudget()).thenReturn(mockBudget);
        when(mockBudget.getId()).thenReturn(budgetId);

        sessionController = new SessionController(mockRegister, mockBudget, mockForecast, mockView, null);
        forecastController = new TestableForecastController(sessionController);

        // Reset tracking state:
        setAllFoundCalled = false;
        setAllFoundValue = true;
        updatedTransactions.clear();
        insertedTransactions.clear();
        insertedForecastItems.clear();
        dbTransactionMap.clear();
        forecastItemByNameMap.clear();
        budgetItemsByPayeeMap.clear();
        existingFileExtension = ".xlsx";
    }


    // ──────────────────────────────────────────────────────────────────────
    // Helper methods to create test objects
    // ──────────────────────────────────────────────────────────────────────

    private ForecastItem buildForecastItem(String category, String payee, double amount) {
        ForecastItem item = new ForecastItem();
        item.setId(forecastItemId);
        item.setForecast(mockForecast);
        item.setCategory(category);
        item.setPayee(payee);
        item.setMemo("");
        item.setAmount(amount);
        item.setPeriod(Item.PeriodType.MONTHLY);
        item.setItemType(Item.ItemType.EXPENSE);
        item.setHowImportant(Item.HowImportant.FIXED_ESSENTIAL);
        item.setHowOccurs(Item.HowOccurs.PERIODIC);
        item.setHowPaid(Item.HowPaid.DEBIT_CARD);
        item.setIdBudgetItem(budgetItemId);
        return item;
    }

    private ForecastItem buildForecastItem(UUID itemId, String category, String payee, double amount) {
        ForecastItem item = buildForecastItem(category, payee, amount);
        item.setId(itemId);
        return item;
    }

    private ForecastTransaction buildForecastTransaction(UUID id, ForecastItem forecastItem,
                                                          Calendar plannedDate, double remainingAmount,
                                                          Calendar version) {
        ForecastTransaction ft = new ForecastTransaction();
        ft.setId(id);
        ft.setForecastItem(forecastItem);
        ft.setIdForecastItem(forecastItem.getId());
        ft.setPlannedDate(plannedDate);
        ft.setRemainingAmount(remainingAmount);
        ft.setVersion(version);
        ft.setFound(false);
        ft.setOverridden(false);
        return ft;
    }

    private Calendar makeDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private Calendar makeVersion(int year, int month, int day, int hour) {
        Calendar cal = makeDate(year, month, day);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        return cal;
    }

    private void setupExternalSource(List<ForecastTransaction> transactions) throws Exception {
        when(mockExternalSourceView.openForecastTransactionSource(anyString())).thenReturn(transactions);
        doNothing().when(mockExternalSourceView).closeForecastTransactionSource(anyString());
    }


    // ──────────────────────────────────────────────────────────────────────
    // Test: No file found → ForecastException
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("When no external file exists")
    class NoFileFound {

        @Test
        @DisplayName("Should throw ForecastException when no Excel or CSV file is found")
        void testNoFileFound_ThrowsForecastException() {
            existingFileExtension = null;

            ForecastException thrown = assertThrows(ForecastException.class,
                    () -> forecastController.updateFromExternalSource());
            assertTrue(thrown.getMessage().contains("No forecast file found"));
        }
    }


    // ──────────────────────────────────────────────────────────────────────
    // Test: External source returns null transaction list
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("When external source returns null transactions")
    class NullTransactions {

        @Test
        @DisplayName("Should report no transactions to update from")
        void testNullTransactions_ReportsNoTransactions() throws Exception {
            when(mockExternalSourceView.openForecastTransactionSource(anyString())).thenReturn(null);

            forecastController.updateFromExternalSource();

            verify(mockView).say(contains("no forecast transactions"));
        }
    }


    // ──────────────────────────────────────────────────────────────────────
    // Tests: Existing transaction updates (ID present, found in DB)
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("When updating existing forecast transactions")
    class UpdateExisting {

        private ForecastItem forecastItem;
        private UUID transactionId;

        @BeforeEach
        void setUp() {
            forecastItem = buildForecastItem("Utilities", "Electric Company", -150.0);
            transactionId = UUID.randomUUID();
        }

        @Test
        @DisplayName("Same date and amount → marks found, saves, no user prompt")
        void testSameDateAndAmount() throws Exception {
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -150.0, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -150.0, (Calendar) version.clone());

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertTrue(dbTransaction.isFound(), "DB transaction should be marked as found");
            assertEquals(1, updatedTransactions.size(), "Should have saved exactly one transaction");
            assertSame(dbTransaction, updatedTransactions.get(0));
            verify(mockView).say(contains("Successfully processed 1 forecast transaction"));
            verify(mockView, never()).selectFromMenu(anyString(), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        }

        @Test
        @DisplayName("Date changed, same version → auto-overwrites without prompt")
        void testDateChanged_SameVersion() throws Exception {
            Calendar oldDate = makeDate(2026, Calendar.MARCH, 15);
            Calendar newDate = makeDate(2026, Calendar.MARCH, 20);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, newDate, -150.0, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, oldDate, -150.0, (Calendar) version.clone());

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertEquals(newDate.get(Calendar.DAY_OF_MONTH),
                    dbTransaction.getPlannedDate().get(Calendar.DAY_OF_MONTH),
                    "DB transaction date should be updated to the new date");
            verify(mockView).say(contains("Date modified for"));
            verify(mockView).say(contains("New date is"));
            verify(mockView, never()).selectFromMenu(anyString(), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        }

        @Test
        @DisplayName("Date changed, older imported version → prompts user, user selects imported")
        void testDateChanged_OlderVersion_UserSelectsImported() throws Exception {
            Calendar oldDate = makeDate(2026, Calendar.MARCH, 15);
            Calendar newDate = makeDate(2026, Calendar.MARCH, 20);
            Calendar olderVersion = makeVersion(2026, Calendar.FEBRUARY, 1, 10);
            Calendar newerVersion = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, newDate, -150.0, olderVersion);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, oldDate, -150.0, newerVersion);

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            when(mockView.selectFromMenu(contains("date"), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn("i");

            forecastController.updateFromExternalSource();

            verify(mockView).selectFromMenu(contains("date"), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
            assertEquals(newDate.get(Calendar.DAY_OF_MONTH),
                    dbTransaction.getPlannedDate().get(Calendar.DAY_OF_MONTH));
            verify(mockView).say(contains("Date modified for"));
        }

        @Test
        @DisplayName("Date changed, older imported version → prompts user, user selects database")
        void testDateChanged_OlderVersion_UserSelectsDatabase() throws Exception {
            Calendar oldDate = makeDate(2026, Calendar.MARCH, 15);
            Calendar newDate = makeDate(2026, Calendar.MARCH, 20);
            Calendar olderVersion = makeVersion(2026, Calendar.FEBRUARY, 1, 10);
            Calendar newerVersion = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, newDate, -150.0, olderVersion);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, oldDate, -150.0, newerVersion);

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            when(mockView.selectFromMenu(contains("date"), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn("d");

            forecastController.updateFromExternalSource();

            verify(mockView).selectFromMenu(contains("date"), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
            assertEquals(oldDate.get(Calendar.DAY_OF_MONTH),
                    dbTransaction.getPlannedDate().get(Calendar.DAY_OF_MONTH),
                    "DB transaction date should remain unchanged when user selects database");
            verify(mockView, never()).say(contains("Date modified for"));
        }

        @Test
        @DisplayName("Amount changed by more than $0.50, same version → auto-overwrites")
        void testAmountChanged_SameVersion() throws Exception {
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -200.0, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -150.0, (Calendar) version.clone());

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertEquals(-200.0, dbTransaction.getRemainingAmount(), 0.001,
                    "DB transaction amount should be updated to the new value");
            assertTrue(dbTransaction.isOverridden(), "Amount change should set overridden flag");
            verify(mockView).say(contains("Amount modified for"));
            verify(mockView).say(contains("New amount is"));
            verify(mockView, never()).selectFromMenu(contains("amount"), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        }

        @Test
        @DisplayName("Amount changed by exactly $0.50 → no update (within rounding tolerance)")
        void testAmountChanged_WithinRoundingTolerance() throws Exception {
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -150.50, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -150.0, (Calendar) version.clone());

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertEquals(-150.0, dbTransaction.getRemainingAmount(), 0.001,
                    "DB amount should not change for difference <= 0.50");
            verify(mockView, never()).say(contains("Amount modified for"));
        }

        @Test
        @DisplayName("Amount changed by $0.51 → triggers update (just above tolerance)")
        void testAmountChanged_JustAboveTolerance() throws Exception {
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -150.51, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -150.0, (Calendar) version.clone());

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertEquals(-150.51, dbTransaction.getRemainingAmount(), 0.001);
            verify(mockView).say(contains("Amount modified for"));
        }

        @Test
        @DisplayName("Amount changed, older imported version → prompts user, user selects imported")
        void testAmountChanged_OlderVersion_UserSelectsImported() throws Exception {
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar olderVersion = makeVersion(2026, Calendar.FEBRUARY, 1, 10);
            Calendar newerVersion = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -200.0, olderVersion);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -150.0, newerVersion);

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            when(mockView.selectFromMenu(contains("amount"), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn("i");

            forecastController.updateFromExternalSource();

            verify(mockView).selectFromMenu(contains("amount"), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
            assertEquals(-200.0, dbTransaction.getRemainingAmount(), 0.001);
            assertTrue(dbTransaction.isOverridden());
            verify(mockView).say(contains("Amount modified for"));
        }

        @Test
        @DisplayName("Amount changed, older imported version → prompts user, user selects database")
        void testAmountChanged_OlderVersion_UserSelectsDatabase() throws Exception {
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar olderVersion = makeVersion(2026, Calendar.FEBRUARY, 1, 10);
            Calendar newerVersion = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -200.0, olderVersion);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -150.0, newerVersion);

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            when(mockView.selectFromMenu(contains("amount"), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn("d");

            forecastController.updateFromExternalSource();

            verify(mockView).selectFromMenu(contains("amount"), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
            assertEquals(-150.0, dbTransaction.getRemainingAmount(), 0.001,
                    "DB amount should remain unchanged when user selects database");
            assertFalse(dbTransaction.isOverridden());
            verify(mockView, never()).say(contains("Amount modified for"));
        }

        @Test
        @DisplayName("Both date and amount changed, same version → both overwritten without prompt")
        void testDateAndAmountChanged_SameVersion() throws Exception {
            Calendar oldDate = makeDate(2026, Calendar.MARCH, 15);
            Calendar newDate = makeDate(2026, Calendar.MARCH, 20);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, newDate, -200.0, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, oldDate, -150.0, (Calendar) version.clone());

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertEquals(newDate.get(Calendar.DAY_OF_MONTH),
                    dbTransaction.getPlannedDate().get(Calendar.DAY_OF_MONTH));
            assertEquals(-200.0, dbTransaction.getRemainingAmount(), 0.001);
            verify(mockView).say(contains("Date modified for"));
            verify(mockView).say(contains("Amount modified for"));
            verify(mockView, never()).selectFromMenu(anyString(), anyList(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
        }

        @Test
        @DisplayName("Amount change sets overridden flag to true")
        void testAmountChanged_SetsOverriddenFlag() throws Exception {
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -200.0, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -150.0, (Calendar) version.clone());
            assertFalse(dbTransaction.isOverridden(), "Should start as not overridden");

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertTrue(dbTransaction.isOverridden(),
                    "Amount change should set overridden flag to prevent deletion during forecast update");
        }

        @Test
        @DisplayName("Date change sets overridden flag to true")
        void testDateChanged_SetsOverriddenFlag() throws Exception {
            Calendar oldDate = makeDate(2026, Calendar.MARCH, 15);
            Calendar newDate = makeDate(2026, Calendar.MARCH, 20);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, newDate, -150.0, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, oldDate, -150.0, (Calendar) version.clone());
            assertFalse(dbTransaction.isOverridden(), "Should start as not overridden");

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertTrue(dbTransaction.isOverridden(),
                    "Date change should set overridden flag to prevent deletion during forecast update");
        }

        @Test
        @DisplayName("Budgeted amount is copied from DB forecast item to SS forecast item")
        void testBudgetedAmountCopiedFromDb() throws Exception {
            ForecastItem ssForecastItem = buildForecastItem(forecastItemId, "Utilities", "Electric", 0.0);
            ForecastItem dbForecastItem = buildForecastItem(forecastItemId, "Utilities", "Electric", -150.0);

            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, ssForecastItem, date, -100.0, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, dbForecastItem, (Calendar) date.clone(), -100.0, (Calendar) version.clone());

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertEquals(-150.0, ssForecastItem.getAmount(), 0.001,
                    "SS ForecastItem should have gotten the budgeted amount from the DB ForecastItem");
        }
    }


    // ──────────────────────────────────────────────────────────────────────
    // Tests: ID present but not found in database → treated as new
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("When transaction has ID but is not found in database")
    class IdNotFoundInDb {

        @Test
        @DisplayName("Should clear ID and create new forecast transaction")
        void testIdNotFound_TreatedAsNewTransaction() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Food", "Grocery Store", -100.0);
            UUID orphanId = UUID.randomUUID();
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    orphanId, forecastItem, date, -100.0, version);

            forecastItemByNameMap.put("Food|Grocery Store", forecastItem);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            verify(mockView).say(contains("not found in database"));
            assertEquals(1, insertedTransactions.size(), "Should have inserted one new transaction");
            ForecastTransaction inserted = insertedTransactions.get(0);
            assertNotNull(inserted.getId(), "New transaction should have a fresh UUID");
            assertNotEquals(orphanId, inserted.getId(), "ID should have been reassigned");
            assertTrue(inserted.isFound());
            assertTrue(inserted.isOverridden());
            verify(mockView).say(contains("has been added to the forecast"));
        }
    }


    // ──────────────────────────────────────────────────────────────────────
    // Tests: New forecast transactions (no ID)
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("When creating new forecast transactions (no ID)")
    class NewTransactions {

        @Test
        @DisplayName("Existing ForecastItem found → uses it, inserts transaction")
        void testNewTransaction_ExistingForecastItem() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Food", "Grocery Store", -100.0);
            Calendar date = makeDate(2026, Calendar.APRIL, 1);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    null, forecastItem, date, -100.0, version);

            forecastItemByNameMap.put("Food|Grocery Store", forecastItem);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertEquals(1, insertedTransactions.size());
            ForecastTransaction inserted = insertedTransactions.get(0);
            assertNotNull(inserted.getId());
            assertTrue(inserted.isFound());
            assertTrue(inserted.isOverridden());
            assertEquals(0, insertedForecastItems.size(), "No new ForecastItem should be created");
            assertSame(forecastItem, ssTransaction.getForecastItem());
            verify(mockView).say(contains("has been added to the forecast"));
        }

        @Test
        @DisplayName("No ForecastItem, single BudgetItem match → creates new ForecastItem")
        void testNewTransaction_SingleBudgetItemMatch() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Insurance", "Auto Insurance", -200.0);
            Calendar date = makeDate(2026, Calendar.APRIL, 1);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    null, forecastItem, date, -200.0, version);

            BudgetItem mockBudgetItem = mock(BudgetItem.class);
            when(mockBudgetItem.getId()).thenReturn(budgetItemId);
            budgetItemsByPayeeMap.put("Auto Insurance", List.of(mockBudgetItem));

            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertEquals(1, insertedForecastItems.size());
            assertEquals(budgetItemId, insertedForecastItems.get(0).getIdBudgetItem());
            assertEquals(1, insertedTransactions.size());
            verify(mockView).say(contains("has been added to the forecast"));
        }

        @Test
        @DisplayName("No ForecastItem, multiple BudgetItem matches → prompts user to select")
        void testNewTransaction_MultipleBudgetItemMatches() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Insurance", "Auto Insurance", -200.0);
            Calendar date = makeDate(2026, Calendar.APRIL, 1);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    null, forecastItem, date, -200.0, version);

            UUID budgetItemId2 = UUID.randomUUID();
            BudgetItem mockBudgetItem1 = mock(BudgetItem.class);
            when(mockBudgetItem1.getId()).thenReturn(budgetItemId);
            BudgetItem mockBudgetItem2 = mock(BudgetItem.class);
            when(mockBudgetItem2.getId()).thenReturn(budgetItemId2);
            List<BudgetItem> matchingItems = List.of(mockBudgetItem1, mockBudgetItem2);
            budgetItemsByPayeeMap.put("Auto Insurance", matchingItems);

            when(mockBudgetController.getUserSelectedBudgetItem(matchingItems)).thenReturn(mockBudgetItem1);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            verify(mockBudgetController).getUserSelectedBudgetItem(matchingItems);
            assertEquals(1, insertedForecastItems.size());
            assertEquals(budgetItemId, insertedForecastItems.get(0).getIdBudgetItem());
            verify(mockView).say(contains("has been added to the forecast"));
        }

        @Test
        @DisplayName("New transaction gets found=true and overridden=true")
        void testNewTransaction_FlagsSetCorrectly() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Utilities", "Water", -50.0);
            Calendar date = makeDate(2026, Calendar.APRIL, 10);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    null, forecastItem, date, -50.0, version);

            forecastItemByNameMap.put("Utilities|Water", forecastItem);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            ForecastTransaction inserted = insertedTransactions.get(0);
            assertTrue(inserted.isFound());
            assertTrue(inserted.isOverridden());
            assertNotNull(inserted.getId());
        }

        @Test
        @DisplayName("No ForecastItem, no BudgetItem match → NullPointerException (known bug)")
        void testNewTransaction_NoBudgetItemMatch_ThrowsNPE() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Unknown", "New Vendor", -50.0);
            Calendar date = makeDate(2026, Calendar.APRIL, 10);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    null, forecastItem, date, -50.0, version);

            // No existing ForecastItem and no BudgetItem match (budgetItemsByPayeeMap is empty)
            setupExternalSource(List.of(ssTransaction));

            // This exposes the known bug: budgetItem is null, then budgetItem.getId() throws NPE
            assertThrows(NullPointerException.class,
                    () -> forecastController.updateFromExternalSource(),
                    "Should throw NullPointerException because no budget item was found and the code " +
                    "doesn't handle the null case (see TODO in ForecastController)");
        }
    }


    // ──────────────────────────────────────────────────────────────────────
    // Tests: Post-processing
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Post-processing: zeroNotFound and close source")
    class PostProcessing {

        @Test
        @DisplayName("zeroNotFound is called after processing all transactions")
        void testZeroNotFoundCalled() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Utilities", "Electric", -100.0);
            UUID transactionId = UUID.randomUUID();
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -100.0, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -100.0, (Calendar) version.clone());

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            verify(mockForecastTransactionController).zeroNotFound(mockForecast);
        }

        @Test
        @DisplayName("closeForecastTransactionSource is called after processing")
        void testCloseSourceCalled() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Utilities", "Electric", -100.0);
            UUID transactionId = UUID.randomUUID();
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -100.0, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -100.0, (Calendar) version.clone());

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            verify(mockExternalSourceView).closeForecastTransactionSource(anyString());
        }
    }


    // ──────────────────────────────────────────────────────────────────────
    // Tests: Transaction count message
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Transaction count reporting")
    class TransactionCounting {

        @Test
        @DisplayName("Reports correct count after processing multiple transactions")
        void testCorrectCountReported() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Utilities", "Electric", -100.0);
            UUID txId1 = UUID.randomUUID();
            UUID txId2 = UUID.randomUUID();
            UUID txId3 = UUID.randomUUID();
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ss1 = buildForecastTransaction(txId1, forecastItem, date, -100.0, version);
            ForecastTransaction ss2 = buildForecastTransaction(txId2, forecastItem, date, -200.0, version);
            ForecastTransaction ss3 = buildForecastTransaction(txId3, forecastItem, date, -300.0, version);

            dbTransactionMap.put(txId1, buildForecastTransaction(txId1, forecastItem, (Calendar) date.clone(), -100.0, (Calendar) version.clone()));
            dbTransactionMap.put(txId2, buildForecastTransaction(txId2, forecastItem, (Calendar) date.clone(), -200.0, (Calendar) version.clone()));
            dbTransactionMap.put(txId3, buildForecastTransaction(txId3, forecastItem, (Calendar) date.clone(), -300.0, (Calendar) version.clone()));
            setupExternalSource(List.of(ss1, ss2, ss3));

            forecastController.updateFromExternalSource();

            verify(mockView).say(contains("Successfully processed 3 forecast transactions"));
        }

        @Test
        @DisplayName("Reports zero count message when external source has empty list")
        void testZeroTransactions() throws Exception {
            setupExternalSource(List.of());

            forecastController.updateFromExternalSource();

            verify(mockView).say(contains("no forecast transactions"));
        }
    }


    // ──────────────────────────────────────────────────────────────────────
    // Tests: Found flag management
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Found flag management")
    class FoundFlagManagement {

        @Test
        @DisplayName("setAllFound(false) is called before processing transactions")
        void testSetAllFoundFalseCalledAtStart() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Utilities", "Electric", -100.0);
            UUID transactionId = UUID.randomUUID();
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -100.0, version);
            dbTransactionMap.put(transactionId, buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -100.0, (Calendar) version.clone()));
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertTrue(setAllFoundCalled, "setAllFound should have been called");
            assertFalse(setAllFoundValue, "setAllFound should have been called with false");
        }

        @Test
        @DisplayName("Existing transaction's found flag is set to true after matching")
        void testFoundFlagSetOnMatchedTransaction() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Utilities", "Electric", -100.0);
            UUID transactionId = UUID.randomUUID();
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -100.0, version);
            ForecastTransaction dbTransaction = buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -100.0, (Calendar) version.clone());
            assertFalse(dbTransaction.isFound(), "Should start as not found");

            dbTransactionMap.put(transactionId, dbTransaction);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertTrue(dbTransaction.isFound(), "DB transaction should be marked as found after matching");
        }
    }


    // ──────────────────────────────────────────────────────────────────────
    // Tests: File type detection
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("File type detection")
    class FileTypeDetection {

        @Test
        @DisplayName("Falls back to CSV when no Excel file exists")
        void testCsvFallback() throws Exception {
            existingFileExtension = ".csv";

            ForecastItem forecastItem = buildForecastItem("Utilities", "Electric", -100.0);
            UUID transactionId = UUID.randomUUID();
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -100.0, version);
            dbTransactionMap.put(transactionId, buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -100.0, (Calendar) version.clone()));
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            verify(mockView).say(contains("CSV/TSV forecast file"));
            verify(mockView).say(contains("Successfully processed 1"));
        }

        @Test
        @DisplayName("Prefers Excel over CSV when Excel file exists")
        void testExcelPreferred() throws Exception {
            existingFileExtension = ".xlsx";

            ForecastItem forecastItem = buildForecastItem("Utilities", "Electric", -100.0);
            UUID transactionId = UUID.randomUUID();
            Calendar date = makeDate(2026, Calendar.MARCH, 15);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    transactionId, forecastItem, date, -100.0, version);
            dbTransactionMap.put(transactionId, buildForecastTransaction(
                    transactionId, forecastItem, (Calendar) date.clone(), -100.0, (Calendar) version.clone()));
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            verify(mockView).say(contains("Excel forecast file"));
            verify(mockView, never()).say(contains("CSV/TSV forecast file"));
        }
    }


    // ──────────────────────────────────────────────────────────────────────
    // Tests: Payee and category handling
    // ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Payee and category handling")
    class PayeeAndCategoryHandling {

        @Test
        @DisplayName("Payee from spreadsheet is used to look up ForecastItem by name")
        void testPayeeLookup() throws Exception {
            ForecastItem forecastItem = buildForecastItem("Groceries", "Walmart", -75.0);
            Calendar date = makeDate(2026, Calendar.APRIL, 1);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    null, forecastItem, date, -75.0, version);

            forecastItemByNameMap.put("Groceries|Walmart", forecastItem);
            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertSame(forecastItem, ssTransaction.getForecastItem());
            assertEquals(0, insertedForecastItems.size());
            assertEquals(1, insertedTransactions.size());
        }

        @Test
        @DisplayName("Category+Payee combination is used for matching, not payee alone")
        void testCategoryAndPayeeCombination() throws Exception {
            ForecastItem itemB = buildForecastItem(UUID.randomUUID(), "Household", "Walmart", -100.0);

            forecastItemByNameMap.put("Household|Walmart", itemB);
            // Note: "Food|Walmart" is NOT registered

            Calendar date = makeDate(2026, Calendar.APRIL, 1);
            Calendar version = makeVersion(2026, Calendar.MARCH, 1, 10);

            ForecastTransaction ssTransaction = buildForecastTransaction(
                    null, itemB, date, -100.0, version);

            setupExternalSource(List.of(ssTransaction));

            forecastController.updateFromExternalSource();

            assertSame(itemB, ssTransaction.getForecastItem(),
                    "Should match by category+payee combination");
        }
    }
}



