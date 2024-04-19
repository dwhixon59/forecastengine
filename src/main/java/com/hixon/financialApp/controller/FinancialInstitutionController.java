package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;

public abstract class FinancialInstitutionController implements FinancialInstitutionInt {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */
   protected Register register;
   protected Budget budget;
   protected Forecast forecast;
   protected ViewInt view;
   protected NotificationServiceInt notificationService;



   /*
    * Getters and setters for the Wells Fargo download file transaction classifier:
    */


   /*
    * Constructors:
    */
   FinancialInstitutionController(Register register, Budget budget, Forecast forecast, ViewInt view,
                                  NotificationServiceInt notificationService) {

      // Set the fields:
      this.register = register;
      this.budget = budget;
      this.forecast = forecast;
      this.view = view;
      this.notificationService = notificationService;
   }

   /*
    * Main methods for the Wells Fargo download file transaction classifier:
    */
}
