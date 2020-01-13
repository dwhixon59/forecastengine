package com.hixon.financialApp.view.base;

import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;

// These are the forecast views in the MVC architecture for the forecast:
public interface ForecastViewInt extends ViewInt {

   // Render the short term forecast:
   public boolean renderShortTermForecast(Forecast forecast) throws Exception, EntityException,
           BudgetException;

   // Render the long term forecast:
   public boolean renderLongTermForecast(Forecast forecast) throws Exception, EntityException, BudgetException, QuitException;
}
