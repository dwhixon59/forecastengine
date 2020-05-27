package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Calendar;

public interface BudgetViewInt {

        // Render the spending report:
        void renderPlannedVsActualReport(Calendar startDate) throws FileNotFoundException,
                UnsupportedEncodingException, EntityException, SQLException, BudgetException, RegisterException, ForecastException;

}
