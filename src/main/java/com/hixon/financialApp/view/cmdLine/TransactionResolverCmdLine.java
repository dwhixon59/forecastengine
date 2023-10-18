package com.hixon.financialApp.view.cmdLine;

import com.hixon.financialApp.controller.Importer.TerminationCondition;
import com.hixon.financialApp.controller.QuitException;
import com.hixon.financialApp.controller.SkipException;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntityInt;
import com.hixon.financialApp.model.forecast.ForecastException;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.forecast.ForecastTransactionSplit;
import com.hixon.financialApp.model.register.*;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.TransactionResolverInt;
import com.hixon.financialApp.view.base.UserResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;

import static com.hixon.financialApp.controller.Importer.TerminationCondition.*;
import static com.hixon.financialApp.model.forecast.ForecastTransactionSplit.SplitDisposition.*;
import static com.hixon.financialApp.utility.Utility.StartDateType.*;
import static java.util.Calendar.YEAR;

public class TransactionResolverCmdLine implements TransactionResolverInt {

    /*
     * Fields for TransactionResolverCmdLine:
     */
    private TerminationCondition terminationCondition;
    private final Scanner in;


    /*
     * Getters and setters for TransactionResolverCmdLine:
     */
    @Override
    public TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }


    /*
     * Constructors for TransactionResolverCmdLine:
     */
    public TransactionResolverCmdLine() {
        terminationCondition = QUIT;
        in = new Scanner(System.in);
    }


    /*
     * Helper methods for TransactionResolverCmdLine:
     */
    @Override
    public void say() {
        System.out.println();
    }

    @Override
    public void say(String s) {
        System.out.println(s);
    }

    @Override
    public void ask(String s) {
        System.out.print(s);
    }

    @Override
    public boolean getYesOrNo(String question) {
        ask(question + " (y/n): ");
        while (true) {
            String line = in.nextLine();
            if (line.equalsIgnoreCase("y")) return true;
            if (line.equalsIgnoreCase("n")) return false;
            ask("\nPlease enter 'y' or 'n': ");
        }
    }

    @Override
    public boolean askContinue(String prompt) {
        ask(prompt + "  Do you want to continue?  " + "(y/n): ");
        while (true) {
            String line = in.nextLine();
            if (line.equalsIgnoreCase("y")) return true;
            if (line.equalsIgnoreCase("n")) return false;
            ask("\nPlease enter 'y' or 'n': ");
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public int getNumberBetween(String prompt, int min, int max) throws SkipException, QuitException {
        return getNumberBetween(prompt, min, max, false, false);
    }

    /**
     * @inheritDoc
     */
    @Override
    public int getNumberBetween(String prompt, int min, int max, boolean isSkipAllowed, boolean isQuitAllowed) throws SkipException, QuitException {
        int result;

        // Setup the skip and quit prompt strings if they are allowed.
        String skipPrompt = (isSkipAllowed) ? "s - skip" : "";
        String quitPrompt = (isQuitAllowed) ? "q - quit" : "";
        String skipAndQuitPrompt = "";
        if (isSkipAllowed || isQuitAllowed) {
            skipAndQuitPrompt = ", or " + skipPrompt + ((isSkipAllowed && isQuitAllowed) ? ", " : "") + quitPrompt;
        }

        ask(prompt + " (" + min + " to " + max + skipAndQuitPrompt + "): ");
        while (true) {
            try {
                String response = in.nextLine();
                if (isSkipAllowed) {
                    if (response.equalsIgnoreCase("s")) {
                        throw new SkipException("User asked to skip this item.");
                    }
                }
                if (isQuitAllowed) {
                    if (response.equalsIgnoreCase("q")) {
                        throw new QuitException("User asked to abort processing.");
                    }
                }
                result = Integer.parseInt(response);
                if (result >= min && result <= max) {
                    break;
                }
                ask("Please enter a number from " + min + " to " + max + skipAndQuitPrompt + ":");
            } catch (NumberFormatException numberFormatException) {
                say("Please enter a number from " + min + " to " + max + skipAndQuitPrompt + ":");
            }
        }
        return result;
    }

    @Override
    /**
     * {@inheritdoc}
     */
    public int selectFromNumberedList(String prompt, List<String> items, Boolean allowNone)
            throws SQLException, EntityException, SkipException, QuitException {

        // Ask which user to send the message to:
        say(prompt + ":  ");
        if (allowNone) say("\t0 - None");
        int i = 1;
        for (String user : items
        ) {
            say("\t" + i++ + " - " + user);
        }
        return getNumberBetween("Enter the number corresponding to the item:", (allowNone) ? 0 : 1, i - 1,
                true, true) - 1;
    }

    /**
     * {@inheritdoc}
     */
    @Override
    public <T extends IndependentEntityInt> T selectByNameFromNumberedList(String prompt, List<T> list, Boolean allowNone)
            throws SQLException, EntityException, SkipException, QuitException {

        // A list to store the names
        List<String> names = new ArrayList<>();

        // Iterate over the list of objects and add the name of each object to the list of names:
        for (T entity : list) {
            // Execute the method String getName() for each object and add the name to the list
            names.add(entity.getName());
        }

        // Ask the user to select one of the names from the list:
        int index = selectFromNumberedList(prompt, names, allowNone);

        // Return the object corresponding to the selected name, or null if none was selected:
        return index == -1 ? null : list.get(index);
    }

    @Override
    /**
     * {@inheritdoc}
     */
    public String selectFromFirstLetterList(String prompt, String menuOptionList) {

        // Parse the menu options out of the menuOptionList:
        String[] options = menuOptionList.split(",");

        // Ask the user to enter one of the values:
        ask(prompt + "  ");

        // Until they enter a valid value:
        String selected = null;
        while (true) {

            // Get the user selection:
            String line = in.nextLine();

            // See if the value entered matches any of the options:
            for (String option : options
            ) {
                if (option.equalsIgnoreCase(line)) {
                    selected = option;
                    break;
                }
            }

            // If the user didn't select one of the options, ask them to do it again:
            if (selected == null) {
                ask("\nPlease enter one of the following letters:  " + menuOptionList + ":  ");
            } else {
                break;
            }
        }
        return selected;
    }

    @Override
    public BudgetItem getBudgetItemFromUser() throws BudgetException, SQLException, EntityException, ParseException {
        // read in a new budget item for this:
        say("Enter the budget item in this order: category, payee, period type, amount, " +
                "running balance, start date, number of payments, end date, item type, how important, " +
                "how occurs, how paid, budget name:");
        BudgetItem budgetItem = BudgetItem.loadFromUserCSV(in.nextLine());
        return budgetItem;
    }

    @Override
    public double getDollarAmount() {
        return parseDouble("Please enter the dollar amount:  ", "Invalid dollar amount,");
    }

    protected double parseDouble(String prompt, String errorMessage) {
        if (prompt.length() > 0) {
            ask(prompt);
        }
        double doubleValue = 0;
        while (true) {
            try {
                String doubleString = in.nextLine();
                doubleValue = Double.parseDouble(doubleString);
                return doubleValue;
            } catch (NumberFormatException nfe) {
                ask(errorMessage + " please re-enter:  ");
            }
        }
    }

    protected int parseInt(String intString, String errorMessage) {
        int intValue = 0;
        while (true) {
            try {
                if (intString.length() > 0) intValue = Integer.parseInt(intString);
                return intValue;
            } catch (NumberFormatException nfe) {
                ask(errorMessage + " please re-enter:  ");
                intString = in.nextLine();
            }
        }
    }

    private String parseDollarAmount(String prompt, double defaultAmount) {
        say(prompt + ", or just press enter to accept the amount " +
                Utility.formatDollarAmount(Math.abs(defaultAmount)) + ":  ");
        String newAmount = in.nextLine();
        if (newAmount.length() == 0) {
            newAmount = Double.toString(defaultAmount);
        } else {
            newAmount = String.valueOf(parseDouble(newAmount, "Invalid amount,"));
        }
        return newAmount;
    }

    // Parse a date in mm/dd/yy format:
    private String parseStringDate(String prompt, Calendar defaultDate) {
        ask(prompt);
        if (defaultDate == null) {
            say(" (mm/dd/yy)");
        } else {
            say(" (mm/dd/yy) or just hit enter to accept the date " + Utility.calendarDateToStringDate(defaultDate));
        }
        String line = in.nextLine();
        boolean done = false;
        while (!done) {
            try {
                if (defaultDate != null && line.length() == 0) {
                    line = Utility.calendarDateToStringDate(defaultDate);
                    done = true;
                } else {
                    if (Utility.stringDateDashToCalendarDate(line) != null) {
                        done = true;
                    } else {
                        say("Invalid date format.  Please re-enter:");
                        line = in.nextLine();
                    }
                }
            } catch (ParseException e) {
                say("Invalid date format.  Please re-enter:");
                line = in.nextLine();
            }
        }
        return line;
    }

    // Parse a date in mm/dd/yy format:
    private Calendar parseCalendarDate(String prompt, Calendar defaultDate) {
        ask(prompt);
        if (defaultDate == null) {
            say(" (MM-DD-YYYY)");
        } else {
            say(" (MM-DD-YYYY) or just hit enter to accept the date " + Utility.calendarDateToStringDate(defaultDate));
        }
        String line = in.nextLine();
        boolean done = false;
        Calendar date = null;
        while (!done) {
            try {
                if (defaultDate != null && line.length() == 0) {
                    line = Utility.calendarDateToStringDate(defaultDate);
                }
                date = Utility.stringDateDashToCalendarDate(line);
                done = true;
            } catch (ParseException e) {
                say("Invalid date format.  Please re-enter:");
                line = in.nextLine();
            }
        }
        return date;
    }

    /**
     * This method checks if a file exists on the file with the name matching the passed in filename.  If the file is
     * not found, then it will ask the user if they want to try again allowing them to create, find, etc. the file.
     *
     * @param fileType A description of the file to be used when interacting with the user if not found.
     * @param fileName The name of the file to check for existence.
     * @return True if the file exists.  Otherwise, false.
     */
    public boolean existsFileWithRetry(String fileType, String fileName) throws QuitException {

        boolean found = false;
        boolean done = false;
        while (!done) {
            try {
                Path path = Paths.get(fileName);
                if (Files.exists(path) && !Files.isDirectory(path) && Files.size(path) > 0) {
                    done = true;
                    found = true;
                } else {
                    say("\n" + fileType + " file " + fileName + " does not exist or is empty.");
                    if (!getYesOrNo("Do you want to try again?")) {
                        done = true;
                    }
                }
            } catch (Exception e) {
                say("\n" + "Exception occurred trying to access " + fileType + " file " + fileName);
                say("\n" + "Exception was:  " + e);
                if (!askRetryContinueQuit()) {
                    found = false;
                }
            }
        }
        return found;
    }

    public boolean askRetryContinueQuit() throws QuitException {

        // Until the user makes a valid selection:
        boolean choice = false;
        try {
            // Ask the use if they would like to retry the operation, continue without retrying, or quit:
            Utility.getResolver().say();
            String prompt = "What would you like to do:  retry, continue without retrying, or quit?";
            String option = Utility.getResolver().selectFromFirstLetterList(prompt, "r,c,q");

            // Invoke a function to execute the user's request:
            switch (option) {
                case "r":
                    choice = true;
                    break;

                case "c":
                    choice = false;
                    break;

                case "q":
                    throw new QuitException("Operation aborted at user request.");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return choice;
    }


    /*
     * Main methods for TransactionResolverCmdLine:
     */
    @Override
    public void beginImportItem(Transaction transaction) {
        // Nothing to do for this type of command line resolver.
    }

    // Find or create a merchant for a transaction:
    @Override
    public Merchant assignMerchant(String merchantPayeeString, String transactionPayeeString, double transactionAmount)
            throws ViewException, RegisterException, EntityException, QuitException, BudgetException {
        try {
            say("\nFailed to find a merchant for payee \"" + merchantPayeeString + "\" derived from transaction payee:  "
                    + "\n\t" + transactionPayeeString + " for the amount of " + Utility.formatDollarAmount(transactionAmount));
            boolean stop = false;
            Merchant merchant = Merchant.getByPayee(merchantPayeeString);
            MerchantPayee merchantPayee;
            while (!stop) {
                ask("Enter the merchant name (or 'skip' or 'quit'): ");
                String line = in.nextLine();
                switch (line) {
                    case "":
                        say("Please enter a merchant name, 'skip', or 'quit'.");
                        continue;

                    case "reset":
                        say("Nothing to reset at this time.");
                        continue;

                    case "restart":
                        say("The import process cannot be restarted.");
                        continue;

                    case "skip":
                        merchant = Merchant.getByName(Merchant.UNKNOWN);
                        merchantPayee = merchant.addPayee(merchantPayeeString);
                        merchantPayee.save(EntityInt.SaveMethod.INSERT_ON_DUPLICATE_SKIP);
                        stop = true;
                        terminationCondition = SKIP;
                        break;

                    case "quit":
                        throw new QuitException("User requested quit during merchant assignment process.");

                    default:
                        if (merchant != null && merchant.getName().equalsIgnoreCase(Merchant.UNKNOWN)) {
                            MerchantPayee.deleteByName(merchantPayeeString);
                        }
                        merchant = Merchant.getByNameLike(line);
                        if (merchant != null) {
                            if (!merchantPayeeString.equalsIgnoreCase("Check")) {
                                merchantPayee = merchant.addPayee(merchantPayeeString);
                                merchantPayee.save();
                            }
                            stop = true;
                            terminationCondition = FOUND;
                            break;
                        } else {

                            // Merchant not found.  Create it if that's what the user wants:
                            ask("Merchant doesn't exist.  Create it (y/n): ");
                            String yesOrNo = in.nextLine();

                            // If the user wants to create a new merchant with that name:
                            if (yesOrNo.equalsIgnoreCase("y")) {
                                merchant = Merchant.loadFromCSV(line);
                                merchant.setAskAlways(getYesOrNo("Do you always want to approve budget allocations for " +
                                        "this merchant?"));
                                User user = getUser("Which user do you want to associate with this merchant?",
                                        User.getAllUsers(), true);
                                if (user != null) {
                                    merchant.setIdUser(user.getId());
                                }

                                // Checks don't have payees:
                                if (!merchantPayeeString.equalsIgnoreCase("Check")) {
                                    merchant.addPayee(merchantPayeeString);
                                }
                                merchant.save();
                                stop = true;
                                terminationCondition = FOUND;
                            } else {
                                stop = false;
                            }
                        }
                }
            }
            return merchant;

        } catch (QuitException e) {
            throw e;
        } catch (Exception e) {
            ViewException ve = new ViewException("Exception occurred trying to assign a merchant for this transaction: " +
                    merchantPayeeString + ".");
            ve.initCause(e);
            throw ve;
        }
    }

    @Override
    public User getUser(String prompt, List<User> users, Boolean allowNull) throws SQLException, EntityException, SkipException, QuitException {

        // Get a list of all the users and create a list of user first names from it:
        List<String> userFirstNames = new ArrayList<>();
        for (User user : users
        ) {
            userFirstNames.add(user.getFirstName());
        }

        // Ask the user to select one of the users from the list of user first names:
        int index = selectFromNumberedList(prompt, userFirstNames, true);
        return (index > -1) ? users.get(index) : null;
    }

    // The account number was not in the payee string, so ask the user for help:
    @Override
    public Register resolveUnmatchedAccount(Calendar date, double amount, String payee) throws RegisterException,
            SkipException, QuitException {
        String accountNumber = null;
        say("\nThere is no account number in the following transaction: " +
                Utility.calendarDateToStringSlashDate(date) + " " + payee + " " + Utility.formatDollarAmount(amount));

        say("Select the account to assign this transaction to:  ");
        List<Register> registers = Register.getListOf();
        for (int i = 1; i <= registers.size(); i++) {
            Register register = registers.get(i - 1);
            say("   " + i + ".  " + register.getName() + ", " + register.getAccountType() + ", " +
                    register.getAccountNumber());
        }

        int selection = Utility.getResolver().getNumberBetween("Enter the number of the selection", 1,
                registers.size(), true, true);

        return registers.get(selection - 1);
    }

    // Assign budget items to a new list of budget items:
    public List<BudgetItemMerchant> assignBudgetItems(Merchant merchant)
            throws BudgetException, ViewException, EntityException, RegisterException {

        say("\nFailed to find any budget items for merchant " + merchant.getName());
        List<BudgetItemMerchant> budgetItems = new ArrayList<>();
        assignMoreBudgetItems(merchant, budgetItems);

        // A null value for budget items means to check the termination condition, so if the termination condition
        // isn't "found", then null out the budget items list:
        if (terminationCondition != FOUND) {
            budgetItems = null;
        }

        return budgetItems;
    }

    // Assign new budget items to an existing list of budget items:
    @Override
    public TerminationCondition assignMoreBudgetItems(Merchant merchant, List<BudgetItemMerchant> budgetItems)
            throws BudgetException, ViewException, EntityException, RegisterException {

        try {
            boolean done = false;
            while (!done) {
                ask("Enter a budget item payee, and optionally, a fixed amount and fixed percentage (or 's' or " +
                        "'q'): ");
                String line = in.nextLine();
                switch (line) {
                    case "":
                        say("Please enter a budget item payee, 'skip', or 'quit'.");
                        continue;

                    case "reset":
                        say("Nothing to reset at this time.");
                        continue;

                    case "restart":
                        say("The import process cannot be restarted.");
                        continue;

                    case "s":
                        terminationCondition = SKIP;
                        break;

                    case "q":
                        terminationCondition = QUIT;
                        break;

                    default:
                        String[] tokens = line.split(",");
                        double amount = 0;
                        int percentage = 0;
                        BudgetItem budgetItem = BudgetItem.getByPayee(tokens[0]);

                        // If the budget item doesn't exist, then create it:
                        if (budgetItem == null) {
                            if (getYesOrNo("Specified budget item not found.  Create as a new budget item")) {
                                budgetItem = getBudgetItemFromUser();
                                budgetItem.save(EntityInt.SaveMethod.INSERT);
                            } else {
                                continue;
                            }
                        }

                        // Associate the budget item with the merchant:
                        if (tokens.length > 1) amount = parseDouble(tokens[1], "Invalid amount");
                        if (tokens.length > 2) percentage = parseInt(tokens[2], "Invalid percentage");
                        BudgetItemMerchant budgetItemMerchant = merchant.addBudgetItem(budgetItem, amount, percentage);
                        if (budgetItemMerchant != null) {
                            budgetItems.add(budgetItemMerchant);
                        }
                        terminationCondition = FOUND;
                        break;

                } // End switch on entered budget item.

                // Ask the user if they are done:
                if (terminationCondition == FOUND) {
                    done = !getYesOrNo("Assign another category to merchant " + merchant.getName());
                } else {
                    done = true;
                }
            } // End while there are budget items to enter.

        } catch (Exception e) {
            ViewException ve = new ViewException("Exception occurred trying to import this transaction: " +
                    merchant + ".");
            ve.initCause(e);
            throw ve;
        }
        return terminationCondition;
    }

    /**
     * Assign amounts to the budget items for a transaction.
     *
     * @param transaction
     * @param merchant
     * @param budgetItemMerchants
     * @return
     * @throws EntityException
     * @throws RegisterException
     * @throws ViewException
     * @throws BudgetException
     */
    @Override
    public List<TransactionSplit> assignAmountsToBudgetItems(Transaction transaction, Merchant merchant, List<BudgetItemMerchant>
            budgetItemMerchants) throws EntityException, RegisterException, ViewException, BudgetException {

        // If we need to ask the user to enter the splits:
        List<TransactionSplit> splits = new ArrayList<>();
        if (
                merchant.isAskAlways() || // If this is a merchant that the user wants to be asked about every time,
                        (
                                (budgetItemMerchants.size() > 1) && // or there is more then one budget item and
                                        // they are not fixed amounts:
                                        ((budgetItemMerchants.get(0).getAmount() == 0) &&
                                                (budgetItemMerchants.get(0).getPercentage() == 0))
                        )
        ) {
            // Ask the user to enter the splits:
            getSplits(transaction, splits, merchant, budgetItemMerchants, true, true);
        } else {
            // Track the total of the splits so that we can ensure they splits balance in the end:
            double transactionAmount = transaction.getAmount();

            // Iterate over the splits one at a time assigning amounts to each one:
            TransactionSplit transactionSplit;
            for (BudgetItemMerchant budgetItemMerchant : budgetItemMerchants
            ) {

                // If this split is for a fixed amount:
                if (budgetItemMerchant.getAmount() > 0) {
                    transactionSplit = new TransactionSplit(budgetItemMerchant.getAmount(),
                            budgetItemMerchant.getIdBudgetItem(), transaction.getId(), null);
                    transactionAmount = transactionAmount - budgetItemMerchant.getAmount();
                }
                // else if this split if for a fixed percentage of the transaction amount:
                else {
                    if (budgetItemMerchant.getPercentage() > 0) {
                        transactionSplit = new TransactionSplit((budgetItemMerchant.getPercentage() /
                                100) * transaction.getAmount(), budgetItemMerchant.getIdBudgetItem(), transaction.getId(),
                                null);
                        transactionAmount = transactionAmount - (budgetItemMerchant.getPercentage() /
                                100) * transaction.getAmount();
                    }
                    // else there is only one budget item, so allocate the whole transaction amount to it:
                    else {
                        transactionSplit = new TransactionSplit(transaction.getAmount(),
                                budgetItemMerchant.getIdBudgetItem(), transaction.getId(), null);
                        transactionAmount = transactionAmount - transaction.getAmount();
                    }
                }
                splits.add(transactionSplit);
            }
            if (transactionAmount != 0) {
                say("Automatic splits don't add up to the transaction amount, please enter them manually.");
                TransactionSplit.deleteSplitsForTransaction(transaction.getId());
                getSplits(transaction, splits, merchant, budgetItemMerchants, true, true);
            }
        }
        return (splits.isEmpty()) ? null : splits;
    }


    /**
     * Interact with the user to confirm or override the budget item amounts and then create splits for them.  Allow the
     * user to add new budget items and create splits for them as well.
     *
     * @param transaction            The transaction to get the splits for.
     * @param splits                 A list of splits that this function will add the splits to.
     * @param merchant               The merchant associated with this transaction.
     * @param budgetItemsForMerchant The budget items associated with the specified merchant.
     * @param skipAllowed            Is the user allowed to skip assigning splits to this transaction?
     * @param inquireAllowed         Is the user allowed to send an inquiry for clarification of this transaction?
     * @throws ViewException
     * @throws EntityException
     * @throws BudgetException
     * @throws RegisterException
     */
    @Override
    public void getSplits(Transaction transaction, List<TransactionSplit> splits, Merchant merchant,
                          List<BudgetItemMerchant> budgetItemsForMerchant, Boolean skipAllowed, Boolean inquireAllowed)
            throws ViewException, EntityException, BudgetException, RegisterException {

        // There should be at least one budget item.  If there isn't then throw an error:
        if (budgetItemsForMerchant.size() == 0) {
            throw new ViewException("Must be at least one budget item assigned to a transaction to be able to get the " +
                    "splits for  it.");
        }

        // Attempt to get a balanced set of splits, or terminate as a "skip" or "inquire".  Repeat as necessary:
        Boolean done = false;
        while (!done) {

            // Assume we will get this done in one iteration:
            done = true;

            // Show the assigned budget items to the user:
            showAssignedBudgetItems(budgetItemsForMerchant, transaction.getAmount());

            /*
             * Figure out the amounts of the splits, e.g. how much of the transaction amount to allocate to each of the
             * budget items:
             */
            // If the amounts are pre-established in the budget item:
            String[] amounts;
            if (budgetItemsForMerchant.get(0).getAmount() > 0 || budgetItemsForMerchant.get(0).getPercentage() > 0) {

                // Then ask the user to confirm or override the amounts:
                amounts = getAndParseCsvLine("Enter the split amounts, or just return to accept displayed amounts:",
                        budgetItemsForMerchant.size(), true, true);

            } else { // the amounts are not pre-established, so ask the user to enter them:
                amounts = getAndParseCsvLine("Enter the split amounts (or a - add, i - inquire, s - skip):  ",
                        0, false, true);
            }

            // Create the splits.  Process any user requests to edit the assigned budget items at the same time:
            // Add a new budget item to current Merchant:
            if (amounts[0].equalsIgnoreCase("a")) {
                assignMoreBudgetItems(merchant, budgetItemsForMerchant);
                done = false;

                // Delete one of the displayed budget items from the merchant for this transaction:
            } else if (amounts[0].equalsIgnoreCase("d")) {
                say("The delete budget item from merchant function has not been implemented yet.");
                done = false;

                // Send an inquiry to someone as to how to categorize this transaction:
            } else if (amounts[0].equalsIgnoreCase("i")) {
                if (inquireAllowed) {
                    say("Sending an inquiry.");
                    terminationCondition = INQUIRE;
                } else {
                    say("Inquiry function not allowed at this time.");
                    done = false;
                }

                // Skip this transaction for now:
            } else if (amounts[0].equalsIgnoreCase("s")) {
                if (skipAllowed) {
                    say("Skipping this transaction.");
                    terminationCondition = SKIP;
                } else {
                    say("Skip not allowed at this time.");
                    done = false;
                }

                // Create the splits from a sparse list of categories entered by the user as "payee_#:amount":
            } else if (amounts[0].matches("^[1-9][0-9]*\\s*:(.*)")) {
                // For each of the payee:amount combinations entered by the user:
                List<Integer> evenRemainders = new ArrayList<>();
                List<Integer> apportionedRemainders = new ArrayList<>();
                List<Integer> addTaxItems = new ArrayList<>();
                for (int i = 0; i < amounts.length; i++) {

                    // Remove leading and trailing blanks from the amount:
                    amounts[i] = amounts[i].trim();

                    // Validate that the current amount is indeed a sparse list amount:
                    if (!amounts[i].matches("^[1-9][0-9]*\\s*:(.*)")) {

                        // The user didn't enter "payee_#:".  Inform them and ask them to re-enter the values:
                        Utility.getResolver().say("The amount " + amounts[i] + " does not start with a number followed by " +
                                "a colon.  Please re-enter the values");
                        done = false;
                        break;
                    }

                    // Get the number of the budget item from the user entered value:
                    String itemNumberString = amounts[i].substring(0, amounts[i].indexOf(':')).trim();
                    int itemNumber = 0;
                    try {
                        itemNumber = Integer.parseInt(itemNumberString);

                    } catch (NumberFormatException nfe) {

                        // The user didn't enter a valid integer.  Inform them and ask them to re-enter the values:
                        Utility.getResolver().say("The payee number " + itemNumberString + "is not a valid number from the " +
                                "list.  " + nfe.getMessage() + "  Please re-enter the values");
                        done = false;
                        break;
                    }

                    // If the user specified payee number is not in the list of payees:
                    if (itemNumber <= 0 || itemNumber > budgetItemsForMerchant.size()) {

                        // Then inform the user and ask them to re-enter the values:
                        Utility.getResolver().say("The payee number " + itemNumberString + "is not in the list.  Please " +
                                "re-enter the values");
                        done = false;
                        break;
                    }

                    // Get the amount to be assigned to the transaction split for this payee:
                    String itemAmountString = amounts[i].substring(amounts[i].indexOf(':') + 1).trim();

                    // If there is a memo after the amount:
                    String memo = null;
                    if (itemAmountString.contains(" ")) {

                        // then copy it into the memo variable:
                        memo = itemAmountString.substring(itemAmountString.indexOf(" ") + 1).trim();

                        // and remove it from the item amount string:
                        itemAmountString = itemAmountString.substring(0, itemAmountString.indexOf(" "));
                    }

                    // Assign the amount to the split:
                    double itemAmount = 0;

                    // If the amount is a remainder split:
                    if (itemAmountString.substring(itemAmountString.length() - 1).equalsIgnoreCase("e")) {

                        // then add this item to the even remainders list:
                        evenRemainders.add(i);

                        // and trim the 'e' off the end of the amount:
                        itemAmountString = itemAmountString.substring(0, itemAmountString.length() - 1);

                    } // else if the amount is an apportionment split:
                    else if (itemAmountString.substring(itemAmountString.length() - 1).equalsIgnoreCase("a")) {

                        // then add this item to the apportionment list:
                        apportionedRemainders.add(i);

                        // and trim the 'a' off the end of the amount:
                        itemAmountString = itemAmountString.substring(0, itemAmountString.length() - 1);

                    } // else if we are supposed to add tax to the amount:
                    else if (itemAmountString.substring(itemAmountString.length() - 1).equalsIgnoreCase("t")) {

                        // then add this item to the add tax list:
                        addTaxItems.add(i);

                        // and trim the 't' off the end of the amount:
                        itemAmountString = itemAmountString.substring(0, itemAmountString.length() - 1);

                    }

                    // If there is anything in the amount string assume it is an amount to be assigned to this item:
                    if (itemAmountString.length() > 0) {

                        // Convert the amount string to a number:
                        try {
                            itemAmount = Utility.parseDollarAmount(itemAmountString);

                        } catch (NumberFormatException nfe) {

                            // The user didn't enter a valid dollar amount.  Inform them and ask them to re-enter the values:
                            Utility.getResolver().say("The item amount " + itemAmountString + "is not a valid dollar amount.  " +
                                    nfe.getMessage() + "  Please re-enter the values");
                            done = false;
                            break;
                        }
                    }

                    // Add a split for this budget item:
                    splits.add(new TransactionSplit(itemAmount, budgetItemsForMerchant.get(itemNumber - 1),
                            transaction, memo));
                }

                // If there was an error in the format of the sparse category string, the have the user re-enter it:
                if (!done) {
                    splits.clear();
                    continue;
                }

                // If there is a remainder to split evenly or apportion across the items, or we need to add tax:
                if (evenRemainders.size() > 0 || apportionedRemainders.size() > 0 || addTaxItems.size() > 0) {
                    TransactionSplit.splitRemainder(transaction.getAmount(), evenRemainders, apportionedRemainders,
                            addTaxItems, splits);
                }

                // Verify that the amounts from the sparse list of payees add up to the transaction total:
                double totalSplitsAmount = 0;
                for (TransactionSplit split : splits) {
                    totalSplitsAmount += split.getAmount();
                }
                if (!Utility.isEqualCurrency(transaction.getAmount(), totalSplitsAmount)) {

                    // The user didn't enter a valid dollar amount.  Inform them and ask them to re-enter the values:
                    Utility.getResolver().say("The total of the list of splits entered (" + totalSplitsAmount + ") does not " +
                            "equal the amount of the transaction (" + transaction.getAmount() + ").    Please re-enter the " +
                            "values");
                    splits.clear();
                    done = false;
                    continue;
                }

                // else if the response is a single use category:
            } else if (amounts[0].matches("[a-zA-Z][a-zA-Z0-9 '()-\\+]+")) {
                say("The allocate, but don't add, function has not been implemented yet.");
                //String payee = amount.substring(0, amount.indexOf(':') - 1);
                done = false;

                // else if the response is a number selection and a memo:
            } else if (amounts[0].matches("^[1-9][0-9]*[\\s]+[^,]*") && amounts.length == 1) {
                String itemNumberString = amounts[0].substring(0, amounts[0].indexOf(' '));
                int itemNumber = Integer.parseInt(itemNumberString);
                String memo = amounts[0].substring(amounts[0].indexOf(' ') + 1);
                if (itemNumber <= budgetItemsForMerchant.size()) {
                    splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(itemNumber - 1),
                            transaction, memo));
                }

                // else if the response is just a number selection:
            } else if (amounts[0].matches("[1-9][0-9]*") && amounts.length == 1) {
                int itemNumber = Integer.parseInt(amounts[0]);
                if (itemNumber <= budgetItemsForMerchant.size()) {
                    splits.add(new TransactionSplit(transaction.getAmount(), budgetItemsForMerchant.get(itemNumber - 1),
                            transaction, null));
                }
            } else {
                // Allocate the splits as directed:
                boolean useEnteredAmounts = amounts.length != 1 || amounts[0].length() != 0;
                for (int i = 0; i < budgetItemsForMerchant.size(); i++) {

                    double enteredAmount = (useEnteredAmounts) ? parseDouble(amounts[i], "Must be a dollar amount.") : 0;

                    // Don't create a split if the user entered zero for this budget item:
                    if (!useEnteredAmounts || enteredAmount != 0) {

                        // If the splits are not based on percentages, then use amounts:
                        if (budgetItemsForMerchant.get(i).getPercentage() == 0) {
                            splits.add(new TransactionSplit(
                                    (useEnteredAmounts) ? enteredAmount : budgetItemsForMerchant.get(i).getAmount(),
                                    budgetItemsForMerchant.get(i), transaction,
                                    null)
                            );
                        } else  // use the percentages:
                        {
                            splits.add(new TransactionSplit((useEnteredAmounts) ?
                                    (Integer.parseInt(amounts[i]) / 100) * transaction.getAmount() :
                                    (budgetItemsForMerchant.get(i).getPercentage() / 100) * transaction.getAmount(),
                                    budgetItemsForMerchant.get(i), transaction, null)
                            );
                        }
                    }
                }
            }
        }
    }


    // Ask the user if the want to regenerate the forecast:
    @Override
    public boolean askRegenerateForecast() {
        return getYesOrNo("Changes were made to budget items and the forecast is out of sync now.  Do you want " +
                "to regenerate the long term forecast?");
    }


    // Print a prompt, get a response, parse it based on commas and return it in a string array:
    @Override
    public String[] getAndParseCsvLine(String prompt, int numberOfRequiredValues, boolean allowNullEntry, boolean
            allowSingleValue) {
        String[] tokens = null;
        boolean done = false;
        while (!done) {
            ask(prompt);
            String line = in.nextLine();
            // if the user just hit enter and that's allowed:
            if (line.isEmpty()) {
                if (allowNullEntry) {
                    // then return an empty array:
                    done = true;
                } else {
                    say("Please enter a value.");
                }
                continue;
            }
            tokens = line.split(",");

            // If we are dealing with special values, and the user entered a single value then were done:
            if (allowSingleValue && (tokens.length == 1)) {
                done = true;
                continue;
            }

            if (numberOfRequiredValues != 0 && (tokens.length < numberOfRequiredValues || tokens.length > numberOfRequiredValues)) {
                ask("Wrong number of values entered.  Please enter " + numberOfRequiredValues + " value(s).");
            } else {
                done = true;
            }
        }
        return tokens;
    }

    // Show a list of the assigned budget items for a transaction, and the amount of the transaction:
    @Override
    public void showAssignedBudgetItems(List<BudgetItemMerchant> budgetItems, double amount) {

        say("The assigned budget items and amounts (if specified) for this merchant are:");
        int i = 1;
        for (BudgetItemMerchant budgetItem : budgetItems
        ) {
            String lineEnd = "";
            if (budgetItem.getAmount() > 0) {
                lineEnd = ", " + Utility.formatDollarAmount(budgetItem.getBudgetItem().getAmount()) + ", 0";
            } else {
                if (budgetItem.getPercentage() > 0) {
                    lineEnd = ", 0, " + budgetItem.getPercentage() + "%";
                }
            }
            say("   " + i++ + ".  " + budgetItem.getBudgetItem().getPayee() + lineEnd);
        }
    }

    // What to do if the split amount exceeds the budgeted amount:
    @Override
    public ForecastTransactionSplit.SplitDisposition assignOverageAmount(String prompt) {
        ForecastTransactionSplit.SplitDisposition disposition = null;

        ask(prompt + "What would you like to do (a-adjust, d-dispute, i-ignore, r-roll)?  ");

        boolean done = false;
        while (!done) {
            done = true;
            String line = in.nextLine();
            switch (line) {
                case "a":
                    disposition = ADJUST;
                    break;

                case "d":
                    disposition = DISPUTE;
                    break;

                case "i":
                    disposition = IGNORE;
                    break;

                case "r":
                    disposition = ROLL_FORWARD;
                    break;

                default:
                    ask("Please enter a, d, i or r.");
                    done = false;
            }
        }
        return disposition;
    }

    // What to do if we're not sure which forecast transaction to assign a split to because the amounts don't match:
    @Override
    public UserResponse assignSplitAmountToForecastTransaction(TransactionSplit split, ForecastTransaction
            forecastTransaction) {
        UserResponse response = new UserResponse();

        say("Applicable  " + forecastTransaction.toStringConcise());
        ask("What would you like to do (a-adjust, s-assign, d-dispute, i-ignore)? ");

        boolean done = false;
        while (!done) {
            done = true;
            String line = in.nextLine();
            switch (line) {
                case "a":
                    response.setDisposition(ADJUST);
                    response.setResponse(parseDollarAmount("Enter the new amount", split.getAmount()));
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
                    say("Please enter a, s, d, or i.");
                    done = false;
            }
        }
        return response;
    }

    // What to do if we're not sure which forecast transaction to assign a split to because the dates don't match:
    @Override
    public UserResponse assignSplitDateToForecastTransaction(TransactionSplit split, ForecastTransaction
            forecastTransaction)
            throws EntityException, SQLException {
        UserResponse response = new UserResponse();

        say("Applicable " + forecastTransaction.toStringConcise());
        ask("What would you like to do (a-adjust, s-assign, d-dispute, i-ignore)? ");

        boolean done = false;
        while (!done) {
            done = true;
            String line = in.nextLine();
            switch (line) {
                case "a":
                    response.setDisposition(ADJUST);
                    response.setResponse(parseStringDate("Enter the new date", split.getTransaction().getDate()));
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
                    say("Please enter a, s, d, or i.");
                    done = false;
            }
        }
        return response;
    }

    // What to do if there is a discrepancy between the planned and actual amounts of a transaction.
    @Override
    public UserResponse transactionAmountDiscrepancy(Transaction transaction, TransactionSplit split,
                                                     ForecastTransaction forecastTransaction) throws BudgetException, SQLException, EntityException, ForecastException {
        UserResponse response = new UserResponse();

        say("The amount of this split is significantly more than the planned amount for the current period (" +
                Utility.formatDollarAmount(-forecastTransaction.getForecastItem().getAmount()) + ").");
        ask("Would you like to adjust the amount for this budget item (a-adjust, s-assign, d-dispute, i-ignore)? ");

        boolean done = false;
        while (!done) {
            done = true;
            String line = in.nextLine();
            switch (line) {
                case "a":
                    response.setDisposition(ADJUST);
                    response.setResponse(parseDollarAmount("Enter the new amount", split.getAmount()));
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
                    say("Please enter a, s, or i.");
                    done = false;
            }
        }
        return response;
    }

    @Override
    public UserResponse getForecastStartDate() throws QuitException {
        UserResponse response = new UserResponse();

        say("What date do you want to start on?  (l-first of last month, <enter> first of next month, t-Today, " +
                "f-First of this month, o-One month from today, c-Custom date)");

        boolean done = false;
        while (!done) {
            done = true;
            String line = in.nextLine();
            switch (line) {
                case "l":
                    response.setStartDate(FIRST_OF_LAST_MONTH);
                    break;

                case "t":
                    response.setStartDate(TODAY);
                    break;

                case "f":
                    response.setStartDate(FIRST_OF_THIS_MONTH);
                    break;

                case "o":
                    response.setStartDate(ONE_MONTH_FROM_TODAY);
                    break;

                case "c":
                    response.setStartDate(ARBITRARY_DATE);
                    Calendar startDate = Calendar.getInstance();
                    response.setDate(parseCalendarDate("Enter the start date", startDate));
                    break;

                default:
                    if (line.length() == 0) {
                        response.setStartDate(FIRST_OF_NEXT_MONTH);
                    } else {
                        say("Please enter l, <enter>, t, f, o, or c.");
                        done = false;
                    }
            }
        }
        return response;
    }

    @Override
    public Calendar getSpendingReportMonth() throws QuitException {
        UserResponse response = new UserResponse();

        say("\nWhat month do you want to report on?  \n" +
                "\tl - last month\n" +
                "\tt or just <enter> - this month\n" +
                "\t1 - 12 January - December in the last 12 months\n" +
                "\tSpecific month (mm-yy)\n" +
                "Enter your selection:  ");

        boolean done = false;
        Calendar month = Calendar.getInstance();
        month.set(Calendar.DATE, 1);
        while (!done) {
            done = true;
            String line = in.nextLine();
            switch (line) {
                case "l":
                    month.add(Calendar.MONTH, -1);
                    break;

                case "t":
                case "":
                    break;

                case "1":
                case "2":
                case "3":
                case "4":
                case "5":
                case "6":
                case "7":
                case "8":
                case "9":
                case "10":
                case "11":
                case "12":
                    month.set(Calendar.MONTH, Integer.parseInt(line) - 1);

                    //  If the selected month is in the future, then change the date to that month a last year:
                    Calendar now = Calendar.getInstance();
                    if (now.compareTo(month) < 0) {
                        month.add(YEAR, -1);
                    }
                    break;

                case "quit":
                    throw new QuitException("Quitting render spending report action.");

                default:
                    try {
                        month = Utility.stringDateDashToCalendarDate(line);
                    } catch (ParseException e) {
                        say("Please enter l, <enter>, t, 1-12 c, or quit.");
                        done = false;
                    }
            }
        }
        return month;
    }

    @Override
    public boolean askDeleteRegisterTransaction(Transaction transaction) {
        say();
        say(transaction.toStringConcise());
        return getYesOrNo("This provisional transaction has disappeared from the list of provisional " +
                "transactions, but it does not appear as a cleared transaction.\nIt has likely been invalidated.  Do you "
                + "want to remove it?");
    }

} // End class TransactionResolverCmdLine.
