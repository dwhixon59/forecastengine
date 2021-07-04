package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastTransactionSplit;
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

import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.*;
import static com.hixon.financialApp.utility.Utility.*;

public class Importer {

    public static final String CLEARED_TRANSACTIONS_FILE_PATHNAME = "C:\\Users\\dwhix\\Downloads\\Checking2.csv";

    public static final String PROVISIONAL_TRANSACTIONS_FILE_PATHNAME = "C:\\Users\\dwhix\\Downloads\\" +
            "ProvisionalTransactions.txt";
    public static final String REGISTER_TRANSACTIONS_FILE = "Register transactions";

    private ImportLog importLog = new ImportLog();


    // Fields:
    public enum TerminationCondition {RESET, RESTART, FOUND, SKIP, INQUIRE, QUIT}

    public List<Transaction> importedTransactions = new ArrayList<>();


    // Constructors:


    // Getters and setters:


    // Helper functions:

    /**
     * Get the full import record id given the import record base name.  If the record id base name does not already
     * exist in the map, the base name is inserted in the map and the first instance.  If it does already exist in the
     * map it is inserted as the n + 1 instance, where n is the highest number instance already in the map.
     *
     * @param map The map containing the record id's.
     * @param importRecordBaseName The base record id (no instance number).
     * @return The full import record id.
     */
    public String constructImportRecordId(HashMap<String, String> map, String importRecordBaseName) {
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

    /**
     * Given a transaction, this method logs the splits associated with the transaction and the reconciled forecast
     * transaction for each of the splits.
     *
     * @param splits The splits to be logged wit their associated forecast transaction.
     */
    private void logSplitsAndReconciliation(Forecast forecast, List<TransactionSplit> splits)
            throws SQLException, EntityException, ForecastException {

        // Log each transaction split one at a time with its reconciled forecast transactions:
        for (TransactionSplit split: splits
             ) {
            getResolver().say(split.toString());
            ForecastTransactionSplit forecastTransactionSplit =
                    ForecastTransactionSplit.getForecastTransactionSplit(forecast, split);
            if (forecastTransactionSplit != null) {
                ForecastTransaction forecastTransaction =
                        ForecastTransaction.getById(forecastTransactionSplit.getIdForecastTransaction());
                getResolver().say(forecastTransaction.toStringConcise());
            }
        }

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

        resolver.say("Beginning register balance:  " + formatDollarAmount(register.getBalance()));

        /*
         * Import transactions from the CSV file:
         */
        int i, j = 0;
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
                // Construct an ID for this import record from the import record base name:
                importRecordId = constructImportRecordId(map, financialInstitution.getRegisterImportRecordBaseName(record));

                // Get the transaction for this import record ID:
                transaction = Transaction.getByImportRecordId(importRecordId);

                List<TransactionSplit> splits = null;
                // Get the merchant and splits for this transaction if we found one:
                if (transaction != null) {
                    merchant = transaction.getMerchant();

                    // Get the splits for the transaction if they already exist:
                    splits = TransactionSplit.getSplitsForTransaction(transaction);
                } else {
                    try {
                        transaction = financialInstitution.createFromCSVRecord(record, importRecordId);
                    } catch (SkipException se) {
                        merchant = Merchant.getByName(Merchant.UNKNOWN);
                        MerchantPayee merchantPayee = new MerchantPayee(transaction.getPayee(), merchant.getId());
                        merchantPayee.save(INSERT_ON_DUPLICATE_SKIP);
                        transaction.setMerchant(merchant);
                        transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                        register.setBalance(register.getBalance() + transaction.getAmount());
                        register.update();
                        continue;
                    }
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
                        }
                        ;
                    }
                    firstTransaction = false;
                }

                // Let the resolver know we are beginning a new item:
                resolver.beginImportItem(transaction);

