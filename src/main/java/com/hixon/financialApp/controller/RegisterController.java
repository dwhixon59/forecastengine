package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.merchant.MerchantPayee;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.model.register.TransactionUtilities;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.UserResponse;
import com.hixon.financialApp.view.base.ViewInt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.hixon.financialApp.controller.ImportController.TerminationCondition.*;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT_ON_DUPLICATE_UPDATE;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.UPDATE;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.*;

public class RegisterController {
    /*
     * Fields for RegisterController:
     */
    private ImportController.TerminationCondition terminationCondition;
    protected Register register;
    private final FinancialInstitutionInt financialInstitution;
    protected Budget budget;
    protected Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;
    private SessionController sessionController;


    /*
     * Getters and setters for RegisterController:
     */
    public ImportController.TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }


    /**
     * Constructors and destructor for RegisterController:
     */
    public RegisterController(Register register, FinancialInstitutionInt financialInstitution, Budget budget,
                              Forecast forecast, ViewInt view, NotificationServiceInt notificationService) {
        terminationCondition = QUIT;
        this.register = register;
        this.financialInstitution = financialInstitution;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
    }

    /**
     * Constructor for RegisterController with SessionController.
     * Used for managing registers across multiple users and budgets.
     *
     * @param sessionController The session controller for accessing user and budget information
     * @param view The view interface for user interaction
     * @param notificationService The notification service for sending notifications
     */
    public RegisterController(SessionController sessionController, ViewInt view, NotificationServiceInt notificationService) {
        terminationCondition = QUIT;
        this.sessionController = sessionController;
        this.financialInstitution = null;
        this.view = view;
        this.notificationService = notificationService;
    }


    /*
     * Main methods for RegisterController:
     */

    /**
     * Allows the user to manage registers interactively.
     * The workflow is:
     * 1. Select a register from all available registers
     * 2. Choose what to do with it (view details, update balance, or select another)
     *
     * @throws Exception if any error occurs during management operations
     */
    public void manageRegisters() throws Exception {
        boolean done = false;

        while (!done) {
            try {
                // Step 1: Select a register from all available registers
                Register selectedRegister = selectRegisterForManagement();

                if (selectedRegister == null) {
                    // User cancelled - exit
                    done = true;
                    continue;
                }

                // Step 2: Show action menu for the selected register
                boolean actionComplete = false;
                while (!actionComplete) {
                    // Display the selected register
                    view.say();
                    view.say("Selected register:");
                    view.say("  " + selectedRegister.toStringConcise());

                    // Ask what to do with this register
                    String action = view.selectFromMenu("What would you like to do with this register?",
                            List.of("view details", "update balance", "select another register"),
                            ViewInt.DO_NOT_ALLOW_NONE, ViewInt.SHOW_CANCEL_QUIT_SKIP,
                            ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP);

                    switch (action) {
                        case "v":  // view details
                            view.say();
                            view.say("Register Details:");
                            view.say("──────────────────────────────────────");
                            displayRegisterDetails(selectedRegister);
                            view.say("──────────────────────────────────────");
                            break;

                        case "u":  // update balance
                            verifyRegisterBalance(selectedRegister);
                            break;

                        case "s":  // select another register
                            actionComplete = true;  // Exit to register selection
                            break;

                        case "q":
                            actionComplete = true;
                            done = true;
                            break;

                        default:
                            throw new InvalidEntryException("selectFromMenu returned an option that wasn't in the option list.");
                    }
                }

            } catch (CancelException e) {
                view.say("Operation cancelled by user.");
                done = true;
            } catch (QuitException e) {
                done = true;
            }
        }
    }

    /**
     * Select a register from all available registers in the system.
     *
     * @return The selected Register, or null if cancelled
     * @throws Exception if any error occurs
     */
    private Register selectRegisterForManagement() throws Exception {
        // Get all registers
        List<Register> allRegisters = Register.getListOf();

        if (allRegisters.isEmpty()) {
            view.say("No registers found in the system.");
            return null;
        }

        // Let user select from the list
        return view.selectByNameFromList("Select a register:", allRegisters, null, ViewInt.DO_NOT_ALLOW_NONE,
                ViewInt.SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);
    }

    /**
     * Select a register by name from a list of all the registers in the database.
     *
     * @return Register The register that was selected.
     * @throws RegisterException If there are no registers in the database or if no register was selected.
     * @throws SQLException      If there is a database error.
     * @throws EntityException   If there is an error non-database error.
     */
    public static Register selectRegister(ViewInt view) throws RegisterException, SQLException, EntityException {

        // Get a list of all the registers:
        List<Register> registers = Register.getListOf();

        // If there are no registers, throw an exception:
        if (registers.isEmpty()) {
            throw new RegisterException("There are no registers in the database.");
        }

        // If there is only one register, return it:
        if (registers.size() == 1) {
            return registers.get(0);
        }

        // Otherwise, let the user select a register:
        Register register = view.selectByNameFromList("Select a register:", registers,
                ViewInt.DO_NOT_ALLOW_NONE);

        // If a register was selected, return it, else throw an exception:
        if (register != null) {
            return register;
        } else {
            throw new RegisterException("No register was selected.");
        }
    }

    public boolean verifyRegisterBalance(Register register) throws EntityException, SQLException, BudgetException,
            RegisterException {
        boolean wasCorrect = true;
        Register dbRegister = Register.getById(register.getId());

        if (!Utility.isEqualCurrency(register.getBalance(), dbRegister.getBalance())) {
            view.say("The in memory register balance is " + Utility.formatDollarAmount(
                    register.getBalance()) + " but the register balance in the database is " + Utility.formatDollarAmount(
                    register.getBalance()) + ".  You should update it.");
        }

        if (view.getYesOrNo("\nThe current balance of the " +
                register.getName() + " is " + Utility.formatDollarAmount(register.getBalance()) +
                "  Do you want to update it?")) {
            double balance = view.getResponseCurrency("Please enter the dollar amount:  ");
            register.setBalance(balance);
            register.update();
            wasCorrect = false;
        }
        return wasCorrect;
    }

    /**
     * The account number was not in the payee string, so ask the user for help.  Retrieve a set containing all the
     * registers in the database and then progressively narrows that set by applying filters to the set until it is down
     * to 1, or zero entities. After applying each filter, see if we are down to one or zero registers in the set.  In
     * the case of zero entries tell the user that there are no registers that fit, if there is one register in the list,
     * confirms that is the correct register with the user.
     *
     * @param date   The date of the transaction.
     * @param amount The amount of the transaction.
     * @param payee  The payee string from the transaction.
     * @return Register The register that was selected by the user.
     * @throws RegisterException If there is an error with the register.
     * @throws SkipException     If the user skips the operation.
     * @throws QuitException     If the user quits the operation.
     */
    public Register resolveUnmatchedAccount(Calendar date, double amount, String payee) throws Exception {

        view.say("\nThere is no account number in the following transaction: " +
                Utility.calendarDateToStringSlashDate(date) + " " + payee + " " + Utility.formatDollarAmount(amount));

        // Get a set of all the registers that we will progressively narrow down.  If at any point the list of possible
        // registers is 1, then return that register:
        Set<Register> possibleRegisters = new HashSet<>(Register.getListOf());

        // Narrow the list of possible registers by removing the register that we are currently working with:
        possibleRegisters.remove(register);
        try {
            return evaluateRegisterSet(possibleRegisters);
        } catch (ContinueFilteringException e) {
            // Continue to the next filter.
        }

        // Narrow the list of possible registers that this transaction could be in to the ones that are of the same type
        // as the register in the payee and owned by the same user:
        Set<Register> registersSameTypeAndUser = new HashSet<>();
        List<User> users = financialInstitution.extractUsers(payee);
        if (users.isEmpty()) {
            view.say("There are no users associated with this payee: " + payee + ".");
        }
        String accountType = financialInstitution.extractAccountType(payee);
        if (accountType == null) {
            view.say("There is no account type in this payee: " + payee);
        }
        if (!users.isEmpty()) {
            for (User user : users) {
                registersSameTypeAndUser.addAll(Register.getListOfByUserAndType(user, accountType));
            }
        }
        else {
            registersSameTypeAndUser.addAll(Register.getListOfByUserAndType(null, accountType));
        }
        possibleRegisters.retainAll(registersSameTypeAndUser);
        if (possibleRegisters.isEmpty()) {
            // If there were no users:
            if (users.isEmpty()) {
                view.say("There are no " + accountType + " accounts.");
            }
            else if (accountType == null) {
                view.say("There are no accounts owned by the user(s) " + users + ".");
            }
            else {
                view.say("There are no " + accountType + " accounts owned by the user(s) " + users + ".");
            }
            return null;
        }

        // See if we are down to one register:
        try {
            return evaluateRegisterSet(possibleRegisters);
        } catch (ContinueFilteringException e) {
            // Continue to the next filter.
        }

        // Registers have nicknames, and users can explicitly name the register being transferred to or from in the memo.
        // Extract the memo from the payee using the method for the financial institution that we are dealing with, and
        // then see if any of the tokens in the memo match the nickname of a possible register.  If we find a match,
        // then return that register:
        String userDescription = financialInstitution.extractUserDescription(payee);
        List<Register> registers;
        if (userDescription != null) {

            // Tokenize the memo:
            String[] tokens = userDescription.split("[\\s,]+" );

            // See if any of the tokens in the user description match the nickname of a register:
            for (String token : tokens) {
                for (Register possibleRegister : possibleRegisters) {
                    if (possibleRegister.getNickname().equalsIgnoreCase(token)) {
                        view.say("Found a register that matches the token in the memo: " + token);
                        return possibleRegister;
                    }
                }
            }
        }

        // Try to find the most recent instance of a transaction with the same payee and approximately the same amount
        // that is also in the list of possible registers.  This transaction will be an almost exact match for the one
        // we are trying to resolve.  If we find one, then ask the user if this is the correct register:
        Transaction transaction = TransactionUtilities.getMostRecentTransactionByPayee(payee, amount);
        if (transaction != null) {
            Merchant merchant = transaction.getMerchant();
            if (merchant != null) {
                Register possibleRegister = Register.getByName(merchant.getName());
                if (possibleRegister != null && possibleRegisters.contains(possibleRegister)) {
                    view.say("The most recent transaction with this payee was: " + transaction.toStringVeryConcise());
                    Set<Register> setWithRecentTransaction = new HashSet<>();
                    setWithRecentTransaction.add(possibleRegister);
                    try {
                        // There is only one register in this set, so this will either return the register or null:
                        if (evaluateRegisterSet(setWithRecentTransaction) != null) {

                            // The user confirmed that this is the correct register:
                            return possibleRegister;
                        }
                        else {
                            // The user said that this is not the correct register, so remove it from the list of possible
                            // registers:
                            possibleRegisters.remove(possibleRegister);

                            // If there are no more possible registers, return null:
                            if (possibleRegisters.isEmpty()) {
                                view.say("There are no more possible registers.");
                                return null;
                            }
                            else {
                                // If there is only one possible register left, return it:
                                try {
                                    return evaluateRegisterSet(possibleRegisters);
                                } catch (ContinueFilteringException e) {
                                    // Continue to the next filter.
                                }
                            }
                        }
                    } catch (ContinueFilteringException e) {
                        possibleRegisters.remove(possibleRegister);
                    }
                }
            }
        }

        // Couldn't match so far, so do a full text search on the memo, get the most relevant transactions and let
        // the user select the register from the list of transactions:
        if (userDescription != null) {

            // Get a list of transactions that match the user description:
            List<Transaction> relevantTransactions = TransactionUtilities.getByUserDescriptionFullText(userDescription);
            if (!relevantTransactions.isEmpty()) {

                // First narrow the list of transactions to only those that are associated with a register that is in the
                // list of possible registers:
                Iterator<Transaction> iterator = relevantTransactions.iterator();
                while (iterator.hasNext()) {
                    Transaction relevantTransaction = iterator.next();
                    Register register = Register.getByName(relevantTransaction.getMerchant().getName());
                    if (register == null) {
                        // throw out the transaction because it is not associated with a register:
                        iterator.remove();
                    } else if (!possibleRegisters.contains(register)) {
                        // throw out the transaction because it is not associated with a register that is in the list of
                        // possible registers:
                        iterator.remove();
                    }
                }
                if (!relevantTransactions.isEmpty()) {

                    // If there is only one transaction, then ask the user if this is the correct register:
                    if (relevantTransactions.size() == 1) {
                        Transaction relevantTransaction = relevantTransactions.get(0);
                        Register register = Register.getByName(relevantTransaction.getMerchant().getName());
                        if (register != null) {
                            view.say("Found a register that matches the token in the memo: " + register.toStringConcise());
                            Set<Register> setWithMatchingToken = new HashSet<>();
                            setWithMatchingToken.add(register);
                            try {
                                return evaluateRegisterSet(setWithMatchingToken);
                            } catch (ContinueFilteringException e) {
                                possibleRegisters.remove(register);
                            }
                        }
                    }

                    // There are multiple transactions, so create a list of "transaction with the register name" strings:
                    List<String> fullTextTrxsWithRegisterNames = new ArrayList<>();
                    Set<Register> associatedRegisters = new HashSet<>();
                    for (Transaction relevantTransaction : relevantTransactions) {
                        Register register = Register.getByName(relevantTransaction.getMerchant().getName());
                        associatedRegisters.add(register);
                        fullTextTrxsWithRegisterNames.add("Date = " +
                                Utility.calendarDateToStringDate(relevantTransaction.getDate()) + ", Payee = " +
                                relevantTransaction.getPayee() + ", associated register: " +
                                relevantTransaction.getMerchant().getName());
                    }

                    // If there is only one or zero registers associated with the transactions, then consult the user to
                    // confirm the register:
                    if (associatedRegisters.size() < 2)
                    {
                        try {
                            return evaluateRegisterSet(associatedRegisters);
                        } catch (ContinueFilteringException e) {
                            // Continue to next filter.
                        }
                    }
                    else {
                        // Allow the user to select the correct register from the list of transactions with register names:
                        int selection = view.selectByPositionFromList("The following transactions match the token in the memo.  " +
                                        "Select a transaction with the same register as the one associated with this transfer",
                                fullTextTrxsWithRegisterNames, ViewInt.ALLOW_NONE);

                        // If the user selected a register, then return it:
                        if (selection > 0) {
                            return Register.getByName(relevantTransactions.get(selection).getMerchant().getName());
                        } else {
                            // Remove the associated registers from the list of possible registers:
                            for (Transaction relevantTransaction : relevantTransactions) {
                                possibleRegisters.remove(relevantTransaction.getRegister());
                            }
                        }
                    }
                }
            }
        }

        // If we haven't found a match, give up and let the user select a register from the list of possible registers:
        List<String> registerNames = new ArrayList<>();
        List<Register> possibleRegistersList = new ArrayList<>(possibleRegisters);
        for (Register possibleRegister : possibleRegistersList) {
            registerNames.add(possibleRegister.toStringConcise());
        }

        // then allow the user to select the correct register from the list of registers:
        int selection = view.selectByPositionFromList("Select the register associated with this transfer",
                registerNames, ViewInt.DO_NOT_ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.ALLOW_SKIP);
        return possibleRegistersList.get(selection);
    }

    /**
     * Evaluates a set of registers and handles different cases based on size.
     *
     * @param possibleRegisters Set of registers to evaluate
     * @return Register if exactly one match is confirmed, null if no matches or user rejects single match,
     *         or throws ContinueFilteringException if multiple matches exist
     * @throws ContinueFilteringException when multiple registers exist and filtering should continue
     */
    private Register evaluateRegisterSet(Set<Register> possibleRegisters) throws ContinueFilteringException {
        // Case 1: No registers found
        if (possibleRegisters.isEmpty()) {
            view.say("No registers match the current criteria.");
            return null;
        }

        // Case 2: Exactly one register found - confirm with user
        if (possibleRegisters.size() == 1) {
            Register singleRegister = possibleRegisters.iterator().next();
            view.say("Found potential match: " + singleRegister.toStringConcise());
            if (view.getYesOrNo("Is this the correct register?")) {
                return singleRegister;
            } else {
                return null;
            }
        }

        // Case 3: Multiple registers - continue filtering
        throw new ContinueFilteringException("Multiple registers match current criteria. Continue filtering.");
    }

    /**
     * Custom exception to indicate that filtering should continue.
     */
    private static class ContinueFilteringException extends Exception {
        public ContinueFilteringException(String message) {
            super(message);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean askDeleteRegisterTransaction(Transaction transaction) {
        view.say();
        view.say(transaction.toStringConcise());
        return view.getYesOrNo("This provisional transaction has disappeared from the list of provisional " +
                "transactions, but it does not appear as a cleared transaction.\nIt has likely been invalidated.  Do you "
                + "want to remove it?");
    }


    /**
     * The amount of the transaction is significantly different from the planned amount for the current period.
     *
     * @param transaction         The transaction with the split that has a discrepancy.
     * @param split               The split that has a discrepancy.
     * @param forecastTransaction The forecast transaction has a discrepancy.
     * @return UserResponse The user's response to the discrepancy.
     * @throws BudgetException   If there is an error retrieving the budget item.
     * @throws SQLException      If there is a database error.
     * @throws EntityException   If there is a non-database error.
     * @throws ForecastException If there is an error retrieving the forecast transaction.
     */
    public UserResponse transactionAmountDiscrepancy(Transaction transaction, TransactionSplit split,
                                                     ForecastTransaction forecastTransaction) throws BudgetException, SQLException, EntityException,
            ForecastException {

        UserResponse response = new UserResponse();

        view.say("The amount of this split is significantly more than the planned amount for the current " +
                "period (" + Utility.formatDollarAmount(-forecastTransaction.getForecastItem().getAmount()) + ").");
        view.ask("Would you like to adjust the amount for this budget item (a-adjust, s-assign, " +
                "d-dispute, i-ignore)? ");

        boolean done = false;
        while (!done) {
            done = true;
            String answer = view.getResponseString().toLowerCase();
            switch (answer) {
                case "a":
                    response.setDisposition(ADJUST);
                    response.setResponse(view.parseDollarAmount("Enter the new amount", split.getAmount()));
                    break;

                case "s":
                    response.setDisposition(ASSIGN);
                    break;

                case "d":
                    response.setDisposition(DISPUTE);
                    break;

                case "i":
                    response.setDisposition(IGNORE);
                    break;

                default:
                    view.say("Please enter a, s, or i.");
                    done = false;
            }
        }
        return response;
    }

    // Reprocess any transactions that are not categorized in the database:
    public boolean processUncategorizedTransactions() throws RegisterException {
        ImportLog importLog = new ImportLog();

        int i = 0;
        BudgetController budgetController = new BudgetController(register, budget, forecast, view,
                notificationService);
        try {
            // Retrieve any transactions that were skipped.
            ResultSet rs = TransactionUtilities.getSkippedTransactionsWrtForecast(forecast);

            // For each transaction in the result set:
            Transaction transaction;
            Merchant merchant;
            while (rs.next()) {

                // Create a transaction object from the result set:
                transaction = new Transaction(rs);

                // Let the user know we are beginning a new item:
                view.beginImportItem(transaction);

                // Get the merchant for this transaction:
                merchant = transaction.getMerchant();

                // If the merchant is the unknown merchant, which means that the user skipped out of the merchant assignment
                // process then do it now:
                if (merchant.getName().equalsIgnoreCase(Merchant.UNKNOWN)) {

                    Merchant unknownMerchant = merchant;
                    try {
                        transaction.setMerchantPayee(financialInstitution.parseMerchantPayee(transaction.getDate(),
                                transaction.getAmount(), transaction.getPayee()));
                    } catch (SkipException se) {

                        // Once again the user skipped out of the merchant payee parsing process, so skip this transaction:
                        continue;
                    }

                    // Detach the merchant payee from the "unknown" merchant:
                    MerchantPayee.deleteByMerchantAndPayee(unknownMerchant, transaction.getMerchantPayee());

                    // And null out the merchant, so we will go through the normal merchant assignment process:
                    merchant = null;
                }

                // If a merchant hasn't been assigned yet, assign one now:
                if (merchant == null) {
                    merchant = Merchant.getByPayee(transaction.getMerchantPayee());
                    if (merchant == null) {
                        ImportController.TerminationCondition terminationCondition = FOUND;
                        try {
                            MerchantController merchantController = new MerchantController(view, notificationService);
                            merchant = merchantController.assignMerchant(transaction.getMerchantPayee(),
                                    transaction.getPayee(), transaction.getAmount());
                        } catch (CancelException ce) {
                            terminationCondition = CANCEL;
                        } catch (SkipException se) {
                            terminationCondition = SKIP;
                        } catch (QuitException qe) {
                            terminationCondition = QUIT;
                        }
                        if (merchant == null) {
                            switch (terminationCondition) {
                                case CANCEL:
                                case SKIP:
                                    continue;

                                case QUIT:
                                    throw new QuitException("Quitting reprocessing of skipped transactions at user " +
                                            "request.");

                                default:
                                    throw new ControllerException("Invalid termination condition " +
                                            view.getTerminationCondition() + " during transaction import");
                            }
                        }
                    }

                    // then update the transaction merchant info from the merchant that we just found or created:
                    transaction.setMerchant(merchant);
                    transaction.setIdMerchant(merchant.getId());
                }

                // If there is a provisional transaction for this transaction, then use the same ID:
                transaction.reconcileWithProvisional();

                // Tell the user about the bank transaction we are processing:
                importLog.logImportEvent(transaction);

                // Get the assigned budget items for the merchant:
                List<BudgetItemMerchant> budgetItemsForMerchant =
                        BudgetItemMerchant.getAssignedUnexpiredBudgetItems(budget, merchant);

                // If we couldn't find any matching items, get some help from the user:
                if (budgetItemsForMerchant.isEmpty()) {
                    try {
                        budgetController.assignBudgetItemsToMerchant(merchant, budgetItemsForMerchant);
                    } catch (SkipException se) {
                        transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                        continue;
                    }
                }

                // Get the splits for the transaction.  Create them if they don't already exist:
                List<TransactionSplit> splits = TransactionSplit.getSplitsForTransaction(transaction);
                if (splits == null) {
                    splits = budgetController.assignAmountsToBudgetItems(transaction, merchant, budget,
                            budgetItemsForMerchant);
                }

                // Since we have changed the transaction, Set the transaction to new so that it will appear in the new
                // transaction report with the new data:
                transaction.setIsNew(true);

                // Save the transaction and associated items:
                transaction.save(UPDATE);
                if (splits != null) {
                    for (TransactionSplit split : splits) {
                        split.save();
                    }

                    // Reconcile this transaction with the forecast:
                    ForecastController forecastController = new ForecastController(register, budget, forecast, view,
                            notificationService);
                    forecastController.reconcile(transaction, splits);
                }

                i++;
            } // End for each record in the transactions file.

            // TODO: Process any significant events that occurred during reconciliation:

            // TODO: Save the import event:

        } catch (Exception e) {
            throw new RegisterException("Exception occurred while processing skipped transactions", e);
        }

        // Return the number of transactions imported:
        if (i > 0) {
            view.say("\nSuccessfully reprocessed " + i + " skipped transactions in the register.");
        } else {
            view.say("\nThere were no skipped transactions in the register '" + register.getName() + "'.");
        }
        return forecast.getInSync();
    }

    // Reprocess any transactions that were previously skipped:
    public boolean processUnreconciledTransactions() throws QuitException, RegisterException {

        ImportLog importLog = new ImportLog();
        BudgetController budgetController = new BudgetController(register, budget, forecast, view,
                notificationService);

        int i = 0;
        try {
            // Retrieve any transactions that were skipped.
            ResultSet rs = TransactionUtilities.getSkippedTransactionsWrtForecast(forecast);

            // For each transaction in the result set:
            Transaction transaction;
            Merchant merchant;
            while (rs.next()) {

                // Get the transaction for this import record:
                transaction = new Transaction(rs);

                // Let the user know we are beginning a new item:
                view.beginImportItem(transaction);

                // Get the merchant for this transaction:
                merchant = transaction.getMerchant();

                // If the merchant is the unknown merchant, which means that the user skipped out of the merchant assignment
                // process then do it now:
                if (merchant.getName().equalsIgnoreCase(Merchant.UNKNOWN)) {

                    try {
                        transaction.setMerchantPayee(financialInstitution.parseMerchantPayee(transaction.getDate(),
                                transaction.getAmount(), transaction.getPayee()));
                    } catch (SkipException se) {

                        // Once again the user skipped out of the merchant payee parsing process, so skip this transaction:
                        continue;
                    }

                    // Detach the merchant payee from the "unknown" merchant:
                    MerchantPayee.deleteByMerchantAndPayee(merchant, transaction.getMerchantPayee());

                    // And null out the merchant so we will go through the normal merchant assignment process:
                    merchant = null;
                }

                // If a merchant hasn't been assigned yet, assign one now:
                if (merchant == null) {
                    merchant = Merchant.getByPayee(transaction.getMerchantPayee());
                    if (merchant == null) {
                        ImportController.TerminationCondition terminationCondition = FOUND;
                        try {
                            MerchantController merchantController = new MerchantController(view, notificationService);
                            merchant = merchantController.assignMerchant(transaction.getMerchantPayee(),
                                    transaction.getPayee(), transaction.getAmount());
                        } catch (CancelException ce) {
                            terminationCondition = CANCEL;
                        } catch (SkipException se) {
                            terminationCondition = SKIP;
                        } catch (QuitException qe) {
                            terminationCondition = QUIT;
                        }
                        if (merchant == null) {
                            switch (terminationCondition) {
                                case CANCEL:
                                case SKIP:
                                    continue;

                                case QUIT:
                                    throw new QuitException("Quitting reprocessing of skipped transactions at user request.");

                                default:
                                    throw new ControllerException("Invalid termination condition " +
                                            view.getTerminationCondition() + " during transaction import");
                            }
                        }
                    }

                    // then update the transaction merchant info from the merchant that we just found or created:
                    transaction.setMerchant(merchant);
                    transaction.setIdMerchant(merchant.getId());
                }

                // If there is a provisional transaction for this transaction, then use the same ID:
                transaction.reconcileWithProvisional();

                // Tell the user about the bank transaction we are processing:
                importLog.logImportEvent(transaction);

                // Get the assigned budget items for the merchant:
                List<BudgetItemMerchant> budgetItemsForMerchant =
                        BudgetItemMerchant.getAssignedUnexpiredBudgetItems(budget, merchant);

                // If we couldn't find any matching items, get some help from the user:
                if (budgetItemsForMerchant.isEmpty()) {
                    try {
                        budgetController.assignBudgetItemsToMerchant(merchant, budgetItemsForMerchant);
                    } catch (SkipException se) {
                        transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                        continue;
                    }
                }


                // Get the splits for the transaction.  Create them if they don't already exist:
                List<TransactionSplit> splits = TransactionSplit.getSplitsForTransaction(transaction);
                if (splits == null) {
                    splits = budgetController.assignAmountsToBudgetItems(transaction, merchant, budget, budgetItemsForMerchant);
                }

                // Since we have changed the transaction, Set the transaction to new so that it will appear in the new
                // transaction report with the new data:
                transaction.setIsNew(true);

                // Save the transaction and associated items:
                transaction.save(INSERT_ON_DUPLICATE_UPDATE);
                if (splits != null) {
                    for (TransactionSplit split : splits) {
                        split.save();
                    }

                    // Reconcile this transaction with the forecast:
                    ForecastController forecastController = new ForecastController(register, budget, forecast, view,
                            notificationService);
                    forecastController.reconcile(transaction, splits);
                }

                i++;
            } // End for each record in the transactions file.

            // TODO: Process any significant events that occurred during reconciliation:

            // TODO: Save the import event:

        } catch (Exception e) {
            throw new RegisterException("Exception occurred while processing skipped transactions", e);
        }

        // Return the number of transactions imported:
        if (i > 0) {
            view.say("\nSuccessfully reprocessed " + i + " skipped transactions in the register.");
        } else {
            view.say("\nThere were no skipped transactions in the register '" + register.getName() + "'.");
        }
        return forecast.getInSync();

    } // End processSkippedTransactions().

    /**
     * Display detailed information about a register in vertical format.
     *
     * @param register The register to display details for
     */
    private void displayRegisterDetails(Register register) {
        view.say("Register: " + register.getName());
        view.say("Nickname: " + (register.getNickname() != null ? register.getNickname() : "(none)"));
        view.say("Account Type: " + (register.getAccountType() != null ? register.getAccountType() : "(none)"));
        view.say("Account Number: " + (register.getAccountNumber() != null ? register.getAccountNumber() : "(none)"));
        view.say("Balance: " + Utility.formatDollarAmount(register.getBalance()));
        view.say("Skipped Amount: " + Utility.formatDollarAmount(register.getSkippedAmount()));
        view.say("Financial Institution: " + (register.getFinancialInstitution() != null ? register.getFinancialInstitution() : "(none)"));
        view.say("Transaction Import File Name: " + (register.getTrxImportFileName() != null ? register.getTrxImportFileName() : "(none)"));
        view.say("Transaction Import File Directory: " + (register.getTrxImportFileDirectory() != null ? register.getTrxImportFileDirectory() : "(none)"));
        view.say("Provisional Transaction File Name: " + (register.getProvisionalTrxFileName() != null ? register.getProvisionalTrxFileName() : "(none)"));
        view.say("Provisional Transaction File Directory: " + (register.getProvisionalTrxFileDirectory() != null ? register.getProvisionalTrxFileDirectory() : "(none)"));
    }
}