package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.UserResource;
import com.hixon.financialApp.view.ViewException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.List;

public interface RegisterViewInt {

    boolean renderTransactionReport(Calendar startDate) throws FileNotFoundException, UnsupportedEncodingException, ViewException;

    // Render the New Transaction Summary report for all users:
    List<UserResource> renderNewTransactionSummaryReport() throws EntityException, Exception, BudgetException,
            ViewException, RegisterException;
}
