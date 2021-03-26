package com.hixon.financialApp.model.forecast;

import com.hixon.financialApp.model.budget.BudgetException;

import java.sql.ResultSet;

/**
 * This convenience class iterates over a ResultSet of forecast transactions from the database.  Its interface is
 * similar to that of a ResultSet in that you iterate over the result set by calling getNext().  However, getNext()
 * of this class returns ForecastTransaction objects rather than a ResultSet where you would need to pull out the values
 * using the ResultSet get() methods.
 */
public class ForecastTransactionDatabaseIterator implements ForecastTransactionIterator {
   protected final ResultSet rs;

   public ForecastTransactionDatabaseIterator(ResultSet rs) {
      this.rs = rs;
   }

   @Override
   public ForecastTransaction getNext() throws Exception, BudgetException {
      return (rs.next()) ? new ForecastTransaction(rs) : null;
   }
}
