package com.hixon.financialApp.view.base;

import com.hixon.financialApp.model.forecast.Forecast;

public abstract class AbstractForecastReport extends AbstractViewReport {

   private final Forecast forecast;

   protected AbstractForecastReport(Forecast forecast) {
      this.forecast = forecast;
   }

}
