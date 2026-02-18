package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.budget.TransactionSplit;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.financialinstitution.FinancialInstitutionInt;
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
import com.hixon.financialApp.view.base.EntityOrStringResult;
import com.hixon.financialApp.view.base.UserResponse;
import com.hixon.financialApp.view.base.ViewInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.hixon.financialApp.controller.ImportController.TerminationCondition.*;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.INSERT_ON_DUPLICATE_UPDATE;
import static com.hixon.financialApp.model.entity.EntityInt.SaveMethod.UPDATE;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.*;
import static com.hixon.financialApp.utility.ForecastTransactionMatcher.findMatchingForecastTransaction;

public class RegisterController {
    private static final Logger logger = LogManager.getLogger(RegisterController.class);

    /*
     * Fields for RegisterController:
     */
    private ImportController.TerminationCondition terminationCondition;
    protected Register register;
    protected FinancialInstitutionInt financialInstitution;
    protected Budget budget;
    protected Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;
    protected SessionController sessionController;


    /*
     * Getters and setters for RegisterController:
     */
    public ImportController.TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }


    /**
     * Constructor for RegisterController with SessionController.
     * Used for managing registers across multiple users and budgets.
     *
     * @param sessionController The session controller for accessing register, budget, and forecast information
     */
    public RegisterController(SessionController sessionController) {
        terminationCondition = QUIT;
        this.sessionController = sessionController;
        this.register = sessionController.getRegister();
        this.financialInstitution = sessionController.getFinancialInstitution();
        this.budget = sessionController.getBudget();
        this.forecast = sessionController.getForecast();
        this.view = sessionController.getView();
        this.notificationService = sessionController.getNotificationService();
    }


    /*
     * Main methods for RegisterController:
     */

    /**
     * Allows the user to manage registers interactively.
     * The workflow is:
     * 1. Select a register from all available registers or create a new one
     * 2. Choose what to do with it (view details, update, delete, or select another)
     *
     * @throws Exception if any error occurs during management operations
     */
    public void manageRegisters() throws Exception {
        boolean done = false;

        while (!done) {
            try {
                // Step 1: Ask if user wants to select existing or create new
                view.sayH1("Manage Registers");
                String mainAction = view.selectFromMenu("What would you like to do?",
                        List.of("select existing register", "create new register"),
                        ViewInt.DO_NOT_ALLOW_NONE, ViewInt.SHOW_CANCEL_QUIT_SKIP,
                        ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP);

                Register selectedRegister = null;

                if (mainAction.equals("s")) {
                    // Select existing register
                    selectedRegister = selectRegisterForManagement();
                    if (selectedRegister == null) {
                        // User cancelled selection
                        continue;
                    }
                } else if (mainAction.equals("c")) {
                    // Create new register
                    selectedRegister = createNewRegister();
                    if (selectedRegister == null) {
                        // User cancelled creation
                        continue;
                    }
                    // After creating, don't show the action menu - just loop back
                    continue;
                }

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
                            List.of("view details", "update this register", "delete this register", "select another register"),
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

                        case "u":  // update this register
                            Register updatedRegister = getRegisterFromUser(selectedRegister);
                            if (updatedRegister != null && updatedRegister.isValid()) {
                                Register confirmedRegister = confirmRegister(updatedRegister, "updated");
                                if (confirmedRegister != null) {
                                    confirmedRegister.setId(selectedRegister.getId()); // Preserve the original ID
                                    confirmedRegister.update();
                                    view.say("Register successfully updated.");

                                    // Update the selected register reference for the next iteration
                                    selectedRegister = confirmedRegister;
                                }
                            } else if (updatedRegister != null) {
                                view.say("Register entered by user is invalid.");
                            }
                            break;

                        case "d":  // delete this register
                            view.say("\nYou are about to delete:");
                            view.say("  " + selectedRegister.toStringConcise());

                            if (view.getYesOrNo("Are you sure you want to delete this register? This action cannot be undone.")) {
                                try {
                                    selectedRegister.delete();
                                    view.say("Register successfully deleted.");
                                    actionComplete = true;  // Exit to register selection
                                } catch (Exception e) {
                                    view.say("Error deleting register: " + e.getMessage());
                                }
                            } else {
                                view.say("Deletion cancelled.");
                            }
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
            RegisterException, CancelException, QuitException, SkipException {
        boolean wasCorrect = true;
        Register dbRegister = Register.getById(register.getId());

        if (!Utility.isEqualCurrency(register.getBalance(), dbRegister.getBalance())) {
            view.say("The in memory register balance is " + Utility.formatDollarAmount(
                    register.getBalance()) + " but the register balance in the database is " + Utility.formatDollarAmount(
                    dbRegister.getBalance()) + ".  You should update it.");
        }

        // Get the balance from QFX file if available
        Double qfxBalance = sessionController.getFinancialInstitution().getImportedLedgerBalance();

        view.sayH4("Current balance of " + register.getName() + ": " +
                Utility.formatDollarAmount(register.getBalance()));

        if (qfxBalance != null) {
            view.say("Downloaded balance: " + Utility.formatDollarAmount(qfxBalance));

            // Check if balances differ
            if (!Utility.isEqualCurrency(register.getBalance(), qfxBalance)) {
                view.say("\nThe balances differ!");

                // Offer user three choices
                String[] choices = {
                    "1 - Use balance from QFX file (" + Utility.formatDollarAmount(qfxBalance) + ")",
                    "2 - Keep database balance (" + Utility.formatDollarAmount(register.getBalance()) + ")",
                    "3 - Enter a different balance"
                };

                view.say("\nWhat would you like to do?");
                for (String choice : choices) {
                    view.say("  " + choice);
                }

                String response = view.getResponseString("Enter your choice (1-3)", null,
                    ViewInt.DO_NOT_ALLOW_NONE, ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                    ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

                Double newBalance = null;
                switch (response) {
                    case "1":
                        newBalance = qfxBalance;
                        break;
                    case "2":
                        // Keep current balance - do nothing
                        break;
                    case "3":
                        newBalance = view.getResponseCurrency("Enter new balance",
                                register.getBalance(), true, true, false, false, false, null);
                        break;
                    default:
                        view.say("Invalid choice. Keeping current balance.");
                        break;
                }

                if (newBalance != null && !Utility.isEqualCurrency(newBalance, register.getBalance())) {
                    register.setBalance(newBalance);
                    register.update();
                    wasCorrect = false;
                }
            } else {
                view.say("Balance matches QFX file. No update needed.");
            }
        } else {
            // QFX balance not available (CSV file or other format)
            Double newBalance = view.getResponseCurrency("Enter new balance (or press Enter to keep current balance)",
                    register.getBalance(), true, true, false, false, false, null);

            if (newBalance != null && !Utility.isEqualCurrency(newBalance, register.getBalance())) {
                register.setBalance(newBalance);
                register.update();
                wasCorrect = false;
            }
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
     * @param date      The date of the transaction.
     * @param amount    The amount of the transaction.
     * @param payee     The payee string from the transaction.
     * @param recurring
     * @return Register The register that was selected by the user.
     * @throws RegisterException If there is an error with the register.
     * @throws SkipException     If the user skips the operation.
     * @throws QuitException     If the user quits the operation.
     */
    public Register resolveUnmatchedAccount(Calendar date, double amount, String payee, boolean recurring) throws Exception {

        logger.debug("");
        logger.debug("=== resolveUnmatchedAccount Debug ===");
        logger.debug("Input:");
        logger.debug("  Date:   {}", date.getTime());
        logger.debug("  Amount: {}", amount);
        logger.debug("  Payee:  '{}'", payee);
        logger.debug("  Current register: {}", register.getName());

        view.say("\nThere is no account number in the following transaction: " +
                Utility.calendarDateToStringSlashDate(date) + " " + payee + " " + Utility.formatDollarAmount(amount));


        // if this is a recurring transfer:
        if (recurring) {

            // then it is most likely in the forecast, so try to determine the merchant payee by getting the
            // corresponding forecast transaction:
            logger.debug("Processing as RECURRING TRANSFER");
            ForecastTransaction forecastTransaction = findMatchingForecastTransaction(date, amount, forecast, null, 5, 5);

            // If we found a matching forecast transaction, use its merchant payee:
            if (forecastTransaction != null) {

                // Get the most recent instance of the matching forecast transaction that has been reconciled:
                Transaction transaction = forecastTransaction.getMostRecentReconciledTransaction(forecastTransaction);

                // If there is one, use its merchant payee:
                if (transaction != null) {
                    logger.debug("  Found matching forecast transaction: '{}'", transaction.getMerchant().getName());
                    return Register.getByName(transaction.getMerchant().getName());
                }
            }
        }

        // Get a set of all the registers that we will progressively narrow down.  If at any point the list of possible
        // registers is 1, then return that register:
        Set<Register> possibleRegisters = new HashSet<>(Register.getListOf());
        logger.debug("Starting with {} total registers", possibleRegisters.size());

        // Narrow the list of possible registers by removing the register that we are currently working with:
        possibleRegisters.remove(register);
        logger.debug("After removing current register: {} possible registers", possibleRegisters.size());
        try {
            return evaluateRegisterSet(possibleRegisters);
        } catch (ContinueFilteringException e) {
            logger.debug("Multiple registers still possible, continuing filtering...");
            // Continue to the next filter.
        }

        // Narrow the list of possible registers that this transaction could be in to the ones that are of the same type
        // as the register in the payee and owned by the same user:
        logger.debug("");
        logger.debug("FILTER: Extracting users and account type from payee...");
        Set<Register> registersSameTypeAndUser = new HashSet<>();
        List<User> users = financialInstitution.extractUsers(payee);
        if (users.isEmpty()) {
            logger.debug("  No users found in payee");
            view.say("There are no users associated with this payee: " + payee + ".");
        } else {
            logger.debug("  Found {} user(s): {}", users.size(), users);
        }
        String accountType = financialInstitution.extractAccountType(payee);
        if (accountType == null) {
            logger.debug("  No account type found in payee");
            view.say("There is no account type in this payee: " + payee);
        } else {
            logger.debug("  Account type: '{}'", accountType);
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
        logger.debug("  After filtering by user and account type: {} possible registers", possibleRegisters.size());
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
            logger.debug("  No matching registers found, returning null");
            return null;
        }

        // See if we are down to one register:
        try {
            Register result = evaluateRegisterSet(possibleRegisters);
            logger.debug("Resolved to single register after user/type filter: {}", result.getName());
            logger.debug("=== End resolveUnmatchedAccount Debug ===");
            logger.debug("");
            return result;
        } catch (ContinueFilteringException e) {
            logger.debug("Multiple registers still possible, continuing filtering...");
            // Continue to the next filter.
        }

        // Registers have nicknames, and users can explicitly name the register being transferred to or from in the memo.
        // Extract the memo from the payee using the method for the financial institution that we are dealing with, and
        // then see if any of the tokens in the memo match the nickname of a possible register.  If we find a match,
        // then return that register:
        logger.debug("");
        logger.debug("FILTER: Checking user description for register nicknames...");
        String userDescription = financialInstitution.extractUserDescription(payee);
        logger.debug("  Extracted user description: '{}'", userDescription);
        List<Register> registers;
        if (userDescription != null) {

            // Tokenize the memo:
            String[] tokens = userDescription.split("[\\s,]+" );
            logger.debug("  Tokenized into {} tokens", tokens.length);

            // See if any of the tokens in the user description match the nickname of a register:
            for (String token : tokens) {
                logger.debug("  Checking token: '{}'", token);
                for (Register possibleRegister : possibleRegisters) {
                    if (possibleRegister.getNickname().equalsIgnoreCase(token)) {
                        logger.debug("  MATCH! Token '{}' matches register nickname: {}", token, possibleRegister.getName());
                        view.say("Found a register that matches the token in the memo: " + token);
                        logger.debug("=== End resolveUnmatchedAccount Debug ===");
                        logger.debug("");
                        return possibleRegister;
                    }
                }
            }
            logger.debug("  No nickname matches found");
        }

        // Try to find the most recent instance of a transaction with the same payee and approximately the same amount
        // that is also in the list of possible registers.  This transaction will be an almost exact match for the one
        // we are trying to resolve.  If we find one, then ask the user if this is the correct register:
        logger.debug("");
        logger.debug("FILTER: Looking for most recent transaction with same payee...");
        Transaction transaction = TransactionUtilities.getMostRecentTransactionByPayee(payee, amount);
        if (transaction != null) {
            logger.debug("  Found recent transaction: {}", transaction.toStringVeryConcise());
            Merchant merchant = transaction.getMerchant();
            if (merchant != null) {
                logger.debug("  Transaction merchant: {}", merchant.getName());
                Register possibleRegister = Register.getByName(merchant.getName());
                if (possibleRegister != null && possibleRegisters.contains(possibleRegister)) {
                    logger.debug("  Merchant matches a register in possible list: {}", possibleRegister.getName());
                    view.say("The most recent transaction with this payee was: " + transaction.toStringVeryConcise());
                    Set<Register> setWithRecentTransaction = new HashSet<>();
                    setWithRecentTransaction.add(possibleRegister);
                    try {
                        // There is only one register in this set, so this will either return the register or null:
                        if (evaluateRegisterSet(setWithRecentTransaction) != null) {

                            // The user confirmed that this is the correct register:
                            logger.debug("  User confirmed register: {}", possibleRegister.getName());
                            logger.debug("=== End resolveUnmatchedAccount Debug ===");
                            logger.debug("");
                            return possibleRegister;
                        }
                        else {
                            // The user said that this is not the correct register, so remove it from the list of possible
                            // registers:
                            logger.debug("  User rejected register, removing from possible list");
                            possibleRegisters.remove(possibleRegister);

                            // If there are no more possible registers, return null:
                            if (possibleRegisters.isEmpty()) {
                                logger.debug("  No more possible registers, returning null");
                                view.say("There are no more possible registers.");
                                logger.debug("=== End resolveUnmatchedAccount Debug ===");
                                logger.debug("");
                                return null;
                            }
                            else {
                                // If there is only one possible register left, return it:
                                try {
                                    Register result = evaluateRegisterSet(possibleRegisters);
                                    logger.debug("  Down to one register after rejection: {}", result.getName());
                                    logger.debug("=== End resolveUnmatchedAccount Debug ===");
                                    logger.debug("");
                                    return result;
                                } catch (ContinueFilteringException e) {
                                    logger.debug("  Multiple registers still possible, continuing filtering...");
                                    // Continue to the next filter.
                                }
                            }
                        }
                    } catch (ContinueFilteringException e) {
                        logger.debug("  User cancelled recent transaction confirmation, removing from possible list");
                        possibleRegisters.remove(possibleRegister);
                    }
                } else {
                    if (possibleRegister == null) {
                        logger.debug("  Merchant name doesn't correspond to a register");
                    } else {
                        logger.debug("  Register not in possible list (already filtered out)");
                    }
                }
            } else {
                logger.debug("  Transaction has no merchant");
            }
        } else {
            logger.debug("  No recent transaction found with this payee");
        }

        // Couldn't match so far, so do a full text search on the memo, get the most relevant transactions and let
        // the user select the register from the list of transactions:
        logger.debug("");
        logger.debug("FILTER: Performing full-text search on user description...");
        if (userDescription != null) {

            // Get a list of transactions that match the user description:
            List<Transaction> relevantTransactions = TransactionUtilities.getByUserDescriptionFullText(userDescription);
            logger.debug("  Found {} transactions matching full-text search", relevantTransactions.size());
            if (!relevantTransactions.isEmpty()) {

                // First narrow the list of transactions to only those that are associated with a register that is in the
                // list of possible registers:
                Iterator<Transaction> iterator = relevantTransactions.iterator();
                while (iterator.hasNext()) {
                    Transaction relevantTransaction = iterator.next();
                    Register register = Register.getByName(relevantTransaction.getMerchant().getName());
                    if (register == null) {
                        // throw out the transaction because it is not associated with a register:
                        logger.debug("  Removing transaction (not associated with register): {}", relevantTransaction.toStringVeryConcise());
                        iterator.remove();
                    } else if (!possibleRegisters.contains(register)) {
                        // throw out the transaction because it is not associated with a register that is in the list of
                        // possible registers:
                        logger.debug("  Removing transaction (register not in possible list): {}", relevantTransaction.toStringVeryConcise());
                        iterator.remove();
                    }
                }
                logger.debug("  After filtering to possible registers: {} transactions remain", relevantTransactions.size());
                if (!relevantTransactions.isEmpty()) {

                    // If there is only one transaction, then ask the user if this is the correct register:
                    if (relevantTransactions.size() == 1) {
                        logger.debug("  Only one transaction found, asking user for confirmation");
                        Transaction relevantTransaction = relevantTransactions.get(0);
                        Register register = Register.getByName(relevantTransaction.getMerchant().getName());
                        if (register != null) {
                            logger.debug("  Associated register: {}", register.getName());
                            view.say("Found a register that matches the token in the memo: " + register.toStringConcise());
                            Set<Register> setWithMatchingToken = new HashSet<>();
                            setWithMatchingToken.add(register);
                            try {
                                Register result = evaluateRegisterSet(setWithMatchingToken);
                                logger.debug("  User confirmed register from full-text match: {}", result.getName());
                                logger.debug("=== End resolveUnmatchedAccount Debug ===");
                                logger.debug("");
                                return result;
                            } catch (ContinueFilteringException e) {
                                logger.debug("  User rejected register, removing from possible list");
                                possibleRegisters.remove(register);
                            }
                        }
                    }

                    // There are multiple transactions, so create a list of "transaction with the register name" strings:
                    logger.debug("  Multiple transactions found ({}), building selection list", relevantTransactions.size());
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
                    logger.debug("  Found {} unique associated registers", associatedRegisters.size());

                    // If there is only one or zero registers associated with the transactions, then consult the user to
                    // confirm the register:
                    if (associatedRegisters.size() < 2)
                    {
                        logger.debug("  Only one unique register, asking user for confirmation");
                        try {
                            Register result = evaluateRegisterSet(associatedRegisters);
                            logger.debug("  User confirmed register: {}", result.getName());
                            logger.debug("=== End resolveUnmatchedAccount Debug ===");
                            logger.debug("");
                            return result;
                        } catch (ContinueFilteringException e) {
                            logger.debug("  User rejected, continuing to next filter");
                            // Continue to next filter.
                        }
                    }
                    else {
                        logger.debug("  Multiple unique registers, asking user to select from list");
                        // Allow the user to select the correct register from the list of transactions with register names:
                        int selection = view.selectByPositionFromList("The following transactions match the token in the memo.  " +
                                        "Select a transaction with the same register as the one associated with this transfer",
                                fullTextTrxsWithRegisterNames, ViewInt.ALLOW_NONE);

                        // If the user selected a register, then return it:
                        if (selection > 0) {
                            Register result = Register.getByName(relevantTransactions.get(selection).getMerchant().getName());
                            logger.debug("  User selected register: {}", result.getName());
                            logger.debug("=== End resolveUnmatchedAccount Debug ===");
                            logger.debug("");
                            return result;
                        } else {
                            logger.debug("  User made no selection, removing associated registers from possible list");
                            // Remove the associated registers from the list of possible registers:
                            for (Transaction relevantTransaction : relevantTransactions) {
                                possibleRegisters.remove(relevantTransaction.getRegister());
                            }
                        }
                    }
                } else {
                    logger.debug("  No transactions remain after filtering to possible registers");
                }
            } else {
                logger.debug("  No transactions found in full-text search");
            }
        } else {
            logger.debug("  No user description available for full-text search");
        }

        // If we haven't found a match, give up and let the user select a register from the list of possible registers:
        logger.debug("");
        logger.debug("FILTER: Final fallback - manual user selection from all possible registers");
        logger.debug("  {} possible registers remaining", possibleRegisters.size());
        List<String> registerNames = new ArrayList<>();
        List<Register> possibleRegistersList = new ArrayList<>(possibleRegisters);
        for (Register possibleRegister : possibleRegistersList) {
            registerNames.add(possibleRegister.toStringConcise());
        }

        // then allow the user to select the correct register from the list of registers:
        int selection = view.selectByPositionFromList("Select the register associated with this transfer",
                registerNames, ViewInt.DO_NOT_ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.ALLOW_SKIP);
        Register result = possibleRegistersList.get(selection);
        logger.debug("  User selected register: {}", result.getName());
        logger.debug("=== End resolveUnmatchedAccount Debug ===");
        logger.debug("");
        return result;
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
//            if (view.getYesOrNo("Is this the correct register?")) {
//                return singleRegister;
//            } else {
//                return null;
//            }
            return singleRegister;
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
        BudgetController budgetController = new BudgetController(sessionController);
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
                            MerchantController merchantController = new MerchantController(sessionController);
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
                    ForecastController forecastController = new ForecastController(
                            sessionController);
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
        BudgetController budgetController = new BudgetController(sessionController);

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
                            MerchantController merchantController = new MerchantController(sessionController);
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
                    ForecastController forecastController = new ForecastController(
                            sessionController);
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
     * Create a new register by collecting information from the user.
     *
     * @return The newly created Register, or null if cancelled
     * @throws Exception if any error occurs during creation
     */
    private Register createNewRegister() throws Exception {
        try {
            view.sayH1("Create New Register");
            view.say("Let's create a new register. You'll be asked to provide details.");
            view.say();

            // Get register details from user (pass null as template for new register)
            Register newRegister = getRegisterFromUser(null);

            if (newRegister == null) {
                // User cancelled
                return null;
            }

            if (!newRegister.isValid()) {
                view.say("The register information entered is invalid. Please try again.");
                return null;
            }

            // Budget has already been assigned during getRegisterFromUser()
            // No need to override it here

            // Confirm with user before saving
            Register confirmedRegister = confirmRegister(newRegister, "created");

            if (confirmedRegister == null) {
                // User chose not to save
                return null;
            }

            // Save the new register to the database
            try {
                confirmedRegister.insert();
                view.say();
                view.say("✓ Register successfully created!");
                view.say("  " + confirmedRegister.toStringConcise());
                return confirmedRegister;
            } catch (Exception e) {
                view.say("Error creating register: " + e.getMessage());
                throw e;
            }

        } catch (CancelException e) {
            view.say("Register creation cancelled.");
            return null;
        } catch (QuitException e) {
            throw e;
        }
    }

    /**
     * Get register information from the user interactively.
     * If a template register is provided, use its values as defaults.
     *
     * @param template Optional template register to pre-fill values (can be null)
     * @return Register object populated with user input, or null if cancelled
     * @throws Exception if any error occurs during input collection
     */
    private Register getRegisterFromUser(Register template) throws Exception {
        try {
            // Let the user know what we are going to do:
            view.sayH1("Register Entry");
            view.say("Please enter the details for the register. You can cancel or quit at any time by entering 'C' or 'Q'.");
            view.say("Press <enter> to accept the default value shown in brackets [].");

            view.sayH2("Basic Information");

            // Get the register name
            String defaultName = template != null ? template.getName() : "";
            String name = view.getResponseString("Register Name", defaultName, ViewInt.DO_NOT_ALLOW_NONE,
                    ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            // Get the nickname
            view.say("Nicknames are suggested to be 3 capital letters (e.g., AVR, CHK, SAV)");
            String defaultNickname = template != null ? template.getNickname() : "";
            String nickname = view.getResponseString("Nickname", defaultNickname, ViewInt.DO_NOT_ALLOW_NONE,
                    ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            // Get the account type
            String defaultAccountType = template != null ? template.getAccountType() : Register.CHECKING;
            view.say("Account types: 1-" + Register.CHECKING + ", 2-" + Register.SAVINGS + ", 3-Credit Card, 4-Investment, 5-Other");
            String accountTypeResponse = view.getResponseString("Select Account Type (or enter account type name)",
                    defaultAccountType, ViewInt.DO_NOT_ALLOW_NONE,
                    ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            // Parse the response - could be a number or a name
            String accountType;
            try {
                int selection = Integer.parseInt(accountTypeResponse);
                switch (selection) {
                    case 1: accountType = Register.CHECKING; break;
                    case 2: accountType = Register.SAVINGS; break;
                    case 3: accountType = "Credit Card"; break;
                    case 4: accountType = "Investment"; break;
                    case 5: accountType = "Other"; break;
                    default: accountType = accountTypeResponse; break;
                }
            } catch (NumberFormatException e) {
                accountType = accountTypeResponse;
            }

            // Get the account number
            String defaultAccountNumber = template != null ? template.getAccountNumber() : "";
            String accountNumber = view.getResponseString("Account Number", defaultAccountNumber, ViewInt.ALLOW_NONE,
                    ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            // Get the financial institution
            String defaultFinancialInstitution = template != null ? template.getFinancialInstitution() : "";
            String financialInstitution = view.getResponseString("Financial Institution", defaultFinancialInstitution,
                    ViewInt.DO_NOT_ALLOW_NONE, ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            // Get the default view type
            String defaultDefaultView = template != null ? template.getReportType() : "periodic";
            view.say("Default view types:");
            view.say("  1-periodic (standard checking account view)");
            view.say("  2-envelope (goal-oriented view showing progress toward savings goals)");
            String defaultViewResponse = view.getResponseString("Select Default View Type (or enter view type name)",
                    defaultDefaultView, ViewInt.DO_NOT_ALLOW_NONE,
                    ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            // Parse the response - could be a number or a name
            String defaultView;
            try {
                int selection = Integer.parseInt(defaultViewResponse);
                switch (selection) {
                    case 1: defaultView = "periodic"; break;
                    case 2: defaultView = "envelope"; break;
                    default: defaultView = defaultViewResponse; break;
                }
            } catch (NumberFormatException e) {
                defaultView = defaultViewResponse;
            }

            view.sayH2("Balance Information");

            // Get the balance
            Double defaultBalance = template != null ? template.getBalance() : 0.0;
            double balance = view.getResponseCurrency("Current Balance", defaultBalance, ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                    ViewInt.DO_NOT_ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            // Get the skipped amount
            Double defaultSkippedAmount = template != null ? template.getSkippedAmount() : 0.0;
            double skippedAmount = view.getResponseCurrency("Skipped Amount", defaultSkippedAmount, ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                    ViewInt.DO_NOT_ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            view.sayH2("Import File Configuration");

            // Get transaction import file name
            String defaultTrxImportFileName = template != null ? template.getTrxImportFileName() : "";
            String trxImportFileName = view.getResponseString("Transaction Import File Name", defaultTrxImportFileName,
                    ViewInt.ALLOW_NONE, ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            // Get transaction import file directory
            String defaultTrxImportFileDirectory = template != null ? template.getTrxImportFileDirectory() : "";
            String trxImportFileDirectory = view.getResponseString("Transaction Import File Directory", defaultTrxImportFileDirectory,
                    ViewInt.ALLOW_NONE, ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            // Get provisional transaction file name
            String defaultProvisionalTrxFileName = template != null ? template.getProvisionalTrxFileName() : "";
            String provisionalTrxFileName = view.getResponseString("Provisional Transaction File Name", defaultProvisionalTrxFileName,
                    ViewInt.ALLOW_NONE, ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            // Get provisional transaction file directory
            String defaultProvisionalTrxFileDirectory = template != null ? template.getProvisionalTrxFileDirectory() : "";
            String provisionalTrxFileDirectory = view.getResponseString("Provisional Transaction File Directory", defaultProvisionalTrxFileDirectory,
                    ViewInt.ALLOW_NONE, ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

            view.sayH2("Budget Assignment");

            // Get or create budget
            UUID budgetId = null;
            try {
                // Get list of existing budgets
                List<Budget> existingBudgets = Budget.getListOf();

                if (existingBudgets.isEmpty()) {
                    view.say("No budgets exist yet. You'll need to create one.");
                    String newBudgetName = view.getResponseString("Budget Name", "", ViewInt.DO_NOT_ALLOW_NONE,
                            ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP, null);

                    // Create new budget
                    Budget newBudget = new Budget();
                    newBudget.setId(UUID.randomUUID());
                    newBudget.setBudgetName(newBudgetName);
                    newBudget.insert();
                    budgetId = newBudget.getId();
                    view.say("✓ Budget '" + newBudgetName + "' created successfully.");

                } else {
                    // Let user select existing budget or create new one
                    EntityOrStringResult<Budget> budgetResult = view.selectByNameFromListOrString(
                            "Select an existing budget or enter a new budget name:",
                            existingBudgets,
                            ViewInt.ALLOW_NONE,
                            true  // allowCreate - user can enter new budget name
                    );

                    if (budgetResult.isEntitySelected() && budgetResult.getSelectedEntity() != null) {
                        // User selected existing budget
                        budgetId = budgetResult.getSelectedEntity().getId();
                        view.say("Selected budget: " + budgetResult.getSelectedEntity().getName());
                    } else if (!budgetResult.isEntitySelected()) {
                        // User entered new budget name
                        String newBudgetName = budgetResult.getSearchString();
                        Budget newBudget = new Budget();
                        newBudget.setId(UUID.randomUUID());
                        newBudget.setBudgetName(newBudgetName);
                        newBudget.insert();
                        budgetId = newBudget.getId();
                        view.say("✓ Budget '" + newBudgetName + "' created successfully.");
                    } else {
                        // User chose to leave budget null for now (selected entity is null)
                        view.say("No budget assigned. You can update this register later to assign a budget.");
                        budgetId = null;
                    }
                }
            } catch (Exception e) {
                view.say("Error accessing budgets: " + e.getMessage());
                view.say("Continuing without budget assignment. You can update the register later.");
                budgetId = null;
            }

            // Create Register object
            Register register = new Register();
            register.setId(UUID.randomUUID());
            register.setName(name);
            register.setNickname(nickname);
            register.setAccountType(accountType);
            register.setDefaultView(defaultView); // Set user-selected default view
            register.setAccountNumber(accountNumber);
            register.setFinancialInstitution(financialInstitution);
            register.setBalance(balance);
            register.setSkippedAmount(skippedAmount);
            register.setTrxImportFileName(trxImportFileName);
            register.setTrxImportFileDirectory(trxImportFileDirectory);
            register.setProvisionalTrxFileName(provisionalTrxFileName);
            register.setProvisionalTrxFileDirectory(provisionalTrxFileDirectory);
            register.setIdBudget(budgetId); // Set the budget ID (may be null)

            // Copy over the budget ID and default_view if updating
            if (template != null) {
                // Only override budgetId if it wasn't just set
                if (budgetId == null && template.getIdBudget() != null) {
                    register.setIdBudget(template.getIdBudget());
                }
                if (template.getReportType() != null) {
                    register.setDefaultView(template.getReportType());
                }
            }

            return register;

        } catch (CancelException e) {
            view.say("Operation cancelled by user.");
            return null;
        } catch (QuitException e) {
            throw e;
        }
    }

    /**
     * Display register details and ask user to confirm before saving.
     *
     * @param register The register to confirm
     * @param action   Description of the action (e.g., "updated", "created")
     * @return The confirmed register, or null if user cancels
     */
    private Register confirmRegister(Register register, String action) {
        view.say();
        view.say("Please review the register details:");
        view.say("──────────────────────────────────────");
        displayRegisterDetails(register);
        view.say("──────────────────────────────────────");

        if (view.getYesOrNo("Is this information correct? The register will be " + action + ".")) {
            return register;
        } else {
            view.say("Operation cancelled.");
            return null;
        }
    }

    /**
     * Display detailed information about a register in vertical format.
     *
     * @param register The register to display details for
     */
    private void displayRegisterDetails(Register register) {
        view.say("Register: " + register.getName());
        view.say("Nickname: " + (register.getNickname() != null ? register.getNickname() : "(none)"));
        view.say("Account Type: " + (register.getAccountType() != null ? register.getAccountType() : "(none)"));
        view.say("Default View: " + (register.getReportType() != null ? register.getReportType() : "(none)"));
        view.say("Account Number: " + (register.getAccountNumber() != null ? register.getAccountNumber() : "(none)"));

        // Display budget if assigned
        if (register.getIdBudget() != null) {
            try {
                Budget budget = Budget.getById(register.getIdBudget());
                view.say("Budget: " + budget.getName());
            } catch (Exception e) {
                view.say("Budget: (error retrieving budget name)");
            }
        } else {
            view.say("Budget: (none)");
        }

        view.say("Balance: " + Utility.formatDollarAmount(register.getBalance()));
        view.say("Skipped Amount: " + Utility.formatDollarAmount(register.getSkippedAmount()));
        view.say("Financial Institution: " + (register.getFinancialInstitution() != null ? register.getFinancialInstitution() : "(none)"));
        view.say("Transaction Import File Name: " + (register.getTrxImportFileName() != null ? register.getTrxImportFileName() : "(none)"));
        view.say("Transaction Import File Directory: " + (register.getTrxImportFileDirectory() != null ? register.getTrxImportFileDirectory() : "(none)"));
        view.say("Provisional Transaction File Name: " + (register.getProvisionalTrxFileName() != null ? register.getProvisionalTrxFileName() : "(none)"));
        view.say("Provisional Transaction File Directory: " + (register.getProvisionalTrxFileDirectory() != null ? register.getProvisionalTrxFileDirectory() : "(none)"));
    }
}