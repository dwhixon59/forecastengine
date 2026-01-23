package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetUtilities;
import com.hixon.financialApp.model.budget.Item;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.view.base.ViewInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the "Add Budget Item" action in BudgetController.
 * Tests the complete flow: getBudgetItemFromUser() -> confirmBudgetItem() -> save()
 */
@DisplayName("Add Budget Item Tests")
public class AddBudgetItemTest {

    private BudgetController budgetController;
    private Budget mockBudget;
    private Register mockRegister;
    private Forecast mockForecast;
    private ViewInt mockView;
    private Budget mockBudget2;

    @BeforeEach
    void setUp() throws Exception {
        // Create mock objects
        mockBudget = mock(Budget.class);
        mockBudget2 = mock(Budget.class);
        mockRegister = mock(Register.class);
        mockForecast = mock(Forecast.class);
        mockView = mock(ViewInt.class);

        // Setup budget mocks
        when(mockBudget.getId()).thenReturn(UUID.randomUUID());
        when(mockBudget.getName()).thenReturn("Test Budget");
        when(mockBudget2.getId()).thenReturn(UUID.randomUUID());
        when(mockBudget2.getName()).thenReturn("Second Budget");

        // Create controller with SessionController
        SessionController sessionController = new SessionController(mockRegister, mockBudget, mockForecast, mockView, null);
        budgetController = new BudgetController(sessionController);
    }

