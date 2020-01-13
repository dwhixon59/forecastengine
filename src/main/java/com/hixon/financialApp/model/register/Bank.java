package com.hixon.financialApp.model.register;

import com.hixon.financialApp.view.base.TransactionResolverInt;

public abstract class Bank implements FinancialInstitution {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */
   protected Register register;
   TransactionResolverInt resolver;


   /*
    * Getters and setters for the Wells Fargo download file transaction classifier:
    */


   /*
    * Constructors:
    */
   Bank(Register register, TransactionResolverInt resolver) {

      this.register = register;
      this.resolver = resolver;
   }

   /*
    * Main methods for the Wells Fargo download file transaction classifier:
    */
}
