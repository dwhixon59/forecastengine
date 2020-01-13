package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.BudgetException;

import java.sql.ResultSet;

public class ForecastTransactionDatabaseIterator implements ForecastTransactionIterator {
   protected final ResultSet rs;
   protected Forecast forecast;

   protected ForecastTransactionDatabaseIterator() {
      rs = null;
   }

   public ForecastTransactionDatabaseIterator(ResultSet rs) {
      this.rs = rs;
   }

   @Override
   public void setForecast(Forecast forecast) {
      this.forecast = forecast;
   }

   @Override
   public ForecastTransaction getNext() throws Exception, BudgetException {
      return (rs.next()) ? new ForecastTransaction(rs) : null;
   }
}
