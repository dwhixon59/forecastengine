package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.*;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.financialinstitution.FinancialInstitutionInt;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastTransactionSplit;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.merchant.MerchantPayee;
import com.hixon.financialApp.model.merchant.MerchantUtilities;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.FinancialAppException;
import com.hixon.financialApp.utility.ForecastTransactionMatcher;
import com.hixon.financialApp.view.base.ViewInt;
import lombok.Getter;
import lombok.Setter;
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

/**
 * Controller responsible for importing financial transactions and budget items from external sources.
 *
 * <p>This controller handles the complex process of importing transactions from financial institutions,
 * including both cleared (posted) and provisional (pending) transactions. It orchestrates the workflow
 * of merchant identification, budget item assignment, forecast reconciliation, and transaction splitting.</p>
 *
 * <h3>Key Responsibilities:</h3>
 * <ul>
 *   <li>Import cleared transactions from CSV files provided by financial institutions</li>
 *   <li>Import provisional (pending) transactions that haven't yet cleared</li>
 *   <li>Match provisional transactions with cleared transactions when they post</li>
 *   <li>Identify merchants from transaction payee information</li>
 *   <li>Automatically match transactions to forecast transactions when possible</li>
 *   <li>Assign budget items to transactions through user interaction or automatic matching</li>
 *   <li>Split transaction amounts across multiple budget items</li>
 *   <li>Reconcile imported transactions with the forecast</li>
 *   <li>Maintain register balance accuracy throughout the import process</li>
 *   <li>Import budget items from CSV files</li>
 * </ul>
 *
 * <h3>Import Process Flow:</h3>
 * <p>The import process follows a multi-phase approach:</p>
 * <ol>
 *   <li><b>Phase 1:</b> Create or retrieve transaction and identify merchant</li>
 *   <li><b>Phase 2:</b> Reconcile with provisional transactions (for cleared imports)</li>
 *   <li><b>Phase 2.5:</b> Attempt automatic forecast matching</li>
 *   <li><b>Phase 3:</b> Get assigned budget items for the merchant</li>
 *   <li><b>Phase 4:</b> Assign transaction splits to budget items</li>
 *   <li><b>Phase 5:</b> Reconcile with forecast transactions</li>
 *   <li><b>Phase 6:</b> Process significant events (future implementation)</li>
 *   <li><b>Phase 7:</b> Clean up and version import files</li>
 * </ol>
 *
 * <h3>Automatic Matching:</h3>
 * <p>The controller includes intelligent automatic matching capabilities:</p>
 * <ul>
 *   <li>Matches provisional transactions to cleared transactions based on date, amount, and payee</li>
 *   <li>Handles tip additions in restaurant transactions (provisional amount < cleared amount)</li>
 *   <li>Matches import transactions to forecast transactions within a ±5 day window</li>
 *   <li>Uses scoring algorithms to identify the most likely matches</li>
 *   <li>Reduces user interaction by automatically identifying merchants from payee strings</li>
 * </ul>
 *
 * <h3>User Interaction:</h3>
 * <p>The controller never directly interacts with users. All user interaction is delegated to
 * the ViewInt interface implementation. This allows the same controller logic to work with
 * different view implementations (command line, Excel, web, mobile app, etc.).</p>
 *
 * <h3>Error Handling:</h3>
 * <p>The import process is designed to be fault-tolerant:</p>
 * <ul>
 *   <li>Transactions are processed individually; a failure on one doesn't prevent others from importing</li>
 *   <li>Users can choose to continue, cancel, skip, or quit at various decision points</li>
 *   <li>Import record IDs prevent duplicate imports of the same transaction</li>
 *   <li>File versioning preserves original import files for recovery</li>
 * </ul>
 *
 * @see Transaction
 * @see Register
 * @see Merchant
 * @see BudgetItem
 * @see ForecastTransaction
 * @see FinancialInstitutionInt
 * @see ViewInt
 *
 * @author David Hixon
 * @version 2.0
 * @since 1.0
 */
@Getter
@Setter
public class ImportController {

    // Logger:
    /** Logger for tracking import events and actions during the import process */
    private final ImportLog importLog = new ImportLog();


    // Fields:
    /**
     * Enumeration of possible termination conditions for the import process.
     * These values indicate how a particular operation or transaction processing was terminated.
     */
    public enum TerminationCondition {
        /** Send an inquiry notification to a user for assistance */
        INQUIRE,
        /** Restart processing of the current transaction */
        RESTART,
        /** Successfully found and processed the required data */
        FOUND,
        /** User cancelled the current operation */
        CANCEL,
        /** User chose to skip the current transaction */
        SKIP,
        /** User chose to quit the entire import process */
        QUIT
    }

    /** The current termination condition, determines how the import process should proceed */
    public TerminationCondition terminationCondition = QUIT;

    /** The session controller managing the overall application session */
    private final SessionController sessionController;

    /** The register (bank account) into which transactions are being imported */
    private final Register register;

    /** The financial institution providing the transaction data (e.g., Wells Fargo) */
    private final FinancialInstitutionInt financialInstitution;

    /** The budget associated with this register */
    private final Budget budget;

    /** The forecast used for reconciling imported transactions */
    private final Forecast forecast;

    /** The view interface for all user interactions */
    private final ViewInt view;

    /** Service for sending notifications to users */
    private final NotificationServiceInt notificationService;


