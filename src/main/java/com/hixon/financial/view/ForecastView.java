package com.hixon.financial.view;

import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.forecast.LongTermForecast;
import com.hixon.financial.model.register.RegisterException;
import com.hixon.financial.view.register.TransactionResolver;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;

// These are the forecast views in the MVC architecture for the forecast:
public interface ForecastView {

   void setLongTermForecast(LongTermForecast forecastToRender);
    boolean renderLongTermForecast(String filename, String encoding) throws Exception, EntityException,
            BudgetException;

   void setShortTermForecast(LongTermForecast forecastToRender);
   boolean renderShortTermForecast(String filename, String encoding) throws Exception, EntityException,
           BudgetException;

    // Create a month-to-date spending report:
   void renderSpendingReportMTD(TransactionResolver resolver) throws FileNotFoundException, UnsupportedEncodingException,
           EntityException, SQLException, BudgetException, RegisterException;
}
