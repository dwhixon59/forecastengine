package com.hixon.financialApp.controller;

import com.hixon.financialApp.controller.ImportController.TerminationCondition;
import com.hixon.financialApp.model.budget.*;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.entity.MatchQuery;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.ViewInt;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.hixon.financialApp.controller.ImportController.TerminationCondition.*;
import static com.hixon.financialApp.model.budget.BudgetItemMerchant.isBudgetItemInList;
import static com.hixon.financialApp.utility.Utility.formatDollarAmount;
import static com.hixon.financialApp.utility.Utility.stringDateDashToCalendarDate;
import static java.util.Calendar.YEAR;

/**
 * This class is the controller for budget related business logic interface.
 */
public class BudgetController {

    /*
     * Fields for BudgetController:
     */
    private ImportController.TerminationCondition terminationCondition;
    Register register;
    Budget budget;
    Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;


    /*
     * Getters and setters for BudgetController:
     */
    public TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }


    /**
     * Constructors and destructor for BudgetController:
     */
    public BudgetController(Register register, Budget budget, Forecast forecast, ViewInt view, NotificationServiceInt
            notificationService) {
        terminationCondition = QUIT;
        this.register = register;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
    }


    /**
     * Main methods for BudgetController:
     */
    /**
     * Get a budget item from the budget items in the database using full text searching.  If none of the budget items
     * in the database are what the user is looking for, then allow the user to create a new budget item.
     *
     * @param seedName a string to start the full text search with, or null
     * @throws SQLException
     * @throws EntityException
     * @throws QuitException
     * @throws SkipException
     */
    public BudgetItem getBudgetItemByNameFullText(String seedName) throws Exception, QuitException, SkipException {

        BudgetItem selectedBudgetItem = null;
        // Use the SelectionController to select a budget item from the database using natural language queries:
        SelectionController selectionController = new SelectionController(view);
        selectedBudgetItem = selectionController.getByNameFullText(
                seedName,
                budget,
                ViewInt.DO_NOT_ALLOW_NONE,
                ViewInt.ALLOW_CREATE,
                ViewInt.ALLOW_CANCEL,
                ViewInt.ALLOW_QUIT,
                ViewInt.ALLOW_SKIP,
                BudgetItem.getPrintableTypeName_static(),
                BudgetItem::getDisplayString,
                new MatchQuery(BudgetItem.getSelectQuery() + " WHERE bi.Budget_idBudget = uuid_to_bin('" +
                        budget.getId() + "') AND (endDate is null OR endDate > CURRENT_DATE) AND ", "bi.payee",
                        "bi.category, bi.payee, bi.memo"),
                rs -> {
                    try {
                        return new BudgetItem(rs);
                    } catch (BudgetException e) {
                        throw new RuntimeException(e);
                    }
                },
                (IndependentEntity budgetObj, String newName) -> new BudgetItem((Budget) budgetObj, newName));

        // If the budget item is new, then fill it out and save it:
        if (selectedBudgetItem.isDirty()) {
            selectedBudgetItem = getBudgetItemFromUser();
            selectedBudgetItem.save(EntityInt.SaveMethod.INSERT);
        }

        return selectedBudgetItem;
    }

    /**
     * Assign budget items to an existing list of budget items for a merchant.  There does not need to be any budget
     * items in the list that is provided.
     *
     * @param merchant
     * @param budgetItemsForMerchant
     * @throws Exception
     * @throws QuitException
     * @throws SkipException
     */
    public void assignBudgetItemsToMerchant(Merchant merchant, List<BudgetItemMerchant>
            budgetItemsForMerchant) throws Exception, CancelException, QuitException, SkipException {

        try {
            boolean firstTime = true;
            boolean done = false;
            int percentage = 0;
            double amount = 0.0;
            BudgetItem firstSelectedBudgetItem = null;
            BudgetItem selectedBudgetItem = null;
            while (!done) {

                // Get a budget item that the user wants to associate with this merchant:
                selectedBudgetItem = getBudgetItemByNameFullText(null);
                BudgetItemMerchant budgetItemMerchant = new BudgetItemMerchant(merchant, selectedBudgetItem);

                // then if the budget item isn't already associated with this merchant:
                if (!isBudgetItemInList(selectedBudgetItem, budgetItemsForMerchant)) {

                    // then if the user wants to add this budget item to the list of budget items for the merchant:
                    if (
                            !firstTime || // Later iterations don't make sense if we don't add them to the list:
                                    view.getYesOrNo("Do you want to add this budget item \"" +
                                            selectedBudgetItem.getPayee() + "\" to the list of budget items for the merchant \""
                                            + merchant.getName() + "\"?")
                    ) {
                        firstTime = false;

                        // then if the user wants to assign a fixed amount, or percentage, to this budget item when
                        // associated with this particular merchant:
                        boolean resp = view.getYesOrNo("Do you want to assign a fixed amount, or percentage, " +
                                "to this budget item when it is associated with this particular merchant");
                        if (resp) {
                            String input = view.getResponseString("Enter the fixed amount or percentage (e.g. 100 " +
                                    "or 10%):");
                            if (input.endsWith("%")) {
                                budgetItemMerchant.setPercentage(Integer.parseInt(input.substring(0, input.length() - 1)));
                            } else {
                                budgetItemMerchant.setAmount(Double.parseDouble(input));
                            }
                        }

                        // Associate the budget item with the merchant in the database:
                        budgetItemMerchant.save();
                    }

                    // Add the budget item to the list of budget items passed in:
                    budgetItemsForMerchant.add(budgetItemMerchant);
                } else {
                    // Tell the user that this budget item is already associated with this merchant:
                    view.say("The budget item you selected \"" + selectedBudgetItem.getPayee() + "\" is already " +
                            "associated with the merchant \"" + merchant.getName() + "\".");
                }

                // Ask the user if they are done:
                done = !view.getYesOrNo("Assign another budget item to merchant " + merchant.getName());

            } // End while there are budget items to enter.

        } catch (CancelException | QuitException | SkipException e) {
            throw e;

        } catch (Exception e) {
            ViewException ve = new ViewException("Exception occurred trying to import this transaction: " +
                    merchant + ".", e);
            throw ve;
        }
    }

    public List<TransactionSplit> assignAmountsToBudgetItems(Transaction transaction, Merchant merchant, Budget
            budget,
                                                             List<BudgetItemMerchant> budgetItemMerchants)
            throws Exception {

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
            getSplits(transaction, splits, merchant, budget, budgetItemMerchants, true, true);
        }
        else {
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
                // else if this split is for a fixed percentage of the transaction amount:
                else {
                    if (budgetItemMerchant.getPercentage() > 0) {
                        transactionSplit = new TransactionSplit(((double) budgetItemMerchant.getPercentage() /
                                100) * transaction.getAmount(), budgetItemMerchant.getIdBudgetItem(), transaction.getId(),
                                null);
                        transactionAmount = transactionAmount - ((double) budgetItemMerchant.getPercentage() /
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
                view.say("Automatic splits don't add up to the transaction amount, please enter them manually.");
                TransactionSplit.deleteSplitsForTransaction(transaction.getId());
                getSplits(transaction, splits, merchant, budget, budgetItemMerchants, true, true);
            }
        }
        return (splits.isEmpty()) ? null : splits;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> generateDisplayableBudgetItemList(List<BudgetItem> budgetItems) throws Exception {

        view.say("The budget items are:");
        List<String> budgetItemNames = new ArrayList<>();
        for (BudgetItem budgetItem : budgetItems
        ) {
            String line = "";
            line += budgetItem.getPayee();
            line += " (";
            line += budgetItem.getCategory();
            line += ", ";
            if (budgetItem.getAmount() != 0) {
                line += Utility.formatRoundedDollarAmount(budgetItem.getAmount());
                line += " ";
            }
            line += Item.generatePeriodType(budgetItem.getPeriod());
            if (budgetItem.getPeriod() != Item.PeriodType.ON_DEMAND) {
                line += ", ";
                line += Utility.calendarDateToStringDate(
                        ForecastTransaction.getApplicableForecastTransaction(
                                budgetItem.getId(), Calendar.getInstance()).getPlannedDate());
            }
            if (budgetItem.getMemo() != null &&
                    !budgetItem.getMemo().isEmpty()) {
                line += ", " + budgetItem.getMemo();
            }
            line += ")";
            budgetItemNames.add(line);
        }
        return budgetItemNames;
    }

    public BudgetItem getBudgetItemFromUser() throws BudgetException, SQLException, EntityException, ParseException {
        // read in a new budget item for this:
        view.say("Enter the budget item in this order: category, payee, memo, period type, amount, " +
                "running balance, start date, number of payments, end date, item type, how important, " +
                "how occurs, how paid, budget name:");
        String line = view.getResponseString();
        BudgetItem budgetItem = BudgetItem.loadFromUserCSV(line);
        return budgetItem;
    }

    public Calendar getSpendingReportMonth() throws QuitException {

        view.say("\nWhat month do you want to report on?  \n" +
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
            String line = view.getResponseString();
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
                        month = stringDateDashToCalendarDate(line);
                    } catch (ParseException e) {
                        view.say("Please enter l, <enter>, t, 1-12 c, or quit.");
                        done = false;
                    }
            }
        }
        return month;
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
     * @throws Exception If there is a problem with the input or the database.
     */
    public void getSplits(Transaction transaction, List<TransactionSplit> splits, Merchant merchant, Budget
            budget,
                          List<BudgetItemMerchant> budgetItemsForMerchant, Boolean skipAllowed, Boolean inquireAllowed)
            throws Exception {

        // There should be at least one budget item.  If there isn't then throw an error:
        if (budgetItemsForMerchant.isEmpty()) {
            throw new ViewException("Must be at least one budget item assigned to a transaction to be able to get the " +
                    "splits for  it.");
        }

        // Attempt to get a balanced set of splits, or terminate as a "skip" or "inquire".  Repeat as necessary:
        boolean done = false;
        while (!done) {

            // Assume we will get this done in one iteration:
            done = true;

            // Show the assigned budget items to the user:
            showBudgetItemsForMerchant(budgetItemsForMerchant, transaction.getAmount());

            /*
             * Figure out the amounts of the splits, e.g. how much of the transaction amount to allocate to each of the
             * budget items:
             */
            // If the amounts are pre-established in the budget item:
            String[] amounts;
            if (budgetItemsForMerchant.get(0).getAmount() > 0 || budgetItemsForMerchant.get(0).getPercentage() > 0) {

                // Then ask the user to confirm or override the amounts:
                amounts = view.getAndParseCsvLine("Enter the split amounts, or just return to accept displayed amounts:",
                        budgetItemsForMerchant.size(), true, true);

            } else { // the amounts are not pre-established, so ask the user to enter them:
                amounts = view.getAndParseCsvLine("Enter the split amounts (or a - add, i - inquire, s - skip):  ",
                        0, false, true);
            }

            // Create the splits.  Process any user requests to edit the assigned budget items at the same time:
            // Add a new budget item to the current Merchant:
            if (amounts[0].equalsIgnoreCase("a")) {
                try {
                    assignBudgetItemsToMerchant(merchant, budgetItemsForMerchant);
                    done = false;
                } catch (SkipException se) {
                    view.say("Skipping this transaction.");
                    terminationCondition = SKIP;
                }
            }
            // Delete one of the displayed budget items from the merchant for this transaction:
            else if (amounts[0].equalsIgnoreCase("d")) {
                view.say("The delete budget item from merchant function has not been implemented yet.");
                done = false;

                // Send an inquiry to someone as to how to categorize this transaction:
            } else if (amounts[0].equalsIgnoreCase("i")) {
                if (inquireAllowed) {
                    view.say("Sending an inquiry.");
                    terminationCondition = INQUIRE;
                } else {
                    view.say("Inquiry function not allowed at this time.");
                    done = false;
                }

                // Skip this transaction for now:
            } else if (amounts[0].equalsIgnoreCase("s")) {
                if (skipAllowed) {
                    view.say("Skipping this transaction.");
                    terminationCondition = SKIP;
                } else {
                    view.say("Skip not allowed at this time.");
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
                        view.say("The amount " + amounts[i] + " does not start with a number followed by " +
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
                        view.say("The payee number " + itemNumberString + "is not a valid number from the " +
                                "list.  " + nfe.getMessage() + "  Please re-enter the values");
                        done = false;
                        break;
                    }

                    // If the user specified payee number is not in the list of payees:
                    if (itemNumber <= 0 || itemNumber > budgetItemsForMerchant.size()) {

                        // Then inform the user and ask them to re-enter the values:
                        view.say("The payee number " + itemNumberString + "is not in the list.  Please " +
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
                            view.say("The item amount " + itemAmountString + "is not a valid dollar amount.  " +
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
                    view.say("The total of the list of splits entered (" + totalSplitsAmount + ") does not " +
                            "equal the amount of the transaction (" + transaction.getAmount() + ").    Please re-enter the " +
                            "values");
                    splits.clear();
                    done = false;
                    continue;
                }

                // else if the response is a single use category:
            } else if (amounts[0].matches("[a-zA-Z][a-zA-Z0-9 '()-\\+]+")) {
                view.say("The allocate, but don't add, function has not been implemented yet.");
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

                    double enteredAmount = (useEnteredAmounts) ?
                            view.getDouble(amounts[i], "Must be a dollar amount.") : 0;

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

    /**
     * {@inheritDoc}
     */
    public BudgetItem getUserSelectedBudgetItem(List<BudgetItem> budgetItems) throws Exception {
        if (budgetItems.isEmpty()) {
            return null;
        }

        // Create a list of budget item names:
        List<String> displayableBudgetItemsList = generateDisplayableBudgetItemList(budgetItems);

        // Ask the user to select one of the budget items:
        int index = view.selectFromNumberedList("Multiple budget items found.  Please select one:",
                displayableBudgetItemsList, false);

        // Return the selected budget item:
        return budgetItems.get(index);
    }

    // Show a list of the assigned budget items for a transaction, and the amount of the transaction:
    public void showAssignedBudgetItems(List<BudgetItemMerchant> budgetItems, double amount) {

        view.say("The assigned budget items and amounts (if specified) for this merchant are:");
        int i = 1;
        for (BudgetItemMerchant budgetItem : budgetItems
        ) {
            String lineEnd = "";
            if (budgetItem.getAmount() > 0) {
                lineEnd = ", " + formatDollarAmount(budgetItem.getBudgetItem().getAmount()) + ", 0";
            } else {
                if (budgetItem.getPercentage() > 0) {
                    lineEnd = ", 0, " + budgetItem.getPercentage() + "%";
                }
            }
            view.say("   " + i++ + ".  " + budgetItem.getBudgetItem().getPayee() + lineEnd);
        }
    }

    /**
     * Displays a list of budget items and amounts (if specified) for a given merchant.
     *
     * @param budgetItemMerchants the list of assigned budget items for the merchant
     * @param amount              the amount of the transaction
     * @throws Exception if an error occurs during the display process
     */
    public void showBudgetItemsForMerchant(List<BudgetItemMerchant> budgetItemMerchants, double amount) throws Exception {
        view.say("The assigned budget items and amounts (if specified) for this merchant are:");
        int i = 1;
        for (BudgetItemMerchant budgetItemMerchant : budgetItemMerchants) {
            String line = "   " + i++ + ".  ";
            line += budgetItemMerchant.getBudgetItem().getDisplayString();  // Using the new method
            if (budgetItemMerchant.getAmount() > 0) {
                line += ", " + Utility.formatDollarAmount(budgetItemMerchant.getAmount()) + ", 0";
            } else {
                if (budgetItemMerchant.getPercentage() > 0) {
                    line += ", 0, " + budgetItemMerchant.getPercentage() + "%";
                }
            }
            view.say(line);
        }
    }
}
