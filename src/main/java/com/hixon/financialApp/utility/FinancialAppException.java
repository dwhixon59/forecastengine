package com.hixon.financialApp.utility;

public class FinancialAppException extends Throwable {
    public FinancialAppException(String s) {
        super(s);
    }

   public FinancialAppException(String s, Exception e) {
      super(s, e);
   }
}
