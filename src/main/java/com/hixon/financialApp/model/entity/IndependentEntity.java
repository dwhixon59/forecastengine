package com.hixon.financialApp.model.entity;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.register.RegisterException;

import java.sql.SQLException;
import java.util.UUID;

public abstract class IndependentEntity extends Entity implements IndependentEntityInt {

   protected UUID id = null;

   @Override
   public UUID getId() {
      return this.id;
   }

   @Override
   public void setId(UUID id) {
      this.id = id;
      setDirty(true);
   }

   public static IndependentEntity getById(UUID uuid) throws EntityException, SQLException, RegisterException,
           BudgetException, ForecastException {
      return null;
   }

   public IndependentEntity(boolean createId) {
      if (createId) {
         id = UUID.randomUUID();
         setDirty(true);
      }
   }
}