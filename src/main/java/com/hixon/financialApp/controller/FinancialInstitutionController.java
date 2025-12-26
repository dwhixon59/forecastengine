package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.financialinstitution.FinancialInstitution;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;

/**
 * @deprecated This class is deprecated. Use {@link FinancialInstitution} directly instead.
 * This class remains for backward compatibility only.
 */
@Deprecated
public abstract class FinancialInstitutionController extends FinancialInstitution {

   /**
    * @deprecated Use {@link FinancialInstitution#FinancialInstitution(SessionController)} instead
    */
   @Deprecated
   FinancialInstitutionController(Register register, Budget budget, Forecast forecast, ViewInt view,
                                  NotificationServiceInt notificationService) {
      super(new SessionController(register, budget, forecast, view, notificationService));
   }
}
