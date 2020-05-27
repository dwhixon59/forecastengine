package com.hixon.financialApp.view.async.file;

import com.hixon.financialApp.model.User;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.async.base.NotificationServiceInt;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;

import static com.hixon.financialApp.utility.Utility.getResolver;

public class fileBasedNotificationService implements NotificationServiceInt {

   String encoding = "UTF-8";
   PrintWriter writer;

   @Override
   public void requestClassifyTransaction(User user, Transaction transaction) throws IOException, EntityException,
           RegisterException, ParseException, BudgetException, SQLException {

      String notificationFilename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
              user.getFirstName() + "_Notifications.txt";

      getResolver().say("Request to classify transaction to user " + user.getFirstName() + "written to the file: " +
              notificationFilename);
      writer = new PrintWriter(notificationFilename, encoding);
      writer.println(user.getFirstName() + ":  ");

      // Get the budget items for the merchant associated with this transaction:
      List<BudgetItemMerchant> budgetItems = BudgetItemMerchant.getAssignedBudgetItems(transaction.getMerchant());
      writer.println("The assigned budget items and amounts (if specified) for this merchant are:");
      int i = 1;
      for (BudgetItemMerchant budgetItem : budgetItems
      ) {
         String lineEnd = "";
         if (budgetItem.getAmount() > 0) {
            lineEnd = ", " + Utility.formatDollarAmount(budgetItem.getBudgetItem().getAmount()) + ", 0";
         } else {
            if (budgetItem.getPercentage() > 0) {
               lineEnd = ", 0, " + budgetItem.getPercentage() + "%";
            }
         }
         writer.println("   " + i++ + ".  " + budgetItem.getBudgetItem().getPayee() + lineEnd);
      }

      writer.close();
   }

}
