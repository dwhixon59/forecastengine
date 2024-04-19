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
import com.hixon.financialApp.view.cmdLine.ViewCmdline;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

                List<BudgetItemMerchant> budgetItems = new ArrayList<>();
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

        // Loop through the assignBudgetItem method:
        while (true) {
            try {

                List<BudgetItemMerchant> budgetItems = new ArrayList<BudgetItemMerchant>();
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
}
