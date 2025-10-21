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

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;

import static com.hixon.financialApp.controller.ImportController.TerminationCondition.QUIT;
import static com.hixon.financialApp.model.budget.BudgetItemMerchant.isBudgetItemInList;
import static com.hixon.financialApp.utility.Utility.stringDateDashToCalendarDate;
import static com.hixon.financialApp.view.base.ViewInt.*;
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

    // Help text properties loaded from file
    private static final Properties helpText = new Properties();

    static {
        try (InputStream input = BudgetController.class.getClassLoader()
                .getResourceAsStream("help-text.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find help-text.properties");
            }
            helpText.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load help text properties", ex);
        }
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
     * Allows the user to manage budget items interactively.
     * Presents a menu to add, copy, update, delete, find, select, or quit.
     * - Add: Prompts for a new budget item and saves it if valid.
     * - Copy: Copies an existing budget item and allows editing before saving.
     * - Find: Searches for budget items by payee or category and displays results.
     * - Select: Allows the user to select a budget item from all available items.
     * - Update/Delete: Requires a selected item; prompts for new data or deletes the item.
     *
     * @throws Exception if any error occurs during management operations
     * @throws QuitException if the user chooses to quit
     */
    public void manageBudgetItems() throws Exception, QuitException {
        BudgetItem selectedBudgetItem = null;
        boolean done = false;
        while (!done) {
            String prompt = "What would you like to do?";
            try {
                String option = view.selectFromMenu(prompt, List.of("add", "replicate", "delete",
                    "update", "find", "select"), DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT,
                        DO_NOT_ALLOW_SKIP);
                switch (option) {
                    case "a":
                        BudgetItem newItem = getBudgetItemFromUser();
                        if (newItem != null && newItem.isValid()) {
                            BudgetItem confirmedItem = confirmBudgetItem(newItem, "created");
                            if (confirmedItem != null) {
                                confirmedItem.save(EntityInt.SaveMethod.INSERT);
                                view.say("Budget item successfully added.");
                            }
                        } else if (newItem != null) {
                            view.say("Budget item entered by user is invalid.");
                        }
                        selectedBudgetItem = null;
                        break;
                    case "r":
                        // First select which budget to work with
                        Budget selectedBudgetForReplication = selectBudgetFromUser(null);



                        // Get all unexpired budget items for the selected budget
                        List<BudgetItem> allItems = BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(selectedBudgetForReplication);
                        if (allItems.isEmpty()) {
                            view.say("No budget items available to replicate in budget: " + selectedBudgetForReplication.getName());
                        } else {
                            BudgetItem template = getUserSelectedBudgetItem(allItems);
                            if (template != null) {
                                BudgetItem copiedItem = getBudgetItemFromUser(template);
                                if (copiedItem != null && copiedItem.isValid()) {
                                    BudgetItem confirmedItem = confirmBudgetItem(copiedItem, "replicated");
                                    if (confirmedItem != null) {
                                        confirmedItem.save(EntityInt.SaveMethod.INSERT);
                                        view.say("Budget item successfully replicated and added.");
                                    }
                                } else if (copiedItem != null) {
                                    view.say("Budget item entered by user is invalid.");
                                }
                            }
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
                        List<BudgetItem> availableItems = BudgetItemUtilities.getAllUnexpiredBudgetItemsForBudget(budget);
                        if (availableItems.isEmpty()) {
                            view.say("No budget items available to select.");
                            selectedBudgetItem = null;
                        } else {
                            selectedBudgetItem = getUserSelectedBudgetItem(availableItems);
                            if (selectedBudgetItem != null) {
                                view.say("Selected budget item: " + selectedBudgetItem.getDisplayString());
                            }
                        }
                        break;
                    case "u":
                        if (selectedBudgetItem == null) {
                            view.say("No budget item selected. Use 's-select' first.");
                        } else {
                            BudgetItem updatedItem = getBudgetItemFromUser(selectedBudgetItem);
                            if (updatedItem != null && updatedItem.isValid()) {
                                BudgetItem confirmedItem = confirmBudgetItem(updatedItem, "updated");
                                if (confirmedItem != null) {
                                    confirmedItem.setId(selectedBudgetItem.getId()); // Preserve the original ID
                                    confirmedItem.update();
                                    view.say("Budget item successfully updated.");
                                }
                            } else if (updatedItem != null) {
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
                    default:
                        throw new InvalidEntryException("selectFromFirstLetterList returned an option that wasn't in the option list.");
                }
            } catch (CancelException e) {
                done = true;
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
                DO_NOT_ALLOW_NONE,
                ViewInt.ALLOW_CREATE,
                ALLOW_CANCEL,
                ALLOW_QUIT,
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
        return getBudgetItemFromUser(null);
    }

    /**
     * Prompts the user to enter or edit details for a budget item, optionally using a template as defaults.
     * This method handles both creating new budget items and updating existing ones by accepting a template
     * whose values are used as defaults for all fields.
     *
     * <p>Budget Assignment: This method allows the user to assign the budget item to any available budget,
     * not just the currently selected one. This supports use cases such as:
     * <ul>
     *   <li>Moving a budget item from one budget to another (e.g., personal to business)</li>
     *   <li>Creating budget items for different budgets without switching context</li>
     *   <li>Copying budget items across budgets</li>
     * </ul>
     * If only one budget exists, it is automatically selected. If multiple budgets exist, the user is prompted
     * to choose, with the current budget as the default.</p>
     *
     * @param template An optional BudgetItem to use as defaults for all fields (null for no defaults)
     * @return A BudgetItem object populated with user input, or null if the user cancels
     * @throws BudgetException If there's an error with budget-related operations
     * @throws SQLException If there's a database error
     * @throws EntityException If there's an entity validation error
     * @throws ParseException If date parsing fails
     * @throws CancelException If the user cancels the operation
     * @throws QuitException If the user quits the operation
     * @throws SkipException If the user skips the operation
     */
    public BudgetItem getBudgetItemFromUser(BudgetItem template) throws BudgetException, SQLException, EntityException, ParseException,
            CancelException, QuitException, SkipException {
        try {
            // Let the user know what we are going to do:
            view.sayH1("Budget Item Entry");
            view.say("Please enter the details for the budget item.  You can cancel or quit at any time by entering " +
                    "'c' or 'q'.");
            view.say("Press <enter> to accept the default value shown in brackets [].");

            view.sayH2("Budget Assignment");

            // Budget selection from numbered list
            // Note: This allows cross-budget operations such as moving items between budgets
            // or creating items for a different budget without switching the main context
            Budget selectedBudget = selectBudgetFromUser(template);

            view.sayH2("Basic Information");

            // Get the category, and validate that it is a valid category:
            String category = view.getResponseString("Category", template != null ? template.getCategory() :
                    null, DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                    () -> helpText.getProperty("budgetitem.category")).trim();

            // Get the payee:
            String defaultPayee = template != null ? template.getPayee() : "";
            String payee = view.getResponseString("Payee", defaultPayee, DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                    ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                    () -> helpText.getProperty("budgetitem.payee"));

            // Get the memo but allow none:
            String defaultMemo = template != null ? template.getMemo() : "";
            String memo = view.getResponseString("Memo", defaultMemo, ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                    ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                    () -> helpText.getProperty("budgetitem.memo"));

            view.sayH2("Schedule and Amount");

            // Get the period type, and validate that it is a valid period type:
            Item.PeriodType defaultPeriodTypeEnum = template != null ? template.getPeriod() : Item.PeriodType.MONTHLY;
            Item.PeriodType selectedPeriodType = view.selectByPositionFromList("Select period type:",
                    defaultPeriodTypeEnum, Item.PeriodType.class, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT,
                    DO_NOT_ALLOW_SKIP);

            // Get the Amount
            Double defaultAmount = template != null ? template.getAmount() : null;
            double amount = view.getResponseCurrency("Amount", defaultAmount, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                    DO_NOT_ALLOW_NONE, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                    () -> helpText.getProperty("budgetitem.amount"));

            view.sayH3("Balance Information");

            // Get the Running Balance
            double defaultRunningBalanceValue = template != null ? template.getRunningBalance() : 0.0;
            double runningBalance = view.getResponseCurrency("Running Balance", defaultRunningBalanceValue,
                    DO_NOT_SHOW_CANCEL_QUIT_SKIP, DO_NOT_ALLOW_NONE, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                    () -> helpText.getProperty("budgetitem.runningbalance"));

            // Get the Minimum Balance
            double defaultMinimumBalanceValue = template != null ? template.getMinimumBalance() : 0.0;
            double minimumBalance = view.getResponseCurrency("Minimum Balance", defaultMinimumBalanceValue,
                    DO_NOT_SHOW_CANCEL_QUIT_SKIP, DO_NOT_ALLOW_NONE, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                    () -> helpText.getProperty("budgetitem.minimumbalance"));

            view.sayH3("Date Range");

            // Get the Start Date
            String defaultStartDateValue = template != null ? Utility.calendarDateToStringDate(template.getStartDate()) :
                    Utility.calendarDateToStringDate(Calendar.getInstance());
            String startDate = view.getResponseString("Start Date (yyyy-MM-dd)", defaultStartDateValue,
                    DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                    () -> helpText.getProperty("budgetitem.startdate"));

            // Get the Number of Payments
            Integer defaultNumberOfPaymentsValue = template != null ? template.getNumberOfPayments() : 0;
            int numberOfPayments = view.getResponseNatural("Number of Payments", defaultNumberOfPaymentsValue,
                    DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL,
                    ALLOW_QUIT, DO_NOT_ALLOW_SKIP, () -> helpText.getProperty("budgetitem.numberofpayments"));

            // Get the End Date
            String defaultEndDateValue = null;
            if (template != null) {
                defaultEndDateValue =
                        template.getEndDate() != null ? Utility.calendarDateToStringDate(template.getEndDate()) : null;
            }
            String endDate = view.getResponseString("End Date (yyyy-MM-dd)", defaultEndDateValue,
                    ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                    () -> helpText.getProperty("budgetitem.enddate"));

            view.sayH2("Classification");

            // Enum selection for Item Type
            Item.ItemType defaultItemType = template != null ? template.getItemType() : Item.ItemType.EXPENSE;
            Item.ItemType itemType = view.selectByPositionFromList("Select Item Type:", defaultItemType,
                    Item.ItemType.class, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

            // Enum selection for How Important
            Item.HowImportant defaultHowImportant = template != null ? template.getHowImportant() :
                    Item.HowImportant.DISCRETIONARY_NONESSENTIAL;
            Item.HowImportant howImportant = view.selectByPositionFromList("Select How Important:", defaultHowImportant,
                    Item.HowImportant.class, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

            // Enum selection for How Occurs
            Item.HowOccurs defaultHowOccurs = template != null ? template.getHowOccurs() : Item.HowOccurs.PERIODIC;
            Item.HowOccurs howOccurs = view.selectByPositionFromList("Select How Occurs:", defaultHowOccurs, Item.HowOccurs.class,
                    DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

            // Enum selection for How Paid
            Item.HowPaid defaultHowPaid = template != null ? template.getHowPaid() : Item.HowPaid.DEBIT_CARD;
            Item.HowPaid howPaid = view.selectByPositionFromList("Select How Paid:", defaultHowPaid, Item.HowPaid.class,
                    DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

            // Create BudgetItem
            BudgetItem budgetItem = new BudgetItem(selectedBudget, payee);
            budgetItem.setId(UUID.randomUUID());
            budgetItem.setCategory(category);
            budgetItem.setMemo(memo);
            budgetItem.setPeriod(selectedPeriodType);
            budgetItem.setAmount(amount);
            budgetItem.setRunningBalance(runningBalance);
            budgetItem.setMinimumBalance(minimumBalance);
            budgetItem.setStartDate(Utility.stringDateDashToCalendarDate(startDate));
            budgetItem.setNumberOfPayments(numberOfPayments);
            if (endDate != null && !endDate.isEmpty()) {
                budgetItem.setEndDate(Utility.stringDateDashToCalendarDate(endDate));
            }
            budgetItem.setItemType(itemType);
            budgetItem.setHowImportant(howImportant);
            budgetItem.setHowOccurs(howOccurs);
            budgetItem.setHowPaid(howPaid);
            budgetItem.setIdBudget(selectedBudget.getId());
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
            String line = view.getResponseString("Just enter for this month, 'l' for last month, or Month number for any other month.");
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
     * Prompts the user to select a budget from all available budgets.
     * If only one budget exists, it is automatically selected.
     * If multiple budgets exist, the user is prompted to choose.
     *
     * @param template An optional BudgetItem to determine the default budget (null for current budget)
     * @return The selected Budget
     * @throws BudgetException If no budgets are available
     * @throws SQLException If there's a database error
     * @throws EntityException If there's an entity error
     * @throws CancelException If the user cancels the operation
     * @throws QuitException If the user quits the operation
     * @throws SkipException If the user skips the operation
     */
    private Budget selectBudgetFromUser(BudgetItem template) throws BudgetException, SQLException, EntityException,
            CancelException, QuitException, SkipException {
        // Default to the template's budget if available, otherwise use the current budget
        Budget defaultBudget = (template != null && template.getIdBudget() != null)
                ? Budget.getById(template.getIdBudget()) : budget;
        Budget selectedBudget = defaultBudget;

        try {
            List<Budget> availableBudgets = BudgetUtilities.getAllBudgets();
            if (availableBudgets.isEmpty()) {
                throw new BudgetException("No budgets available. Please create a budget first.");
            }
            if (availableBudgets.size() > 1) {
                // This supports moving budget items between budgets (e.g., personal to business)
                if (template != null && template.getIdBudget() != null && !template.getIdBudget().equals(budget.getId())) {
                    view.say("Template's budget: " + defaultBudget.getName() + " (will be used as default)");
                }
                selectedBudget = view.selectByNameFromList("Select Budget", availableBudgets, defaultBudget,
                        DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                        null);
            }
        } catch (Exception e) {
            view.say("Error loading budgets, using default budget: " + e.getMessage());
            // selectedBudget is already set to defaultBudget
        }

        return selectedBudget;
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
            int index = view.selectByPositionFromList("Multiple budget items found.  Please select one:",
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
                lineEnd = ", " + Utility.formatDollarAmount(budgetItem.getBudgetItem().getAmount()) + ", 0";
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
     *
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

    /**
     * Displays a budget item to the user for confirmation and provides options to accept, update, or cancel.
     * If the user chooses to update, prompts for new values and repeats the confirmation process.
     *
     * @param budgetItem the BudgetItem to confirm
     * @param operation the operation being performed (e.g., "created", "updated", "copied")
     * @return the final BudgetItem if accepted, or null if cancelled
     * @throws Exception if any error occurs during the confirmation process
     */
    private BudgetItem confirmBudgetItem(BudgetItem budgetItem, String operation) throws Exception {
        while (budgetItem != null) {
            view.say();
            view.say("Budget item " + operation + ":");
            view.say("──────────────────────────────────────");
            displayBudgetItemDetails(budgetItem);
            view.say("──────────────────────────────────────");

            String prompt = "What would you like to do with this budget item?";
            try {
                String choice = view.selectFromMenu(prompt,
                    List.of("accept and save", "update", "cancel"),
                    DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, DO_NOT_ALLOW_CANCEL, DO_NOT_ALLOW_QUIT,
                        DO_NOT_ALLOW_SKIP);

                switch (choice) {
                    case "a":
                        return budgetItem;  // Accept the item
                    case "u":
                        // Update the budget item
                        BudgetItem updatedItem = getBudgetItemFromUser(budgetItem);
                        if (updatedItem != null && updatedItem.isValid()) {
                            budgetItem = updatedItem;
                            // Continue the loop to show confirmation again
                        } else if (updatedItem != null) {
                            view.say("Updated budget item is invalid. Please try again.");
                        } else {
                            // User cancelled the update, return to confirmation
                        }
                        break;
                    case "c":
                        view.say("Budget item " + operation + " cancelled.");
                        return null;  // Cancel the operation
                    default:
                        throw new InvalidEntryException("Unexpected option returned: " + choice);
                }
            } catch (Exception e) {
                view.say("Error during confirmation: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * Displays detailed information about a budget item in a formatted way.
     *
     * @param budgetItem the BudgetItem to display
     * @throws Exception if an error occurs while getting budget item details
     */
    private void displayBudgetItemDetails(BudgetItem budgetItem) throws Exception {
        // Get budget name directly using Budget.getById().getName()
        String budgetName = "(none)";
        if (budgetItem.getIdBudget() != null) {
            try {
                budgetName = Budget.getById(budgetItem.getIdBudget()).getName();
            } catch (Exception e) {
                budgetName = "Unknown Budget";
            }
        }
        view.say("Budget: " + budgetName);
        view.say("Payee: " + (budgetItem.getPayee() != null ? budgetItem.getPayee() : ""));
        view.say("Category: " + (budgetItem.getCategory() != null ? budgetItem.getCategory() : ""));
        view.say("Memo: " + (budgetItem.getMemo() != null && !budgetItem.getMemo().isEmpty() ? budgetItem.getMemo() : "(none)"));
        view.say("Amount: " + Utility.formatDollarAmount(budgetItem.getAmount()));
        view.say("Period: " + (budgetItem.getPeriod() != null ? budgetItem.getPeriod() : ""));
        view.say("Running Balance: " + Utility.formatDollarAmount(budgetItem.getRunningBalance()));
        view.say("Minimum Balance: " + Utility.formatDollarAmount(budgetItem.getMinimumBalance()));
        view.say("Start Date: " + (budgetItem.getStartDate() != null ? Utility.calendarDateToStringDate(budgetItem.getStartDate()) : ""));
        view.say("Number of Payments: " + budgetItem.getNumberOfPayments());
        view.say("End Date: " + (budgetItem.getEndDate() != null ? Utility.calendarDateToStringDate(budgetItem.getEndDate()) : "(none)"));
        view.say("Item Type: " + (budgetItem.getItemType() != null ? budgetItem.getItemType() : ""));
        view.say("How Important: " + (budgetItem.getHowImportant() != null ? budgetItem.getHowImportant() : ""));
        view.say("How Occurs: " + (budgetItem.getHowOccurs() != null ? budgetItem.getHowOccurs() : ""));
        view.say("How Paid: " + (budgetItem.getHowPaid() != null ? budgetItem.getHowPaid() : ""));
    }
}
