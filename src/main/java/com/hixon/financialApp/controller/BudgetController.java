package com.hixon.financialApp.controller;

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
import lombok.Getter;
import lombok.Setter;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.hixon.financialApp.controller.ImportController.TerminationCondition.QUIT;
import static com.hixon.financialApp.model.budget.BudgetItemMerchant.isBudgetItemInList;
import static com.hixon.financialApp.utility.Utility.formatDollarAmount;
import static com.hixon.financialApp.utility.Utility.stringDateDashToCalendarDate;
import static java.util.Calendar.YEAR;

/**
 * This class is the controller for budget related business logic interface.
 */
@Getter
@Setter
public class BudgetController {

    /*
     * Fields for BudgetController:
     */
    private final ImportController.TerminationCondition terminationCondition;
    Register register;
    Budget budget;
    Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;


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
     * Allows the user to manage budget items interactively.
     * Presents a menu to add, update, delete, find, select, or quit.
     * - Add: Prompts for a new budget item and saves it if valid.
     * - Find: Searches for budget items by payee or category and displays results.
     * - Select: Allows the user to select a budget item from all available items.
     * - Update/Delete: Requires a selected item; prompts for new data or deletes the item.
     * - Quit: Exits the management loop.
     *
     * @throws Exception if any error occurs during management operations
     */
    public void manageBudgetItems() throws Exception {
        BudgetItem selectedBudgetItem = null;
        boolean done = false;
        while (!done) {
            view.say();
            String prompt = "What would you like to do (a-add, d-delete, u-update, f-find, s-select, q-quit)?";
            String option = view.selectFromFirstLetterList(prompt, "a,d,u,f,s,q");
            switch (option) {
                case "a":
                    BudgetItem newItem = getBudgetItemFromUser();
                    if (newItem != null && newItem.isValid()) {
                        newItem.save(EntityInt.SaveMethod.INSERT);
                        view.say("Budget item added.");
                    } else {
                        view.say("Budget item entered by user is invalid.");
                    }
                    selectedBudgetItem = null;
                    break;
                case "f":
                    String criteria = view.getResponseString("Enter search criteria (payee or category): ");
                    List<BudgetItem> foundItems = findBudgetItems(criteria);
                    if (foundItems.isEmpty()) {
                        view.say("No budget items found matching: " + criteria);
                    } else {
                        List<String> displayList = generateDisplayableBudgetItemList(foundItems);
                        view.say("Found budget items:");
                        for (String line : displayList) {
                            view.say(line);
                        }
                    }
                    break;
                case "s":
                    List<BudgetItem> allItems = BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(budget);
                    if (allItems.isEmpty()) {
                        view.say("No budget items available to select.");
                        selectedBudgetItem = null;
                    } else {
                        selectedBudgetItem = getUserSelectedBudgetItem(allItems);
                        if (selectedBudgetItem != null) {
                            view.say("Selected budget item: " + selectedBudgetItem.getDisplayString());
                        }
                    }
                    break;
                case "u":
                    if (selectedBudgetItem == null) {
                        view.say("No budget item selected. Use 's-select' first.");
                    } else {
                        BudgetItem updatedItem = getBudgetItemFromUser();
                        if (updatedItem != null && updatedItem.isValid()) {
                            updatedItem.update();
                            view.say("Budget item updated.");
                        } else {
                            view.say("Budget item entered by user is invalid.");
                        }
                        selectedBudgetItem = null;
                    }
                    break;
                case "d":
                    if (selectedBudgetItem == null) {
                        view.say("No budget item selected. Use 's-select' first.");
                    } else {
                        if (selectedBudgetItem.isValid()) {
                            selectedBudgetItem.delete();
                            view.say("Budget item deleted.");
                        } else {
                            view.say("Budget item entered by user is invalid.");
                        }
                        selectedBudgetItem = null;
                    }
                    break;
                case "q":
                    done = true;
                    break;
                default:
                    throw new InvalidEntryException("selectFromFirstLetterList returned an option that wasn't in the option list.");
            }
        }
    }


