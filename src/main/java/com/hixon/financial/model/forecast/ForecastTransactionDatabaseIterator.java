package com.hixon.financial.model.forecast;

import java.sql.ResultSet;

public class ForecastTransactionDatabaseIterator implements ForecastTransactionIterator {
   private final ResultSet rs;
   private Forecast forecast;

   ForecastTransactionDatabaseIterator(ResultSet rs) {
      this.rs = rs;
   }

   @Override
   public void setForecast(Forecast forecast) {
      this.forecast = forecast;
   }

   @Override
   public ForecastTransaction getNext() throws Exception {
      return (rs.next()) ? new ForecastTransaction(rs) : null;
   }
}
