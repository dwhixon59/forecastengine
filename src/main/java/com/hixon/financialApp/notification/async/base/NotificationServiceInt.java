package com.hixon.financialApp.notification.async.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.NotificationServiceException;
import com.hixon.financialApp.view.ViewException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.text.ParseException;

public interface NotificationServiceInt {

   // Send a request to the specified user to identify the merchant specified in a transaction payee:
   void requestIdentifyMerchant(User user, Transaction transaction) throws FileNotFoundException,
           UnsupportedEncodingException;

   // Send a request to the specified user to assign budget items the specified merchant:
   void requestAssignBudgetItems(User user, Merchant merchant) throws FileNotFoundException, UnsupportedEncodingException;

   // Send a request to the specified user to classify a transaction:
   void requestAssignSplits(User user, Transaction transaction) throws IOException, EntityException, RegisterException,
           ParseException, BudgetException, SQLException;

   // Create an items of interest report and send it to a user:
   void sendItemsOfInterestReport(Forecast forecast) throws Exception, EntityException, BudgetException, ViewException,
           RegisterException;

   // Create an Overdue and Upcoming Report and send it to a user:"
   void sendOverdueAndUpcomingItemsReport(Forecast forecast) throws Exception, ViewException,
           EntityException, BudgetException, RegisterException, NotificationServiceException;

   // Create a New Transactions Summary Report and send it to a user:"
   void sendNewTransactionSummaryReport(Register register) throws Exception, ViewException, EntityException,
           BudgetException, RegisterException, NotificationServiceException;
}

