package com.hixon.financialApp.utility;

public class FinancialException extends Throwable {
    public FinancialException(String s) {
        super(s);
    }

   public FinancialException(String s, Exception e) {
      super(s, e);
   }
}
