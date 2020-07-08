package com.hixon.financialApp.view;

import com.hixon.financialApp.utility.FinancialException;

public class ViewException extends FinancialException {
    public ViewException(String s) {
        super(s);
    }

   public ViewException(String s, Exception e) {
      super(s, e);
   }
}