    /**
     * Retrieves a budget item from the database using full text search.
     * If no matching item is found, allows the user to create a new budget item.
     * If the selected item is new, prompts the user to fill out its fields and saves it.
     *
     * @param seedName a string to start the full text search with, or null
     * @return the selected or newly created BudgetItem
     * @throws SQLException    if a database error occurs
     * @throws EntityException if an entity error occurs
     * @throws QuitException   if the user chooses to quit
     * @throws SkipException   if the user chooses to skip
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

    /**
     * Assigns transaction amounts to budget items for a merchant.
     * Handles both fixed amounts and percentages, and prompts the user for manual splits if needed.
     * Ensures splits balance with the transaction amount.
     *
     * @param transaction         the Transaction to split
     * @param merchant            the Merchant associated with the transaction
     * @param budget              the Budget context
     * @param budgetItemMerchants the list of BudgetItemMerchant associations
     * @return a list of TransactionSplit objects representing the splits, or null if none
     * @throws Exception if an error occurs
     */
    public List<TransactionSplit> assignAmountsToBudgetItems(Transaction transaction, Merchant merchant, Budget
            budget, List<BudgetItemMerchant> budgetItemMerchants)
            throws Exception {

        // If we need to ask the user to enter the splits:
        List<TransactionSplit> splits = new ArrayList<>();
        if (
                merchant.isAskAlways() || // If this is a merchant that the user wants to be asked about every time,
                        (
                                // or there is more than one budget item:
                                (budgetItemMerchants.size() > 1) &&
                                        // and they are not fixed amounts:
                                        ((budgetItemMerchants.get(0).getAmount() == 0) && (budgetItemMerchants.get(0).getPercentage() == 0)))
        ) {
            // then ask the user to enter the splits:
            TransactionSplitsController transactionSplitsController = new TransactionSplitsController(register, budget,
                    forecast, view, notificationService);
            transactionSplitsController.getSplits(transaction, splits, merchant, budget, budgetItemMerchants, true, true);
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
                TransactionSplitsController transactionSplitsController = new TransactionSplitsController(register, budget, forecast, view, notificationService);
                transactionSplitsController.getSplits(transaction, splits, merchant, budget, budgetItemMerchants, true, true);
            }
        }
        return (splits.isEmpty()) ? null : splits;
    }

    /**
     * Generates a displayable list of budget item descriptions for the user interface.
     * Each entry includes payee, category, amount, period, date, and memo if present.
     *
     * @param budgetItems the list of BudgetItem objects to display
     * @return a list of formatted strings for display
     * @throws Exception if an error occurs during formatting
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

    /**
     * Prompts the user for each budget item field individually, with input validation and defaults.
     * Returns a BudgetItem object filled out with the user's responses, or null if cancelled.
     *
     * @return a BudgetItem object filled out with the user's responses, or null if cancelled
     * @throws BudgetException if a budget error occurs
     * @throws SQLException    if a database error occurs
     * @throws EntityException if an entity error occurs
     * @throws ParseException  if a date parsing error occurs
     * @throws CancelException if the user cancels
     * @throws QuitException   if the user quits
     * @throws SkipException   if the user skips
     */
    public BudgetItem getBudgetItemFromUser() throws BudgetException, SQLException, EntityException, ParseException,
            CancelException, QuitException, SkipException {

        // Alow the user to cancel this operation at any time:
        try {
            String defaultMemo = "";
            String defaultPeriodType = "MONTHLY";
            double defaultAmount = 0.0;
            double defaultRunningBalance = 0.0;
            double defaultMinimumBalance = 0.0;
            String defaultStartDate = Utility.calendarDateToStringDate(Calendar.getInstance());
            int defaultNumberOfPayments = 0;
            String defaultEndDate = "";
            String defaultItemType = "EXPENSE";
            String defaultHowImportant = "NORMAL";
            String defaultHowOccurs = "RECURRING";
            String defaultHowPaid = "CASH";
            String defaultBudgetName = budget != null ? budget.getName() : "DefaultBudget";

            // Get the category.  There is no default category, but if the user just hits <enter>, then tell them they must
            // enter a category:
            String category = view.getResponseString("Category: ", ViewInt.DO_NOT_ALLOW_NONE, ViewInt.ALLOW_CANCEL,
                    ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
            boolean done = false;
            while (!done) {
                if (Item.isValidCategory(category)) {
                    done = true;
                } else {
                    view.say("Invalid category. Please enter a valid category.");
                    category = view.getResponseString("Category: ", ViewInt.DO_NOT_ALLOW_NONE, ViewInt.ALLOW_CANCEL,
                            ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                }
            }

            // Get the payee in the same way as category:
            String payee = view.getResponseString("Payee: ", ViewInt.DO_NOT_ALLOW_NONE, ViewInt.ALLOW_CANCEL,
                    ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
            boolean payeeDone = false;
            while (!payeeDone) {
                if (!payee.isEmpty()) {
                    payeeDone = true;
                } else {
                    view.say("Invalid payee. Please enter a valid payee.");
                    payee = view.getResponseString("Payee: ", ViewInt.DO_NOT_ALLOW_NONE, ViewInt.ALLOW_CANCEL,
                            ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                }
            }

            // Get the memo in the same way as payee, but allow none:
            String memo = view.getResponseString("Memo: ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL,
                    ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();

            // Enum validation for Period Type
            String periodType = defaultPeriodType;
            while (true) {
                String input = view.getResponseString(STR."Period Type [{defaultPeriodType}] ({java.util.Arrays.toString(Item.PeriodType.values())}): ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                if (input.isEmpty()) break;
                try {
                    Item.PeriodType.valueOf(input.toUpperCase());
                    periodType = input.toUpperCase();
                    break;
                } catch (IllegalArgumentException e) {
                    view.say(STR."Invalid period type. Allowed: {java.util.Arrays.toString(Item.PeriodType.values())}");
                }
            }

            // Numeric validation for Amount
            double amount = defaultAmount;
            while (true) {
                String input = view.getResponseString(STR."Amount [{defaultAmount}]: ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                if (input.isEmpty()) break;
                try {
                    amount = Double.parseDouble(input);
                    break;
                } catch (NumberFormatException e) {
                    view.say("Invalid amount. Please enter a valid number.");
                }
            }

            // Numeric validation for Running Balance
            double runningBalance = defaultRunningBalance;
            while (true) {
                String input = view.getResponseString(STR."Running Balance [{defaultRunningBalance}]: ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                if (input.isEmpty()) break;
                try {
                    runningBalance = Double.parseDouble(input);
                    break;
                } catch (NumberFormatException e) {
                    view.say("Invalid running balance. Please enter a valid number.");
                }
            }

            // Numeric validation for Minimum Balance
            double minimumBalance = defaultMinimumBalance;
            while (true) {
                String input = view.getResponseString(STR."Minimum Balance [{defaultMinimumBalance}]: ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                if (input.isEmpty()) break;
                try {
                    minimumBalance = Double.parseDouble(input);
                    break;
                } catch (NumberFormatException e) {
                    view.say("Invalid minimum balance. Please enter a valid number.");
                }
            }

            // Date validation for Start Date
            String startDate = defaultStartDate;
            while (true) {
                String input = view.getResponseString(STR."Start Date [{defaultStartDate}]: ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                if (input.isEmpty()) break;
                try {
                    Utility.stringDateDashToCalendarDate(input);
                    startDate = input;
                    break;
                } catch (ParseException e) {
                    view.say("Invalid date format. Please use yyyy-MM-dd.");
                }
            }

            // Numeric validation for Number of Payments
            int numberOfPayments = defaultNumberOfPayments;
            while (true) {
                String input = view.getResponseString(STR."Number of Payments [{defaultNumberOfPayments}]: ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                if (input.isEmpty()) break;
                try {
                    numberOfPayments = Integer.parseInt(input);
                    break;
                } catch (NumberFormatException e) {
                    view.say("Invalid number. Please enter a valid integer.");
                }
            }

            // Date validation for End Date
            String endDate = defaultEndDate;
            while (true) {
                String input = view.getResponseString(STR."End Date [{defaultEndDate}]: ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                if (input.isEmpty()) break;
                try {
                    if (!input.isEmpty()) Utility.stringDateDashToCalendarDate(input);
                    endDate = input;
                    break;
                } catch (ParseException e) {
                    view.say("Invalid date format. Please use yyyy-MM-dd.");
                }
            }

            // Enum validation for Item Type
            String itemType = defaultItemType;
            while (true) {
                String input = view.getResponseString(STR."Item Type [{defaultItemType}] ({java.util.Arrays.toString(Item.ItemType.values())}): ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                if (input.isEmpty()) break;
                try {
                    Item.ItemType.valueOf(input.toUpperCase());
                    itemType = input.toUpperCase();
                    break;
                } catch (IllegalArgumentException e) {
                    view.say(STR."Invalid item type. Allowed: {java.util.Arrays.toString(Item.ItemType.values())}");
                }
            }

            // Enum validation for How Important
            String howImportant = defaultHowImportant;
            while (true) {
                String input = view.getResponseString(STR."How Important [{defaultHowImportant}] ({java.util.Arrays.toString(Item.HowImportant.values())}): ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                if (input.isEmpty()) break;
                try {
                    Item.HowImportant.valueOf(input.toUpperCase());
                    howImportant = input.toUpperCase();
                    break;
                } catch (IllegalArgumentException e) {
                    view.say(STR."Invalid value. Allowed: {java.util.Arrays.toString(Item.HowImportant.values())}");
                }
            }

            // Enum validation for How Occurs
            String howOccurs = defaultHowOccurs;
            while (true) {
                String input = view.getResponseString(STR."How Occurs [{defaultHowOccurs}] ({java.util.Arrays.toString(Item.HowOccurs.values())}): ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                if (input.isEmpty()) break;
                try {
                    Item.HowOccurs.valueOf(input.toUpperCase());
                    howOccurs = input.toUpperCase();
                    break;
                } catch (IllegalArgumentException e) {
                    view.say(STR."Invalid value. Allowed: {java.util.Arrays.toString(Item.HowOccurs.values())}");
                }
            }

            // Enum validation for How Paid
            String howPaid = defaultHowPaid;
            while (true) {
                String input = view.getResponseString(STR."How Paid [{defaultHowPaid}] ({java.util.Arrays.toString(Item.HowPaid.values())}): ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
                if (input.isEmpty()) break;
                try {
                    Item.HowPaid.valueOf(input.toUpperCase());
                } catch (IllegalArgumentException e) {
                    view.say(STR."Invalid value. Allowed: {java.util.Arrays.toString(Item.HowPaid.values())}");
                }
            }

            String budgetName = view.getResponseString(STR."Budget Name [{defaultBudgetName}]: ", ViewInt.ALLOW_NONE, ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP).trim();
            if (budgetName.isEmpty()) budgetName = defaultBudgetName;

            // Create BudgetItem
            BudgetItem budgetItem = new BudgetItem(budget, payee);
            budgetItem.setCategory(category);
            budgetItem.setMemo(memo);
            budgetItem.setPeriod(Item.PeriodType.valueOf(periodType));
            budgetItem.setAmount(amount);
            budgetItem.setRunningBalance(runningBalance);
            budgetItem.setMinimumBalance(minimumBalance);
            budgetItem.setStartDate(Utility.stringDateDashToCalendarDate(startDate));
            budgetItem.setNumberOfPayments(numberOfPayments);
            if (!endDate.isEmpty()) {
                budgetItem.setEndDate(Utility.stringDateDashToCalendarDate(endDate));
            }
            budgetItem.setItemType(Item.ItemType.valueOf(itemType));
            budgetItem.setHowImportant(Item.HowImportant.valueOf(howImportant));
            budgetItem.setHowOccurs(Item.HowOccurs.valueOf(howOccurs));
            budgetItem.setHowPaid(Item.HowPaid.valueOf(howPaid));
            budgetItem.setIdBudget(budget.getId());
            return budgetItem;
        } catch (CancelException e) {

            // If the user canceled, then return null:
            return null;
        }
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
     * Prompts the user to select a budget item from a list.
     * Displays the list and returns the selected item, or null if the list is empty.
     *
     * @param budgetItems the list of BudgetItem objects to select from
     * @return the selected BudgetItem, or null if none
     * @throws Exception if an error occurs during selection
     */
    public BudgetItem getUserSelectedBudgetItem(List<BudgetItem> budgetItems) throws Exception {

        BudgetItem selectedBudgetItem = null;
        // If there is only one budget item, then return it:
        if (budgetItems.size() == 1) {
            selectedBudgetItem = budgetItems.get(0);
        } else {

            // Show a list of the budget items and ask the user to select one:
            List<String> displayableBudgetItemsList = generateDisplayableBudgetItemList(budgetItems);
            int index = view.selectFromNumberedList("Multiple budget items found.  Please select one:",
                    displayableBudgetItemsList, false);
            selectedBudgetItem = budgetItems.get(index);
        }
        // Ask the user to select one of the budget items:
        return selectedBudgetItem;
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
     * @param relevancyScores
     * @throws Exception if an error occurs during the display process
     */
    public void showBudgetItemsForMerchant(List<BudgetItemMerchant> budgetItemMerchants, List<Double> relevancyScores,
                                           double amount) throws Exception {
        view.say("The assigned budget items and amounts (if specified) for this merchant are:");
        int i = 1;
        for (BudgetItemMerchant budgetItemMerchant : budgetItemMerchants) {
            String line = "   " + i + ".  ";
            line += budgetItemMerchant.getBudgetItem().getDisplayString();  // Using the new method
            if (budgetItemMerchant.getAmount() > 0) {
                line += ", " + Utility.formatDollarAmount(budgetItemMerchant.getAmount()) + ", 0";
            } else {
                if (budgetItemMerchant.getPercentage() > 0) {
                    line += ", 0, " + budgetItemMerchant.getPercentage() + "%";
                }
            }

            // If relevancy scores are provided, append them to the line.  If the user added a budget item to the list,
            // then there won't be a relevancy score for it, so we check if the index is valid:
            // Note: The relevancyScores list is expected to be one less than the budgetItemMerchants list
            if (relevancyScores != null && (i - 1) < relevancyScores.size() && relevancyScores.get(i - 1) != null) {
                line += ", Relevancy Score: " + relevancyScores.get(i - 1);
            }

            view.say(line);
            i++;
        }
    }

    /**
     * Renews expired budget items by prompting the user to select one if multiple are found.
     * If a budget item is selected, it is un-expired.
     *
     * @param expiredBudgetItemMerchants the list of expired BudgetItemMerchant objects
     * @throws Exception if an error occurs during the renewal process
     */
    public void renewBudgetItems(List<BudgetItemMerchant> expiredBudgetItemMerchants) throws Exception {

        // If there are more than one expired budget items:
        BudgetItem budgetItem = null;
        if (expiredBudgetItemMerchants.size() > 1) {

            // then create a list of the expired budget items:
            List<BudgetItem> expiredBudgetItems = new ArrayList<>();
            for (BudgetItemMerchant budgetItemMerchant : expiredBudgetItemMerchants) {
                expiredBudgetItems.add(budgetItemMerchant.getBudgetItem());
            }

            // and ask the user to select one:
            view.say("Multiple expired budget items found.  Please select one:");
            budgetItem = getUserSelectedBudgetItem(expiredBudgetItems);
        }

        // and if they did select one:
        if (budgetItem != null) {

            // Then un-expire it:
            budgetItem.renew();
        }
    }

    /**
     * Finds budget items for the current budget that match the given criteria (payee or category).
     * @param criteria The search string to match against payee or category.
     * @return List of matching BudgetItem objects.
     */
    private List<BudgetItem> findBudgetItems(String criteria) {
        List<BudgetItem> result = new ArrayList<>();
        try {
            List<BudgetItem> allItems = BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(budget);
            String lowerCriteria = criteria == null ? "" : criteria.trim().toLowerCase();
            for (BudgetItem item : allItems) {
                String payee = item.getPayee() != null ? item.getPayee().toLowerCase() : "";
                String category = item.getCategory() != null ? item.getCategory().toLowerCase() : "";
                if (payee.contains(lowerCriteria) || category.contains(lowerCriteria)) {
                    result.add(item);
                }
            }
        } catch (Exception e) {
            view.say("Error finding budget items: " + e.getMessage());
        }
        return result;
    }

}
