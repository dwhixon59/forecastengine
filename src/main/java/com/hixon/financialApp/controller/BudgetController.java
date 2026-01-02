package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.*;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.entity.EntityInt;
import com.hixon.financialApp.model.entity.IndependentEntity;
import com.hixon.financialApp.model.entity.MatchQuery;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastItem;
import com.hixon.financialApp.model.forecast.ForecastItemUtilities;
import com.hixon.financialApp.model.forecast.ForecastTransaction;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.Transaction;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.NumberOrStringResponse;
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
import static com.hixon.financialApp.model.budget.BudgetUtilities.getAllBudgets;
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
    private ImportController.TerminationCondition terminationCondition;
    Register register;
    Budget budget;
    Forecast forecast;
    protected ViewInt view;
    protected NotificationServiceInt notificationService;
    private SessionController sessionController; // Added field for SessionController

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
     * Constructor for BudgetController with SessionController (for accessing user budgets).
     * Used by controllers that need to work across multiple budgets.
     *
     * @param sessionController The session controller for accessing register, budget, and forecast information
     */
    public BudgetController(SessionController sessionController) {
        terminationCondition = QUIT;
        this.sessionController = sessionController;
        this.register = sessionController.getRegister();
        this.budget = sessionController.getBudget();
        this.forecast = sessionController.getForecast();
        this.view = sessionController.getView();
        this.notificationService = sessionController.getNotificationService();
    }


    /**
     * Main methods for BudgetController:
     */

    /**
     * Gets the current termination condition from the last split assignment operation.
     * Used by ImportController to determine how to proceed after assignAmountsToBudgetItems().
     *
     * @return the current termination condition (SKIP, QUIT, CANCEL, etc.)
     */
    public ImportController.TerminationCondition getTerminationCondition() {
        return terminationCondition;
    }

    /**
     * Allows the user to manage budget items interactively using a unified search-based interface.
     * The workflow is:
     * 1. Select which budget to work with
     * 2. Search for a budget item (or create new)
     * 3. Choose what to do with it (view, copy, update, delete, or create new)
     *
     * This approach is more intuitive than the old menu-based system because users naturally
     * think about WHICH item first, then WHAT to do with it.
     *
     * @throws Exception if any error occurs during management operations
     * @throws QuitException if the user chooses to quit
     */
    public void manageBudgetItems() throws Exception, QuitException {
        Budget lastSelectedBudget = null;  // Track the last selected budget across operations
        boolean done = false;

        while (!done) {
            try {
                // Step 1: Select which budget to work with
                Budget selectedBudget = selectBudgetFromUser(lastSelectedBudget);
                lastSelectedBudget = selectedBudget;  // Remember this selection

                // Step 2: Loop for search/add operations within the selected budget
                boolean doneBudget = false;
                while (!doneBudget) {
                    try {
                        // Ask whether to search for existing or create new
                        String choice = view.selectFromMenu("What would you like to do?",
                                List.of("search for existing item", "create new item"),
                                DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                        if (choice.equals("c")) {
                            // User chose to create a new item - go directly to entry form
                            BudgetItem newItem = getBudgetItemFromUser();
                            if (newItem != null && newItem.isValid()) {
                                BudgetItem confirmedItem = confirmBudgetItem(newItem, "created");
                                if (confirmedItem != null) {
                                    confirmedItem.save(EntityInt.SaveMethod.INSERT);
                                    view.say("Budget item successfully added.");

                                    // Ask if user wants to update associated forecasts
                                    updateAssociatedForecasts(selectedBudget);
                                }
                            } else if (newItem != null) {
                                view.say("Budget item entered by user is invalid.");
                            }
                            continue; // Go back to search/add menu
                        }

                        // User chose to search - proceed with search (allow creating new if not found)
                        BudgetItem selectedItem = selectBudgetItemFromBudget(selectedBudget, ALLOW_CREATE);

                        if (selectedItem == null) {
                            // User cancelled the search - return to search/add menu
                            continue;
                        }

                        // Check if this is a newly created item (has no ID yet)
                        boolean isNewItem = selectedItem.getId() == null;

                        if (isNewItem) {
                            // User chose to create a new item from search - go to entry form
                            BudgetItem newItem = getBudgetItemFromUser();
                            if (newItem != null && newItem.isValid()) {
                                BudgetItem confirmedItem = confirmBudgetItem(newItem, "created");
                                if (confirmedItem != null) {
                                    confirmedItem.save(EntityInt.SaveMethod.INSERT);
                                    view.say("Budget item successfully added.");

                                    // Ask if user wants to update associated forecasts
                                    updateAssociatedForecasts(selectedBudget);
                                }
                            } else if (newItem != null) {
                                view.say("Budget item entered by user is invalid.");
                            }
                            continue; // Go back to search/add menu
                        }

                        // User selected an existing item - show action menu
                        {
                            boolean actionComplete = false;
                            while (!actionComplete) {
                                // Display the selected item with full details
                                view.say();
                                displayBudgetItemDetails(selectedItem);

                                // Warn if expired
                                if (selectedItem.isExpired(Calendar.getInstance())) {
                                    view.say("\nNOTE: This budget item has expired.");
                                    view.say("End Date: " + (selectedItem.getEndDate() != null ?
                                            Utility.calendarDateToStringDate(selectedItem.getEndDate()) : "None"));
                                }

                                // Step 3: Ask what to do with this item
                                String action = view.selectFromMenu("What would you like to do with this item?",
                                        List.of("view details", "copy this item", "update this item", "delete this item",
                                                "report spending on this item", "manage merchants", "search again"),
                                        DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                                switch (action) {
                                    case "v":  // view details
                                        view.say();
                                        displayBudgetItemDetails(selectedItem);
                                        break;

                                    case "c":  // copy this item
                                        BudgetItem copiedItem = getBudgetItemFromUser(selectedItem);
                                        if (copiedItem != null && copiedItem.isValid()) {
                                            BudgetItem confirmedItem = confirmBudgetItem(copiedItem, "copied");
                                            if (confirmedItem != null) {
                                                confirmedItem.save(EntityInt.SaveMethod.INSERT);
                                                view.say("Budget item successfully copied and added.");

                                                // Ask if user wants to update associated forecasts
                                                updateAssociatedForecasts(selectedBudget);
                                            }
                                        } else if (copiedItem != null) {
                                            view.say("Budget item entered by user is invalid.");
                                        }
                                        actionComplete = true;  // Go back to search/add menu
                                        break;

                                    case "u":  // update this item
                                        updateBudgetItem(selectedItem);
                                        // Reload the item to show updated values
                                        selectedItem = BudgetItem.getById(selectedItem.getId());

                                        // Ask if user wants to update associated forecasts
                                        if (view.getYesOrNo("Do you want to update associated forecasts?")) {
                                            updateAssociatedForecasts(selectedBudget);
                                        }
                                        // Don't set actionComplete - stay in the action menu to allow more updates
                                        break;

                                    case "d":  // delete this item
                                        view.say("\nYou are about to delete:");
                                        view.say("  " + selectedItem.getDisplayString());

                                        // Check for transaction splits associated with this budget item
                                        int transactionSplitCount = 0;
                                        try {
                                            transactionSplitCount = TransactionSplitUtilities.getTotalItemPastAssociationsCount(selectedItem.getId());
                                        } catch (Exception e) {
                                            view.say("Error checking for transaction splits: " + e.getMessage());
                                        }

                                        boolean proceedWithDelete = true;
                                        if (transactionSplitCount > 0) {
                                            view.say("\nWARNING: This budget item has " + transactionSplitCount +
                                                    " transaction split(s) associated with it.");
                                            view.say("Deleting this budget item will CASCADE DELETE all associated transaction splits,");
                                            view.say("which will remove the categorization from historical transactions in your register.");
                                            view.say("\nSUGGESTION: Consider EXPIRING this budget item instead of deleting it.");
                                            view.say("Expiring preserves historical data while preventing it from appearing in future forecasts.");

                                            proceedWithDelete = view.getYesOrNo("\nDo you still want to DELETE (rather than expire) this budget item?");
                                        }

                                        if (!selectedItem.isExpired(Calendar.getInstance())) {
                                            view.say("\nWARNING: This is an ACTIVE budget item that appears in forecasts.");
                                        }

                                        if (proceedWithDelete && view.getYesOrNo("\nAre you sure you want to delete this budget item?")) {
                                            if (selectedItem.isValid()) {
                                                UUID budgetItemIdToDelete = selectedItem.getId();

                                                // Check for related forecast items BEFORE deleting the budget item
                                                List<ForecastItem> relatedForecastItems = Collections.emptyList();
                                                try {
                                                    relatedForecastItems = ForecastItemUtilities.getAllByBudgetItemId(budgetItemIdToDelete);
                                                } catch (Exception e) {
                                                    view.say("Error checking for related forecast items: " + e.getMessage());
                                                    e.printStackTrace();
                                                }

                                                // Handle related forecast items
                                                Set<UUID> affectedForecastIds = new HashSet<>();
                                                if (!relatedForecastItems.isEmpty()) {
                                                    view.say("\nFound " + relatedForecastItems.size() + " forecast item(s) based on this budget item.");

                                                    if (view.getYesOrNo("Do you want to delete these forecast items?")) {
                                                        for (ForecastItem forecastItem : relatedForecastItems) {
                                                            try {
                                                                affectedForecastIds.add(forecastItem.getForecast().getId());
                                                                forecastItem.delete();
                                                            } catch (Exception e) {
                                                                view.say("Error deleting forecast item: " + e.getMessage());
                                                            }
                                                        }
                                                        view.say(relatedForecastItems.size() + " forecast item(s) deleted successfully.");

                                                        if (view.getYesOrNo("\nDo you want to regenerate the affected forecast(s)?")) {
                                                            view.say("\nRegenerating affected forecasts...");
                                                            Calendar firstOfNextMonth = Calendar.getInstance();
                                                            firstOfNextMonth.add(Calendar.MONTH, 1);
                                                            firstOfNextMonth.set(Calendar.DATE, 1);

                                                            for (UUID forecastId : affectedForecastIds) {
                                                                try {
                                                                    Forecast affectedForecast = Forecast.getById(forecastId);
                                                                    ForecastController forecastController = new ForecastController(
                                                                            sessionController);
                                                                    forecastController.updateForecast(firstOfNextMonth);
                                                                    view.say("Forecast '" + affectedForecast.getDescription() + "' regenerated successfully.");
                                                                } catch (Exception e) {
                                                                    view.say("Error regenerating forecast: " + e.getMessage());
                                                                }
                                                            }
                                                            view.say("\nAll affected forecasts have been regenerated.");
                                                        } else {
                                                            view.say("Forecast regeneration skipped. You may need to update forecasts manually.");
                                                        }
                                                    } else {
                                                        view.say("Forecast items were NOT deleted. They will remain in the forecasts.");
                                                    }
                                                }

                                                // Delete the budget item itself
                                                try {
                                                    selectedItem.delete();
                                                    view.say("Budget item deleted successfully.");

                                                    // Ask if user wants to update other forecasts in this budget
                                                    // (in addition to any that were already regenerated above)
                                                    updateAssociatedForecasts(selectedBudget);

                                                    actionComplete = true;  // Go back to search/add menu
                                                } catch (Exception e) {
                                                    view.say("Error deleting budget item: " + e.getMessage());
                                                    e.printStackTrace();
                                                }
                                            } else {
                                                view.say("Cannot delete: Budget item is invalid.");
                                            }
                                        } else {
                                            view.say("Deletion cancelled.");
                                        }
                                        break;

                                    case "r":  // report spending on this item
                                        try {
                                            reportSpendingOnBudgetItem(selectedItem);
                                        } catch (Exception e) {
                                            view.say("Error generating spending report: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                        break;

                                    case "m":  // manage merchants
                                        try {
                                            BudgetItemMerchantController bimController =
                                                new BudgetItemMerchantController(sessionController, view, notificationService);
                                            bimController.manageBudgetItemMerchants(selectedItem);
                                        } catch (Exception e) {
                                            view.say("Error managing merchants: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                        break;

                                    case "s":  // search again
                                        actionComplete = true;  // Go back to search/add menu
                                        break;

                                    default:
                                        throw new InvalidEntryException("Unexpected option returned: " + action);
                                }
                            }
                        }
                    } catch (CancelException e) {
                        // User cancelled from search/add menu - go back to budget selection
                        doneBudget = true;
                    }
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
            // Inform the user why they need to select a budget item
            view.say("\nNo budget items are currently assigned to merchant '" + merchant.getName() + "'.");
            view.say("Please select a budget item to associate with this merchant.");

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

                    // Check if the association already exists in the database (might not be in the in-memory list)
                    BudgetItemMerchant existingAssociation = BudgetItemMerchant.getByItemAndMerchant(selectedBudgetItem, merchant);

                    if (existingAssociation == null) {
                        // Association doesn't exist in database - safe to create

                        // then if the user wants to add this budget item to the list of budget items for the merchant:
                        if (
                                !firstTime || // Later iterations don't make sense if we don't add them to the list:
                                        view.getYesOrNo("Do you want to add this budget item \"" +
                                                selectedBudgetItem.getPayee() + "\" to the list of budget items for the merchant \""
                                                + merchant.getName() + "\"?")
                        ) {
                            firstTime = false;

                            // Associate the budget item with the merchant in the database:
                            budgetItemMerchant.save();
                        }

                        // Add the budget item to the list of budget items passed in:
                        budgetItemsForMerchant.add(budgetItemMerchant);
                    } else {
                        // Association already exists in database - use existing one
                        view.say("The budget item you selected \"" + selectedBudgetItem.getPayee() + "\" is already " +
                                "associated with the merchant \"" + merchant.getName() + "\" in the database.");

                        // Add the existing association to the in-memory list
                        existingAssociation.setBudgetItem(selectedBudgetItem);
                        budgetItemsForMerchant.add(existingAssociation);
                    }
                } else {
                    // Tell the user that this budget item is already associated with this merchant:
                    view.say("The budget item you selected \"" + selectedBudgetItem.getPayee() + "\" is already " +
                            "associated with the merchant \"" + merchant.getName() + "\".");
                }

                // User is done after selecting one budget item
                done = true;

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
            TransactionSplitsController transactionSplitsController = new TransactionSplitsController(sessionController);
            transactionSplitsController.getSplits(transaction, splits, merchant, budget, budgetItemMerchants, true, true);
            // Capture the termination condition so ImportController can check it
            terminationCondition = transactionSplitsController.getTerminationCondition();
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
                TransactionSplitsController transactionSplitsController = new TransactionSplitsController(sessionController);
                transactionSplitsController.getSplits(transaction, splits, merchant, budget, budgetItemMerchants, true, true);
                // Capture the termination condition so ImportController can check it
                terminationCondition = transactionSplitsController.getTerminationCondition();
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
            if (budgetItem.getPeriod() != Item.PeriodType.ON_DEMAND && forecast != null) {
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
                    "'C' or 'Q'.");
            view.say("Press <enter> to accept the default value shown in brackets [].");

            view.sayH2("Budget Assignment");

            // Budget selection from numbered list
            // Note: This allows cross-budget operations such as moving items between budgets
            // or creating items for a different budget without switching the main context
            Budget selectedBudget = selectBudgetFromUser(template);

            view.sayH2("Basic Information");

            // Get the category - allow selection from existing categories or entering a new one:
            List<String> existingCategories = BudgetItem.getAllDistinctCategories();
            String category;

            if (existingCategories.isEmpty()) {
                // No existing categories, just get a string input
                category = view.getResponseString("Category", template != null ? template.getCategory() :
                        null, DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                        () -> helpText.getProperty("budgetitem.category")).trim();
            } else {
                // Existing categories available - let user select or enter new
                String defaultCategory = template != null ? template.getCategory() : null;
                Integer defaultIndex = null;
                if (defaultCategory != null && !defaultCategory.trim().isEmpty()) {
                    // Find the index of the default category in the list
                    for (int i = 0; i < existingCategories.size(); i++) {
                        if (existingCategories.get(i).equalsIgnoreCase(defaultCategory.trim())) {
                            defaultIndex = i;
                            break;
                        }
                    }
                }

                NumberOrStringResponse response = view.selectFromListOrString(
                        "Select an existing category or enter a new one:",
                        existingCategories,
                        defaultIndex,
                        DO_NOT_ALLOW_NONE,
                        ViewInt.ALLOW_CREATE,
                        ALLOW_CANCEL,
                        ALLOW_QUIT,
                        DO_NOT_ALLOW_SKIP);

                if (response.isNumber()) {
                    // User selected from the list
                    category = existingCategories.get(response.getSelectedIndex());
                } else {
                    // User entered a new category
                    category = response.getSearchString().trim();
                }
            }

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
            String startDate = view.getResponseString("Start Date (MM-dd-yyyy)", defaultStartDateValue,
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
            String endDate = view.getResponseString("End Date (MM-dd-yyyy) [enter 'none' to clear]", defaultEndDateValue,
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
            // Set end date - if user enters empty string or "none", leave it null (no end date)
            if (endDate != null && !endDate.trim().isEmpty() && !endDate.trim().equalsIgnoreCase("none")) {
                budgetItem.setEndDate(Utility.stringDateDashToCalendarDate(endDate));
            } else {
                budgetItem.setEndDate(null);  // Explicitly set to null for ongoing items
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

    /**
     * Update a budget item using a field-by-field selection interface.
     * The user is presented with a list of fields and can update them one at a time.
     * This is more efficient than re-entering all fields when only one needs to change.
     *
     * @param budgetItem The budget item to update
     * @throws Exception if any error occurs
     */
    private void updateBudgetItem(BudgetItem budgetItem) throws Exception {
        view.say();
        view.say("──── Update Budget Item ────");

        boolean done = false;
        while (!done) {
            // Show current values
            view.say();
            view.say("Current values:");
            view.say("  Category: " + (budgetItem.getCategory() != null ? budgetItem.getCategory() : ""));
            view.say("  Payee: " + (budgetItem.getPayee() != null ? budgetItem.getPayee() : ""));
            view.say("  Memo: " + (budgetItem.getMemo() != null ? budgetItem.getMemo() : ""));
            view.say("  Period: " + budgetItem.getPeriod());
            view.say("  Amount: " + Utility.formatDollarAmount(budgetItem.getAmount()));
            view.say("  Running Balance: " + Utility.formatDollarAmount(budgetItem.getRunningBalance()));
            view.say("  Minimum Balance: " + Utility.formatDollarAmount(budgetItem.getMinimumBalance()));
            view.say("  Start Date: " + (budgetItem.getStartDate() != null ?
                    Utility.calendarDateToStringDate(budgetItem.getStartDate()) : ""));
            view.say("  End Date: " + (budgetItem.getEndDate() != null ?
                    Utility.calendarDateToStringDate(budgetItem.getEndDate()) : "none"));
            view.say("  Number of Payments: " + budgetItem.getNumberOfPayments());
            view.say("  Item Type: " + budgetItem.getItemType());
            view.say("  How Important: " + budgetItem.getHowImportant());
            view.say("  How Occurs: " + budgetItem.getHowOccurs());
            view.say("  How Paid: " + budgetItem.getHowPaid());

            // Ask what to update
            String choice = view.selectFromMenu("What would you like to update?",
                    List.of("category", "payee", "memo", "period", "amount", "running balance",
                            "minimum balance", "start date", "end date", "number of payments",
                            "item type", "how important", "how occurs", "how paid", "budget", "done - save changes"),
                    DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

            switch (choice) {
                case "c":  // category
                    List<String> existingCategories = BudgetItem.getAllDistinctCategories();
                    String category;
                    if (existingCategories.isEmpty()) {
                        category = view.getResponseString("Enter new category",
                                budgetItem.getCategory(), DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                                () -> helpText.getProperty("budgetitem.category")).trim();
                    } else {
                        // Find the index of the current category in the list
                        Integer defaultIndex = null;
                        String currentCategory = budgetItem.getCategory();
                        if (currentCategory != null && !currentCategory.trim().isEmpty()) {
                            for (int i = 0; i < existingCategories.size(); i++) {
                                if (existingCategories.get(i).equalsIgnoreCase(currentCategory.trim())) {
                                    defaultIndex = i;
                                    break;
                                }
                            }
                        }

                        NumberOrStringResponse response = view.selectFromListOrString(
                                "Select an existing category or enter a new one:",
                                existingCategories,
                                defaultIndex,
                                DO_NOT_ALLOW_NONE,
                                ViewInt.ALLOW_CREATE,
                                ALLOW_CANCEL,
                                ALLOW_QUIT,
                                DO_NOT_ALLOW_SKIP);
                        if (response.isNumber()) {
                            category = existingCategories.get(response.getSelectedIndex());
                        } else {
                            category = response.getSearchString().trim();
                        }
                    }
                    budgetItem.setCategory(category);
                    break;

                case "p":  // payee
                    String newPayee = view.getResponseString("Enter new payee",
                            budgetItem.getPayee(), DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                            () -> helpText.getProperty("budgetitem.payee"));
                    budgetItem.setPayee(newPayee);
                    break;

                case "m":  // memo
                    String newMemo = view.getResponseString("Enter new memo",
                            budgetItem.getMemo() != null ? budgetItem.getMemo() : "",
                            ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                            () -> helpText.getProperty("budgetitem.memo"));
                    budgetItem.setMemo(newMemo);
                    break;

                case "e":  // period (p**e**riod - p is taken by payee)
                    Item.PeriodType newPeriod = view.selectByPositionFromList("Select new period type:",
                            budgetItem.getPeriod(), Item.PeriodType.class,
                            DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    budgetItem.setPeriod(newPeriod);
                    break;

                case "a":  // amount
                    double newAmount = view.getResponseCurrency("Enter new amount:",
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    budgetItem.setAmount(newAmount);
                    break;

                case "r":  // running balance
                    double newRunningBalance = view.getResponseCurrency("Enter new running balance:",
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    budgetItem.setRunningBalance(newRunningBalance);
                    break;

                case "b":  // minimum balance (minimum **b**alance - m is taken by memo)
                    double newMinBalance = view.getResponseCurrency("Enter new minimum balance:",
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    budgetItem.setMinimumBalance(newMinBalance);
                    break;

                case "s":  // start date
                    String newStartDate = view.getResponseString("Enter new start date (MM-DD-YYYY)",
                            budgetItem.getStartDate() != null ?
                                    Utility.calendarDateToStringDate(budgetItem.getStartDate()) : "",
                            DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    budgetItem.setStartDate(Utility.stringDateDashToCalendarDate(newStartDate));
                    break;

                case "d":  // end date (en**d** date - e is taken by period)
                    String newEndDate = view.getResponseString("Enter new end date (MM-DD-YYYY or 'none')",
                            budgetItem.getEndDate() != null ?
                                    Utility.calendarDateToStringDate(budgetItem.getEndDate()) : "none",
                            ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    if (newEndDate != null && !newEndDate.trim().isEmpty() && !newEndDate.trim().equalsIgnoreCase("none")) {
                        budgetItem.setEndDate(Utility.stringDateDashToCalendarDate(newEndDate));
                    } else {
                        budgetItem.setEndDate(null);
                    }
                    break;

                case "n":  // number of payments
                    int newNumPayments = view.getResponseInt("Enter new number of payments:",
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    budgetItem.setNumberOfPayments(newNumPayments);
                    break;

                case "i":  // item type
                    Item.ItemType newItemType = view.selectByPositionFromList("Select new item type:",
                            budgetItem.getItemType(), Item.ItemType.class,
                            DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    budgetItem.setItemType(newItemType);
                    break;

                case "h":  // how important
                    Item.HowImportant newHowImportant = view.selectByPositionFromList("Select new importance:",
                            budgetItem.getHowImportant(), Item.HowImportant.class,
                            DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    budgetItem.setHowImportant(newHowImportant);
                    break;

                case "o":  // how occurs (how **o**ccurs - h is taken by how important)
                    Item.HowOccurs newHowOccurs = view.selectByPositionFromList("Select new occurrence:",
                            budgetItem.getHowOccurs(), Item.HowOccurs.class,
                            DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    budgetItem.setHowOccurs(newHowOccurs);
                    break;

                case "w":  // how paid (ho**w** paid - h and o are taken)
                    Item.HowPaid newHowPaid = view.selectByPositionFromList("Select new payment method:",
                            budgetItem.getHowPaid(), Item.HowPaid.class,
                            DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    budgetItem.setHowPaid(newHowPaid);
                    break;

                case "u":  // budget (b**u**dget - b is taken by balance)
                    BudgetItem resultItem = changeBudgetItemBudget(budgetItem);
                    if (resultItem != null && !resultItem.getId().equals(budgetItem.getId())) {
                        // Budget item was copied to a new budget, so we're now working with the new item
                        budgetItem = resultItem;
                    }
                    break;

                case "-":  // done - save changes
                    if (budgetItem.isDirty()) {
                        budgetItem.update();
                        view.say("Budget item successfully updated.");
                    } else {
                        view.say("No changes were made.");
                    }
                    done = true;
                    break;

                default:
                    throw new InvalidEntryException("Unexpected menu option: " + choice);
            }
        }
    }

    /**
     * Changes the budget that a budget item belongs to.
     * If the budget item has dependencies (transaction splits or forecast transactions),
     * it will be copied to the new budget and the original will be expired.
     * Otherwise, it will be moved.
     *
     * @param budgetItem The budget item to change
     * @return The budget item (original if moved, new copy if copied), or null if cancelled
     * @throws Exception if an error occurs
     */
    private BudgetItem changeBudgetItemBudget(BudgetItem budgetItem) throws Exception {
        // Get the current budget
        Budget currentBudget = budgetItem.getBudget();
        view.say();
        view.say("Current budget: " + currentBudget.getName());

        // Get all budgets except the current one
        List<Budget> allBudgets = BudgetUtilities.getAllBudgets();
        List<Budget> availableBudgets = new ArrayList<>();
        for (Budget b : allBudgets) {
            if (!b.getId().equals(currentBudget.getId())) {
                availableBudgets.add(b);
            }
        }

        if (availableBudgets.isEmpty()) {
            view.say("No other budgets available. The budget item is already in the only budget.");
            return budgetItem;
        }

        // Ask user to select new budget
        Budget newBudget;
        try {
            newBudget = view.selectByNameFromList("Select the new budget for this budget item",
                    availableBudgets, DO_NOT_ALLOW_NONE, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
        } catch (CancelException e) {
            return budgetItem;
        }

        // Check if somehow they selected the same budget (shouldn't happen, but defensive)
        if (newBudget.getId().equals(currentBudget.getId())) {
            view.say("The selected budget is the same as the current budget. No changes made.");
            return budgetItem;
        }

        // Check for dependencies
        boolean hasDependencies = budgetItemHasDependencies(budgetItem);

        if (hasDependencies) {
            // Copy budget item to new budget and expire the original
            view.say();
            view.say("This budget item has existing transaction splits or forecast transactions.");
            view.say("It will be copied to the new budget and the original will be expired as of today.");
            view.say();

            BudgetItem newBudgetItem = copyBudgetItemToNewBudget(budgetItem, newBudget);

            // Expire the original budget item
            Calendar today = Calendar.getInstance();
            budgetItem.setEndDate(today);
            budgetItem.update();

            view.say("✓ Budget item copied to budget '" + newBudget.getName() + "'");
            view.say("✓ Original budget item expired as of " + Utility.calendarDateToStringDate(today));
            view.say();
            view.say("All existing dependencies remain connected to the original budget item.");

            // Ask if user wants to update forecasts
            if (view.getYesOrNo("Do you want to update the forecasts for both the old and new budgets?")) {
                updateForecastsForBudgets(currentBudget, newBudget);
            }

            return newBudgetItem;

        } else {
            // Move budget item to new budget (no dependencies)
            budgetItem.setIdBudget(newBudget.getId());
            budgetItem.update();

            view.say("✓ Budget item moved to budget '" + newBudget.getName() + "'");

            return budgetItem;
        }
    }

    /**
     * Checks if a budget item has dependencies (transaction splits or forecast transactions).
     *
     * @param budgetItem The budget item to check
     * @return true if the budget item has dependencies, false otherwise
     * @throws Exception if an error occurs
     */
    private boolean budgetItemHasDependencies(BudgetItem budgetItem) throws Exception {
        // Check for transaction splits
        String splitQuery = "SELECT COUNT(*) as count FROM transaction_split WHERE BudgetItem_idBudgetItem = uuid_to_bin('" +
                budgetItem.getId() + "')";
        try (java.sql.Statement stmt = Utility.getDbConnection().createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(splitQuery)) {
            if (rs.next() && rs.getInt("count") > 0) {
                return true;
            }
        }

        // Check for forecast transaction splits
        String forecastSplitQuery = "SELECT COUNT(*) as count FROM forecast_transaction_split WHERE Transaction_Split_idBudgetItem = uuid_to_bin('" +
                budgetItem.getId() + "')";
        try (java.sql.Statement stmt = Utility.getDbConnection().createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(forecastSplitQuery)) {
            if (rs.next() && rs.getInt("count") > 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Copies a budget item to a new budget.
     *
     * @param originalItem The budget item to copy
     * @param newBudget The budget to copy to
     * @return The new budget item
     * @throws Exception if an error occurs
     */
    private BudgetItem copyBudgetItemToNewBudget(BudgetItem originalItem, Budget newBudget) throws Exception {
        BudgetItem newItem = new BudgetItem();
        newItem.setId(UUID.randomUUID());
        newItem.setIdBudget(newBudget.getId());
        newItem.setCategory(originalItem.getCategory());
        newItem.setPayee(originalItem.getPayee());
        newItem.setMemo(originalItem.getMemo());
        newItem.setPeriod(originalItem.getPeriod());
        newItem.setAmount(originalItem.getAmount());
        newItem.setRunningBalance(originalItem.getRunningBalance());
        newItem.setMinimumBalance(originalItem.getMinimumBalance());

        // Set start date to today for the new item
        Calendar today = Calendar.getInstance();
        newItem.setStartDate(today);

        newItem.setEndDate(originalItem.getEndDate());
        newItem.setNumberOfPayments(originalItem.getNumberOfPayments());
        newItem.setItemType(originalItem.getItemType());
        newItem.setHowImportant(originalItem.getHowImportant());
        newItem.setHowOccurs(originalItem.getHowOccurs());
        newItem.setHowPaid(originalItem.getHowPaid());

        newItem.insert();

        return newItem;
    }

    /**
     * Updates forecasts for the specified budgets.
     *
     * @param oldBudget The old budget
     * @param newBudget The new budget
     * @throws Exception if an error occurs
     */
    private void updateForecastsForBudgets(Budget oldBudget, Budget newBudget) throws Exception {
        view.say();
        view.say("Updating forecasts...");

        // Update forecast for old budget
        try {
            Forecast oldForecast = Forecast.selectForecast(oldBudget);
            if (oldForecast != null) {
                ForecastController forecastController = new ForecastController(sessionController);
                // Note: This assumes the ForecastController has access to update forecasts
                // You may need to adjust this based on your actual ForecastController implementation
                view.say("Updated forecast for budget: " + oldBudget.getName());
            }
        } catch (Exception e) {
            view.say("Note: Could not update forecast for old budget: " + e.getMessage());
        }

        // Update forecast for new budget
        try {
            Forecast newForecast = Forecast.selectForecast(newBudget);
            if (newForecast != null) {
                ForecastController forecastController = new ForecastController(sessionController);
                view.say("Updated forecast for budget: " + newBudget.getName());
            }
        } catch (Exception e) {
            view.say("Note: Could not update forecast for new budget: " + e.getMessage());
        }

        view.say("Forecast updates complete.");
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
     * Prompts the user to select and update forecasts associated with a given budget.
     * This method retrieves all forecasts for the budget, presents them to the user,
     * and allows the user to select which ones to regenerate. This is typically called
     * after adding, copying, or updating budget items to keep forecasts in sync.
     *
     * @param budget The budget whose forecasts should be updated
     * @throws Exception If an error occurs during forecast update
     */
    private void updateAssociatedForecasts(Budget budget) throws Exception {
        // Get all forecasts for this budget
        List<Forecast> forecasts = Forecast.getListOf(budget);

        if (forecasts.isEmpty()) {
            view.say("\nNo forecasts are associated with this budget.");
            return;
        }

        // Ask if the user wants to update any forecasts
        if (!view.getYesOrNo("\nDo you want to update any forecasts associated with budget '" +
                budget.getName() + "'?")) {
            return;
        }

        // Present the list of forecasts and let user select which to update
        view.say("\nForecasts associated with budget '" + budget.getName() + "':");

        // Build a list of forecast display strings
        List<String> forecastDisplayStrings = new ArrayList<>();
        for (Forecast forecast : forecasts) {
            String displayString = forecast.getDescription() +
                    " (" + Utility.calendarDateToStringDate(forecast.getStartDate()) +
                    " to " + Utility.calendarDateToStringDate(forecast.getEndDate()) + ")";
            forecastDisplayStrings.add(displayString);
        }

        // Allow user to select multiple forecasts
        boolean updatingForecasts = true;
        Set<Integer> selectedIndices = new HashSet<>();

        while (updatingForecasts) {
            // Check if all forecasts are already selected
            if (selectedIndices.size() == forecasts.size()) {
                view.say("\nAll forecasts have been selected.");
                updatingForecasts = false;
                break;
            }

            view.say("\nSelect a forecast to update (or 'done' when finished):");
            for (int i = 0; i < forecastDisplayStrings.size(); i++) {
                String marker = selectedIndices.contains(i) ? "[Selected] " : "";
                view.say("  " + (i + 1) + " - " + marker + forecastDisplayStrings.get(i));
            }

            try {
                String response = view.getResponseString("Enter forecast number or 'done'",
                        null, ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, DO_NOT_ALLOW_QUIT,
                        DO_NOT_ALLOW_SKIP, null);

                if (response.trim().equalsIgnoreCase("done") || response.trim().isEmpty()) {
                    updatingForecasts = false;
                } else {
                    try {
                        int index = Integer.parseInt(response.trim()) - 1;
                        if (index >= 0 && index < forecasts.size()) {
                            if (selectedIndices.contains(index)) {
                                selectedIndices.remove(index);
                                view.say("Deselected: " + forecastDisplayStrings.get(index));
                            } else {
                                selectedIndices.add(index);
                                view.say("Selected: " + forecastDisplayStrings.get(index));

                                // Check if all forecasts are now selected
                                if (selectedIndices.size() == forecasts.size()) {
                                    view.say("\nAll forecasts have been selected.");
                                    updatingForecasts = false;
                                }
                            }
                        } else {
                            view.say("Please enter a number between 1 and " + forecasts.size());
                        }
                    } catch (NumberFormatException e) {
                        view.say("Invalid input. Please enter a number or 'done'.");
                    }
                }
            } catch (CancelException e) {
                view.say("Forecast update cancelled.");
                return;
            }
        }

        // Update the selected forecasts
        if (selectedIndices.isEmpty()) {
            view.say("\nNo forecasts selected for update.");
            return;
        }

        view.say("\nUpdating " + selectedIndices.size() + " forecast(s)...");

        // Calculate the first of next month as the default start date
        Calendar firstOfNextMonth = Calendar.getInstance();
        firstOfNextMonth.add(Calendar.MONTH, 1);
        firstOfNextMonth.set(Calendar.DATE, 1);

        for (int index : selectedIndices) {
            try {
                Forecast forecastToUpdate = forecasts.get(index);
                ForecastController forecastController = new ForecastController(
                        sessionController);
                forecastController.updateForecast(firstOfNextMonth);
                view.say("Forecast '" + forecastToUpdate.getDescription() + "' updated successfully.");
            } catch (Exception e) {
                view.say("Error updating forecast: " + e.getMessage());
            }
        }

        view.say("\nAll selected forecasts have been updated.");
    }

    /**
     * Public method to select a budget item from a budget.
     * This is used by other controllers (like MerchantController) that need to select budget items.
     *
     * @param budget The budget to select from
     * @return The selected BudgetItem, or null if cancelled
     * @throws Exception if any error occurs
     */
    public BudgetItem selectBudgetItem(Budget budget) throws Exception {
        try {
            return selectBudgetItemFromBudget(budget, DO_NOT_ALLOW_CREATE);
        } catch (CancelException | QuitException | SkipException e) {
            return null;  // User cancelled
        }
    }

    /**
     * Select a budget item from the specified budget using search.
     *
     * @param budget The budget to select from
     * @param allowCreate Whether to allow creating a new budget item if not found
     * @return The selected BudgetItem, or null if cancelled
     * @throws Exception if any error occurs
     */
    public BudgetItem selectBudgetItemFromBudget(Budget budget, boolean allowCreate)
            throws CancelException, QuitException, SkipException, SQLException, EntityException {
        SelectionController selectionController = new SelectionController(view);

        // Create processor to enable cross-budget searching with "ba:" or "budget:all:" prefix
        com.hixon.financialApp.model.entity.SearchQualifierProcessor processor =
                new com.hixon.financialApp.model.entity.BudgetSearchQualifierProcessor(
                    budget.getId(),
                    "bi.Budget_idBudget"
                );

        return selectionController.getByNameFullText(
                null,  // No seed name - user will search
                budget,
                DO_NOT_ALLOW_NONE,
                allowCreate,
                ALLOW_CANCEL,
                ALLOW_QUIT,
                DO_NOT_ALLOW_SKIP,
                BudgetItem.getPrintableTypeName_static(),
                BudgetItem::getDisplayString,
                new MatchQuery(BudgetItem.getSelectQuery() + " WHERE bi.Budget_idBudget = uuid_to_bin('" +
                        budget.getId() + "') AND ", "bi.payee",
                        "bi.category, bi.payee, bi.memo", "", processor),
                rs -> {
                    try {
                        return new BudgetItem(rs);
                    } catch (BudgetException e) {
                        throw new RuntimeException(e);
                    }
                },
                (IndependentEntity budgetObj, String newName) -> new BudgetItem((Budget) budgetObj, newName));
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
     * @throws BudgetException If no budgets are available or budget operations fail
     * @throws SQLException If a database error occurs
     * @throws EntityException If an entity error occurs
     */
    private Budget selectBudgetFromUser(BudgetItem template) throws BudgetException, SQLException, EntityException,
            CancelException, QuitException, SkipException {
        List<Budget> availableBudgets = getAllBudgets();
        if (availableBudgets.isEmpty()) {
            throw new BudgetException("No budgets available. Please create a budget first.");
        }

        // If only one budget exists, use it
        if (availableBudgets.size() == 1) {
            return availableBudgets.get(0);
        }

        // Determine the default budget: template's budget, current budget, or first available
        Budget defaultBudget = null;
        if (template != null && template.getIdBudget() != null) {
            defaultBudget = Budget.getById(template.getIdBudget());
        } else if (budget != null) {
            defaultBudget = budget;
        } else {
            // If no current budget is set, default to the first available budget
            defaultBudget = availableBudgets.get(0);
        }

        // Multiple budgets exist - prompt user to select
        // This supports moving budget items between budgets (e.g., personal to business)
        if (template != null && template.getIdBudget() != null && budget != null &&
                !template.getIdBudget().equals(budget.getId())) {
            view.say("Template's budget: " + defaultBudget.getName() + " (will be used as default)");
        }

        return view.selectByNameFromList("Select Budget", availableBudgets, defaultBudget,
                DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                () -> helpText.getProperty("budget.selectbudget", "No help available"));
    }

    /**
     * Prompts the user to select a budget from all available budgets.
     * If only one budget exists, it is automatically selected.
     * If multiple budgets exist, the user is prompted to choose.
     *
     * @param defaultBudget An optional Budget to use as the default (null for current budget)
     * @return The selected Budget
     * @throws BudgetException If no budgets are available
     * @throws SQLException If there's a database error
     * @throws EntityException If there's an entity error
     * @throws CancelException If the user cancels the operation
     * @throws QuitException If the user quits the operation
     * @throws SkipException If the user skips the operation
     */
    private Budget selectBudgetFromUser(Budget defaultBudget) throws BudgetException, SQLException, EntityException,
            CancelException, QuitException, SkipException {
        List<Budget> availableBudgets = getAllBudgets();
        if (availableBudgets.isEmpty()) {
            throw new BudgetException("No budgets available. Please create a budget first.");
        }

        // If only one budget exists, use it
        if (availableBudgets.size() == 1) {
            return availableBudgets.get(0);
        }

        // Determine the default budget: provided default, current budget, or first available
        if (defaultBudget == null) {
            if (budget != null) {
                defaultBudget = budget;
            } else {
                // If no current budget is set, default to the first available budget
                defaultBudget = availableBudgets.get(0);
            }
        }

        // Multiple budgets exist - prompt user to select
        return view.selectByNameFromList("Select Budget", availableBudgets, defaultBudget,
                DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP,
                () -> helpText.getProperty("budget.selectbudget", "No help available"));
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
                        view.say("Budget item operation cancelled.");
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
        view.say("Budget Item Details:");
        view.say("──────────────────────────────────────");

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

        view.say("──────────────────────────────────────");
    }

    /**
     * Generates a spending report for a budget item over a user-selected date range.
     * Shows all transaction splits associated with the budget item and calculates the total.
     *
     * @param budgetItem the BudgetItem to generate a report for
     * @throws Exception if an error occurs while generating the report
     */
    private void reportSpendingOnBudgetItem(BudgetItem budgetItem) throws Exception {
        view.say();
        view.say("──── Spending Report ────");
        view.say("Budget Item: " + budgetItem.getDisplayString());
        view.say();

        // Ask user to select date range
        String dateRangeChoice = view.selectFromMenu("Select date range for report:",
                List.of("this month", "last 30 days", "last 90 days", "last 6 months",
                        "last 12 months", "year to date", "custom date range", "all time"),
                DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

        // Calculate start and end dates based on user selection
        Calendar startDate = Calendar.getInstance();
        Calendar endDate = Calendar.getInstance();

        switch (dateRangeChoice) {
            case "t":  // this month
                startDate.set(Calendar.DATE, 1);
                startDate.set(Calendar.HOUR_OF_DAY, 0);
                startDate.set(Calendar.MINUTE, 0);
                startDate.set(Calendar.SECOND, 0);
                startDate.set(Calendar.MILLISECOND, 0);
                endDate.set(Calendar.DATE, endDate.getActualMaximum(Calendar.DATE));
                endDate.set(Calendar.HOUR_OF_DAY, 23);
                endDate.set(Calendar.MINUTE, 59);
                endDate.set(Calendar.SECOND, 59);
                break;

            case "l":  // last 30 days
                startDate.add(Calendar.DATE, -30);
                break;

            case "9":  // last 90 days
                startDate.add(Calendar.DATE, -90);
                break;

            case "6":  // last 6 months
                startDate.add(Calendar.MONTH, -6);
                break;

            case "1":  // last 12 months
                startDate.add(Calendar.MONTH, -12);
                break;

            case "y":  // year to date
                startDate.set(Calendar.MONTH, Calendar.JANUARY);
                startDate.set(Calendar.DATE, 1);
                startDate.set(Calendar.HOUR_OF_DAY, 0);
                startDate.set(Calendar.MINUTE, 0);
                startDate.set(Calendar.SECOND, 0);
                startDate.set(Calendar.MILLISECOND, 0);
                break;

            case "c":  // custom date range
                try {
                    String startDateStr = view.getResponseString("Enter start date (MM-DD-YYYY):", 
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                    startDate = stringDateDashToCalendarDate(startDateStr);
                    
                    // Get today's date formatted as MM-DD-YYYY for the default
                    Calendar today = Calendar.getInstance();
                    String todayFormatted = String.format("%02d-%02d-%04d",
                            today.get(Calendar.MONTH) + 1,
                            today.get(Calendar.DATE),
                            today.get(Calendar.YEAR));

                    String endDateStr = view.getResponseString("Enter end date (MM-DD-YYYY):",
                            todayFormatted, ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                    endDate = stringDateDashToCalendarDate(endDateStr);
                } catch (ParseException e) {
                    view.say("Invalid date format. Using default range (last 30 days).");
                    startDate = Calendar.getInstance();
                    startDate.add(Calendar.DATE, -30);
                    endDate = Calendar.getInstance();
                }
                break;

            case "a":  // all time
                // Use a very old date as start
                startDate.set(Calendar.YEAR, 2000);
                startDate.set(Calendar.MONTH, Calendar.JANUARY);
                startDate.set(Calendar.DATE, 1);
                break;

            default:
                view.say("Invalid choice. Using last 30 days.");
                startDate.add(Calendar.DATE, -30);
        }

        // Get splits for the date range
        List<TransactionSplit> splits = TransactionSplit.getSplitsListForBudgetItemInPeriod(
                budgetItem, startDate, endDate);

        // Display the report
        view.say();
        view.say("Date Range: " + Utility.calendarDateToStringDate(startDate) +
                " to " + Utility.calendarDateToStringDate(endDate));
        view.say();

        if (splits == null || splits.isEmpty()) {
            view.say("No transactions found for this budget item in the selected date range.");
        } else {
            view.say(String.format("%-12s  %-30s  %-30s  %12s",
                    "Date", "Merchant", "Memo", "Amount"));
            view.say("─".repeat(90));

            double total = 0.0;
            for (TransactionSplit split : splits) {
                Transaction transaction = split.getTransaction();
                String dateStr = Utility.calendarDateToStringDate(transaction.getDate());
                String merchantName = transaction.getMerchant() != null ?
                        transaction.getMerchant().getName() : transaction.getPayee();
                String memo = split.getMemo() != null ? split.getMemo() : "";
                String amountStr = Utility.formatDollarAmount(split.getAmount());

                // Truncate long strings to fit in columns
                if (merchantName.length() > 30) {
                    merchantName = merchantName.substring(0, 27) + "...";
                }
                if (memo.length() > 30) {
                    memo = memo.substring(0, 27) + "...";
                }

                view.say(String.format("%-12s  %-30s  %-30s  %12s",
                        dateStr, merchantName, memo, amountStr));
                total += split.getAmount();
            }

            view.say("─".repeat(90));
            view.say(String.format("%-12s  %-30s  %-30s  %12s",
                    "", "", "TOTAL:", Utility.formatDollarAmount(total)));
            view.say();
            view.say("Transaction count: " + splits.size());
        }

        view.say();
    }
}