    // Constructors:
    /**
     * Creates a new ImportController for importing transactions into a specific register.
     *
     * @param sessionController The session controller managing the application session
     */
    ImportController(SessionController sessionController) {

        this.sessionController = sessionController;
        this.register = sessionController.getRegister();
        this.financialInstitution = sessionController.getFinancialInstitution();
        this.budget = sessionController.getBudget();
        this.forecast = sessionController.getForecast();
        this.view = sessionController.getView();
        this.notificationService = sessionController.getNotificationService();
    }


    // Helper functions:

    /**
     * Constructs a unique import record ID for a transaction to prevent duplicate imports.
     *
     * <p>Import record IDs are used to track which transactions have already been imported.
     * If a transaction with the same base characteristics is imported multiple times
     * (which can happen with some financial institutions), this method assigns an
     * instance number to distinguish between them.</p>
     *
     * <p>For example, if a base record ID is "2025-11-15\t-50.00\tWalmart":</p>
     * <ul>
     *   <li>First occurrence: "2025-11-15\t-50.00\tWalmart\t1"</li>
     *   <li>Second occurrence: "2025-11-15\t-50.00\tWalmart\t2"</li>
     *   <li>Third occurrence: "2025-11-15\t-50.00\tWalmart\t3"</li>
     * </ul>
     *
     * @param map A HashMap tracking instance numbers for each base record ID.
     *            The key is the base record ID, the value is the highest instance number seen.
     * @param importRecordBaseName The base record ID without an instance number.
     *                              Typically includes date, amount, and payee information.
     * @return The full import record ID with instance number appended
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
     * Logs transaction splits and their associated forecast transactions to the view.
     *
     * <p>For each transaction split, this method displays:</p>
     * <ul>
     *   <li>The split details (amount, budget item, memo)</li>
     *   <li>The forecast transaction that was reconciled with this split (if any)</li>
     * </ul>
     *
     * <p>This is useful for showing the user which forecast transactions were satisfied
     * by an imported transaction, helping them understand how their actual spending
     * compares to their planned spending.</p>
     *
     * @param forecast The forecast containing the forecast transactions to check against
     * @param splits The list of transaction splits to log
     * @throws SQLException If a database error occurs while retrieving forecast transaction data
     * @throws EntityException If an error occurs while accessing entity data
     * @throws ForecastException If an error occurs while accessing forecast data
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
                if (forecastTransaction != null) {
                    view.say(forecastTransaction.toStringConcise());
                }
            }
        }

    }


    /*
     * Main methods:
     */

    /**
     * Imports transactions from the register's import file.
     *
     * @return true if the forecast is in sync after the import, false otherwise
     * @throws ControllerException If a controller logic error occurs during import
     * @throws QuitException If the user quits the import process
     * @throws Exception If an error occurs closing the financial institution
     * @see Register#getTrxImportFilePath()
     */
    public boolean importRegisterTransactionFile() throws ControllerException, QuitException, Exception {

        boolean inSync = true;

        // TODO:  Get an iterator for the transactions file:

        // TODO:  Process each transaction in the iterator:

  try {
      while (financialInstitution.hasNext()) {
          Transaction t = financialInstitution.next();
          // Process transaction...
      }
  } finally {
      financialInstitution.close();
  }

        // Call the main import method with the register's configured import file path:
        return inSync;
    }

