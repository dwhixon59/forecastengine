package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.User;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.register.*;
import com.hixon.financialApp.view.ViewException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT_ON_DUPLICATE_UPDATE;
import static com.hixon.financialApp.utility.Utility.*;

public class Importer {

   // Fields:
   public enum TerminationCondition {RESET, RESTART, FOUND, SKIP, INQUIRE, QUIT}


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
         Transaction transaction;

         // Instantiate the target register:
         Register register = Register.getByName(registerName);

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
         List<CSVRecord> recordList = new ArrayList<>();
         Iterable<CSVRecord> records = CSVFormat.RFC4180.withHeader(Transaction.Headers.class).parse(in);
         for (CSVRecord record : records) {
            recordList.add(record);
         }

         // TODO:  Assign splits to any transactions that were skipped.  They are in the database with no splits.

         // For each row in the import file:
         HashMap<String, String> map = new HashMap<>();
         String importRecordId;
         for (i = recordList.size() - 1; i > -1; i--) {
            CSVRecord record = recordList.get(i);

            // Let the resolver know we are beginning a new item:
            resolver.beginImportItem();

            // Construct an ID for this import record:
            importRecordId = financialInstitution.getRegisterImportRecordBaseName(record);
            importRecordId = getImportRecordId(map, importRecordId);

            // Get the transaction for this import record:
            transaction = Transaction.getByImportRecordId(importRecordId);

            // If the transaction hasn't been imported before:
            Merchant merchant;
            if (transaction == null) {

               // Then see if there is a provisional transaction for it
               transaction = financialInstitution.getMatchingProvisionalTransaction(record);

               // If we found a provisional transaction, then update it from the posted transaction:
               if (transaction != null) {
                  financialInstitution.updateFromCSVRecord(transaction, record, importRecordId);
                  merchant = transaction.getMerchant();

               } else {
                  // We still haven't found a transaction, so create one:
                  transaction = financialInstitution.createFromCSVRecord(record, importRecordId);

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
                  transaction.setIdMerchant(merchant.getId());
               }
            } else {
               merchant = transaction.getMerchant();
            }

            // Get the assigned budget items for the merchant:
            List<BudgetItemMerchant> budgetItems = BudgetItemMerchant.getAssignedBudgetItems(merchant);

            // If we couldn't find any matching items, get some help from the user:
            if (budgetItems.size() < 1) {
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
                    calendarDateToStringDate(transaction.getAuthorizationDate()) :
                    calendarDateToStringDate(transaction.getPostDate())));

            // Get the splits for the transaction.  Create them if they don't already exist:
            List<TransactionSplit> splits = TransactionSplit.getSplitsForTransaction(transaction);
            if (splits == null) {
               splits = resolver.assignAmountsToBudgetItems(transaction, merchant, budgetItems);
            }

            // Save the transaction and associated items:
            transaction.save(INSERT_ON_DUPLICATE_UPDATE);
            for (TransactionSplit split : splits) {
               System.out.println(split.toString());
               split.save();
            }

