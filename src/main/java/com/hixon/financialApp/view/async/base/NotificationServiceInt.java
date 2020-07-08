package com.hixon.financialApp.view.async.base;

import com.hixon.financialApp.model.User;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.Merchant;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;

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
}

