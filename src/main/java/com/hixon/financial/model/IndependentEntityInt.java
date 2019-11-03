package com.hixon.financial.model;

import java.util.UUID;

public interface IndependentEntityInt extends EntityInt {
   // The ID operations:
   UUID getId();

   void setId(UUID id);

}
