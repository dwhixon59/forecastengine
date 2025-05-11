package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.*;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastTransactionSplit;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.merchant.MerchantPayee;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.FinancialAppException;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

import static com.hixon.financialApp.controller.ImportController.TerminationCondition.*;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT_ON_DUPLICATE_SKIP;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT_ON_DUPLICATE_UPDATE;
import static com.hixon.financialApp.utility.Utility.*;

public class ImportController {

    // Logger:
    private ImportLog importLog = new ImportLog();


    // Fields:
    public enum TerminationCondition {INQUIRE, RESTART, FOUND, CANCEL, SKIP, QUIT}

    public TerminationCondition terminationCondition = QUIT;
    private Register register;
    private final FinancialInstitutionInt financialInstitution;
    private Budget budget;
    private Forecast forecast;
    private ViewInt view;
    private NotificationServiceInt notificationService;
    public List<Transaction> importedTransactions = new ArrayList<>();


    // Constructors:
    ImportController(Register register, FinancialInstitutionInt financialInstitution, Budget budget, Forecast forecast,
                     ViewInt view, NotificationServiceInt notificationService) {

        this.register = register;
        this.financialInstitution = financialInstitution;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
    }


    // Getters and setters:


    // Helper functions:

    /**
     * Get the full import record id given the import record base name.  If the record id base name does not already
     * exist in the map, the base name is inserted in the map and the first instance.  If it does already exist in the
     * map it is inserted as the n + 1 instance, where n is the highest number instance already in the map.
     *
     * @param map                  The map containing the record id's.
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
        for (TransactionSplit split : splits
        ) {
            view.say(split.toString());
            ForecastTransactionSplit forecastTransactionSplit =
                    ForecastTransactionSplit.getForecastTransactionSplit(forecast, split);
            if (forecastTransactionSplit != null) {
                ForecastTransaction forecastTransaction =
                        ForecastTransaction.getById(forecastTransactionSplit.getIdForecastTransaction());
                view.say(forecastTransaction.toStringConcise());
            }
        }

    }


    /*
     * Main methods:
     */
    // Import transactions from a bank in CSV format into the register:
    public boolean importCsvRegisterTransactionFile() throws ControllerException, ViewException,
            EntityException, SQLException, BudgetException, RegisterException, QuitException {
        return importCsvRegisterTransactionFile(register.getTrxImportFilePath());
    }

