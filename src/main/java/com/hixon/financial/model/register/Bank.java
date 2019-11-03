package com.hixon.financial.model.register;

import com.hixon.financial.view.register.TransactionResolver;

public abstract class Bank implements FinancialInstitution {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */
   protected Register register;
   TransactionResolver resolver;


   /*
    * Getters and setters for the Wells Fargo download file transaction classifier:
    */


   /*
    * Constructors:
    */
   Bank(Register register, TransactionResolver resolver) {

      this.register = register;
      this.resolver = resolver;
   }

   /*
    * Main methods for the Wells Fargo download file transaction classifier:
    */
}
