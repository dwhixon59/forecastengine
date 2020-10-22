package com.hixon.financialApp.notification.async.file;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.model.user.UserResource;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.text.TextForecastView;
import com.hixon.financialApp.view.text.TextRegisterView;

import java.io.*;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;

import static com.hixon.financialApp.utility.Utility.getResolver;

public class fileBasedNotificationService implements NotificationServiceInt {

   /*
    * Fields:
    */
   private static final String NOTIFICATION_FILE_PREFIX = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business" +
           "\\Finances\\Expenses\\";
   public static final String NOTIFICATION_FILE_POSTFIX = "_Notifications.txt";
   private static final String ENCODING = "UTF-8";


   /*
    * Getters and setters:
    */
   private String getNotificationFilename(User user) {
      return NOTIFICATION_FILE_PREFIX + user.getFirstName() + NOTIFICATION_FILE_POSTFIX;
   }

   /*
    * Helper methods:
    */


   /*
    * Main methods:
    */
   @Override
   public void requestIdentifyMerchant(User user, Transaction transaction) throws FileNotFoundException,
           UnsupportedEncodingException {

      try (PrintWriter writer = new PrintWriter(getNotificationFilename(user), ENCODING)){
         writer.println("");
         writer.println(user.getFirstName() + ":  Please identify the merchant for the following transaction:");
         writer.println(transaction);
         getResolver().say("Request to identify the merchant for a transaction sent to user " + user.getFirstName() +
                 " was written to the file: " + getNotificationFilename(user));
      }
   }

   @Override
   public void requestAssignBudgetItems(User user, Merchant merchant) throws FileNotFoundException,
           UnsupportedEncodingException {

      try (PrintWriter writer = new PrintWriter(getNotificationFilename(user), ENCODING)){
         writer.println("");
         writer.println("Hi " + user.getFirstName() + ".  What budget items should be associated with the merchant "
                 + merchant + "?");
         getResolver().say("Request to assign budget items the merchant " + merchant + " sent to " +
                 user.getFirstName() + " was written to the file: " + getNotificationFilename(user));
      }
   }

   @Override
   public void requestAssignSplits(User user, Transaction transaction) throws IOException, EntityException,
           RegisterException, ParseException, BudgetException, SQLException {

      try (FileWriter writer = new FileWriter(getNotificationFilename(user), true)) {

         writer.append("");
         writer.append("\nHi " + user.getFirstName() + ":  Please classify the following transaction:\n");
         writer.append(transaction.toStringSummary());

         // Get the budget items for the merchant associated with this transaction:
         List<BudgetItemMerchant> budgetItems = BudgetItemMerchant.getAssignedBudgetItems(transaction.getMerchant());
         writer.append("\nThe assigned budget items and amounts (if specified) for this merchant are:\n");
         int i = 1;
         for (BudgetItemMerchant budgetItem : budgetItems
         ) {
            String lineEnd = "\n";
            if (budgetItem.getAmount() > 0) {
               lineEnd = ", " + Utility.formatDollarAmount(budgetItem.getBudgetItem().getAmount()) + ", 0";
            } else {
               if (budgetItem.getPercentage() > 0) {
                  lineEnd = ", 0, " + budgetItem.getPercentage() + "%";
               }
            }
            writer.append("   " + i++ + ".  " + budgetItem.getBudgetItem().getPayee() + lineEnd);
         }
         writer.append("Enter:  item_number <sp> amount <sp> memo (if multiple items add <comma> between):  \n");

         getResolver().say("Request to " + user.getFirstName() + " classify transaction was written to the " +
                 "file: " + getNotificationFilename(user));
      }
   }

   @Override
   public void sendItemsOfInterestReport() throws Exception, EntityException,
           BudgetException, ViewException, RegisterException {
      TextForecastView textForecastView = new TextForecastView();
      List<UserResource> reports = textForecastView.renderItemsOfInterestReport();
      for (UserResource userResource: reports
           ) {
         Utility.getResolver().say("Items of interest report for user " + userResource.getUser().getFirstName() +
                 " written to the file " + userResource.getFile().getAbsolutePath());
      }
   }

   @Override
   public void sendNewTransactionSummaryReport(Register register) throws ForecastException, ViewException, IOException, EntityException,
           SQLException, BudgetException, RegisterException {
      TextRegisterView textRegisterView = new TextRegisterView(register);
      List<UserResource> reports = textRegisterView.renderNewTransactionSummaryReport();
      for (UserResource userResource: reports
      ) {
         Utility.getResolver().say("New Transaction Summary report for user " + userResource.getUser().getFirstName() +
                 " written to the file " + userResource.getFile().getAbsolutePath());
      }

      // If we successfully rendered the new transaction reports, then set the new transactions flags to false:
      register.setTransactionsToNotNew();
   }
}
