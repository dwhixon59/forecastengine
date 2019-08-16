package com.hixon.financial.controller;

import com.hixon.financial.Utility;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.Budget;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.budget.BudgetItemMerchant;
import com.hixon.financial.model.forecast.ForecastTransaction;
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
import java.util.List;

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
      System.out.println("Import new transactions from the file " + filename + " into the register '" + registerName + "'.");

      int i = 0;
      try {
         Transaction transaction = null;

         // Instantiate the target register:
         Register register = Register.getByName(registerName);

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
         Budget budget = Budget.getById(register.getIdBudget());
         while (!stop) {
            stop = true;
            for (CSVRecord record : records) {

               // Let the resolver know we are beginning a new item:
               resolver.beginImportItem();

               // Create a transaction from the row:
               transaction = financialInstitution.loadFromCSV(record);

               // Get the merchant for this transaction:
               Merchant merchant = Merchant.getByPayee(transaction.getMerchantPayee());

               // If we couldn't find a merchant for the transaction, get some help from the user to create one:
               if (merchant == null) {
                  merchant = resolver.assignMerchant(transaction.getMerchantPayee());
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

               // Get the assigned budget items for the merchant:
               List<BudgetItemMerchant> budgetItems = BudgetItemMerchant.getAssignedBudgetItems(merchant);

               // If we couldn't find any matching items, get some help from the user:
               if (budgetItems == null || budgetItems.size() <1 ) {
                  budgetItems = resolver.assignBudgetItems(merchant);
                  if (budgetItems == null) {
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

               // Tell the user about the bank transaction we are processing:
               System.out.println("Imported a bank transaction to " + merchant.getName() + " for $" +
                       Math.abs(transaction.getAmount()) + " on " + ((transaction.getAuthorizationDate() != null) ?
                       Utility.calendarDateToStringDate(transaction.getAuthorizationDate()) :
                       Utility.calendarDateToStringDate(transaction.getPostDate())));

               // Assign amounts to the budget items for the transaction:
               resolver.assignAmountsToBudgetItems(transaction, merchant, budgetItems);

               // Reconcile this transaction with the forecast:
               ForecastTransaction.reconcile(transaction);

               // Save the transaction and associated items:
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
