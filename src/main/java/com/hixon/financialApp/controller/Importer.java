package com.hixon.financialApp.controller;

import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.register.*;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.TransactionResolverInt;
import com.hixon.financialApp.view.cmdLine.TransactionResolverCmdLine;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class Importer {

   // Fields:
   public enum TerminationCondition {RESET, RESTART, FOUND, SKIP, QUIT}


   // Constructors:


   // Getters and setters:


   // Helper functions:


   /*
    * Main methods:
    */
   // Import transactions from a bank in CSV format into the register:
   public boolean importCsvTransactionFile(String filename, String financialInstitutionName, String registerName,
                                           Forecast forecast)
           throws SQLException, BudgetException, ControllerException, ViewException, RegisterException, EntityException {
      System.out.println("Import new transactions from the file " + filename + " into the register '" + registerName + "'.");

      int i = 0;
      try {
         Transaction transaction = null;

         // Instantiate the target register:
         Register register = Register.getByName(registerName);

         // Create a command line resolver for the import:
         TransactionResolverInt resolver = new TransactionResolverCmdLine();

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

         // Read the records in the file into a list so that we can process them in reverse order:
         List<CSVRecord> recordList = new LinkedList<>();
         Iterable<CSVRecord> records = CSVFormat.RFC4180.withHeader(Transaction.Headers.class).parse(in);
         for (CSVRecord record : records) {
            recordList.add(record);
         }

         // For each row in the import file:
         boolean stop = false;
         Budget budget = Budget.getById(register.getIdBudget());
         HashMap<String, String> map = new HashMap<String, String>();
         String importRecordId = null;
         while (!stop) {
            stop = true;
            for (i = recordList.size() - 1; i > -1; i--) {
               CSVRecord record = recordList.get(i);

               // Let the resolver know we are beginning a new item:
               resolver.beginImportItem();

               // Construct an ID for this import record:
               String importRecordBaseName = financialInstitution.getImportRecordBaseName(record);
               if (map.containsKey(importRecordBaseName)) {
                  int instance = Integer.parseInt(map.get(importRecordBaseName)) + 1;
                  map.put(importRecordBaseName, Integer.toString(instance));
                  importRecordId = importRecordBaseName + "\t" + instance;
               } else {
                  map.put(importRecordBaseName, "1");
                  importRecordId = importRecordBaseName + "\t1";
               }

               // Get the transaction for this import record:
               transaction = Transaction.getByImportRecordId(importRecordId);

               // If the transaction hasn't been imported before, then create it:
               Merchant merchant;
               if (transaction == null) {
                  transaction = financialInstitution.loadFromCSV(record, importRecordId);

                  // Get the merchant for this transaction:
                  merchant = Merchant.getByPayee(transaction.getMerchantPayee());
                  // If we couldn't find a merchant for the transaction, get some help from the user to create one:
                  if (merchant == null) {
                     merchant = resolver.assignMerchant(transaction.getMerchantPayee(), transaction.getPayee());
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
               } else {
                  // The merchant was identified the previous time this transaction was imported:
                  merchant = Merchant.getById(transaction.getIdMerchant());
               }

               // Get the assigned budget items for the merchant:
               List<BudgetItemMerchant> budgetItems = BudgetItemMerchant.getAssignedBudgetItems(merchant);

               // If we couldn't find any matching items, get some help from the user:
               if (budgetItems == null || budgetItems.size() < 1) {
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

               // Get the splits for the transaction.  Create them if they don't already exist:
               List<TransactionSplit> splits = TransactionSplit.getSplitsForTransaction(transaction);
               if (splits == null) {
                  splits = resolver.assignAmountsToBudgetItems(transaction, merchant, budgetItems);
               }

               // Save the transaction and associated items:
               transaction.save(EntityInt.SaveMethod.INSERT);
               for (TransactionSplit split : splits) {
                  System.out.println(split.toString());
                  split.save();
               }

               // Reconcile this transaction with the forecast:
               ForecastTransaction.reconcile(forecast, transaction, splits, resolver);

               // TODO:  Run through the short term forecast transactions and roll up any stragglers:
               //while (transaction.getDate < today) {

               // TODO: Process any significannt events that occurred during reconciliation:
               //  (register.getSignificantEvents())

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
      return forecast.getInSync();

   } // End importCsvTransactionFile(Connection dbConnection).

   // Import a list of budget items:
   public void importCsvBudgetItemFile(String filename) throws EntityException, BudgetException, RegisterException, ControllerException {

      System.out.println("Import new budget items from the file " + filename + ".");

      int i = 0;
      try {
         BudgetItem budgetItem = new BudgetItem();

         // Open the import file:
         Reader in = new FileReader(filename);

         // For each row in the import file:
         Iterable<CSVRecord> records = CSVFormat.RFC4180.withHeader(BudgetItem.Headers.class).parse(in);
         boolean stop = false;
         while (!stop) {
            stop = true;
            for (CSVRecord record : records) {

               // Create a budgetItem from the row:
               budgetItem.loadFromCsvRecord(record);

               // Save the budgetItem and associated items:
               budgetItem.save(EntityInt.SaveMethod.INSERT_ON_DUPLICATE_UPDATE);
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

      // Return wether the forecast is in sync:
      System.out.println("Successfully imported " + i + " budget items into the database.");
   }


}
