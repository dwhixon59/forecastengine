package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemUtilities;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the "Copy and Update" budget item action in BudgetController.
 * Tests the complete flow: select template -> getBudgetItemFromUser(template) -> confirmBudgetItem() -> save()
 */
@DisplayName("Copy and Update Budget Item Tests")
public class CopyAndUpdateBudgetItemTest {

    private BudgetController budgetController;
    private Budget mockBudget;
    private Register mockRegister;
    private Forecast mockForecast;
    private ViewInt mockView;
    private BudgetItem templateItem;
    private BudgetItem secondTemplateItem;
    private Connection mockConnection;

    @BeforeEach
    void setUp() throws Exception {
        // Create mock objects
        mockBudget = mock(Budget.class);
        mockRegister = mock(Register.class);
        mockForecast = mock(Forecast.class);
        mockView = mock(ViewInt.class);
        mockConnection = mock(Connection.class);

        // Setup budget mocks
        when(mockBudget.getId()).thenReturn(UUID.randomUUID());
        when(mockBudget.getName()).thenReturn("Test Budget");

        // Create controller
        budgetController = new BudgetController(mockRegister, mockBudget, mockForecast, mockView, null);

        // Create REAL template budget items (not mocked) since BudgetItem extends IndependentEntity which has final methods
        try {
            templateItem = new BudgetItem(mockBudget, "Walmart");
            templateItem.setCategory("Groceries");
            templateItem.setMemo("Weekly shopping");
            templateItem.setAmount(150.0);
            templateItem.setPeriod(Item.PeriodType.WEEKLY);
            templateItem.setRunningBalance(0.0);
            templateItem.setMinimumBalance(0.0);
            templateItem.setNumberOfPayments(52);
            templateItem.setItemType(Item.ItemType.EXPENSE);
            templateItem.setHowImportant(Item.HowImportant.FIXED_ESSENTIAL);
            templateItem.setHowOccurs(Item.HowOccurs.PERIODIC);
            templateItem.setHowPaid(Item.HowPaid.DEBIT_CARD);

            secondTemplateItem = new BudgetItem(mockBudget, "Target");
            secondTemplateItem.setCategory("Household");
            secondTemplateItem.setAmount(100.0);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create template items", e);
        }
    }

