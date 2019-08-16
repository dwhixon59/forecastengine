package com.hixon.financial.model;

import com.hixon.financial.model.register.RegisterException;

public interface FinancialAppEntity {

   // The dirty bit for save operations:
   public boolean getDirty();

   public void setDirty(boolean dirty);

   // The save operation:
   public void save(String insertQuery, String exceptionMessage) throws RegisterException, EntityException;

}
