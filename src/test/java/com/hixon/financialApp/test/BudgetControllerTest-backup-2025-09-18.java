// Backup of BudgetControllerTest.java as of 2025-09-18
// This file is a direct copy of the current BudgetControllerTest.java for restore purposes.

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
    // ...existing code...
    TestController testController;
    public BudgetControllerTest(TestController testController) {
        this.testController = testController;
    }
    public static void main(String[] goals) throws Exception {
        // ...existing code...
        TestController testController = new TestController("dwhixon", "Bill Pay Account",
                "Bill Pay Account", "Bill Pay Account", new ViewCmdline(), new fileBasedNotificationService());
        BudgetControllerTest budgetControllerTest = new BudgetControllerTest(testController);
        Register register = Register.getByName("Bill Pay Account");
        Budget budget = Budget.getByName("Bill Pay Account");
        Forecast forecast = Forecast.getByName("Bill Pay Account");
        Merchant merchant = Merchant.getByName("Amazon");
        BudgetController budgetController = new BudgetController(register, budget, forecast, testController.getView(),
                testController.getNotificationService());
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
        while (true) {
            try {
                List<BudgetItemMerchant> budgetItems = new ArrayList<>();
                budgetController.assignBudgetItemsToMerchant(merchant, budgetItems);
                showWork(budgetItems);
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
    // ...existing code...
}