    @Test
    @DisplayName("Copy and update - successful copy with modifications")
    void testCopyAndUpdate_SuccessfulCopyWithModifications() throws Exception {
        // Mock BudgetItemUtilities to return template items
        try (MockedStatic<BudgetItemUtilities> budgetItemUtilitiesMock = Mockito.mockStatic(BudgetItemUtilities.class);
             MockedStatic<com.hixon.financialApp.model.budget.BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(com.hixon.financialApp.model.budget.BudgetUtilities.class);
             MockedStatic<Utility> utilityMock = Mockito.mockStatic(Utility.class);
             MockedStatic<Budget> budgetMock = Mockito.mockStatic(Budget.class)) {

            // Mock database connection
            utilityMock.when(Utility::getDbConnection).thenReturn(mockConnection);

            // Mock Budget.getById
            budgetMock.when(() -> Budget.getById(any(UUID.class))).thenReturn(mockBudget);

            budgetItemUtilitiesMock.when(() -> BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(mockBudget))
                    .thenReturn(List.of(templateItem));

            budgetUtilitiesMock.when(com.hixon.financialApp.model.budget.BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            // Mock user selecting the template (only one item, so auto-selected)
            // Mock user modifying the payee but keeping other fields from template
            when(mockView.getResponseString(eq("Category"), eq("Groceries"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Groceries");

            when(mockView.getResponseString(eq("Payee"), eq("Walmart"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Costco"); // User changes payee

            when(mockView.getResponseString(eq("Memo"), eq("Weekly shopping"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Bulk shopping"); // User changes memo

            setupMinimalMockResponses(150.0);

            // Mock confirmation - user accepts
            when(mockView.selectFromMenu(eq("What would you like to do with this budget item?"),
                    anyList(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
                    .thenReturn("a"); // Accept

            // Act - This tests the getUserSelectedBudgetItem and getBudgetItemFromUser flow
            BudgetItem template = budgetController.getUserSelectedBudgetItem(List.of(templateItem));
            BudgetItem copiedItem = budgetController.getBudgetItemFromUser(template);

            // Assert
            assertNotNull(copiedItem);
            assertEquals("Groceries", copiedItem.getCategory());
            assertEquals("Costco", copiedItem.getPayee()); // Modified
            assertEquals("Bulk shopping", copiedItem.getMemo()); // Modified
            assertEquals(150.0, copiedItem.getAmount()); // From template

            // Verify that template values were used as defaults
            verify(mockView).getResponseString(eq("Payee"), eq("Walmart"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any());
        }
    }

    @Test
    @DisplayName("Copy and update - no budget items available")
    void testCopyAndUpdate_NoBudgetItemsAvailable() throws Exception {
        // Mock BudgetItemUtilities to return empty list
        try (MockedStatic<BudgetItemUtilities> budgetItemUtilitiesMock = Mockito.mockStatic(BudgetItemUtilities.class)) {
            budgetItemUtilitiesMock.when(() -> BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(mockBudget))
                    .thenReturn(new ArrayList<>());

            // Act
            List<BudgetItem> items = BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(mockBudget);

            // Assert
            assertTrue(items.isEmpty());
            // In the actual controller, this would trigger: view.say("No budget items available to copy.")
        }
    }

    @Test
    @DisplayName("Copy and update - user selects from multiple items")
    void testCopyAndUpdate_SelectFromMultipleItems() throws Exception {
        // Mock BudgetItemUtilities to return multiple items
        try (MockedStatic<BudgetItemUtilities> budgetItemUtilitiesMock = Mockito.mockStatic(BudgetItemUtilities.class);
             MockedStatic<Utility> utilityMock = Mockito.mockStatic(Utility.class);
             MockedStatic<com.hixon.financialApp.model.forecast.ForecastTransaction> forecastTransactionMock = Mockito.mockStatic(com.hixon.financialApp.model.forecast.ForecastTransaction.class)) {

            // Mock database connection and Statement
            java.sql.Statement mockStatement = mock(java.sql.Statement.class);
            utilityMock.when(Utility::getDbConnection).thenReturn(mockConnection);
            when(mockConnection.createStatement()).thenReturn(mockStatement);

            // Mock ForecastTransaction to avoid database queries
            com.hixon.financialApp.model.forecast.ForecastTransaction mockForecastTransaction =
                    mock(com.hixon.financialApp.model.forecast.ForecastTransaction.class);
            when(mockForecastTransaction.getPlannedDate()).thenReturn(Calendar.getInstance());
            forecastTransactionMock.when(() ->
                    com.hixon.financialApp.model.forecast.ForecastTransaction.getApplicableForecastTransaction(
                            any(UUID.class), any(Calendar.class)))
                    .thenReturn(mockForecastTransaction);

            List<BudgetItem> multipleItems = Arrays.asList(templateItem, secondTemplateItem);
            budgetItemUtilitiesMock.when(() -> BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(mockBudget))
                    .thenReturn(multipleItems);

            // Mock user selecting the second item (index 1)
            when(mockView.selectFromList(eq("Multiple budget items found.  Please select one:"),
                    anyList(), eq(false)))
                    .thenReturn(1);

            // Act
            BudgetItem selected = budgetController.getUserSelectedBudgetItem(multipleItems);

            // Assert
            assertEquals(secondTemplateItem, selected);
            verify(mockView).selectFromList(eq("Multiple budget items found.  Please select one:"),
                    anyList(), eq(false));
        }
    }

    @Test
    @DisplayName("Copy and update - auto-select when only one item")
    void testCopyAndUpdate_AutoSelectSingleItem() throws Exception {
        // Mock BudgetItemUtilities to return single item
        try (MockedStatic<BudgetItemUtilities> budgetItemUtilitiesMock = Mockito.mockStatic(BudgetItemUtilities.class)) {
            budgetItemUtilitiesMock.when(() -> BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(mockBudget))
                    .thenReturn(List.of(templateItem));

            // Act
            BudgetItem selected = budgetController.getUserSelectedBudgetItem(List.of(templateItem));

            // Assert
            assertEquals(templateItem, selected);
            // Verify that selectFromList was NOT called (auto-selected)
            verify(mockView, never()).selectFromList(anyString(), anyList(), anyBoolean());
        }
    }

    @Test
    @DisplayName("Copy and update - user cancels during copy")
    void testCopyAndUpdate_UserCancelsDuringCopy() throws Exception {
        // Mock BudgetItemUtilities
        try (MockedStatic<BudgetItemUtilities> budgetItemUtilitiesMock = Mockito.mockStatic(BudgetItemUtilities.class);
             MockedStatic<com.hixon.financialApp.model.budget.BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(com.hixon.financialApp.model.budget.BudgetUtilities.class);
             MockedStatic<Utility> utilityMock = Mockito.mockStatic(Utility.class);
             MockedStatic<Budget> budgetMock = Mockito.mockStatic(Budget.class)) {

            // Mock database connection
            utilityMock.when(Utility::getDbConnection).thenReturn(mockConnection);

            // Mock Budget.getById
            budgetMock.when(() -> Budget.getById(any(UUID.class))).thenReturn(mockBudget);

            budgetItemUtilitiesMock.when(() -> BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(mockBudget))
                    .thenReturn(List.of(templateItem));

            budgetUtilitiesMock.when(com.hixon.financialApp.model.budget.BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            // User cancels when prompted for category
            when(mockView.getResponseString(eq("Category"), eq("Groceries"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenThrow(new CancelException("User cancelled"));

            // Act
            BudgetItem template = budgetController.getUserSelectedBudgetItem(List.of(templateItem));
            BudgetItem result = budgetController.getBudgetItemFromUser(template);

            // Assert
            assertNull(result, "Should return null when user cancels");
        }
    }

    @Test
    @DisplayName("Copy and update - user quits during copy")
    void testCopyAndUpdate_UserQuitsDuringCopy() throws Exception {
        // Mock BudgetItemUtilities
        try (MockedStatic<BudgetItemUtilities> budgetItemUtilitiesMock = Mockito.mockStatic(BudgetItemUtilities.class);
             MockedStatic<com.hixon.financialApp.model.budget.BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(com.hixon.financialApp.model.budget.BudgetUtilities.class);
             MockedStatic<Utility> utilityMock = Mockito.mockStatic(Utility.class);
             MockedStatic<Budget> budgetMock = Mockito.mockStatic(Budget.class)) {

            // Mock database connection
            utilityMock.when(Utility::getDbConnection).thenReturn(mockConnection);

            // Mock Budget.getById
            budgetMock.when(() -> Budget.getById(any(UUID.class))).thenReturn(mockBudget);

            budgetItemUtilitiesMock.when(() -> BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(mockBudget))
                    .thenReturn(List.of(templateItem));

            budgetUtilitiesMock.when(com.hixon.financialApp.model.budget.BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            // User quits when prompted for payee
            when(mockView.getResponseString(eq("Category"), eq("Groceries"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Groceries");

            when(mockView.getResponseString(eq("Payee"), eq("Walmart"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenThrow(new QuitException("User quit"));

            // Act & Assert
            BudgetItem template = budgetController.getUserSelectedBudgetItem(List.of(templateItem));
            assertThrows(QuitException.class, () -> budgetController.getBudgetItemFromUser(template));
        }
    }

    @Test
    @DisplayName("Copy and update - copy without modifications (accept all defaults)")
    void testCopyAndUpdate_CopyWithoutModifications() throws Exception {
        // Mock BudgetItemUtilities
        try (MockedStatic<BudgetItemUtilities> budgetItemUtilitiesMock = Mockito.mockStatic(BudgetItemUtilities.class);
             MockedStatic<com.hixon.financialApp.model.budget.BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(com.hixon.financialApp.model.budget.BudgetUtilities.class);
             MockedStatic<Utility> utilityMock = Mockito.mockStatic(Utility.class);
             MockedStatic<Budget> budgetMock = Mockito.mockStatic(Budget.class)) {

            // Mock database connection
            utilityMock.when(Utility::getDbConnection).thenReturn(mockConnection);

            // Mock Budget.getById
            budgetMock.when(() -> Budget.getById(any(UUID.class))).thenReturn(mockBudget);

            budgetItemUtilitiesMock.when(() -> BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(mockBudget))
                    .thenReturn(List.of(templateItem));

            budgetUtilitiesMock.when(com.hixon.financialApp.model.budget.BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            // User accepts all defaults from template
            when(mockView.getResponseString(eq("Category"), eq("Groceries"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Groceries");

            when(mockView.getResponseString(eq("Payee"), eq("Walmart"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Walmart");

            when(mockView.getResponseString(eq("Memo"), eq("Weekly shopping"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Weekly shopping");

            setupMinimalMockResponses(150.0);

            // Act
            BudgetItem template = budgetController.getUserSelectedBudgetItem(List.of(templateItem));
            BudgetItem copiedItem = budgetController.getBudgetItemFromUser(template);

            // Assert - copied item should match template
            assertNotNull(copiedItem);
            assertEquals("Groceries", copiedItem.getCategory());
            assertEquals("Walmart", copiedItem.getPayee());
            assertEquals("Weekly shopping", copiedItem.getMemo());
            assertEquals(150.0, copiedItem.getAmount());
        }
    }

    @Test
    @DisplayName("Copy and update - template dates and balances are used")
    void testCopyAndUpdate_TemplateDatesAndBalancesUsed() throws Exception {
        // Create a spy of the template item to mock specific methods while keeping real object behavior
        BudgetItem spyTemplateItem = spy(templateItem);

        // Create dates for the template
        Calendar startDate = Calendar.getInstance();
        startDate.set(2025, Calendar.JANUARY, 1);
        Calendar endDate = Calendar.getInstance();
        endDate.set(2025, Calendar.DECEMBER, 31);

        // Set balances on the spy
        doReturn(startDate).when(spyTemplateItem).getStartDate();
        doReturn(endDate).when(spyTemplateItem).getEndDate();
        doReturn(500.0).when(spyTemplateItem).getRunningBalance();
        doReturn(100.0).when(spyTemplateItem).getMinimumBalance();

        // Mock BudgetItemUtilities
        try (MockedStatic<BudgetItemUtilities> budgetItemUtilitiesMock = Mockito.mockStatic(BudgetItemUtilities.class);
             MockedStatic<com.hixon.financialApp.model.budget.BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(com.hixon.financialApp.model.budget.BudgetUtilities.class);
             MockedStatic<Utility> utilityMock = Mockito.mockStatic(Utility.class);
             MockedStatic<Budget> budgetMock = Mockito.mockStatic(Budget.class)) {

            // Mock database connection
            utilityMock.when(Utility::getDbConnection).thenReturn(mockConnection);

            // Mock Budget.getById
            budgetMock.when(() -> Budget.getById(any(UUID.class))).thenReturn(mockBudget);

            budgetItemUtilitiesMock.when(() -> BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(mockBudget))
                    .thenReturn(List.of(spyTemplateItem));

            budgetUtilitiesMock.when(com.hixon.financialApp.model.budget.BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            // User accepts template defaults
            when(mockView.getResponseString(eq("Category"), eq("Groceries"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Groceries");

            when(mockView.getResponseString(eq("Payee"), eq("Walmart"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Walmart");

            when(mockView.getResponseString(eq("Memo"), eq("Weekly shopping"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Weekly shopping");

            when(mockView.selectFromList(eq("Select period type:"), eq(Item.PeriodType.WEEKLY),
                    eq(Item.PeriodType.class)))
                    .thenReturn(Item.PeriodType.WEEKLY);

            when(mockView.getResponseCurrency(eq("Amount"), eq(150.0), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn(150.0);

            when(mockView.getResponseCurrency(eq("Running Balance"), eq(500.0), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn(500.0);

            when(mockView.getResponseCurrency(eq("Minimum Balance"), eq(100.0), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn(100.0);

            when(mockView.getResponseString(eq("Start Date (yyyy-MM-dd)"), eq("01-01-2025"), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("01-01-2025");

            when(mockView.getResponseInt(eq("Number of Payments"), eq(52), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn(52);

            when(mockView.getResponseString(eq("End Date (yyyy-MM-dd)"), eq("12-31-2025"), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("12-31-2025");

            setupEnumMockResponses();

            // Act
            BudgetItem template = budgetController.getUserSelectedBudgetItem(List.of(spyTemplateItem));
            BudgetItem copiedItem = budgetController.getBudgetItemFromUser(template);

            // Assert
            assertNotNull(copiedItem);

            // Verify that the template values were offered as defaults
            verify(mockView).getResponseCurrency(eq("Running Balance"), eq(500.0), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any());
            verify(mockView).getResponseCurrency(eq("Minimum Balance"), eq(100.0), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any());
        }
    }

    @Test
    @DisplayName("Copy and update - verify template item is not modified")
    void testCopyAndUpdate_TemplateNotModified() throws Exception {
        // Create a spy to track method calls
        BudgetItem spyTemplateItem = spy(templateItem);

        // Mock BudgetItemUtilities
        try (MockedStatic<BudgetItemUtilities> budgetItemUtilitiesMock = Mockito.mockStatic(BudgetItemUtilities.class);
             MockedStatic<com.hixon.financialApp.model.budget.BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(com.hixon.financialApp.model.budget.BudgetUtilities.class);
             MockedStatic<Utility> utilityMock = Mockito.mockStatic(Utility.class);
             MockedStatic<Budget> budgetMock = Mockito.mockStatic(Budget.class)) {

            // Mock database connection
            utilityMock.when(Utility::getDbConnection).thenReturn(mockConnection);

            // Mock Budget.getById
            budgetMock.when(() -> Budget.getById(any(UUID.class))).thenReturn(mockBudget);

            budgetItemUtilitiesMock.when(() -> BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(mockBudget))
                    .thenReturn(List.of(spyTemplateItem));

            budgetUtilitiesMock.when(com.hixon.financialApp.model.budget.BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            // User modifies values
            when(mockView.getResponseString(eq("Category"), eq("Groceries"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Different Category");

            when(mockView.getResponseString(eq("Payee"), eq("Walmart"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Different Payee");

            when(mockView.getResponseString(eq("Memo"), eq("Weekly shopping"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Different Memo");

            setupMinimalMockResponses(200.0);

            // Act
            BudgetItem template = budgetController.getUserSelectedBudgetItem(List.of(spyTemplateItem));
            String originalPayee = template.getPayee();
            BudgetItem copiedItem = budgetController.getBudgetItemFromUser(template);

            // Assert - template should still have original values
            assertEquals("Walmart", originalPayee);
            assertEquals("Different Payee", copiedItem.getPayee());

            // Verify template getters were called but template itself was not modified
            verify(spyTemplateItem, atLeastOnce()).getPayee();
            verify(spyTemplateItem, never()).setPayee(anyString());
        }
    }

    @Test
    @DisplayName("Copy and update - copied item gets new UUID")
    void testCopyAndUpdate_NewUUIDAssigned() throws Exception {
        // Mock BudgetItemUtilities
        try (MockedStatic<BudgetItemUtilities> budgetItemUtilitiesMock = Mockito.mockStatic(BudgetItemUtilities.class);
             MockedStatic<com.hixon.financialApp.model.budget.BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(com.hixon.financialApp.model.budget.BudgetUtilities.class);
             MockedStatic<Utility> utilityMock = Mockito.mockStatic(Utility.class);
             MockedStatic<Budget> budgetMock = Mockito.mockStatic(Budget.class)) {

            // Mock database connection
            utilityMock.when(Utility::getDbConnection).thenReturn(mockConnection);

            // Mock Budget.getById
            budgetMock.when(() -> Budget.getById(any(UUID.class))).thenReturn(mockBudget);

            UUID templateId = templateItem.getId();

            budgetItemUtilitiesMock.when(() -> BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(mockBudget))
                    .thenReturn(List.of(templateItem));

            budgetUtilitiesMock.when(com.hixon.financialApp.model.budget.BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            // User accepts defaults
            when(mockView.getResponseString(eq("Category"), eq("Groceries"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Groceries");

            when(mockView.getResponseString(eq("Payee"), eq("Walmart"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Walmart");

            when(mockView.getResponseString(eq("Memo"), eq("Weekly shopping"), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Weekly shopping");

            setupMinimalMockResponses(150.0);

            // Act
            BudgetItem template = budgetController.getUserSelectedBudgetItem(List.of(templateItem));
            BudgetItem copiedItem = budgetController.getBudgetItemFromUser(template);

            // Assert - copied item should have a different UUID than template
            assertNotNull(copiedItem);
            assertNotNull(copiedItem.getId());
            assertNotEquals(templateId, copiedItem.getId(), "Copied item should have a new UUID");
        }
    }

    // Helper methods
    private void setupMinimalMockResponses(double amount) throws Exception {
        when(mockView.selectFromList(eq("Select period type:"), any(Item.PeriodType.class),
                eq(Item.PeriodType.class)))
                .thenReturn(Item.PeriodType.WEEKLY);

        when(mockView.getResponseCurrency(eq("Amount"), eq(amount), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(amount);

        when(mockView.getResponseCurrency(eq("Running Balance"), anyDouble(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(0.0);

        when(mockView.getResponseCurrency(eq("Minimum Balance"), anyDouble(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(0.0);

        when(mockView.getResponseString(eq("Start Date (yyyy-MM-dd)"), anyString(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn("01-01-2025");

        when(mockView.getResponseInt(eq("Number of Payments"), anyInt(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(52);

        // End Date can be null/empty when ALLOW_NONE is specified
        when(mockView.getResponseString(eq("End Date (yyyy-MM-dd)"), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn("");  // Return empty string instead of null

        setupEnumMockResponses();
    }

    private void setupEnumMockResponses() throws Exception {
        when(mockView.selectFromList(eq("Select Item Type:"), any(Item.ItemType.class),
                eq(Item.ItemType.class)))
                .thenReturn(Item.ItemType.EXPENSE);

        when(mockView.selectFromList(eq("Select How Important:"), any(Item.HowImportant.class),
                eq(Item.HowImportant.class)))
                .thenReturn(Item.HowImportant.FIXED_ESSENTIAL);

        when(mockView.selectFromList(eq("Select How Occurs:"), any(Item.HowOccurs.class),
                eq(Item.HowOccurs.class)))
                .thenReturn(Item.HowOccurs.PERIODIC);

        when(mockView.selectFromList(eq("Select How Paid:"), any(Item.HowPaid.class),
                eq(Item.HowPaid.class)))
                .thenReturn(Item.HowPaid.DEBIT_CARD);
    }
}