    public boolean importCsvRegisterTransactionFile(String clearedTransactionsFilename)
            throws SQLException, BudgetException, ControllerException, ViewException, RegisterException, EntityException,
            QuitException {

        resolver.say("Beginning register balance:  " + formatDollarAmount(register.getBalance()));

        /*
         * Import transactions from the CSV file:
         */
        int i, j = 0;
        try {
            Transaction transaction;

            // Open the import file:
            BufferedReader br = Utility.openBufferedFileReader(Transaction.CLEARED_TRANSACTIONS_FILE,
                    clearedTransactionsFilename);

            // Use CSVFormat.Builder for better forward compatibility
            CSVFormat format = CSVFormat.Builder.create(CSVFormat.RFC4180)
                    .setHeader(Transaction.Headers.class)
                    .setTrim(true)
                    .build();

            List<CSVRecord> recordList = new ArrayList<>();

            try (CSVParser parser = new CSVParser(br, format)) {
                for (CSVRecord record : parser) {
                    recordList.add(record);
                }
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

                // Get the merchant and splits for this transaction if we found one:
                List<TransactionSplit> splits = null;
                if (transaction != null) {
                    // This transaction has already been imported, so get the merchant and splits for it:
                    merchant = transaction.getMerchant();
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
                        view.say("\nThe earliest transaction in the import file seems old.");
                        view.say(transaction.toStringConcise());
                        if (!view.getYesOrNo("Are you sure you want to import it?")) {
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

                    // If there wasn't a merchant associated with the transaction payee, then assign or create one:
                    if (merchant == null) {
                        try {
                            MerchantController merchantController = new MerchantController(view, notificationService);
                            merchant = merchantController.assignMerchant(transaction.getMerchantPayee(), transaction.getPayee(),
                                    transaction.getAmount());
                        } catch (CancelException ce) {
                            terminationCondition = CANCEL;
                        } catch (SkipException se) {
                            terminationCondition = SKIP;
                        } catch (QuitException qe) {
                            terminationCondition = QUIT;
                        }

                        // If the user aborted the merchant assignment process, then figure out what to do:
                        if (merchant == null) {
                            switch (terminationCondition) {

                                case INQUIRE:
                                    List<User> users = User.getAllUsers();
                                    User user = view.getUser("Select the user to send the notification to",
                                            users, true);
                                    if (user != null) {
                                        notificationService.requestIdentifyMerchant(user, transaction);
                                    }
                                    continue;

                                case CANCEL:
                                    // Restart processing of the current reocrd:
                                    i++;
                                    j--;
                                    continue;

                                case SKIP:
                                    merchant = Merchant.getByName(Merchant.UNKNOWN);
                                    transaction.setMerchant(merchant);
                                    transaction.setIdMerchant(merchant.getId());
                                    transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                                    register.setBalance(register.getBalance() + transaction.getAmount());
                                    register.update();
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
                    // If there is a provisional transaction for this transaction, then use the same ID.  Also, if there
                    // is a provisional transaction, then the amount of this transaction has already been deducted from
                    // the register balance, so no need to do that:
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

                    /*
                     * Phase 3:  Get the assigned budget items for this merchant:
                     */
                    // If there was a provisional transaction with assigned splits, then the splits are already assigned.
                    // If that is not the case then we need to assign the splits now.
                    BudgetController budgetController = new BudgetController(register, budget, forecast, view,
                            notificationService);
                    if (splits == null) {

                        // Get the assigned budget items for the merchant:
                        List<BudgetItemMerchant> budgetItemsForMerchant =
                                BudgetItemMerchant.getAssignedUnexpiredBudgetItems(budget, merchant);

                        // If we couldn't find any matching items, get some help from the user:
                        if (budgetItemsForMerchant.isEmpty()) {
                            try {
                                budgetController.assignBudgetItemsToMerchant(merchant, budgetItemsForMerchant);
                            } catch (CancelException ce) {
                                // Restart processing of the current record:
                                i++;
                                j--;
                                continue;
                            } catch (SkipException se) {
                                continue;
                            }
                            if (budgetItemsForMerchant.isEmpty()) {
                                List<User> users = User.getAllUsers();
                                User user = view.getUser("Select the user to send the notification to",
                                        users, true);
                                if (user != null) {
                                    notificationService.requestAssignBudgetItems(user, merchant);
                                }
                            }
                        }

                        /*
                         * Phase 4:  Assign the splits to the transaction:
                         */
                        // Get the splits for the transaction.  Create them if they don't already exist:
                        splits = budgetController.assignAmountsToBudgetItems(transaction, merchant, budget,
                                budgetItemsForMerchant);

                        // If the user aborted the split assignment process, then figure out what to do:
                        if (splits == null) {
                            switch (budgetController.getTerminationCondition()) {
                                case CANCEL:
                                    // Restart processing of the current record:
                                    i++;
                                    j--;
                                    continue;

                                case SKIP:
                                    continue;

                                case INQUIRE:
                                    List<User> users = User.getAllUsers();
                                    User user = view.getUser("Select the user to send the notification to",
                                            users, true);
                                    if (user != null) {
                                        notificationService.requestAssignSplits(user, transaction, budget);
                                    }
                                    continue;

                                case QUIT:
                                    break;

                                default:
                                    throw new ControllerException("Invalid termination condition " +
                                            resolver.getTerminationCondition() + " during split assignment.");
                            }
                        }

                        // If the user entered any splits:
                        if (splits != null) {
                            // then they are now complete, so save them off
                            for (TransactionSplit split : splits) {
                                split.save();
                            }
                        }
                    } else {
                        view.say("Already assigned splits.");
                    }
                } else {

                    // Tell the user what we just did:
                    importLog.logImportEvent(transaction);
                }

                /*
                 * Phase 5:  Reconcile the transaction with the forecast:
                 */

                // Reconcile this transaction with the forecast:
                ForecastController forecastController = new ForecastController(register, budget, forecast, view,
                        notificationService);
                forecastController.reconcile(transaction, splits);

                // We don't need to figure out what to do if the user aborted the reconciliation process
                // because there is nothing left to do with this transaction.

            } // End for each record in the transaction file.

            /*
             * Phase 6:  Perform any tasks that are necessitated by the results of the update:
             */
            // TODO: Process any significant events that occurred during reconciliation:

            /*
             * Phase 7:  Clean up and terminate:
             */
            // Create a save version of the import file:
            versionFile(clearedTransactionsFilename);

            // TODO: Save the import event:

        } catch (FileNotFoundException e) {
            if (!view.getYesOrNo("Do you want to continue?")) {
                QuitException qe = new QuitException(Transaction.CLEARED_TRANSACTIONS_FILE + " " +
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
            view.say("\nSuccessfully imported " + j + " cleared transactions into the register:  " +
                    register.getName() + " from file " + clearedTransactionsFilename + ".");
        }
        return forecast.getInSync();

    } // End importCsvTransactionFile(Connection dbConnection).


    /*
     *  Import the provisional transactions from the import file:
     */
    public boolean importCsvProvisionalTransactionFile() throws FinancialAppException {
        return importCsvProvisionalTransactionFile(register.getProvisionalTrxFileDirectory() + "\\" +
                register.getProvisionalTrxFileName());
    }

    public boolean importCsvProvisionalTransactionFile(String filename) throws RegisterException,
            ControllerException, EntityException, BudgetException, FinancialAppException {

        view.say("Import provisional transactions from the file " + filename + " into the register '" +
                register.getName() + "'.");

        int provTrxIndex = 0;
        try {
            Transaction transaction = null;

            /*
             * Create a list of provisional register transactions in ascending payee + amount order from the import file:
             */
            // Open the import file:
            BufferedReader br = openBufferedFileReader("Provisional transactions", filename);

            // Let the user know what we are doing:
            view.say("\n----------\nRead in the provisional transactions, and assign merchants to them.");

            // Read the records in the provisional transactions file into a list of provisional register transactions:
            List<Transaction> provisionalTransactions = new ArrayList<>();
            String line;
            HashMap<String, String> map = new HashMap<>();
            while ((line = br.readLine()) != null) {
                try {
                    // Load the transaction from the CSV line:
                    try {
                        transaction = financialInstitution.loadProvisionalTransactionFromCSV(line, register);
                    } catch (CancelException ce) {
                        continue;
                    } catch (SkipException se) {
                        continue;
                    }

                    // Construct an ID for this import record and store it in the transaction:
                    String importRecordBaseName = calendarDateToStringSlashDate(transaction.getPostDate()) + "\t" +
                            formatDollarAmount(transaction.getAmount()).substring(1) + "\t" +
                            transaction.isCleared() + "\t" + transaction.getCheckNumber() + "\t" + transaction.getPayee();
                    transaction.setImportRecordId(constructImportRecordId(map, importRecordBaseName));

                    // Get the merchant for this transaction:
                    Merchant merchant = Merchant.getByPayee(transaction.getMerchantPayee());

                    // If we couldn't find a merchant for the transaction, get some help from the user to create one:
                    if (merchant == null) {
                        try {
                            MerchantController merchantController = new MerchantController(view, notificationService);
                            merchant = merchantController.assignMerchant(transaction.getMerchantPayee(),
                                    transaction.getPayee(), transaction.getAmount());
                        } catch (CancelException ce) {
                            terminationCondition = CANCEL;
                            continue;
                        } catch (SkipException se) {
                            transaction.setMerchant(merchant.getByName(Merchant.UNKNOWN));
                            transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                            terminationCondition = SKIP;
                            continue;
                        }
                    }
                    transaction.setMerchant(merchant);

                    // Add the transaction to the array of provisional transactions:
                    provisionalTransactions.add(transaction);
                    //TransactionHistory.getInstance().get().stream().forEach(t -> System.out.println(t.toStringConcise()));

                } catch (ParseException ignored) {
                }
            }
            br.close();

            // If we found any provisional transactions, then process them:
            if (provisionalTransactions.size() > 0) {

                //  Sort the list in ascending order by merchant + amount:
                Comparator<Transaction> comparator = (t1, t2) -> {
                    try {
                        String t1Key = t1.getMerchant().getName() + t1.getAmount() + t1.getPayee();
                        String t2Key = t2.getMerchant().getName() + t2.getAmount() + t2.getPayee();
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
                view.say("\n----------\nCategorize the provisional transactions.");
                provTrxIndex = 0;
                int regTrxIndex = 0;
                List<TransactionSplit> splits;
                RegisterController registerController = new RegisterController(register, financialInstitution, budget,
                        forecast, view, notificationService);
                BudgetController budgetController = new BudgetController(register, budget, forecast, view,
                        notificationService);
                while (provTrxIndex < provisionalTransactions.size() || regTrxIndex < registerTransactions.size()) {

                    // Tell the user about the bank transaction we are processing:
                    if (provTrxIndex < provisionalTransactions.size()) {
                        importLog.logImportEvent(provisionalTransactions.get(provTrxIndex));
                    }

                    // Compare the current provisional transaction to the current register transaction:
                    int comparison;
                    if (provTrxIndex < provisionalTransactions.size() && regTrxIndex < registerTransactions.size()) {
                        comparison = comparator.compare(provisionalTransactions.get(provTrxIndex),
                                registerTransactions.get(regTrxIndex));
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
                            for (TransactionSplit split : splits
                            ) {
                                if (ForecastTransactionSplit.getForecastTransactionSplit(forecast, split) == null) {
                                    comparison = -1;
                                }
                            }
                        } else {
                            comparison = -1;
                        }

                        // If the transaction was previously imported, but either the split assignment or reconciliation
                        // was skipped, then copy the register transaction on to the provisional transaction so that we
                        // don't try to insert the provisional transaction into the database:
                        if (comparison == -1) {
                            provisionalTransactions.set(provTrxIndex, registerTransactions.get(regTrxIndex));
                        }
                    }

                    // If the key to the provisional transaction is less than the key to the register transaction:
                    if (comparison < 0) {

                        /*
                         * then this is a new provisional transaction, so add this transaction to the database:
                         */
                        // Get the assigned budget items for the merchant:
                        Merchant merchant = provisionalTransactions.get(provTrxIndex).getMerchant();
                        List<BudgetItemMerchant> budgetItemMerchants =
                                BudgetItemMerchant.getAssignedUnexpiredBudgetItems(budget, merchant);

                        // If we couldn't find any matching items, get some help from the user:
                        if (budgetItemMerchants.size() < 1) {
                            try {
                                // See if there are any expired budget items assigned to the merchant:
                                List<BudgetItemMerchant> expiredBudgetItemMerchants =
                                        BudgetItemMerchant.getAssignedExpiredBudgetItems(budget, merchant);

                                // If there is exactly one expired budget item assigned to the merchant:
                                if (expiredBudgetItemMerchants.size() == 1) {

                                    // Then ask the user if they want to renew it:
                                    BudgetItem budgetItem = BudgetItem.getById(expiredBudgetItemMerchants.get(0).getIdBudgetItem());
                                    if (view.getYesOrNo("There is an expired budget item assigned to the merchant " +
                                            merchant.getName() + "\n" + budgetItem.toStringVeryConcise() +
                                            "\nDo you want to renew it?")) {

                                        // Renew the expired budget item and regenerate the list of budget item merchants:
                                        budgetItem.renew();
                                        budgetItemMerchants = BudgetItemMerchant.getAssignedUnexpiredBudgetItems(budget, merchant);
                                    }
                                }
                                // If there is more than one expired budget item assigned to the merchant:
                                else if (expiredBudgetItemMerchants.size() > 1) {
                                    if (view.getYesOrNo("There are expired budget items assigned to the merchant " +
                                            merchant.getName() + ".  Do you want to view them?")) {
                                        try {
                                            budgetController.renewBudgetItems(expiredBudgetItemMerchants);

                                            // Then ask the user which one they want to renew:
                                            budgetItemMerchants = BudgetItemMerchant.getAssignedUnexpiredBudgetItems(budget, merchant);
                                        } catch (CancelException ce) {
                                            // User canceled the renewal of an expired budget item, so continue without one.
                                        }
                                    }
                                }
                            }
                            catch (SkipException se) {
                                // Move to the next provisional transaction:
                                provTrxIndex++;
                                continue;
                            }

                            // If the user didn't renew any expired budget items, then assign new ones:
                            if (budgetItemMerchants.size() < 1) {
                                try {
                                    budgetController.assignBudgetItemsToMerchant(merchant, budgetItemMerchants);
                                } catch (CancelException|SkipException ce) {

                                    // Move to the next provisional transaction:
                                    provTrxIndex++;
                                    continue;
                                }
                            }
                        }

                        // Get the splits for the transaction:
                        splits = TransactionSplit.getSplitsForTransaction(provisionalTransactions.get(provTrxIndex));

                        // If we couldn't find any matching items, get some help from the user:
                        if (splits == null) {
                            splits = budgetController.assignAmountsToBudgetItems(provisionalTransactions.get(provTrxIndex),
                                    merchant, budget, budgetItemMerchants);

                            if (splits == null || splits.isEmpty()) {
                                switch (view.getTerminationCondition()) {
                                    case INQUIRE:
                                        List<User> users = User.getAllUsers();
                                        User user = view.getUser("Select the user to send the notification to",
                                                users, true);
                                        if (user != null) {
                                            notificationService.requestAssignSplits(user,
                                                    provisionalTransactions.get(provTrxIndex), budget);
                                        }
                                        // Move to the next provisional transaction:
                                        provTrxIndex++;
                                        continue;

                                    case CANCEL:
                                        // Move to the next provisional transaction:
                                        provTrxIndex++;
                                        continue;

                                    case SKIP:
                                        // Move to the next provisional transaction:
                                        provTrxIndex++;
                                        continue;

                                    case QUIT:
                                    case RESTART:
                                        break;

                                    case FOUND:
                                        break;

                                    default:
                                        throw new ControllerException("Invalid termination condition " +
                                                view.getTerminationCondition() + " during transaction import");
                                }
                            }
                        }

                        // Update the balance in the register and save it:
                        register.setBalance(register.getBalance() + provisionalTransactions.get(provTrxIndex).getAmount());
                        register.update();

                        // Save the provisional transaction:
                        provisionalTransactions.get(provTrxIndex).save(INSERT_ON_DUPLICATE_UPDATE);

                        // If the user entered some transaction splits:
                        if (splits != null) {

                            // then save the splits:
                            for (TransactionSplit split : splits) {
                                split.save();
                            }

                            // and then reconcile the splits with the forecast:
                            ForecastController forecastController = new ForecastController(register, budget, forecast, view,
                                    notificationService);
                            forecastController.reconcile(provisionalTransactions.get(provTrxIndex), splits);
                        }

                        // Move to the next provisional transaction:
                        provTrxIndex++;

                    } else if (comparison == 0) {  // else, if the transaction was previously imported:

                        // Tell the user what we did:
                        view.say("Transaction wws previously imported.");
                        logSplitsAndReconciliation(forecast,
                                TransactionSplit.getSplitsForTransaction(registerTransactions.get(regTrxIndex)));

                        // then the transaction has already been entered, so move to the next one on both lists:
                        provTrxIndex++;
                        regTrxIndex++;

                    } else {  // else the provisional transaction from the database has fallen off.

                        //  If the register transaction is more than one business day old, then it has likely been
                        // withdrawn:
                        if (businessDaysBeteween(Calendar.getInstance(), registerTransactions.get(regTrxIndex).getDate()) > 1) {

                            // Confirm that with the user and remove if they agree:
                            if (registerController.askDeleteRegisterTransaction(registerTransactions.get(regTrxIndex))) {

                                // Add back the amount previously deducted from the register and save it:
                                register.setBalance(register.getBalance() - registerTransactions.get(regTrxIndex).getAmount());
                                register.update();

                                // And delete the transaction that has fallen off:
                                registerTransactions.get(regTrxIndex).delete();
                            }
                        }
                        // Move to the next register transaction:
                        regTrxIndex++;
                    } // End else the key to the imported transaction is greater than the key to existing transaction.
                } // End while there are provisional or register transactions left to process.
            } // End if there were any transactions in the provisional transactions file.

            // Save off the pending transactions file:
            versionFileAndClear(filename);

            // TODO: Save the import event:

        } catch (FileNotFoundException e) {
            view.say("\nProvisional transactions file " + filename + " not found.");
            if (!view.getYesOrNo("Do you want to continue?")) {
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
            view.say("\nSuccessfully imported " + provTrxIndex + " provisional transactions into the register:  " +
                    register.getName() + " from file " + filename + ".");
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
