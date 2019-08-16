package com.hixon.financial.model;

import java.util.UUID;

public interface IndependentEntityInt extends FinancialAppEntityInt {
   // The ID operations:
   public UUID getId();

   public void setId(UUID id);

}