            // Reconcile this transaction with the forecast:
            ForecastTransaction.reconcile(forecast, transaction, splits, resolver);

         } // End for each record in the transactions file.

         // TODO: Process any significant events that occurred during reconciliation:

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


   public String getImportRecordId(HashMap<String, String> map, String importRecordBaseName) {
      String importRecordId;
      if (map.containsKey(importRecordBaseName)) {
         int instance = Integer.parseInt(map.get(importRecordBaseName)) + 1;
         map.put(importRecordBaseName, Integer.toString(instance));
         importRecordId = importRecordBaseName + "\t" + instance;
      } else {
         map.put(importRecordBaseName, "1");
         importRecordId = importRecordBaseName + "\t1";
      }
      return importRecordId;
   }


   /*
    *  Import the provisional transactions from the import file:
    */
   public boolean importCsvProvisionalTransactionFile(String filename, String financialInstitutionName,
                                                      String registerName, Forecast forecast) throws RegisterException, ControllerException,
           EntityException, BudgetException, ViewException {
      System.out.println("Import provisional transactions from the file " + filename + " into the register '" +
              registerName + "'.");

      int i = 0;
      try {
         Transaction transaction = null;

         // Instantiate the target register:
         Register register = Register.getByName(registerName);

         // Instantiate the proper type of financialInstitution:
         FinancialInstitution financialInstitution = null;
         switch (financialInstitutionName) {

            case "Wells Fargo Bank":
               financialInstitution = new WellsFargoBank(register, getResolver());
         }

         /*
          * Create a list of new provisional register transactions in ascending payee + amount order from the import file:
          */
         // Open the import file:
         File file = new File("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                 "ProvisionalTransactions.txt");
         BufferedReader br = new BufferedReader(new FileReader(file));

         // Read the records in the provisional transactions file into a list provisional register transactions:
         List<Transaction> provisionalTransactions = new ArrayList<>();
         String line;
         HashMap<String, String> map = new HashMap<>();
         while ((line = br.readLine()) != null) {
            try {
               // Let the resolver know we are beginning a new item:
               resolver.beginImportItem();

               // Load the transaction from the CSV line:
               transaction = financialInstitution.loadProvisionalTransactionFromCSV(line, register);

               // Construct an ID for this import record and store it in the transaction:
               String importRecordBaseName = calendarDateToStringSlashDate(transaction.getPostDate()) + "\t" +
                       formatDollarAmount(transaction.getAmount()).substring(1) + "\t" + transaction.isCleared()
                       + "\t" + transaction.getCheckNumber() + "\t" + transaction.getPayee();
               transaction.setImportRecordId(getImportRecordId(map, importRecordBaseName));

               // Get the merchant for this transaction:
               Merchant merchant = Merchant.getByPayee(transaction.getMerchantPayee());

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
                                   resolver.getTerminationCondition() + " during provisional transaction import");

                     }
                  }
               }
               if (merchant == null) break;
               transaction.setMerchant(merchant);

               // Add the transaction to the array of provisional transactions:
               provisionalTransactions.add(transaction);

            } catch (ParseException ignored) {
            }
         }

         // If we didn't find any provisional transactions, then abort:
         if (provisionalTransactions.size() == 0) {
            throw new ParseException("No provisional transactions found in the input file.  Aborting.", 0);
         }

         //  Sort the list in ascending order by merchant + amount:
         Comparator<Transaction> comparator = (t1, t2) -> {
            try {
               String t1Key = t1.getMerchant().getName() + t1.getAmount();
               String t2Key = t2.getMerchant().getName() + t2.getAmount();
               return t1Key.compareTo(t2Key);
            } catch (EntityException | RegisterException e) {
               throw new ClassCastException(e.getMessage());
            }
         };
         provisionalTransactions.sort(comparator);

         /*
          * Retrieve a list of the existing provisional transactions from the database and them sort them in ascending
          * order by merchant + amount :
          */
         ResultSet rs = EntityInt.getRS(Transaction.getSelectQuery() + " where cleared = false",
                 "attempting to retrieve a list of provisional transactions.");
         List<Transaction> registerTransactions = new ArrayList<>();
         while (rs.next()) {
            registerTransactions.add(new Transaction(rs));
         }
         registerTransactions.sort(comparator);

         /*
          * Merge the two lists of transactions updating the database as we go:
          *
          */
         i = 0;
         int j = 0;
         while (i < provisionalTransactions.size() || j < registerTransactions.size()) {

            // Compare the current provisional transaction to the current register transaction:
            int comparison;
            if (i < provisionalTransactions.size() && j < registerTransactions.size()) {
               comparison = comparator.compare(provisionalTransactions.get(i), registerTransactions.get(j));
            } else if (i == provisionalTransactions.size()) {
               comparison = 1;
            } else {
               comparison = -1;
            }

            // If the key to the provisional transaction is less than the key to the register transaction:
            if (comparison < 0) {

               /*
                * then this is a new provisional transaction, so add this transaction to the database:
                */
               // Get the assigned budget items for the merchant:
               Merchant merchant = provisionalTransactions.get(i).getMerchant();
               List<BudgetItemMerchant> budgetItems = BudgetItemMerchant.getAssignedBudgetItems(merchant);

               // If we couldn't find any matching items, get some help from the user:
               if (budgetItems.size() < 1) {
                  budgetItems = getResolver().assignBudgetItems(merchant);
                  if (budgetItems == null) {
                     switch (getResolver().getTerminationCondition()) {
                        case SKIP:
                           continue;

                        case QUIT:
                           break;

                        default:
                           throw new ControllerException("Invalid termination condition " +
                                   getResolver().getTerminationCondition() + " during transaction import");
                     }
                  }
               }

               // Tell the user about the bank transaction we are processing:
               System.out.println("\n*** Imported a bank transaction to " + merchant.getName() + " for $" +
                       Math.abs(provisionalTransactions.get(i).getAmount()) + " on " +
                       ((provisionalTransactions.get(i).getAuthorizationDate() != null) ?
                               calendarDateToStringDate(provisionalTransactions.get(i).getAuthorizationDate()) :
                               calendarDateToStringDate(provisionalTransactions.get(i).getPostDate())) + "***");

               // Get the splits for the transaction:
               List<TransactionSplit> splits = TransactionSplit.getSplitsForTransaction(provisionalTransactions.get(i));

               // If we couldn't find any matching items, get some help from the user:
               if (splits == null) {
                  splits = getResolver().assignAmountsToBudgetItems(provisionalTransactions.get(i), merchant,
                          budgetItems);

                  if (splits == null) {
                     switch (getResolver().getTerminationCondition()) {
                        case INQUIRE:
                           List<User> users = User.getAllUsers();
                           User user = getResolver().getUser("Select the user to send the notification to",
                                   users, true);
                           if (user != null) {
                              getNotificationService().requestClassifyTransaction(user, provisionalTransactions.get(i));
                           }
                           continue;

                        case SKIP:
                           continue;

                        case QUIT:
                           break;

                        default:
                           throw new ControllerException("Invalid termination condition " +
                                   getResolver().getTerminationCondition() + " during transaction import");
                     }
                  }
               }

               // Save the transaction and associated items:
               provisionalTransactions.get(i).save(INSERT);
               for (TransactionSplit split : splits) {
                  System.out.println(split.toString());
                  split.save();
               }

               // Reconcile this transaction with the forecast:
               ForecastTransaction.reconcile(forecast, transaction, splits, getResolver());

               // Move to the next provisional transaction:
               i++;

            } else if (comparison == 0) {  // else, if key to provision transaction is equal to key of register transaction:

               // then the transaction has already been entered, so move to the next one on both lists:
               i++;
               j++;

            } else {  // else the key to imported transaction is greater than the key to existing transaction

               // The provisional transaction from the database has fallen off.  If the register transaction is more
               // than one business day old, then it has likely been withdrawn:
               if (businessDaysBeteween(Calendar.getInstance(), registerTransactions.get(j).getDate()) > 1) {

                  // Confirm that with the user and remove if they agree:
                  if (getResolver().askDeleteRegisterTransaction(registerTransactions.get(j))) {
                     registerTransactions.get(j).delete();
                  }

                  // Move to the next register transaction:
                  j++;
               }
            } // End else the key to the imported transaction is greater than the key to existing transaction.
         } // End while there are provisional or register transactions left to process.

         // TODO: Save the import event:

      } catch (FileNotFoundException e) {
         ControllerException ce = new ControllerException("Provisional transactions file " + filename + " not found.");
         ce.initCause(e);
         throw (ce);
      } catch (IOException e) {
         ControllerException ce = new ControllerException("I/O error reading from the provisional transactions file " +
                 filename + "on line " + i + ".");
         ce.initCause(e);
         throw (ce);
      } catch (Exception e) {
         ControllerException ce = new ControllerException("Exception while processing the provisional transactions file " +
                 filename + " on line " + i + ".");
         ce.initCause(e);
         throw ce;
      }

      // Return the number of transactions imported:
      System.out.println("Successfully imported " + i + " provisional transactions into the register:  " + registerName + ".");
      return forecast.getInSync();

   } // End importCsvProvisionalTransactionFile().


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

      // Return whether the forecast is in sync:
      System.out.println("Successfully imported " + i + " budget items into the database.");
   }

} // End class Importer.
