package com.hixon.financialApp.utility;

public class FinancialAppException extends Exception {
    public FinancialAppException(String s) {
        super(s);
    }

   public FinancialAppException(String s, Exception e) {
      super(s, e);
   }
}
