package com.hixon.financialApp.view.base;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.register.RegisterException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

// These are the forecast views in the MVC architecture for the forecast:
public interface ForecastViewInt {

   // Render the short term forecast:
   boolean renderShortTermForecast(Forecast forecast) throws Exception, EntityException,
           BudgetException;

   // Render the long term forecast:
   boolean renderLongTermForecast(Forecast forecast) throws Exception, EntityException, BudgetException, QuitException;

   // Open the source of forecast transactions to update from:
   List<ForecastTransaction> openForecastTransactionSource() throws IOException, ControllerException, BudgetException;

   void updateFromExternalSoure() throws ControllerException, ForecastException, EntityException, SQLException, RegisterException, BudgetException;
}
