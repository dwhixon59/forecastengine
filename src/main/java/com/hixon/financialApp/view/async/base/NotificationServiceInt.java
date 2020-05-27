package com.hixon.financialApp.view.async.base;

import com.hixon.financialApp.model.User;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;

public interface NotificationServiceInt {

   // Send a request to the specified user to classify a transaction:
   void requestClassifyTransaction(User user, Transaction transaction) throws IOException, EntityException, RegisterException, ParseException, BudgetException, SQLException;

}
