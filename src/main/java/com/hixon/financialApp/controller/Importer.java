package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.register.*;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.FinancialAppException;
import com.hixon.financialApp.utility.Utility;
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

   public static final String CLEARED_TRANSACTIONS_FILE_PATHNAME = "C:\\Users\\dwhix\\Downloads\\Checking2.csv";

   public static final String PROVISIONAL_TRANSACTIONS_FILE_PATHNAME = "C:\\Users\\dwhix\\Downloads\\" +
           "ProvisionalTransactions.txt";
   public static final String REGISTER_TRANSACTIONS_FILE = "Register transactions";

   // Fields:
   public enum TerminationCondition {RESET, RESTART, FOUND, SKIP, INQUIRE, QUIT}


   // Constructors:


   // Getters and setters:


   // Helper functions:
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

   public static void logImportEvent(Transaction transaction, Merchant merchant) {

      String creditOrDebitString;
      if (transaction.getPayee().contains("TRANSFER FROM")) {
         creditOrDebitString = "transfer from ";
      } else if (transaction.getPayee().contains("TRANSFER TO")) {
         creditOrDebitString = "transfer to ";
      } else if (transaction.getAmount() > 0) {
         creditOrDebitString = "deposit from ";
      } else {
         creditOrDebitString = "debit to ";
      }
      getResolver().say("Imported a " + creditOrDebitString + merchant.getName() + " for " +
              formatDollarAmount(Math.abs(transaction.getAmount())) + " on " +
              ((transaction.getAuthorizationDate() != null) ?
                      calendarDateToStringDate(transaction.getAuthorizationDate()) :
                      calendarDateToStringDate(transaction.getPostDate())));
   }


   /*
    * Main methods:
    */
   // Import transactions from a bank in CSV format into the register:
   public boolean importCsvRegisterTransactionFile(FinancialInstitutionInt financialInstitution, Register register,
                                                   Forecast forecast) throws ControllerException, ViewException,
           EntityException, SQLException, BudgetException, RegisterException, QuitException {
      return importCsvRegisterTransactionFile(CLEARED_TRANSACTIONS_FILE_PATHNAME, financialInstitution, register,
              forecast);
   }
   public boolean importCsvRegisterTransactionFile(String clearedTransactionsFilename, FinancialInstitutionInt financialInstitution,
                                                   Register register, Forecast forecast)
           throws SQLException, BudgetException, ControllerException, ViewException, RegisterException, EntityException, QuitException {

      /*
       * Import transactions from the CSV file:
       */
      int i,j = 0;
      try {
         Transaction transaction;

         // Open the import file:
         BufferedReader br = Utility.openBufferedFileReader(REGISTER_TRANSACTIONS_FILE, clearedTransactionsFilename);

         // Read the records in the file into a list so that we can process them in reverse order:
         List<CSVRecord> recordList = new ArrayList<>();
         Iterable<CSVRecord> records = CSVFormat.RFC4180.withHeader(Transaction.Headers.class).parse(br);
         for (CSVRecord record : records) {
            recordList.add(record);
         }
         br.close();

         // For each transaction in the import file:
         HashMap<String, String> map = new HashMap<>();
         String importRecordId;
         Merchant merchant;
         boolean firstTransaction = true;
         for (i = recordList.size() - 1, j = 0; i > -1; i--, j++) {
            CSVRecord record = recordList.get(i);

            /*
             * Phase 1:  create or retrieve the transaction and the merchant associated with it:
             */
            // Construct an ID for this import record:
            importRecordId = financialInstitution.getRegisterImportRecordBaseName(record);
            importRecordId = getImportRecordId(map, importRecordId);

            // Get the transaction and merchant for this import record:
            transaction = Transaction.getByImportRecordId(importRecordId);

            if (transaction != null) {
               merchant = transaction.getMerchant();
            } else {
               transaction = financialInstitution.createFromCSVRecord(record, importRecordId);
               merchant = Merchant.getByPayee(transaction.getMerchantPayee());
            }

            // It is expected that transactions will be downloaded almost daily, so if the first transaction is more
            // than a week old, ask the user to verify that they indeed want to import these old transactions:
            if (firstTransaction) {
               Calendar oneWeekAgo = Calendar.getInstance();
               oneWeekAgo.add(Calendar.DATE, -7);
               if (transaction.getDate().before(oneWeekAgo)) {
                  getResolver().say("\nThe earliest transaction in the import file seems old.");
                  getResolver().say(transaction.toStringConcise());
                  if (!getResolver().getYesOrNo("Are you sure you want to import it?")) {
                     throw new FileNotFoundException("Specified import file contains old transactions.");
                  };
               }
               firstTransaction = false;
            }

            // Let the resolver know we are beginning a new item:
            resolver.beginImportItem(transaction);

            // If there wasn't a merchant associated with the transaction payee then assign or create one:
            if (merchant == null) {
               merchant = resolver.assignMerchant(transaction.getMerchantPayee(), transaction.getPayee(), transaction.getAmount());

               // If the user aborted the merchant assignment process then figure out what to do:
               if (merchant == null) {
                  switch (resolver.getTerminationCondition()) {
                     case SKIP:
                        transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                        continue;

                     case INQUIRE:
                        List<User> users = User.getAllUsers();
                        User user = getResolver().getUser("Select the user to send the notification to",
                                users, true);
                        if (user != null) {
                           getNotificationService().requestIdentifyMerchant(user, transaction);
                        }
                        continue;

                     case QUIT:
                        break;

                     default:
                        throw new ControllerException("Invalid termination condition " +
                                resolver.getTerminationCondition() + " during transaction import");
                  }
               }
            }

            // then update the transaction merchant info from the merchant that we just assigned or created:
            transaction.setMerchant(merchant);
            transaction.setIdMerchant(merchant.getId());

            /*
             * Phase 2:  Reconcile the transaction with any existing provisional transactions
             */

            // If there is a provisional transaction for this transaction, then use the same ID.  Also, if there is a
            // provisional transaction, then the amount of this transaction has already been deducted from the register
            // balance, so no need to do that:
            Transaction provisionalTransaction = financialInstitution.getMatchingProvisionalTransaction(record,
                    merchant);
            if (provisionalTransaction != null) {
               transaction.setId(provisionalTransaction.getId());
               transaction.setIsImproper(provisionalTransaction.getIsImproper());
               transaction.setIsNew(provisionalTransaction.getIsNew());
            } else {
               // Since there is no provisional transaction, the amount has not yet been deducted from the register
               // balance, so deduct it now:
               register.setBalance(register.getBalance() + transaction.getAmount());
            }

            // At this point the transaction is complete, so save it off:
            transaction.save(INSERT_ON_DUPLICATE_UPDATE);
            register.update();

            // Tell the user what we just did:
            logImportEvent(transaction, merchant);

            /*
             * Phase 3:  Get the assigned budget items for this merchant:
             */

            // Get the assigned budget items for the merchant:
            List<BudgetItemMerchant> budgetItems = BudgetItemMerchant.getAssignedBudgetItems(merchant);

            // If we couldn't find any matching items, get some help from the user:
            if (budgetItems.size() < 1) {
               budgetItems = resolver.assignBudgetItems(merchant);
               if (budgetItems == null) {
                  switch (resolver.getTerminationCondition()) {
                     case SKIP:
                        continue;

                     case INQUIRE:
                        List<User> users = User.getAllUsers();
                        User user = getResolver().getUser("Select the user to send the notification to",
                                users, true);
                        if (user != null) {
                           getNotificationService().requestAssignBudgetItems(user, merchant);
                        }
                        continue;

                     case QUIT:
                        break;

                     default:
                        throw new ControllerException("Invalid termination condition " +
                                resolver.getTerminationCondition() + " during transaction import");
                  }
               }
            }

            /*
             * Phase 4:  Assign the splits to the transaction:
             */

            // Get the splits for the transaction.  Create them if they don't already exist:
            List<TransactionSplit> splits = TransactionSplit.getSplitsForTransaction(transaction);
            if (splits == null) {
               splits = resolver.assignAmountsToBudgetItems(transaction, merchant, budgetItems);
            }

            // If the user aborted the split assignment process, then figure out what to do:
            if (splits == null) {
               switch (resolver.getTerminationCondition()) {
                  case SKIP:
                     continue;

                  case INQUIRE:
                     List<User> users = User.getAllUsers();
                     User user = getResolver().getUser("Select the user to send the notification to",
                             users, true);
                     if (user != null) {
                        getNotificationService().requestAssignSplits(user, transaction);
                     }
                     continue;

                  case QUIT:
                     break;

                  default:
                     throw new ControllerException("Invalid termination condition " +
                             resolver.getTerminationCondition() + " during split assignment.");
               }
            }

            // The splits are now complete so save them off:
            for (TransactionSplit split : splits) {
               System.out.println(split.toString());
               split.save();
            }

            /*
             * Phase 5:  Reconcile the transaction with the forecast:
             */

            // Reconcile this transaction with the forecast:
            ForecastTransaction.reconcile(forecast, transaction, splits, resolver);

            // We don't need to figure out what to do if the user aborted the reconciliation process
            // because there is nothing left to do with this transaction.

         } // End for each record in the transactions file.

         /*
          * Phase 6:  Performa any tasks that are necessitated by the results of the update:
          */
         // TODO: Process any significant events that occurred during reconciliation:

         /*
          * Phase 7:  Clean up and terminate:
          */
         // Create a save version of the import file:
         versionFile(clearedTransactionsFilename);

         // TODO: Save the import event:

      } catch (FileNotFoundException e) {
         if (!getResolver().getYesOrNo("Do you want to continue?")) {
            QuitException qe = new QuitException(REGISTER_TRANSACTIONS_FILE + " " +
                    clearedTransactionsFilename + " is invalid or not found.");
            qe.initCause(e);
            throw (qe);
         }
      } catch (IOException e) {
         ControllerException ce = new ControllerException("I/O error reading from the transactions file " +
                 clearedTransactionsFilename + "on line " + j + ".");
         ce.initCause(e);
         throw (ce);
      } catch (FinancialAppException e) {
         ControllerException ve =  new ControllerException("Error occured while creating a previous version of the " +
                 "forecast transaction import file.");
         ve.initCause(e);
         throw ve;
      } catch (Exception e) {
         ControllerException ce = new ControllerException("Exception while processing the transactions file " +
                 clearedTransactionsFilename + " on line " + j + ".");
         ce.initCause(e);
         throw ce;
      }

      // Return the number of transactions imported:
      if (j > 0) {
         getResolver().say("\nSuccessfully imported " + j + " transactions into the register.");
      }
      return forecast.getInSync();

   } // End importCsvTransactionFile(Connection dbConnection).


   /*
    *  Import the provisional transactions from the import file:
    */
   public boolean importCsvProvisionalTransactionFile(FinancialInstitutionInt financialInstitution, Register register,
                                                      Forecast forecast) throws RegisterException, ControllerException, EntityException, BudgetException, FinancialAppException {
      return importCsvProvisionalTransactionFile(PROVISIONAL_TRANSACTIONS_FILE_PATHNAME, financialInstitution,
              register, forecast);
   }

   public boolean importCsvProvisionalTransactionFile(String filename, FinancialInstitutionInt financialInstitution,
                                                      Register register, Forecast forecast) throws RegisterException,
           ControllerException, EntityException, BudgetException, FinancialAppException {

      getResolver().say("Import provisional transactions from the file " + filename + " into the register '" +
              register.getRegisterName() + "'.");

      int provTrxIndex = 0;
      try {
         Transaction transaction = null;

         /*
          * Create a list of new provisional register transactions in ascending payee + amount order from the import file:
          */
         // Open the import file:
         BufferedReader br = openBufferedFileReader("Provisional transactions", filename);

         // Read the records in the provisional transactions file into a list provisional register transactions:
         List<Transaction> provisionalTransactions = new ArrayList<>();
         String line;
         HashMap<String, String> map = new HashMap<>();
         while ((line = br.readLine()) != null) {
            try {
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
                  merchant = resolver.assignMerchant(transaction.getMerchantPayee(), transaction.getPayee(), transaction.getAmount());
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
         br.close();

         // If we found any provisional transactions, then process them:
         if (provisionalTransactions.size() > 0) {

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
            ResultSet rs = EntityInt.getRS(Transaction.getSelectQuery() + " where tr.cleared = false",
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
            provTrxIndex = 0;
            int regTrxIndex = 0;
            while (provTrxIndex < provisionalTransactions.size() || regTrxIndex < registerTransactions.size()) {

               // Compare the current provisional transaction to the current register transaction:
               int comparison;
               if (provTrxIndex < provisionalTransactions.size() && regTrxIndex < registerTransactions.size()) {
                  comparison = comparator.compare(provisionalTransactions.get(provTrxIndex), registerTransactions.get(regTrxIndex));
               } else if (provTrxIndex == provisionalTransactions.size()) {
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
                  Merchant merchant = provisionalTransactions.get(provTrxIndex).getMerchant();
                  List<BudgetItemMerchant> budgetItems = BudgetItemMerchant.getAssignedBudgetItems(merchant);

                  // If we couldn't find any matching items, get some help from the user:
                  if (budgetItems.size() < 1) {
                     budgetItems = getResolver().assignBudgetItems(merchant);
                     if (budgetItems == null) {
                        switch (getResolver().getTerminationCondition()) {
                           case SKIP:
                              // Move to the next provisional transaction:
                              provTrxIndex++;
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
                  getResolver().say();
                  logImportEvent(provisionalTransactions.get(provTrxIndex), merchant);

                  // Get the splits for the transaction:
                  List<TransactionSplit> splits = TransactionSplit.getSplitsForTransaction(provisionalTransactions.get(provTrxIndex));

                  // If we couldn't find any matching items, get some help from the user:
                  if (splits == null) {
                     splits = getResolver().assignAmountsToBudgetItems(provisionalTransactions.get(provTrxIndex), merchant,
                             budgetItems);

                     if (splits == null || splits.isEmpty()) {
                        switch (getResolver().getTerminationCondition()) {
                           case INQUIRE:
                              List<User> users = User.getAllUsers();
                              User user = getResolver().getUser("Select the user to send the notification to",
                                      users, true);
                              if (user != null) {
                                 getNotificationService().requestAssignSplits(user, provisionalTransactions.get(provTrxIndex));
                              }
                              // Move to the next provisional transaction:
                              provTrxIndex++;
                              continue;

                           case SKIP:
                              // Move to the next provisional transaction:
                              provTrxIndex++;
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
                  provisionalTransactions.get(provTrxIndex).save(INSERT);

                  // Update the balance in the register and save it:
                  register.setBalance(register.getBalance() + transaction.getAmount());
                  register.update();

                  for (TransactionSplit split : splits != null ? splits : null) {
                     System.out.println(split.toString());
                     split.save();
                  }

                  // Reconcile this transaction with the forecast:
                  ForecastTransaction.reconcile(forecast, transaction, splits, getResolver());

                  // Move to the next provisional transaction:
                  provTrxIndex++;

               } else if (comparison == 0) {  // else, if key to provision transaction is equal to key of register transaction:

                  // then the transaction has already been entered, so move to the next one on both lists:
                  provTrxIndex++;
                  regTrxIndex++;

               } else {  // else the key to imported transaction is greater than the key to existing transaction

                  // The provisional transaction from the database has fallen off.  If the register transaction is more
                  // than one business day old, then it has likely been withdrawn:
                  if (businessDaysBeteween(Calendar.getInstance(), registerTransactions.get(regTrxIndex).getDate()) > 1) {

                     // Confirm that with the user and remove if they agree:
                     if (getResolver().askDeleteRegisterTransaction(registerTransactions.get(regTrxIndex))) {
                        registerTransactions.get(regTrxIndex).delete();

                        // Update the balance in the register to put back the amount previously deducted and save it:
                        register.setBalance(register.getBalance() - transaction.getAmount());
                        register.update();
                     }

                     // Move to the next register transaction:
                     regTrxIndex++;
                  }
               } // End else the key to the imported transaction is greater than the key to existing transaction.
            } // End while there are provisional or register transactions left to process.
         } // End if there were any transactions in the provisional transactions file.

         // Save off the pending transactions file:
         versionFileAndClear(filename);

         // TODO: Save the import event:

      } catch (FileNotFoundException e) {
         getResolver().say("\nProvisional transactions file " + filename + " not found.");
         if (!getResolver().getYesOrNo("Do you want to continue?")) {
            ControllerException ce = new ControllerException("Transactions file " + filename + " not found.");
            ce.initCause(e);
            throw (ce);
         }
      } catch (IOException e) {
         ControllerException ce = new ControllerException("I/O error reading from the provisional transactions file " +
                 filename + "on line " + provTrxIndex + ".");
         ce.initCause(e);
         throw (ce);
      } catch (Exception e) {
         ControllerException ce = new ControllerException("Exception while processing the provisional transactions file " +
                 filename + " on line " + provTrxIndex + ".");
         ce.initCause(e);
         throw ce;
      }

      // Tell the user the number of transactions imported:
      if (provTrxIndex > 0) {
         getResolver().say("\nSuccessfully imported " + provTrxIndex + " provisional transactions into the register:  " +
                 register + " from file " + filename + ".");
       }
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
