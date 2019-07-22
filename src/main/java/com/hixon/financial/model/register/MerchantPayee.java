package com.hixon.financial.model.register;

import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.FinancialAppEntityBase;

import java.util.UUID;

public class MerchantPayee extends FinancialAppEntityBase {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */
   private static final String insertQuery = "insert into forecastdatabase.merchant_payee (Merchant_idMerchant, payee) " +
           "values (";
   private UUID idMerchant = null;
   private String payee = null;

   /*
    * Getters and setters for MerchantPayee:
    */
   public UUID getIdMerchant() {
      return idMerchant;
   }

   public void setIdMerchant(UUID idMerchant) {
      this.idMerchant = idMerchant;
   }


   /*
    * Constructors for MerchantPayee:
    */
   public MerchantPayee(String payee, UUID idMerchant) {
      super(true);
      this.idMerchant = idMerchant;
      this.payee = payee;
   }


   /*
    * Load and save methods for MerchantPayee:
    */
   public void save() throws RegisterException, EntityException {
      super.save(insertQuery + "uuid_to_bin('" + idMerchant + "'), \"" + payee + "\")",
              "Databsae error occurred inserting MerchantPayee into the database.");
   }


   /*
    * Main methods for MerchantPayee
    */


}
