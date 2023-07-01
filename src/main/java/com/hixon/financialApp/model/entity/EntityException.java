package com.hixon.financialApp.model.entity;

import com.hixon.financialApp.utility.FinancialAppException;

public class EntityException extends FinancialAppException {
   public EntityException(String exceptionMessage) {
      super(exceptionMessage);
   }
}