    @Test
    @DisplayName("Add budget item with valid input - all fields provided")
    void testAddBudgetItem_ValidInput_AllFields() throws Exception {
        // Mock BudgetUtilities to return a single budget
        try (MockedStatic<BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(BudgetUtilities.class)) {
            budgetUtilitiesMock.when(BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            // Setup mock view responses for all fields
            when(mockView.getResponseString(eq("Category"), isNull(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Groceries");

            when(mockView.getResponseString(eq("Payee"), eq(""), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Walmart");

            when(mockView.getResponseString(eq("Memo"), eq(""), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Weekly shopping");

            when(mockView.selectByPositionFromList(eq("Select period type:"), eq(Item.PeriodType.MONTHLY),
                    eq(Item.PeriodType.class)))
                    .thenReturn(Item.PeriodType.WEEKLY);

            when(mockView.getResponseCurrency(eq("Amount"), isNull(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn(150.00);

            when(mockView.getResponseCurrency(eq("Running Balance"), eq(0.0), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn(0.0);

            when(mockView.getResponseCurrency(eq("Minimum Balance"), eq(0.0), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn(0.0);

            when(mockView.getResponseString(eq("Start Date (yyyy-MM-dd)"), anyString(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("2025-10-01");

            when(mockView.getResponseInt(eq("Number of Payments"), eq(0), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn(52);

            when(mockView.getResponseString(eq("End Date (yyyy-MM-dd)"), isNull(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("2026-09-30");

            when(mockView.selectByPositionFromList(eq("Select Item Type:"), eq(Item.ItemType.EXPENSE),
                    eq(Item.ItemType.class)))
                    .thenReturn(Item.ItemType.EXPENSE);

            when(mockView.selectByPositionFromList(eq("Select How Important:"), eq(Item.HowImportant.DISCRETIONARY_NONESSENTIAL),
                    eq(Item.HowImportant.class)))
                    .thenReturn(Item.HowImportant.FIXED_ESSENTIAL);

            when(mockView.selectByPositionFromList(eq("Select How Occurs:"), eq(Item.HowOccurs.PERIODIC),
                    eq(Item.HowOccurs.class)))
                    .thenReturn(Item.HowOccurs.PERIODIC);

            when(mockView.selectByPositionFromList(eq("Select How Paid:"), eq(Item.HowPaid.DEBIT_CARD),
                    eq(Item.HowPaid.class)))
                    .thenReturn(Item.HowPaid.DEBIT_CARD);

            // Act - call getBudgetItemFromUser
            BudgetItem result = budgetController.getBudgetItemFromUser();

            // Assert
            assertNotNull(result);
            assertEquals("Groceries", result.getCategory());
            assertEquals("Walmart", result.getPayee());
            assertEquals("Weekly shopping", result.getMemo());
            assertEquals(Item.PeriodType.WEEKLY, result.getPeriod());
            assertEquals(150.00, result.getAmount());
            assertEquals(52, result.getNumberOfPayments());
            assertEquals(Item.ItemType.EXPENSE, result.getItemType());
            assertEquals(Item.HowImportant.FIXED_ESSENTIAL, result.getHowImportant());
            assertEquals(mockBudget.getId(), result.getIdBudget());

            // Verify view interactions
            verify(mockView).sayH1("Budget Item Entry");
            verify(mockView).sayH2("Budget Assignment");
            verify(mockView).sayH2("Basic Information");
            verify(mockView).sayH2("Schedule and Amount");
            verify(mockView).sayH2("Classification");
        }
    }

    @Test
    @DisplayName("Add budget item - user selects different budget")
    void testAddBudgetItem_SelectDifferentBudget() throws Exception {
        // Mock BudgetUtilities to return multiple budgets
        try (MockedStatic<BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(BudgetUtilities.class)) {
            budgetUtilitiesMock.when(BudgetUtilities::getAllBudgets)
                    .thenReturn(Arrays.asList(mockBudget, mockBudget2));

            // User selects second budget
            when(mockView.selectByNameFromList(eq("Select Budget"), anyList(), eq(mockBudget),
                    anyBoolean(), true, anyBoolean(), anyBoolean(), anyBoolean(), null))
                    .thenReturn(mockBudget2);

            // Setup minimal mock responses for other fields
            setupMinimalValidResponses();

            // Act
            BudgetItem result = budgetController.getBudgetItemFromUser();

            // Assert
            assertNotNull(result);
            assertEquals(mockBudget2.getId(), result.getIdBudget());
            verify(mockView).selectByNameFromList(eq("Select Budget"), anyList(), eq(mockBudget),
                    anyBoolean(), true, anyBoolean(), anyBoolean(), anyBoolean(), null);
        }
    }

    @Test
    @DisplayName("Add budget item - user cancels during input")
    void testAddBudgetItem_UserCancels() throws Exception {
        // Mock BudgetUtilities
        try (MockedStatic<BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(BudgetUtilities.class)) {
            budgetUtilitiesMock.when(BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            // User cancels when entering category
            when(mockView.getResponseString(eq("Category"), isNull(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenThrow(new CancelException("User cancelled"));

            // Act
            BudgetItem result = budgetController.getBudgetItemFromUser();

            // Assert
            assertNull(result, "Should return null when user cancels");
        }
    }

    @Test
    @DisplayName("Add budget item - user quits during input")
    void testAddBudgetItem_UserQuits() throws Exception {
        // Mock BudgetUtilities
        try (MockedStatic<BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(BudgetUtilities.class)) {
            budgetUtilitiesMock.when(BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            // User quits when entering payee
            when(mockView.getResponseString(eq("Category"), isNull(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("Utilities");

            when(mockView.getResponseString(eq("Payee"), eq(""), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenThrow(new QuitException("User quit"));

            // Act & Assert
            assertThrows(QuitException.class, () -> budgetController.getBudgetItemFromUser());
        }
    }

    @Test
    @DisplayName("Add budget item with minimal input - accept all defaults")
    void testAddBudgetItem_MinimalInput_Defaults() throws Exception {
        // Mock BudgetUtilities
        try (MockedStatic<BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(BudgetUtilities.class)) {
            budgetUtilitiesMock.when(BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            setupMinimalValidResponses();

            // Act
            BudgetItem result = budgetController.getBudgetItemFromUser();

            // Assert
            assertNotNull(result);
            assertEquals("Test Category", result.getCategory());
            assertEquals("Test Payee", result.getPayee());
            assertTrue(result.isValid());
        }
    }

    @Test
    @DisplayName("Add budget item - copy from template")
    void testAddBudgetItem_WithTemplate() throws Exception {
        // Create a template budget item
        BudgetItem template = new BudgetItem(mockBudget, "Original Payee");
        template.setCategory("Food");
        template.setMemo("Original memo");
        template.setAmount(100.0);
        template.setPeriod(Item.PeriodType.MONTHLY);

        // Mock BudgetUtilities
        try (MockedStatic<BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(BudgetUtilities.class)) {
            budgetUtilitiesMock.when(BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            // Mock Budget.getById to return mockBudget
            try (MockedStatic<Budget> budgetMock = Mockito.mockStatic(Budget.class)) {
                budgetMock.when(() -> Budget.getById(any(UUID.class))).thenReturn(mockBudget);

                // User modifies the payee but keeps other defaults from template
                when(mockView.getResponseString(eq("Category"), eq("Food"), anyBoolean(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean(), any()))
                        .thenReturn("Food"); // Keep template value

                when(mockView.getResponseString(eq("Payee"), eq("Original Payee"), anyBoolean(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean(), any()))
                        .thenReturn("New Payee"); // Change from template

                when(mockView.getResponseString(eq("Memo"), eq("Original memo"), anyBoolean(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean(), any()))
                        .thenReturn("Original memo");

                when(mockView.selectByPositionFromList(eq("Select period type:"), eq(Item.PeriodType.MONTHLY),
                        eq(Item.PeriodType.class)))
                        .thenReturn(Item.PeriodType.MONTHLY);

                when(mockView.getResponseCurrency(eq("Amount"), eq(100.0), anyBoolean(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean(), any()))
                        .thenReturn(100.0);

                setupAdditionalMinimalResponses();

                // Act
                BudgetItem result = budgetController.getBudgetItemFromUser(template);

                // Assert
                assertNotNull(result);
                assertEquals("Food", result.getCategory());
                assertEquals("New Payee", result.getPayee());
                assertEquals("Original memo", result.getMemo());
                assertEquals(100.0, result.getAmount());

                // Verify that defaults were shown from template
                verify(mockView).getResponseString(eq("Category"), eq("Food"), anyBoolean(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean(), any());
                verify(mockView).getResponseString(eq("Payee"), eq("Original Payee"), anyBoolean(), anyBoolean(),
                        anyBoolean(), anyBoolean(), anyBoolean(), any());
            }
        }
    }

    @Test
    @DisplayName("Add budget item - no budgets available")
    void testAddBudgetItem_NoBudgetsAvailable() throws Exception {
        // Mock BudgetUtilities to return empty list
        try (MockedStatic<BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(BudgetUtilities.class)) {
            budgetUtilitiesMock.when(BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of());

            setupMinimalValidResponses();

            // Act - should use default budget and show error message
            BudgetItem result = budgetController.getBudgetItemFromUser();

            // Assert
            assertNotNull(result);
            verify(mockView).say(contains("Error loading budgets"));
        }
    }

    @Test
    @DisplayName("Add budget item - empty end date allowed")
    void testAddBudgetItem_EmptyEndDate() throws Exception {
        // Mock BudgetUtilities
        try (MockedStatic<BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(BudgetUtilities.class)) {
            budgetUtilitiesMock.when(BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            setupMinimalValidResponses();

            // Override end date to be empty
            when(mockView.getResponseString(eq("End Date (yyyy-MM-dd)"), isNull(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("");

            // Act
            BudgetItem result = budgetController.getBudgetItemFromUser();

            // Assert
            assertNotNull(result);
            assertNull(result.getEndDate(), "End date should be null when empty string provided");
        }
    }

    @Test
    @DisplayName("Verify all enum selections use selectFromList")
    void testAddBudgetItem_EnumSelectionsUseSelectFromList() throws Exception {
        // Mock BudgetUtilities
        try (MockedStatic<BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(BudgetUtilities.class)) {
            budgetUtilitiesMock.when(BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            setupMinimalValidResponses();

            // Act
            BudgetItem result = budgetController.getBudgetItemFromUser();

            // Assert - verify selectFromList was called for each enum
            verify(mockView).selectByPositionFromList(eq("Select period type:"), any(Item.PeriodType.class),
                    eq(Item.PeriodType.class));
            verify(mockView).selectByPositionFromList(eq("Select Item Type:"), any(Item.ItemType.class),
                    eq(Item.ItemType.class));
            verify(mockView).selectByPositionFromList(eq("Select How Important:"), any(Item.HowImportant.class),
                    eq(Item.HowImportant.class));
            verify(mockView).selectByPositionFromList(eq("Select How Occurs:"), any(Item.HowOccurs.class),
                    eq(Item.HowOccurs.class));
            verify(mockView).selectByPositionFromList(eq("Select How Paid:"), any(Item.HowPaid.class),
                    eq(Item.HowPaid.class));
        }
    }

    @Test
    @DisplayName("Add budget item validates date format")
    void testAddBudgetItem_ValidatesDateFormat() throws Exception {
        // Mock BudgetUtilities
        try (MockedStatic<BudgetUtilities> budgetUtilitiesMock = Mockito.mockStatic(BudgetUtilities.class)) {
            budgetUtilitiesMock.when(BudgetUtilities::getAllBudgets)
                    .thenReturn(List.of(mockBudget));

            setupMinimalValidResponses();

            // Valid date format MM-dd-yyyy (not yyyy-MM-dd as the prompt suggests)
            when(mockView.getResponseString(eq("Start Date (yyyy-MM-dd)"), anyString(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), anyBoolean(), any()))
                    .thenReturn("10-08-2025");

            // Act
            BudgetItem result = budgetController.getBudgetItemFromUser();

            // Assert
            assertNotNull(result);
            assertNotNull(result.getStartDate());
            Calendar startDate = result.getStartDate();
            assertEquals(2025, startDate.get(Calendar.YEAR));
            assertEquals(Calendar.OCTOBER, startDate.get(Calendar.MONTH));
            assertEquals(8, startDate.get(Calendar.DAY_OF_MONTH));
        }
    }

    // Helper method to setup minimal valid responses
    private void setupMinimalValidResponses() throws Exception {
        when(mockView.getResponseString(eq("Category"), isNull(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn("Test Category");

        when(mockView.getResponseString(eq("Payee"), anyString(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn("Test Payee");

        when(mockView.getResponseString(eq("Memo"), anyString(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn("Test Memo");

        when(mockView.selectByPositionFromList(eq("Select period type:"), any(), eq(Item.PeriodType.class)))
                .thenReturn(Item.PeriodType.MONTHLY);

        when(mockView.getResponseCurrency(eq("Amount"), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(100.0);

        setupAdditionalMinimalResponses();
    }

    private void setupAdditionalMinimalResponses() throws Exception {
        when(mockView.getResponseCurrency(eq("Running Balance"), anyDouble(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(0.0);

        when(mockView.getResponseCurrency(eq("Minimum Balance"), anyDouble(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(0.0);

        when(mockView.getResponseString(eq("Start Date (yyyy-MM-dd)"), anyString(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn("2025-10-01");

        when(mockView.getResponseInt(eq("Number of Payments"), anyInt(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(12);

        when(mockView.getResponseString(eq("End Date (yyyy-MM-dd)"), any(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any()))
                .thenReturn("2026-10-01");

        when(mockView.selectByPositionFromList(eq("Select Item Type:"), any(), eq(Item.ItemType.class)))
                .thenReturn(Item.ItemType.EXPENSE);

        when(mockView.selectByPositionFromList(eq("Select How Important:"), any(), eq(Item.HowImportant.class)))
                .thenReturn(Item.HowImportant.FIXED_ESSENTIAL);

        when(mockView.selectByPositionFromList(eq("Select How Occurs:"), any(), eq(Item.HowOccurs.class)))
                .thenReturn(Item.HowOccurs.PERIODIC);

        when(mockView.selectByPositionFromList(eq("Select How Paid:"), any(), eq(Item.HowPaid.class)))
                .thenReturn(Item.HowPaid.DEBIT_CARD);
    }
}
