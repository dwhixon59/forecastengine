package com.hixon.financial.model.register;

import com.hixon.financial.view.ViewException;
import com.hixon.financial.view.register.TransactionResolver;
import org.apache.commons.csv.CSVRecord;

import java.sql.SQLException;
import java.text.ParseException;

public class Bank implements FinancialInstitution {

   /*
    * Fields in the Wells Fargo download file transaction classifier:
    */
   protected Register register = null;
   protected Transaction transaction = null;
   protected TransactionResolver resolver;


   /*
    * Getters and setters for the Wells Fargo download file transaction classifier:
    */


   /*
    * Constructors:
    */
   public Bank(Register register, TransactionResolver resolver) {

      this.register = register;
      this.transaction = new Transaction(register);
      this.resolver = resolver;
   }

   /*
    * Main methods for the Wells Fargo download file transaction classifier:
    */
   @Override
   public Transaction loadFromCSV(CSVRecord record) throws ParseException, RegisterException, ViewException, SQLException {
      return null;
   }

   @Override
   public String parseMerchantPayee() throws ParseException, RegisterException, SQLException {
      return null;
   }
}
