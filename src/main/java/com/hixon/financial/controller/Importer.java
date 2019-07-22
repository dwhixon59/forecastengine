package com.hixon.financial.controller;

import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.register.*;
import com.hixon.financial.view.ViewException;
import com.hixon.financial.view.register.TransactionResolver;
import com.hixon.financial.view.register.TransactionResolverCmdLine;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;

public class Importer {

   // Fields:
   private final Connection dbConnection;

   public enum TerminationCondition {RESET, RESTART, FOUND, SKIP, QUIT}

   ;

   // Constructors:
   public Importer(Connection dbConnection) {
      this.dbConnection = dbConnection;
   }


   // Getters and setters:


   // Helper functions:


   /*
    * Main methods:
    */
   // Import transactions from a bank in CSV format into the register:
   public int importCsvTransactionFile(String filename, String financialInstitutionName, String registerName)
           throws SQLException, BudgetException, ControllerException, ViewException, RegisterException, EntityException {
      System.out.println("Import new transactions to the register.");

      int i = 0;
      try {
         Transaction transaction = null;

         // Instantiate the target register:
         Register register = new Register(registerName);

         // Create a command line resolver for the import:
         TransactionResolver resolver = new TransactionResolverCmdLine();

         // Instantiate the proper type of financialInstitution:
         FinancialInstitution financialInstitution = null;
         switch (financialInstitutionName) {

            case "Wells Fargo Bank":
               financialInstitution = new WellsFargoBank(register, resolver);
         }

         /*
          * Import transactions from the CSV file:
          */
         // Open the import file:
         Reader in = new FileReader(filename);

         // For each row in the import file:
         Iterable<CSVRecord> records = CSVFormat.RFC4180.withHeader(Transaction.Headers.class).parse(in);
         boolean stop = false;
         while (!stop) {
            stop = true;
            for (CSVRecord record : records) {

               // Create a transaction from the row:
               transaction = financialInstitution.loadFromCSV(record);

               // Get the merchant for this transaction:
               Merchant merchant = Merchant.getByPayee(transaction.getMerchantPayee());

               // If we couldn't find a merchant for the transaction, get some help from the user to create one:
               if (merchant == null) {
                  merchant = resolver.resolveUnmatchedMerchant(transaction.getMerchantPayee());
                  if (merchant == null) {
                     switch (resolver.getTerminationCondition()) {
                        case SKIP:
                           continue;

                        case QUIT:
                           break;

                        default:
                           throw new ControllerException("Invalid termination condition " +
                                   resolver.getTerminationCondition() + " during transaction import");

                     }
                  }
               }
               if (merchant == null) break;
               transaction.setIdMerchant(merchant.getId());

/*
               // Match the transaction to a budget item:
               BudgetItem budgetItem = financialInstitution.classify(transaction);

               // If we couldn't find a matching item, get some help from the user:
               if (budgetItem == null) {
                  budgetItem = resolver.resolveUnmatchedBudgetItem(transaction);
                  if (budgetItem == null) {
                     switch (resolver.getTerminationCondition()) {
                        case SKIP:
                           continue;

                        case QUIT:
                           break;

                        default:
                           throw new ControllerException("Invalid termination condition " +
                                   resolver.getTerminationCondition() + " during transaction import");

                     }
                  }
               }
               transaction.setIdBudgetitem(budgetItem.getIdBudgetItem());
*/

               // Save the transaction and move to the next one:
               transaction.save();
               i++;

            } // End for each record in the transactions file.
         } // End while(!stop).

         // TODO: Save the import event:

      } catch (FileNotFoundException e) {
         ControllerException ce = new ControllerException("Transactions file " + filename + " not found.");
         ce.initCause(e);
         throw (ce);
      } catch (IOException e) {
         ControllerException ce = new ControllerException("I/O error reading from the transactions file " +
                 filename + "on line " + i + ".");
         ce.initCause(e);
         throw (ce);
      } catch (Exception e) {
         ControllerException ce = new ControllerException("Exception while processing the transactions file " +
                 filename + " on line " + i + ".");
         ce.initCause(e);
         throw ce;
      }

      // Return the number of transactions imported:
      System.out.println("Successfully imported " + i + " transactions into the register.");
      return i;

   } // End importCsvTransactionFile(Connection dbConnection).

}
