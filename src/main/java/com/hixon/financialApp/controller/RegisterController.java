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
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.UserResponse;
import com.hixon.financialApp.view.base.ViewInt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

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


    /*
     * Main methods for RegisterController:
     */

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
            double balance = view.getDollarAmount();
            register.setBalance(balance);
            register.update();
            wasCorrect = false;
        }
        return wasCorrect;
    }

    /**
     * The account number was not in the payee string, so ask the user for help:
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

        // Narrow the list of possible registers that this transaction could be in to the ones that are of the same type
        // as the payee and owned by the same user:
        List<Pair<Register, User>> registers = Register.getListOfByUserAndType(financialInstitution.extractUser(payee),
                financialInstitution.extractAccountType(payee));
        if (registers.size() == 1)
            Register register = registers.get(0);
            view.say("The only register of type " + register.getAccountType() " owned by " + register.toStringConcise());
            if (view.getYesOrNo("Is this the correct register for this transfer?")) {
                return register;
            }
        }

        // Get the merchant from the most recent transaction that used this payee, if there is one, and use that as the
        // register name if it matches the name of a register and the user approves:
        Transaction transaction = Transaction.getMostRecentTransactionByPayee(payee);
        if (transaction != null) {
            view.say("The most recent transaction with this payee was: " + transaction);
            Merchant merchant = transaction.getMerchant();
            if (merchant != null) {
                Register register = Register.getByName(merchant.getName());
                if (register != null) {
                    view.say("The register for this transaction is: " + register.toStringConcise());
                    if (view.getYesOrNo("Is this the correct register for this transfer?")) {
                        return register;
                    }
                }
            }
        }

        // Extract the memo from the payee using the method for the financial institution that we are dealing with:
        String userDescription = financialInstitution.extractUserDescription(payee);
        List<Register> registers;
        if (userDescription != null) {

            // Tokenize the memo:
            String[] tokens = userDescription.split("[\\s,]+");

            // Get a list of all the registers:
            registers = Register.getListOf();

            // See if any of the tokens in the user description match the nickname of a register:
            for (String token : tokens) {
                for (Register register : registers) {
                    if (register.getNickname().equalsIgnoreCase(token)) {
                        view.say("The following register matches the token in the memo: " + register);
                        if (view.getYesOrNo("Is this the correct register for this transfer?")) {
                            return register;
                        }
                    }
                }
            }

            // Couldn't match on a register nickname, so do a full text search on the memo and get the most relevant
            // transactions that match:
            List<Transaction> transactions = Transaction.getByUserDescriptionFullText(userDescription);
            List<Transaction> relevantTransactions = new ArrayList<Transaction>();
            if (!transactions.isEmpty()) {

                // Create a list of registers from the list of transactions:
                registers.clear();
                for (Transaction relevantTransaction : transactions) {
                    Register relevantRegister = Register.getByName(relevantTransaction.getMerchant().getName());
                    if (relevantRegister != null) {
                        relevantTransactions.add(relevantTransaction);
                        registers.add(relevantRegister);
                    }
                }

                // Show the user the list of relevant transactions and the register associated with each one:
                if (!relevantTransactions.isEmpty()) {
                    for (int i = 1; i <= relevantTransactions.size(); i++) {
                        Transaction relevantTransaction = relevantTransactions.get(i - 1);
                        Register associatedRegister = registers.get(i - 1);
                        view.say(STR."   \{i}.  \{relevantTransaction.getPayee()}, \{associatedRegister.getName()}");
                    }

                    view.say("The following register matches the token in the memo: " + registers.get(0).toStringConcise());
                    if (view.getYesOrNo("Is this the correct register for this transfer?")) {
                        return register;
                    }
                }
            }
        }

        // TODO:  If it is a recurring transfer, then try matching on recurring transfers for the same amount:

        // If we haven't found a match, give up and let the user select a register from a list of all registers:
        view.say("Select the account to assign this transaction to:  ");
        registers = Register.getListOf();
        for (int i = 1; i <= registers.size(); i++) {
            Register register = registers.get(i - 1);
            view.say(STR."   \{i}.  \{register.getName()}, \{register.getAccountType()}, \{register.getAccountNumber()}");
        }
        int selection = view.getNumberBetween("Enter the number of the selection", 1,
                registers.size(), ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.ALLOW_SKIP);

        return registers.get(selection - 1);
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
            ResultSet rs = Transaction.getSkippedTransactionsWrtForecast(forecast);

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

                // Tell the user about the bank transaction we are processing:
                importLog.logImportEvent(transaction);

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
            ResultSet rs = Transaction.getSkippedTransactionsWrtForecast(forecast);

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

                // Tell the user about the bank transaction we are processing:
                importLog.logImportEvent(transaction);

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
}
