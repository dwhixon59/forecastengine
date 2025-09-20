package com.hixon.financialApp.test;

import com.hixon.financialApp.controller.BudgetController;
import com.hixon.financialApp.controller.CancelException;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.controller.SkipException;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.notification.async.file.fileBasedNotificationService;
import com.hixon.financialApp.view.base.ViewInt;
import com.hixon.financialApp.view.cmdLine.ViewCmdline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BudgetControllerTest {

    /*
     * Member variables for budgetControllerTest:
     */
    TestController testController;

    /*
     * Constructors for budgetControllerTest:
     */
    public BudgetControllerTest(TestController testController) {
        this.testController = testController;
    }

    /*
     * Main methods for the budgetControllerTest class:
     */
    // Test the assignBudgetItem method:
    public static void main(String[] goals) throws Exception {

        // Create a TestController with the user 'dwhixon', the database connection, the command line transaction
        // resolver and the file-based notification service:
        TestController testController = new TestController("dwhixon", "Bill Pay Account",
                "Bill Pay Account", "Bill Pay Account", new ViewCmdline(), new fileBasedNotificationService());

        // Create the test object:
        BudgetControllerTest budgetControllerTest = new BudgetControllerTest(testController);

        // Create the budget controller, budget and merchant objects:
        Register register = Register.getByName("Bill Pay Account");
        Budget budget = Budget.getByName("Bill Pay Account");
        Forecast forecast = Forecast.getByName("Bill Pay Account");
        Merchant merchant = Merchant.getByName("Amazon");
        BudgetController budgetController = new BudgetController(register, budget, forecast, testController.getView(),
                testController.getNotificationService());

        // Test the getBudgetItemByNameFullText() method:
        while (true) {
            BudgetItem budgetItem = null;
            String seedName = "water";
            try {

                budgetItem = budgetController.getBudgetItemByNameFullText(seedName);
                System.out.println("The selected budget item is:  " + budgetItem);

            } catch (CancelException ce) {
                System.out.println("Caught CancelException: " + ce.getMessage());
            } catch (SkipException se) {
                System.out.println("Caught SkipException: " + se.getMessage());
            } catch (QuitException qe) {
                System.out.println("Caught QuitException: " + qe.getMessage());
                break;
            } catch (Exception e) {
                System.out.println("Caught unexpected exception: " + e.getMessage());
                break;
            }
        }

        // Test the assignBudgetItemsToMerchant method:
        while (true) {
            try {

                List<BudgetItemMerchant> budgetItems = new ArrayList<>();
                budgetController.assignBudgetItemsToMerchant(merchant, budgetItems);
                showWork(budgetItems);

                // Delete any test budget items added during the call to assignBudgetItems:

            } catch (CancelException ce) {
                System.out.println("Caught CancelException: " + ce.getMessage());
            } catch (SkipException se) {
                System.out.println("Caught SkipException: " + se.getMessage());
            } catch (QuitException qe) {
                System.out.println("Caught QuitException: " + qe.getMessage());
                break;
            } catch (Exception e) {
                System.out.println("Caught unexpected exception: " + e.getMessage());
                break;
            }
        }
    }

    public static void showWork(List<BudgetItemMerchant> budgetItems) throws SQLException, EntityException {
        System.out.println("\nbudgetItems: " + budgetItems);
        //budgetItems.get().forEach(payee -> System.out.println(payee));
    }

    import org.junit.jupiter.api.*;
    import static org.junit.jupiter.api.Assertions.*;
    import org.mockito.Mockito;
    import com.hixon.financialApp.view.base.ViewInt;

    class BudgetControllerUnitTest {
        private BudgetController budgetController;
        private Budget budget;
        private Register register;
        private Forecast forecast;
        private ViewInt view;

        @BeforeEach
        void setUp() {
            budget = Mockito.mock(Budget.class);
            register = Mockito.mock(Register.class);
            forecast = Mockito.mock(Forecast.class);
            view = Mockito.mock(ViewInt.class);
            budgetController = new BudgetController(register, budget, forecast, view, null);
            Mockito.when(budget.getName()).thenReturn("TestBudget");
            Mockito.when(budget.getBudgetItems()).thenReturn(new ArrayList<>());
        }

        @Test
        void testAddBudgetItemWithValidInput() throws Exception {
            // Simulate valid user input for all fields
            Mockito.when(view.getResponseString(Mockito.anyString()))
                .thenReturn("Food", "Walmart", "Groceries", "MONTHLY", "100.0", "0.0", "0.0", "2025-09-18", "12", "2026-09-18", "EXPENSE", "NORMAL", "RECURRING", "CASH", "TestBudget");
            BudgetItem item = budgetController.getBudgetItemFromUser();
            assertEquals("Food", item.getCategory());
            assertEquals("Walmart", item.getName());
            assertEquals(100.0, item.getAmount());
            assertEquals("Groceries", item.getMemo());
            assertEquals(12, item.getNumberOfPayments());
            assertEquals("TestBudget", budget.getName());
        }

        @Test
        void testAddBudgetItemWithDefaults() throws Exception {
            // Simulate pressing Enter for all fields (accept defaults)
            Mockito.when(view.getResponseString(Mockito.anyString())).thenReturn("");
            BudgetItem item = budgetController.getBudgetItemFromUser();
            assertEquals("General", item.getCategory());
            assertEquals("Unknown", item.getName());
            assertEquals(0.0, item.getAmount());
        }

        @Test
        void testAddBudgetItemWithInvalidEnumReprompt() throws Exception {
            // Simulate invalid enum, then valid
            Mockito.when(view.getResponseString(Mockito.contains("Period Type")))
                .thenReturn("INVALID", "MONTHLY");
            Mockito.when(view.getResponseString(Mockito.anyString())).thenReturn("");
            BudgetItem item = budgetController.getBudgetItemFromUser();
            assertEquals("MONTHLY", item.getPeriod().name());
            Mockito.verify(view, Mockito.atLeastOnce()).say(Mockito.contains("Invalid period type"));
        }

        @Test
        void testAddBudgetItemWithInvalidNumberReprompt() throws Exception {
            // Simulate invalid number, then valid
            Mockito.when(view.getResponseString(Mockito.contains("Amount")))
                .thenReturn("abc", "123.45");
            Mockito.when(view.getResponseString(Mockito.anyString())).thenReturn("");
            BudgetItem item = budgetController.getBudgetItemFromUser();
            assertEquals(123.45, item.getAmount());
            Mockito.verify(view, Mockito.atLeastOnce()).say(Mockito.contains("Invalid amount"));
        }

        @Test
        void testAddBudgetItemWithInvalidDateReprompt() throws Exception {
            // Simulate invalid date, then valid
            Mockito.when(view.getResponseString(Mockito.contains("Start Date")))
                .thenReturn("notadate", "2025-09-18");
            Mockito.when(view.getResponseString(Mockito.anyString())).thenReturn("");
            BudgetItem item = budgetController.getBudgetItemFromUser();
            assertNotNull(item.getStartDate());
            Mockito.verify(view, Mockito.atLeastOnce()).say(Mockito.contains("Invalid date format"));
        }

        @Test
        void testFindBudgetItems() {
            List<BudgetItem> items = new ArrayList<>();
            BudgetItem item1 = Mockito.mock(BudgetItem.class);
            Mockito.when(item1.getName()).thenReturn("Walmart");
            Mockito.when(item1.getCategory()).thenReturn("Food");
            items.add(item1);
            Mockito.when(budget.getBudgetItems()).thenReturn(items);
            List<BudgetItem> found = budgetController.findBudgetItems("Walmart");
            assertEquals(1, found.size());
            assertEquals(item1, found.get(0));
        }

        @Test
        void testSelectBudgetItem() throws Exception {
            List<BudgetItem> items = new ArrayList<>();
            BudgetItem item1 = Mockito.mock(BudgetItem.class);
            Mockito.when(item1.getName()).thenReturn("Walmart");
            items.add(item1);
            Mockito.when(view.selectFromNumberedList(Mockito.anyString(), Mockito.anyList(), Mockito.anyBoolean())).thenReturn(0);
            BudgetItem selected = budgetController.getUserSelectedBudgetItem(items);
            assertEquals(item1, selected);
        }

        @Test
        void testDeleteBudgetItem() throws Exception {
            BudgetItem item = Mockito.mock(BudgetItem.class);
            item.delete(); // Should not throw
            Mockito.verify(item, Mockito.times(1)).delete();
        }
    }
}