                // If we haven't already assigned the splits to this transaction in a previous run:
                if (splits == null) {

                    // If there wasn't a merchant associated with the transaction payee then assign or create one:
                    if (merchant == null) {
                        merchant = resolver.assignMerchant(transaction.getMerchantPayee(), transaction.getPayee(), transaction.getAmount());

                        // If the user aborted the merchant assignment process then figure out what to do:
                        if (merchant == null) {
                            switch (resolver.getTerminationCondition()) {
                                case SKIP:
                                    merchant = Merchant.getByName(Merchant.UNKNOWN);
                                    MerchantPayee merchantPayee = new MerchantPayee(transaction.getMerchantPayee(), merchant.getId());
                                    merchantPayee.save(INSERT_ON_DUPLICATE_SKIP);
                                    transaction.setMerchant(merchant);
                                    transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                                    register.setBalance(register.getBalance() + transaction.getAmount());
                                    register.update();
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

                        // and get the splits if there are any for the provisional transaction:
                        splits = TransactionSplit.getSplitsForTransaction(transaction);
                    } else {
                        // Since there is no provisional transaction, the amount has not yet been deducted from the register
                        // balance, so deduct it now:
                        register.setBalance(register.getBalance() + transaction.getAmount());
                        register.update();
                    }

                    // At this point the transaction is complete, so save it off:
                    transaction.save(INSERT_ON_DUPLICATE_UPDATE);

                    // Tell the user what we just did:
                    importLog.logImportEvent(transaction);

                    // If there was a provisional transaction with assigned splits, then the splits are already assigned.
                    // If that is not the case then we need to assign the splits now.
                    if (splits == null) {

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
                        splits = resolver.assignAmountsToBudgetItems(transaction, merchant, budgetItems);

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
                            split.save();
                        }
                    } else {
                        getResolver().say("Already assigned splits.");
                    }
                } else {

                    // Tell the user what we just did:
                    importLog.logImportEvent(transaction);
                }

                /*
                 * Phase 5:  Reconcile the transaction with the forecast:
                 */

                // Reconcile this transaction with the forecast:
                ForecastTransaction.reconcile(forecast, transaction, splits);

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
            ControllerException ve = new ControllerException("Error occured while creating a previous version of the " +
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
            getResolver().say("\nSuccessfully imported " + j + " cleared transactions into the register:  " +
                    register.getRegisterName() + " from file " + clearedTransactionsFilename + ".");
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
             * Create a list of provisional register transactions in ascending payee + amount order from the import file:
             */
            // Open the import file:
            BufferedReader br = openBufferedFileReader("Provisional transactions", filename);

            // Let the user know what we are doing:
            getResolver().say("\n----------\nRead in the provisional transactions, and assign merchants to them.");

            // Read the records in the provisional transactions file into a list of provisional register transactions:
            List<Transaction> provisionalTransactions = new ArrayList<>();
            String line;
            HashMap<String, String> map = new HashMap<>();
            while ((line = br.readLine()) != null) {
                try {
                    // Load the transaction from the CSV line:
                    try {
                        transaction = financialInstitution.loadProvisionalTransactionFromCSV(line, register);
                    } catch (SkipException se) {
                        continue;
                    }

                    // Construct an ID for this import record and store it in the transaction:
                    String importRecordBaseName = calendarDateToStringSlashDate(transaction.getPostDate()) + "\t" +
                            formatDollarAmount(transaction.getAmount()).substring(1) + "\t" + transaction.isCleared()
                            + "\t" + transaction.getCheckNumber() + "\t" + transaction.getPayee();
                    transaction.setImportRecordId(constructImportRecordId(map, importRecordBaseName));

                    // Get the merchant for this transaction:
                    Merchant merchant = Merchant.getByPayee(transaction.getMerchantPayee());

                    // If we couldn't find a merchant for the transaction, get some help from the user to create one:
                    if (merchant == null) {
                        merchant = resolver.assignMerchant(transaction.getMerchantPayee(), transaction.getPayee(),
                                transaction.getAmount());
                     }
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
                // Let the user know what we are doing:
                getResolver().say("\n----------\nCategorize the provisional transactions.");
                provTrxIndex = 0;
                int regTrxIndex = 0;
                List<TransactionSplit> splits;
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

                    // If the transaction was previously imported, but either the split assignment or reconciliation
                    // was skipped, then treat it as if it were a new transaction:
                    if (comparison == 0) {

                        // Get the splits for the transaction:
                        splits = TransactionSplit.getSplitsForTransaction(registerTransactions.get(regTrxIndex));
                        if (splits != null) {
                            for (TransactionSplit split: splits
                                 ) {
                                if (ForecastTransactionSplit.getForecastTransactionSplit(forecast, split) == null) {
                                    comparison = -1;
                                }
                            }
                        } else {
                            comparison = -1;
                        }
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
                        importLog.logImportEvent(provisionalTransactions.get(provTrxIndex));

                        // Get the splits for the transaction:
                        splits = TransactionSplit.getSplitsForTransaction(provisionalTransactions.get(provTrxIndex));

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

                        // Update the balance in the register and save it:
                        register.setBalance(register.getBalance() + provisionalTransactions.get(provTrxIndex).getAmount());
                        register.update();

                        // Save the transaction and associated splits:
                        provisionalTransactions.get(provTrxIndex).save(INSERT);
                        for (TransactionSplit split : splits != null ? splits : null) {
                            split.save();
                        }

                        // Reconcile this transaction with the forecast:
                        ForecastTransaction.reconcile(forecast, provisionalTransactions.get(provTrxIndex), splits);

                        // Move to the next provisional transaction:
                        provTrxIndex++;

                    } else if (comparison == 0) {  // else, if the transaction was previously imported:

                        // Tell the user what we did:
                        importLog.logImportEvent(provisionalTransactions.get(provTrxIndex));
                        getResolver().say("Transaction wws previously imported.");
                        logSplitsAndReconciliation(forecast,
                                TransactionSplit.getSplitsForTransaction(registerTransactions.get(regTrxIndex)));

                        // then the transaction has already been entered, so move to the next one on both lists:
                        provTrxIndex++;
                        regTrxIndex++;

                    } else {  // else the The provisional transaction from the database has fallen off.

                        //  If the register transaction is more than one business day old, then it has likely been
                        // withdrawn:
                        if (businessDaysBeteween(Calendar.getInstance(), registerTransactions.get(regTrxIndex).getDate()) > 1) {

                            // Confirm that with the user and remove if they agree:
                            if (getResolver().askDeleteRegisterTransaction(registerTransactions.get(regTrxIndex))) {

                                // Add back the amount previously deducted from the register and save it:
                                register.setBalance(register.getBalance() - registerTransactions.get(regTrxIndex).getAmount());
                                register.update();

                                // And delete the transaction that has fallen off:
                                registerTransactions.get(regTrxIndex).delete();
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
                    register.getRegisterName() + " from file " + filename + ".");
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
