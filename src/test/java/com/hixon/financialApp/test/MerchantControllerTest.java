package com.hixon.financialApp.test;

import com.hixon.financialApp.controller.CancelException;
import com.hixon.financialApp.controller.MerchantController;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.controller.SkipException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.merchant.MerchantPayee;
import com.hixon.financialApp.notification.async.file.fileBasedNotificationService;
import com.hixon.financialApp.view.cmdLine.ViewCmdline;

import java.sql.SQLException;

public class MerchantControllerTest {

    /*
     * Member variables for MerchantControllerTest:
     */
    TestController testController;

    /*
     * Constructors for MerchantControllerTest:
     */
    public MerchantControllerTest(TestController testController) {
        this.testController = testController;
    }


    /*
     * Main methods for the MerchantControllerTest class:
     */
    // Test the assignMerchant method:
    public static void main(String[] goals) throws Exception {

        // Create a TestController with the user 'dwhixon', the database connection, the command line transaction
        // resolver and the file-based notification service:
        TestController testController = new TestController("dwhixon", "Bill Pay Account",
                "Bill Pay Account", "Bill Pay Account", new ViewCmdline(),
                new fileBasedNotificationService());

        // Create a MerchantControllerTest object:
        MerchantControllerTest merchantControllerTest = new MerchantControllerTest(testController);

        // Create the MerchantController with SessionController
        SessionController sessionController = testController.getSessionController();
        MerchantController merchantController = new MerchantController(sessionController);
        Merchant merchant = null;
        while (true) {
            try {
                // Delete any instance of a Merchant with the name "merchantPayeeString" from the merchant table:
                Merchant.deleteByName("merchantPayeeString");

                // Delete any instance of a MerchantPayee with the name merchantPayeeString from the merchant_payee table:
                MerchantPayee.deleteByName("merchantPayeeString");

                merchant = merchantController.assignMerchant("merchantPayeeString",
                        "transactionPayeeString", 100.00);
                showWork(merchant);
            } catch (CancelException ce) {
                System.out.println("Caught CancelException: " + ce.getMessage());
            } catch (SkipException se) {
                System.out.println("Caught SkipException: " + se.getMessage());
            } catch (QuitException qe) {
                System.out.println("Caught QuitException: " + qe.getMessage());
                break;
            }
        }
    }

    public static void showWork(Merchant merchant) throws SQLException, EntityException {
        System.out.println("\nmerchant: " + merchant);
        merchant.getPayees().forEach(payee -> System.out.println(payee));
    }
}
