package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.RegisterException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Calendar;

public interface BudgetViewInt extends ViewInt {

        // Render the spending report:
        public void renderSpendingReport(Calendar startDate) throws FileNotFoundException,
                UnsupportedEncodingException, EntityException, SQLException, BudgetException, RegisterException;

}
