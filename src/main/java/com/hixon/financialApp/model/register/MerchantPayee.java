package com.hixon.financialApp.model.register;

import com.hixon.financialApp.model.entity.DependentEntity;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;

import java.util.UUID;

public class MerchantPayee extends DependentEntity {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */
   private static final String insertQuery = "insert into merchant_payee (Merchant_idMerchant, payee) " +
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
   public String getUpdateByIdQuery() {
      return null;
   }

   @Override
   public String getDeleteByIdQuery() {
      return null;
   }

   @Override
   public String getPrintableEntityTypeName() {
      return "merchant payee";
   }


   /*
    * Constructors for MerchantPayee:
    */
   public MerchantPayee(String payee, UUID idMerchant) {
      super();
      this.idMerchant = idMerchant;
      this.payee = payee;
      super.setDirty(true);
   }

   /**
    * Validate the fields of an object.  Every entity is required to provide a method that validates the contents of
    * the entity.
    *
    * @return true if the object is valid
    */
   @Override
   public boolean isValid() { return true; }


   /*
    * Load and save methods for MerchantPayee:
    */
   public void save() throws RegisterException, EntityException {
      super.executeQueryForThis(insertQuery + "uuid_to_bin('" + idMerchant + "'), \"" + payee + "\")",
              "Databsae error occurred inserting MerchantPayee into the database.");
   }

   public static void deleteByMerchantAndPayee(Merchant merchant, String merchantPayeeString) throws EntityException, RegisterException {
      EntityInt.executeUpdate("delete from merchant_payee where Merchant_idMerchant = uuid_to_bin('" + merchant.getId() +
              "') and payee = '" + merchantPayeeString + "', ", "deleting a merchant payee from the database.");
   }


   /*
    * Main methods for MerchantPayee
    */


}
