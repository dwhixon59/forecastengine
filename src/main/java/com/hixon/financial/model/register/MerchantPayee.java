package com.hixon.financial.model.register;

import com.hixon.financial.model.DependentEntity;
import com.hixon.financial.model.EntityException;

import java.util.UUID;

public class MerchantPayee extends DependentEntity {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */
   private static final String insertQuery = "insert into forecastdatabase.merchant_payee (Merchant_idMerchant, payee) " +
           "values (";
   private UUID idMerchant;
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

   @Override
   public String getInsertQuery() {
      return null;
   }

   @Override
   public String getInsertOnDuplicateUpdateQuery() {
      return null;
   }

   @Override
   public String getUpdateQuery() {
      return null;
   }

   @Override
   public String getDeleteQuery() {
      return null;
   }

   @Override
   public String getEntityTypeName() {
      return "merchant payee";
   }


   /*
    * Constructors for MerchantPayee:
    */
   MerchantPayee(String payee, UUID idMerchant) {
      super();
      this.idMerchant = idMerchant;
      this.payee = payee;
      super.setDirty(true);
   }


   /*
    * Load and save methods for MerchantPayee:
    */
   public void save() throws RegisterException, EntityException {
      super.executeQueryForThis(insertQuery + "uuid_to_bin('" + idMerchant + "'), \"" + payee + "\")",
              "Databsae error occurred inserting MerchantPayee into the database.");
   }


   /*
    * Main methods for MerchantPayee
    */


}
