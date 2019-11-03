package com.hixon.financial.model;

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
   }

   public IndependentEntity(boolean createId) {
      if (createId) {
         id = UUID.randomUUID();
         setDirty(true);
      }
   }
}
