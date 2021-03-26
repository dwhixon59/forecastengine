package com.hixon.financialApp.view.base;

import com.hixon.financialApp.controller.ControllerException;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.model.user.UserResource;
import com.hixon.financialApp.view.ViewException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

// These are the forecast views in the MVC architecture for the forecast:
public interface ForecastViewInt {

   // Render the short term forecast:
   boolean renderShortTermForecast(Forecast forecast) throws Exception, EntityException,
           BudgetException;

   // Render the long term forecast:
   boolean renderLongTermForecast(Forecast forecast) throws Exception, EntityException, BudgetException, QuitException, RegisterException;

   // Update the forecast from an external representation of the forecast like a spreadsheet:
   void updateFromExternalSource() throws ControllerException, ForecastException, EntityException, SQLException,
           RegisterException, BudgetException, ViewException, IOException;

   // Render items of interest report for a specific user:
   UserResource renderItemsOfInterestReport(User user) throws EntityException, Exception, BudgetException,
           ViewException, RegisterException;

   // Render items of interest report for a specific user to a specific file:
   boolean renderItemsOfInterestReport(User user, File file) throws EntityException, Exception, BudgetException,
           ViewException, RegisterException;

   // Render the items of interest report for all users:
   List<UserResource> renderItemsOfInterestReport() throws EntityException, Exception, BudgetException,
           ViewException, RegisterException;

   // Render the overdue items report for all users:
   List<UserResource> renderOverdueItemsReport(Forecast forecast) throws EntityException, ViewException, Exception, BudgetException, RegisterException;

   // Render items of interest report for a specific user:
   UserResource renderOverdueItemsReport(Forecast forecast, User user) throws EntityException, ViewException, Exception, BudgetException, RegisterException;

   // Render items of interest report for a specific user to a specific file:
   boolean renderOverdueItemsReport(Forecast forecast, User user, File file) throws EntityException, ViewException, Exception, RegisterException, BudgetException;

   // Render the upcoming items report for all users:
   List<UserResource> renderUpcomingItemsReport(Forecast forecast) throws EntityException, ViewException, Exception, BudgetException, RegisterException;

   // Render upcoming items report for a specific user:
   UserResource renderUpcomingItemsReport(Forecast forecast, User user) throws EntityException, ViewException, Exception, BudgetException, RegisterException;

   // Render upcoming items report for a specific user to a specific file:
   boolean renderUpcomingItemsReport(Forecast forecast, User user, File file) throws EntityException, ViewException, Exception, BudgetException, RegisterException;

}
