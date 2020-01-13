package com.hixon.financialApp.model.entity;

import java.util.UUID;

public interface IndependentEntityInt extends EntityInt {
   // The ID operations:
   UUID getId();

   void setId(UUID id);

}
