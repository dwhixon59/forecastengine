package com.hixon.financialApp.view;

import com.hixon.financialApp.utility.FinancialAppException;

public class ViewException extends FinancialAppException {
    public ViewException(String s) {
        super(s);
    }

   public ViewException(String s, Exception e) {
      super(s, e);
   }
}