    /**
     * Imports cleared (posted) transactions from a CSV file into the register.
     *
     * <p>This is the main method for importing transactions that have cleared (posted) at the
     * financial institution. The method performs a comprehensive multi-phase import process:</p>
     *
     * <h4>Phase 1: Transaction Creation/Retrieval</h4>
     * <ul>
     *   <li>Parses each CSV record using the financial institution's format</li>
     *   <li>Creates unique import record IDs to prevent duplicate imports</li>
     *   <li>Retrieves existing transactions if already imported</li>
     *   <li>Checks for outdated transactions (warns if first transaction is over a week old)</li>
     * </ul>
     *
     * <h4>Phase 2: Provisional Transaction Reconciliation</h4>
     * <ul>
     *   <li>Searches for matching provisional (pending) transactions</li>
     *   <li>Handles tip additions (e.g., restaurant charges where tip is added later)</li>
     *   <li>Transfers splits from provisional to cleared transaction</li>
     *   <li>Updates register balance only for new transactions</li>
     * </ul>
     *
     * <h4>Phase 2.5: Automatic Forecast Matching</h4>
     * <ul>
     *   <li>Attempts to identify possible merchants from transaction payee</li>
     *   <li>Searches for forecast transactions within ±5 days</li>
     *   <li>Scores potential matches based on date proximity and amount similarity</li>
     *   <li>Auto-assigns budget items if a confident match is found</li>
     * </ul>
     *
     * <h4>Phase 3: Merchant Identification</h4>
     * <ul>
     *   <li>Looks up merchant from transaction payee</li>
     *   <li>Prompts user to identify or create merchant if not found</li>
     *   <li>Handles user cancellation, skip, or quit requests</li>
     * </ul>
     *
     * <h4>Phase 4: Split Assignment</h4>
     * <ul>
     *   <li>Retrieves budget items assigned to the merchant</li>
     *   <li>Assigns transaction amounts to budget items (may be split across multiple items)</li>
     *   <li>Prompts user for split amounts if needed</li>
     *   <li>Saves splits to database</li>
     * </ul>
     *
     * <h4>Phase 5: Forecast Reconciliation</h4>
     * <ul>
     *   <li>Reconciles each split with corresponding forecast transactions</li>
     *   <li>Updates forecast transaction remaining amounts</li>
     *   <li>Marks forecast transactions as found</li>
     * </ul>
     *
     * <h4>Phase 6: Event Processing (Future)</h4>
     * <ul>
     *   <li>Reserved for processing significant events from reconciliation</li>
     * </ul>
     *
     * <h4>Phase 7: Cleanup</h4>
     * <ul>
     *   <li>Versions the import file (creates backup copy)</li>
     *   <li>Reports number of transactions imported</li>
     * </ul>
     *
     * <p><b>Error Handling:</b> The method is fault-tolerant. If an error occurs with one
     * transaction, the user can choose to continue importing remaining transactions. The
     * import record ID system prevents duplicate imports if the import is restarted.</p>
     *
     * <p><b>User Interaction:</b> At various points, the user may be prompted to:
     * <ul>
     *   <li>Identify or create merchants</li>
     *   <li>Assign budget items to merchants</li>
     *   <li>Specify split amounts across budget items</li>
     *   <li>Choose to cancel, skip, or quit the process</li>
     * </ul>
     * </p>
     *
     * @param clearedTransactionsFilename The full path to the CSV file containing cleared transactions
     * @return true if the forecast is in sync after the import, false otherwise
     * @throws ControllerException If a controller logic error occurs during import
     * @throws QuitException If the user chooses to quit the import process
     * @see Transaction
     * @see Register
     * @see Merchant
     * @see TransactionSplit
     * @see ForecastTransaction
     */
    public boolean importCsvRegisterTransactionFile(String clearedTransactionsFilename)
            throws ControllerException, QuitException {

        resolver.say("Beginning register balance:  " + formatDollarAmount(register.getBalance()));

        /*
         * Import transactions from the CSV file:
         */
        int i, j = 0;
        try {
            Transaction currentTransaction;

            // Open the import file:
            BufferedReader br = openBufferedFileReader(Transaction.CLEARED_TRANSACTIONS_FILE,
                    clearedTransactionsFilename);

            // Describe the format of the CSV file for the CSVFormat.Builder:
            CSVFormat format = CSVFormat.RFC4180.builder()
                    .setHeader(financialInstitution.getCsvHeadersClass())
                    .setTrim(true)
                    .get();

            List<CSVRecord> recordList = new ArrayList<>();

            // Read all records from the CSV file into a list:
            try (CSVParser parser = format.parse(br)) {
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

                // Set up for processing this transaction:
                merchant = null;
                boolean autoMatched = false;  // Track if we auto-matched and already reconciled in Phase 2.5

                /*
                 * Phase 1:  create or retrieve the transaction and the merchant associated with it.  The reason we can
                 * retrieve an existing transaction is that the transaction may have been previously imported:
                 */
                // Construct an ID for this import record from the import record base name:
                importRecordId = constructImportRecordId(map, financialInstitution.getRegisterImportRecordBaseName(record));

                // Get the transaction for this import record ID:
                currentTransaction = Transaction.getByImportRecordId(importRecordId);

                // Track whether this is a new transaction (not previously imported)
                boolean isNewTransaction = (currentTransaction == null);

                // Get the merchant and splits for this transaction if we found one:
                List<TransactionSplit> splits = null;
                if (currentTransaction != null) {
                    // This transaction has already been imported, so get the merchant and splits for it:
                    merchant = currentTransaction.getMerchant();
                    splits = TransactionSplit.getSplitsForTransaction(currentTransaction);
                } else {
                    try {
                        currentTransaction = financialInstitution.createFromCSVRecord(record, importRecordId);
                    } catch (SkipException se) {
                        merchant = Merchant.getByName(Merchant.UNKNOWN);
                        MerchantPayee merchantPayee = new MerchantPayee(currentTransaction.getPayee(), merchant.getId());
                        merchantPayee.save(INSERT_ON_DUPLICATE_SKIP);
                        currentTransaction.setMerchant(merchant);
                        currentTransaction.save(INSERT_ON_DUPLICATE_UPDATE);
                        register.setBalance(register.getBalance() + currentTransaction.getAmount());
                        register.update();
                        continue;
                    }
                }

                // It is expected that transactions will be downloaded almost daily, so if the first transaction is more
                // than a week old, ask the user to verify that they indeed want to import these old transactions:
                if (firstTransaction) {
                    Calendar oneWeekAgo = Calendar.getInstance();
                    oneWeekAgo.add(Calendar.DATE, -7);
                    if (currentTransaction.getDate().before(oneWeekAgo)) {
                        view.say("\nThe earliest transaction in the import file seems old.");
                        view.say(currentTransaction.toStringConcise());
                        if (!view.getYesOrNo("Are you sure you want to import it?")) {
                            throw new FileNotFoundException("Specified import file contains old transactions.");
                        }
                    }
                    firstTransaction = false;
                }

                // Let the resolver know we are beginning a new item:
                resolver.beginImportItem(currentTransaction);

                // If we haven't already assigned the splits to this transaction in a previous run:
                if (splits == null) {

                    /*
                     * Phase 2:  Reconcile the transaction with any existing provisional transactions
                     */
                    // Get matching provisional transaction and reconcile it with the cleared transaction.
                    // The financial institution class handles all the details including tip detection
                    // and balance adjustments. Matching is now done purely on payee, date, and amount.
                    Transaction provisionalTransaction =
                            financialInstitution.getMatchingProvisionalTransaction(currentTransaction);

                    // If we found a provisional transaction:
                    boolean reconciledWithProvisional = false;
                    if (provisionalTransaction != null) {

                        // The merchant from a provisional transaction should never be null, but just in case:
                        if (provisionalTransaction.getMerchant() == null) {
                            throw new ControllerException("Provisional transaction has no merchant assigned.");
                        }

                        // If the merchant is the unknown merchant:
                        if (provisionalTransaction.getMerchant().getName().equals(Merchant.UNKNOWN)) {

                            // then get the merchant with the help of the user:
                            merchant = Merchant.getByPayee(currentTransaction.getMerchantPayee());
                            currentTransaction.setMerchant(merchant);
                        }
                        else {
                            merchant = provisionalTransaction.getMerchant();
                        }

                        // Get the splits from the provisional transaction:
                        splits = TransactionSplit.getSplitsForTransaction(provisionalTransaction);

                        // Let the financial institution reconcile the provisional with cleared transaction
                        reconciledWithProvisional = financialInstitution.reconcileProvisionalTransaction(
                                currentTransaction, provisionalTransaction, register, splits);
                    }

                    // If no provisional transaction was found and this is a new transaction,
                    // update the register balance
                    if (!reconciledWithProvisional && isNewTransaction) {
                        register.setBalance(register.getBalance() + currentTransaction.getAmount());
                        register.update();
                    }

                    /*
                     * Phase 2.5: Auto-match with forecast transactions (if enabled)
                     */
                    if (splits == null) {
                        // Get possible merchants from the transaction payee (0, 1, or more matches)
                        List<Merchant> possibleMerchants =
                            MerchantUtilities.getPossibleMerchantsByPayee(
                                currentTransaction.getMerchantPayee());

                        // Try to find a matching forecast transaction within ±5 days
                        ForecastTransaction matchedForecast =
                            ForecastTransactionMatcher.findMatchingForecastTransaction(
                                currentTransaction, forecast, possibleMerchants, 5, 5);

                        // If we found a confident match
                        if (matchedForecast != null) {
                            // Get the budget item from the forecast transaction
                            UUID idBudgetItem = matchedForecast.getForecastItem().getIdBudgetItem();

                            // Create the split automatically
                            splits = new ArrayList<>();
                            splits.add(new TransactionSplit(currentTransaction.getAmount(), idBudgetItem,
                                    currentTransaction.getId(), null));

                            // Inform the user about the auto-match as a heading
                            view.sayH3("Auto-matched to forecast transaction: " + matchedForecast.toStringConcise());

                            // If we found a merchant from the payee, use it
                            if (possibleMerchants != null && possibleMerchants.size() == 1) {
                                merchant = possibleMerchants.getFirst();  // Update local variable
                                currentTransaction.setMerchant(merchant);
                                currentTransaction.setIdMerchant(merchant.getId());
                            } else if (merchant != null) {
                                // Use the merchant we already identified
                                currentTransaction.setMerchant(merchant);
                                currentTransaction.setIdMerchant(merchant.getId());
                            }

                            // Save the transaction with merchant info
                            currentTransaction.save(INSERT_ON_DUPLICATE_UPDATE);

                            // Save the splits
                            for (TransactionSplit split : splits) {
                                split.save(INSERT_ON_DUPLICATE_UPDATE);
                            }

                            // Reconcile immediately with the forecast (no need to do it again in Phase 5)
                            ForecastController forecastController = new ForecastController(
                                    sessionController);
                            forecastController.reconcile(currentTransaction, splits);

                            // Mark that we've auto-matched and already reconciled
                            autoMatched = true;
                        }
                    }

                    // If we haven't determined the merchant yet, then assign or create one:
                    if (merchant == null) {
                        try {
                            MerchantController merchantController = new MerchantController(sessionController);
                            merchant = merchantController.assignMerchant(currentTransaction.getMerchantPayee(), currentTransaction.getPayee(),
                                    currentTransaction.getAmount());
                            currentTransaction.setIdMerchant(merchant.getId());
                            currentTransaction.setMerchant(merchant);
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
                                        notificationService.requestIdentifyMerchant(user, currentTransaction);
                                    }
                                    continue;

                                case CANCEL:
                                    // Restart processing of the current reocrd:
                                    i++;
                                    j--;
                                    continue;

                                case SKIP:
                                    merchant = Merchant.getByName(Merchant.UNKNOWN);
                                    if (merchant != null) {
                                        currentTransaction.setMerchant(merchant);
                                        currentTransaction.setIdMerchant(merchant.getId());
                                    }
                                    currentTransaction.save(INSERT_ON_DUPLICATE_UPDATE);
                                    register.setBalance(register.getBalance() + currentTransaction.getAmount());
                                    register.update();
                                    continue;

                                case QUIT:
                                    throw new QuitException("User quit during merchant assignment");

                                default:
                                    throw new ControllerException("Invalid termination condition " +
                                            resolver.getTerminationCondition() + " during transaction import");
                            }
                        }
                    }

                    // At this point the transaction is complete, so save it off:
                    // Only save if we didn't already save in Phase 2.5
                    currentTransaction.save(INSERT_ON_DUPLICATE_UPDATE);

                    // Tell the user what we just did:
                    importLog.logImportEvent(currentTransaction, isNewTransaction);

                    /*
                     * Phase 3:  Get the assigned budget items for this merchant:
                     */
                    // If there was a provisional transaction with assigned splits, then the splits are already assigned.
                    // If that is not the case then we need to assign the splits now.
                    if (splits == null) {
                        // Declare BudgetController here since it's only needed in this block
                        BudgetController budgetController = new BudgetController(sessionController);

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
                        splits = budgetController.assignAmountsToBudgetItems(currentTransaction, merchant, budget,
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
                                        notificationService.requestAssignSplits(user, currentTransaction, budget);
                                    }
                                    continue;

                                case QUIT:
                                    throw new QuitException("User quit during split assignment");

                                default:
                                    throw new ControllerException("Invalid termination condition " +
                                            resolver.getTerminationCondition() + " during split assignment.");
                            }
                        }

                        // Save the splits using INSERT_ON_DUPLICATE_UPDATE to handle both new and existing splits
                        for (TransactionSplit split : splits) {
                            split.save(INSERT_ON_DUPLICATE_UPDATE);
                        }
                    } else {
                        view.say("Already assigned splits.");
                    }
                } else {

                    // Tell the user what we just did:
                    importLog.logImportEvent(currentTransaction, false);
                }

                /*
                 * Phase 5:  Reconcile the transaction with the forecast:
                 */

                // Only reconcile if we didn't already reconcile in Phase 2.5
                if (!autoMatched) {
                    // Reconcile this transaction with the forecast:
                    ForecastController forecastController = new ForecastController(
                            sessionController);
                    forecastController.reconcile(currentTransaction, splits);
                }

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


    /**
     * Imports provisional (pending) transactions from the register's default import file.
     *
     * <p>This is a convenience method that calls {@link #importCsvProvisionalTransactionFile(String)}
     * with the file path constructed from the register's provisional transaction directory and filename.</p>
     *
     * @return true if the forecast is in sync after the import, false otherwise
     * @throws FinancialAppException If any error occurs during the import process
     * @see Register#getProvisionalTrxFileDirectory()
     * @see Register#getProvisionalTrxFileName()
     */
    public boolean importCsvProvisionalTransactionFile() throws FinancialAppException {
        return importCsvProvisionalTransactionFile(register.getProvisionalTrxFileDirectory() + "\\" +
                register.getProvisionalTrxFileName());
    }

    /**
     * Imports provisional (pending) transactions from a TSV (tab-separated values) file into the register.
     *
     * <p>Provisional transactions are transactions that have been authorized but not yet posted
     * (cleared) at the financial institution. Examples include:</p>
     * <ul>
     *   <li>Restaurant charges before the tip is added</li>
     *   <li>Gas station pre-authorizations before the final amount is determined</li>
     *   <li>Debit card purchases that haven't cleared yet</li>
     *   <li>Scheduled transfers that haven't executed yet</li>
     * </ul>
     *
     * <p>This method performs a sophisticated merge operation between provisional transactions
     * from the import file and existing provisional transactions in the database:</p>
     *
     * <h4>Processing Steps:</h4>
     * <ol>
     *   <li><b>Read and Parse:</b> Load all provisional transactions from the file</li>
     *   <li><b>Sort Both Lists:</b> Sort file transactions and database transactions by payee + amount</li>
     *   <li><b>Merge Algorithm:</b> Compare sorted lists to identify:
     *     <ul>
     *       <li>New provisional transactions to add</li>
     *       <li>Existing provisional transactions to skip</li>
     *       <li>Old provisional transactions that have been withdrawn</li>
     *     </ul>
     *   </li>
     *   <li><b>Automatic Forecast Matching:</b> For new transactions:
     *     <ul>
     *       <li>Attempt to match with forecast transactions (±5 days)</li>
     *       <li>Auto-assign budget items if confident match found</li>
     *     </ul>
     *   </li>
     *   <li><b>Merchant Identification:</b> Identify merchant from payee (only if forecast matching fails)</li>
     *   <li><b>Budget Item Assignment:</b> Get assigned budget items for the merchant</li>
     *   <li><b>Split Assignment:</b> Prompt user to assign amounts to budget items</li>
     *   <li><b>Expired Item Handling:</b> Offer to renew expired budget items</li>
     *   <li><b>Reconciliation:</b> Reconcile splits with forecast</li>
     *   <li><b>Withdrawal Detection:</b> Remove provisional transactions that are more than
     *       1 business day old and no longer in the file (likely withdrawn)</li>
     * </ol>
     *
     * <h4>Merge Logic:</h4>
     * <p>The method uses a two-pointer merge algorithm similar to merging sorted arrays:</p>
     * <ul>
     *   <li><b>File transaction &lt; DB transaction:</b> New provisional transaction, process and insert it</li>
     *   <li><b>File transaction = DB transaction:</b> Already imported, skip it</li>
     *   <li><b>File transaction &gt; DB transaction:</b> DB transaction missing from file, may be withdrawn</li>
     * </ul>
     *
     * <h4>Special Handling:</h4>
     * <ul>
     *   <li><b>Expired Budget Items:</b> If a merchant has expired budget items, offers to renew them</li>
     *   <li><b>Withdrawn Transactions:</b> Asks user to confirm deletion of old transactions no longer in file</li>
     *   <li><b>Incomplete Reconciliation:</b> Treats previously imported transactions with incomplete
     *       reconciliation as new transactions</li>
     * </ul>
     *
     * <p><b>Why Provisional Transactions Matter:</b> Importing provisional transactions allows
     * users to see pending charges immediately in their forecast, rather than waiting days
     * for transactions to clear. When the transaction eventually clears, the cleared import
     * process will match it to the provisional transaction and merge them.</p>
     *
     * <p><b>File Format:</b> The file is expected to be in TSV format compatible with the
     * financial institution's provisional transaction export format.</p>
     *
     * <p><b>Cleanup:</b> After successful import, the provisional transaction file is versioned
     * (backed up) and cleared, ready for the next download.</p>
     *
     * @param filename The full path to the TSV file containing provisional transactions
     * @return true if the forecast is in sync after the import, false otherwise
     * @throws RegisterException If an error occurs while updating the register
     * @throws ControllerException If a controller logic error occurs during import
     * @throws EntityException If a database error occurs
     * @throws BudgetException If an error occurs while processing budget items
     * @throws FinancialAppException If any other error occurs during the import process
     * @see #importCsvRegisterTransactionFile(String)
     * @see Transaction
     * @see TransactionSplit
     * @see ForecastTransaction
     */
    public boolean importCsvProvisionalTransactionFile(String filename) throws
            ControllerException, FinancialAppException {

        view.say("Import provisional transactions from the file " + filename + " into the register '" +
                register.getName() + "'.");

        int provTrxIndex = 0;
        try {
            Transaction transaction;

            /*
             * Create a list of provisional register transactions in ascending payee + amount order from the import file:
             */
            // Open the import file:
            BufferedReader br = openBufferedFileReader("Provisional transactions", filename);

            // Let the user know what we are doing:
            view.say("\n----------\nRead in the provisional transactions.");

            // Read the records in the provisional transactions file into a list of provisional register transactions:
            List<Transaction> provisionalTransactions = new ArrayList<>();
            String line;
            HashMap<String, String> map = new HashMap<>();
            while ((line = br.readLine()) != null) {
                try {
                    // Load the transaction from the CSV line:
                    try {
                        transaction = financialInstitution.loadProvisionalTransactionFromCSV(line, register);
                    } catch (CancelException | SkipException ce) {
                        continue;
                    }

                    // Construct an ID for this import record and store it in the transaction:
                    String importRecordBaseName = calendarDateToStringSlashDate(transaction.getPostDate()) + "\t" +
                            formatDollarAmount(transaction.getAmount()).substring(1) + "\t" +
                            transaction.isCleared() + "\t" + transaction.getCheckNumber() + "\t" + transaction.getPayee();
                    transaction.setImportRecordId(constructImportRecordId(map, importRecordBaseName));

                    // Add the transaction to the array of provisional transactions:
                    provisionalTransactions.add(transaction);
                    //TransactionHistory.getInstance().get().stream().forEach(t -> System.out.println(t.toStringConcise()));

                } catch (ParseException ignored) {
                }
            }
            br.close();

            // If we found any provisional transactions, then process them:
            if (!provisionalTransactions.isEmpty()) {

                //  Sort the list in ascending order by payee + amount:
                Comparator<Transaction> comparator = (t1, t2) -> {
                    String t1Key = t1.getPayee() + t1.getAmount();
                    String t2Key = t2.getPayee() + t2.getAmount();
                    return t1Key.compareTo(t2Key);
                };
                provisionalTransactions.sort(comparator);

                /*
                 * Retrieve a list of the existing provisional transactions from the database and them sort them in ascending
                 * order by payee + amount :
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
                int regTrxIndex = 0;
                List<TransactionSplit> splits;
                RegisterController registerController = new RegisterController(sessionController);
                BudgetController budgetController = new BudgetController(sessionController);
                while (provTrxIndex < provisionalTransactions.size() || regTrxIndex < registerTransactions.size()) {


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

                        // Parse the merchant payee for this NEW transaction (deferred from loadProvisionalTransactionFromCSV).
                        // This avoids prompting user for transfers without account numbers when the transaction
                        // already exists in the database.
                        String merchantPayee = financialInstitution.parseMerchantPayee(
                                provisionalTransactions.get(provTrxIndex).getPostDate(),
                                provisionalTransactions.get(provTrxIndex).getAmount(),
                                provisionalTransactions.get(provTrxIndex).getPayee());
                        provisionalTransactions.get(provTrxIndex).setMerchantPayee(merchantPayee);

                        // Display basic transaction info so user knows what we're processing
                        view.say("\nProcessing provisional transaction:");
                        view.say("  Date: " + calendarDateToStringDate(provisionalTransactions.get(provTrxIndex).getPostDate()));
                        view.say("  Amount: " + formatDollarAmount(provisionalTransactions.get(provTrxIndex).getAmount()));
                        view.say("  Payee: " + provisionalTransactions.get(provTrxIndex).getPayee());
                        view.say("  Merchant Payee: " + merchantPayee);
                        view.say("  Merchant: " +
                                (provisionalTransactions.get(provTrxIndex).getMerchant() != null ?
                                        provisionalTransactions.get(provTrxIndex).getMerchant().getName() : "Not assigned"));

                        // Get the splits for the transaction:
                        splits = TransactionSplit.getSplitsForTransaction(provisionalTransactions.get(provTrxIndex));

                        /*
                         * Phase 2.5: Try to match with forecast transactions directly (bypassing manual budget item selection)
                         */

                        // If we don't have splits yet, try forecast matching
                        if (splits == null) {
                            // Get possible merchants from the transaction payee (0, 1, or more matches)
                            List<Merchant> possibleMerchants =
                                MerchantUtilities.getPossibleMerchantsByPayee(
                                    provisionalTransactions.get(provTrxIndex).getMerchantPayee());

                            // Try to find a matching forecast transaction within ±5 days
                            ForecastTransaction matchedForecast =
                                ForecastTransactionMatcher.findMatchingForecastTransaction(
                                    provisionalTransactions.get(provTrxIndex), forecast, possibleMerchants, 5, 5);

                            // If we found a confident match
                            if (matchedForecast != null) {
                                // Get the budget item from the forecast transaction
                                UUID idBudgetItem = matchedForecast.getForecastItem().getIdBudgetItem();

                                // Create the split automatically
                                splits = new ArrayList<>();
                                splits.add(new TransactionSplit(provisionalTransactions.get(provTrxIndex).getAmount(),
                                        idBudgetItem, provisionalTransactions.get(provTrxIndex).getId(), null));

                                // Inform the user about the auto-match as a heading
                                view.sayH3("Auto-matched to forecast transaction: " + matchedForecast.toStringConcise());

                                // If we found a merchant from the payee, use it
                                if (possibleMerchants != null && possibleMerchants.size() == 1) {
                                    provisionalTransactions.get(provTrxIndex).setMerchant(possibleMerchants.getFirst());
                                } else if (possibleMerchants != null && !possibleMerchants.isEmpty()) {
                                    // Multiple possible merchants - get the first one or ask user
                                    provisionalTransactions.get(provTrxIndex).setMerchant(possibleMerchants.getFirst());
                                }

                                // Update the balance in the register and save it:
                                register.setBalance(register.getBalance() + provisionalTransactions.get(provTrxIndex).getAmount());
                                register.update();

                                // Log the import event
                                importLog.logImportEvent(provisionalTransactions.get(provTrxIndex));

                                // Save the provisional transaction with merchant info
                                provisionalTransactions.get(provTrxIndex).save(INSERT_ON_DUPLICATE_UPDATE);

                                // Save the splits
                                for (TransactionSplit split : splits) {
                                    split.save(INSERT_ON_DUPLICATE_UPDATE);
                                }

                                // Reconcile immediately with the forecast (no need to do it again later)
                                ForecastController forecastController = new ForecastController(
                                        sessionController);
                                forecastController.reconcile(provisionalTransactions.get(provTrxIndex), splits);

                                // Move to the next provisional transaction since we're done with this one
                                provTrxIndex++;
                                continue;
                            }
                        }

                        // Now determine merchant if needed - either from exact match or user input
                        Merchant merchant = provisionalTransactions.get(provTrxIndex).getMerchant();

                        // If merchant still not determined, try exact match first
                        if (merchant == null) {
                            merchant = Merchant.getByPayee(provisionalTransactions.get(provTrxIndex).getMerchantPayee());
                            if (merchant != null) {
                                provisionalTransactions.get(provTrxIndex).setMerchant(merchant);
                            }
                        }

                        // Get budget items for merchant if we have one
                        List<BudgetItemMerchant> budgetItemMerchants = new ArrayList<>();
                        if (merchant != null) {
                            budgetItemMerchants = BudgetItemMerchant.getAssignedUnexpiredBudgetItems(budget, merchant);
                        }

                        // If we couldn't find any matching items, get some help from the user:
                        if (splits == null && budgetItemMerchants.isEmpty() && merchant != null) {
                            try {
                                // See if there are any expired budget items assigned to the merchant:
                                List<BudgetItemMerchant> expiredBudgetItemMerchants =
                                        BudgetItemMerchant.getAssignedExpiredBudgetItems(budget, merchant);

                                // If there is exactly one expired budget item assigned to the merchant:
                                if (expiredBudgetItemMerchants.size() == 1) {

                                    // Then ask the user if they want to renew it:
                                    BudgetItem budgetItem = BudgetItem.getById(expiredBudgetItemMerchants.getFirst().getIdBudgetItem());
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
                            if (budgetItemMerchants.isEmpty()) {
                                try {
                                    budgetController.assignBudgetItemsToMerchant(merchant, budgetItemMerchants);
                                } catch (CancelException|SkipException ce) {

                                    // Move to the next provisional transaction:
                                    provTrxIndex++;
                                    continue;
                                }
                            }
                        }

                        // If forecast matching didn't provide splits, we need manual reconciliation
                        // This requires identifying the merchant if we don't have one yet
                        if (splits == null) {
                            // If we still don't have a merchant, ask the user to identify it
                            if (merchant == null) {
                                try {
                                    MerchantController merchantController = new MerchantController(sessionController);
                                    Merchant assignedMerchant = merchantController.assignMerchant(
                                            provisionalTransactions.get(provTrxIndex).getMerchantPayee(),
                                            provisionalTransactions.get(provTrxIndex).getPayee(),
                                            provisionalTransactions.get(provTrxIndex).getAmount());

                                    if (assignedMerchant != null) {
                                        merchant = assignedMerchant;
                                        provisionalTransactions.get(provTrxIndex).setMerchant(merchant);
                                        // Get budget item merchants list for the newly identified merchant
                                        budgetItemMerchants = BudgetItemMerchant.getAssignedUnexpiredBudgetItems(budget, merchant);
                                    }
                                } catch (CancelException ce) {
                                    // User cancelled, move to next transaction
                                    provTrxIndex++;
                                    continue;
                                } catch (SkipException se) {
                                    // User skipped, move to next transaction
                                    provTrxIndex++;
                                    continue;
                                }
                            }

                            // Now attempt to assign budget items to the transaction
                            if (merchant != null) {
                                splits = budgetController.assignAmountsToBudgetItems(provisionalTransactions.get(provTrxIndex),
                                        merchant, budget, budgetItemMerchants);

                                if (splits == null || splits.isEmpty()) {
                                    switch (budgetController.getTerminationCondition()) {
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
                                            throw new QuitException("User quit during provisional transaction split assignment");

                                        case RESTART:
                                            break;

                                        case FOUND:
                                            break;

                                        default:
                                            throw new ControllerException("Invalid termination condition " +
                                                    budgetController.getTerminationCondition() + " during transaction import");
                                    }
                                }
                            }
                        }

                        // Log the import event now that merchant is determined
                        importLog.logImportEvent(provisionalTransactions.get(provTrxIndex));

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
                            ForecastController forecastController = new ForecastController(
                                    sessionController);
                            forecastController.reconcile(provisionalTransactions.get(provTrxIndex), splits);
                        }

                        // Move to the next provisional transaction:
                        provTrxIndex++;

                    } else if (comparison == 0) {  // else, if the transaction was previously imported:

                        // Log the import event
                        provisionalTransactions.get(provTrxIndex).setMerchant(registerTransactions.get(regTrxIndex).getMerchant());
                        importLog.logImportEvent(provisionalTransactions.get(provTrxIndex));

                        // Tell the user what we did:
                        view.say("Transaction wws previously imported.");
                        List<TransactionSplit> txSplits = TransactionSplit.getSplitsForTransaction(registerTransactions.get(regTrxIndex));
                        if (txSplits != null) {
                            logSplitsAndReconciliation(forecast, txSplits);
                        }

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


    /**
     * Imports budget items from a CSV file into the budget.
     *
     * <p>This method allows bulk import of budget items from a CSV file, which is useful for:</p>
     * <ul>
     *   <li>Initial budget setup with many items</li>
     *   <li>Migrating budget data from another system</li>
     *   <li>Restoring budget items from a backup</li>
     *   <li>Sharing budget templates between users</li>
     * </ul>
     *
     * <h4>CSV Format:</h4>
     * <p>The CSV file must have a header row matching the {@link BudgetItem.Headers} enum fields.
     * Each subsequent row represents one budget item to import.</p>
     *
     * <h4>Import Process:</h4>
     * <ol>
     *   <li>Opens and parses the CSV file</li>
     *   <li>For each record (row):
     *     <ul>
     *       <li>Creates a BudgetItem object from the CSV data</li>
     *       <li>Saves the budget item using INSERT_ON_DUPLICATE_UPDATE strategy</li>
     *       <li>This means existing items with matching IDs will be updated, new items will be inserted</li>
     *     </ul>
     *   </li>
     *   <li>Reports the number of items successfully imported</li>
     * </ol>
     *
     * <h4>Duplicate Handling:</h4>
     * <p>The method uses INSERT_ON_DUPLICATE_UPDATE, which means:</p>
     * <ul>
     *   <li>If a budget item with the same ID exists, it will be updated with the imported values</li>
     *   <li>If no matching ID exists, a new budget item will be created</li>
     *   <li>This allows the same import file to be used for both initial import and updates</li>
     * </ul>
     *
     * <h4>Error Handling:</h4>
     * <p>If an error occurs during import:</p>
     * <ul>
     *   <li>A ControllerException is thrown with details about which line failed</li>
     *   <li>The exception wraps the underlying cause (FileNotFoundException, IOException, etc.)</li>
     *   <li>Previously imported items from the same file remain in the database</li>
     * </ul>
     *
     * <p><b>Note:</b> This method does not currently create a backup/version of the import file.
     * The TODO comment indicates this functionality may be added in the future.</p>
     *
     * @param filename The full path to the CSV file containing budget items to import
     * @throws ControllerException If an error occurs during file processing or import
     * @see BudgetItem
     * @see BudgetItem.Headers
     * @see EntityInt.SaveMethod#INSERT_ON_DUPLICATE_UPDATE
     */
    public void importCsvBudgetItemFile(String filename) throws ControllerException {

        System.out.println("Import new budget items from the file " + filename + ".");

        int i = 0;
        try {
            BudgetItem budgetItem = new BudgetItem();

            // Open the import file:
            Reader in = new FileReader(filename);

            // For each row in the import file:
            Iterable<CSVRecord> records = CSVFormat.RFC4180.builder()
                    .setHeader(BudgetItem.Headers.class)
                    .get()
                    .parse(in);
            boolean stop = false;
            while (!stop) {
                stop = true;
                for (CSVRecord record : records) {

                    // Create a budgetItem from the row:
                    budgetItem.loadFromCsvRecord(record);

                    // Save the budgetItem and associated items:
                    budgetItem.save(INSERT_ON_DUPLICATE_UPDATE);
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
