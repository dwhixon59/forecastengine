package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetItem;
import com.hixon.financialApp.model.budget.BudgetItemMerchant;
import com.hixon.financialApp.model.merchant.Merchant;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static com.hixon.financialApp.view.base.ViewInt.*;

/**
 * Controller for managing the association between budget items and merchants.
 * Provides bidirectional management:
 * - From a merchant: manage which budget items are associated
 * - From a budget item: manage which merchants are associated
 */
@Getter
@Setter
public class BudgetItemMerchantController {

    private SessionController sessionController;
    private ViewInt view;
    private NotificationServiceInt notificationService;

    /**
     * Constructor for BudgetItemMerchantController.
     *
     * @param sessionController The session controller for accessing user and budget information
     * @param view The view interface for user interaction
     * @param notificationService The notification service for sending notifications
     */
    public BudgetItemMerchantController(SessionController sessionController, ViewInt view,
                                        NotificationServiceInt notificationService) {
        this.sessionController = sessionController;
        this.view = view;
        this.notificationService = notificationService;
    }

    /**
     * Manage budget items associated with a merchant.
     * Shows list of associated budget items and allows user to view, update, remove, or add.
     *
     * @param merchant The merchant whose budget items to manage
     * @throws Exception if any error occurs
     */
    public void manageBudgetItemMerchants(Merchant merchant) throws Exception {
        boolean done = false;

        while (!done) {
            try {
                view.say();
                view.say("──── Manage Budget Items for " + merchant.getName() + " ────");

                // Get all budget items associated with this merchant across all user budgets
                List<Budget> budgets = sessionController.getUserBudgets();
                List<BudgetItemMerchant> allBudgetItemMerchants = new ArrayList<>();

                for (Budget budget : budgets) {
                    List<BudgetItemMerchant> budgetItemsForBudget =
                        BudgetItemMerchant.getAssignedBudgetItems(budget, merchant);
                    allBudgetItemMerchants.addAll(budgetItemsForBudget);
                }

                // Handle empty list
                if (allBudgetItemMerchants.isEmpty()) {
                    view.say("No budget items currently associated with this merchant.");
                    view.say();

                    String choice = view.selectFromMenu("What would you like to do?",
                            List.of("add budget item", "return"),
                            DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                    if (choice.equals("a")) {
                        addBudgetItemToMerchant(merchant);
                    } else {
                        done = true;
                    }
                    continue;
                }

                // Ask what to do
                view.say();
                String choice = view.selectFromMenu("What would you like to do?",
                        List.of("select existing budget item", "add budget item", "done"),
                        DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                if (choice.equals("a")) {
                    addBudgetItemToMerchant(merchant);
                    continue;
                } else if (choice.equals("d")) {
                    done = true;
                    continue;
                }

                // User chose to select an existing budget item
                BudgetItemMerchant selectedBim = selectBudgetItemMerchant(allBudgetItemMerchants, merchant);
                if (selectedBim == null) {
                    continue;  // User cancelled or invalid selection
                }

                // Show action menu for the selected budget item
                manageSingleBudgetItemMerchant(merchant, selectedBim);

            } catch (CancelException e) {
                done = true;
            }
        }
    }

    /**
     * Manage merchants associated with a budget item.
     * Shows list of associated merchants and allows user to view, update, remove, or add.
     *
     * @param budgetItem The budget item whose merchants to manage
     * @throws Exception if any error occurs
     */
    public void manageBudgetItemMerchants(BudgetItem budgetItem) throws Exception {
        boolean done = false;

        while (!done) {
            try {
                view.say();
                view.say("──── Manage Merchants for " + budgetItem.getDisplayString() + " ────");

                // Get all merchants associated with this budget item
                List<BudgetItemMerchant> allMerchantAssociations =
                    BudgetItemMerchant.getAssignedMerchantsForBudgetItem(budgetItem);

                // Handle empty list
                if (allMerchantAssociations.isEmpty()) {
                    view.say("No merchants currently associated with this budget item.");
                    view.say();

                    String choice = view.selectFromMenu("What would you like to do?",
                            List.of("add merchant", "return"),
                            DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                    if (choice.equals("a")) {
                        addMerchantToBudgetItem(budgetItem);
                    } else {
                        done = true;
                    }
                    continue;
                }

                // Ask what to do
                view.say();
                String choice = view.selectFromMenu("What would you like to do?",
                        List.of("select existing merchant", "add merchant", "done"),
                        DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                if (choice.equals("a")) {
                    addMerchantToBudgetItem(budgetItem);
                    continue;
                } else if (choice.equals("d")) {
                    done = true;
                    continue;
                }

                // User chose to select an existing merchant
                BudgetItemMerchant selectedBim = selectMerchantAssociation(allMerchantAssociations, budgetItem);
                if (selectedBim == null) {
                    continue;  // User cancelled or invalid selection
                }

                // Show action menu for the selected merchant
                manageSingleMerchantForBudgetItem(budgetItem, selectedBim);

            } catch (CancelException e) {
                done = true;
            }
        }
    }

    /**
     * Manage a single budget item-merchant association (from merchant side).
     *
     * @param merchant The merchant
     * @param bim The budget item-merchant association
     * @throws Exception if any error occurs
     */
    private void manageSingleBudgetItemMerchant(Merchant merchant, BudgetItemMerchant bim) throws Exception {
        boolean actionComplete = false;
        BudgetItem item = bim.getBudgetItem();

        while (!actionComplete) {
            view.say();
            view.say("Selected budget item:");
            displayBudgetItemMerchantSummary(bim);

            String action = view.selectFromMenu("What would you like to do with this budget item?",
                    List.of("view details", "update amount/percentage",
                            "remove association", "select another item"),
                    DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

            switch (action) {
                case "v":  // view details
                    viewBudgetItemDetails(bim);
                    break;

                case "u":  // update amount/percentage
                    updateAssociation(merchant, null, bim);
                    actionComplete = true;
                    break;

                case "r":  // remove association
                    removeAssociation(merchant, null, bim);
                    actionComplete = true;
                    break;

                case "s":  // select another item
                case "c":  // cancel
                    actionComplete = true;
                    break;

                default:
                    throw new InvalidEntryException("Unexpected menu option: " + action);
            }
        }
    }

    /**
     * Manage a single merchant-budget item association (from budget item side).
     *
     * @param budgetItem The budget item
     * @param bim The budget item-merchant association
     * @throws Exception if any error occurs
     */
    private void manageSingleMerchantForBudgetItem(BudgetItem budgetItem, BudgetItemMerchant bim) throws Exception {
        boolean actionComplete = false;

        while (!actionComplete) {
            view.say();
            view.say("Selected merchant:");
            displayMerchantAssociationSummary(bim);

            String action = view.selectFromMenu("What would you like to do with this merchant?",
                    List.of("view details", "update amount/percentage",
                            "remove association", "select another merchant"),
                    DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

            switch (action) {
                case "v":  // view details
                    viewMerchantDetails(bim);
                    break;

                case "u":  // update amount/percentage
                    updateAssociation(null, budgetItem, bim);
                    actionComplete = true;
                    break;

                case "r":  // remove association
                    removeAssociation(null, budgetItem, bim);
                    actionComplete = true;
                    break;

                case "s":  // select another merchant
                case "c":  // cancel
                    actionComplete = true;
                    break;

                default:
                    throw new InvalidEntryException("Unexpected menu option: " + action);
            }
        }
    }

    /**
     * Select a budget item-merchant association from a list.
     *
     * @param associations List of associations
     * @param merchant The merchant (for display context)
     * @return The selected association, or null if cancelled
     * @throws Exception if any error occurs
     */
    private BudgetItemMerchant selectBudgetItemMerchant(List<BudgetItemMerchant> associations,
                                                        Merchant merchant) throws Exception {
        view.say();
        view.say("Budget items associated with " + merchant.getName() + ":");
        for (int i = 0; i < associations.size(); i++) {
            BudgetItemMerchant bim = associations.get(i);
            BudgetItem item = bim.getBudgetItem();
            String displayStr = (i + 1) + " - " + item.getDisplayString();
            if (bim.getAmount() != 0.0) {
                displayStr += " [Amount: $" + bim.getAmount() + "]";
            }
            if (bim.getPercentage() != 0) {
                displayStr += " [Percentage: " + bim.getPercentage() + "%]";
            }
            view.say(displayStr);
        }
        view.say();

        Integer selection = view.getResponseInt("Enter the number of the budget item:",
                null, DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

        if (selection == null || selection < 1 || selection > associations.size()) {
            view.say("Invalid selection.");
            return null;
        }

        return associations.get(selection - 1);
    }

    /**
     * Select a merchant association from a list.
     *
     * @param associations List of associations
     * @param budgetItem The budget item (for display context)
     * @return The selected association, or null if cancelled
     * @throws Exception if any error occurs
     */
    private BudgetItemMerchant selectMerchantAssociation(List<BudgetItemMerchant> associations,
                                                         BudgetItem budgetItem) throws Exception {
        view.say();
        view.say("Merchants associated with " + budgetItem.getPayee() + ":");
        for (int i = 0; i < associations.size(); i++) {
            BudgetItemMerchant bim = associations.get(i);
            Merchant merchant = Merchant.getById(bim.getIdMerchant());
            String displayStr = (i + 1) + " - " + merchant.getName();
            if (bim.getAmount() != 0.0) {
                displayStr += " [Amount: $" + bim.getAmount() + "]";
            }
            if (bim.getPercentage() != 0) {
                displayStr += " [Percentage: " + bim.getPercentage() + "%]";
            }
            view.say(displayStr);
        }
        view.say();

        Integer selection = view.getResponseInt("Enter the number of the merchant:",
                null, DO_NOT_ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

        if (selection == null || selection < 1 || selection > associations.size()) {
            view.say("Invalid selection.");
            return null;
        }

        return associations.get(selection - 1);
    }

    /**
     * Display a summary of a budget item-merchant association.
     *
     * @param bim The association
     */
    private void displayBudgetItemMerchantSummary(BudgetItemMerchant bim) {
        BudgetItem item = bim.getBudgetItem();
        String displayStr = "  " + item.getDisplayString();
        if (bim.getAmount() != 0.0) {
            displayStr += " [Amount: $" + bim.getAmount() + "]";
        }
        if (bim.getPercentage() != 0) {
            displayStr += " [Percentage: " + bim.getPercentage() + "%]";
        }
        view.say(displayStr);
    }

    /**
     * Display a summary of a merchant association.
     *
     * @param bim The association
     * @throws Exception if any error occurs
     */
    private void displayMerchantAssociationSummary(BudgetItemMerchant bim) throws Exception {
        Merchant merchant = Merchant.getById(bim.getIdMerchant());
        String displayStr = "  " + merchant.getName();
        if (bim.getAmount() != 0.0) {
            displayStr += " [Amount: $" + bim.getAmount() + "]";
        }
        if (bim.getPercentage() != 0) {
            displayStr += " [Percentage: " + bim.getPercentage() + "%]";
        }
        view.say(displayStr);
    }

    /**
     * View details of a budget item in an association.
     *
     * @param bim The budget item-merchant association
     * @throws Exception if any error occurs
     */
    private void viewBudgetItemDetails(BudgetItemMerchant bim) throws Exception {
        BudgetItem item = bim.getBudgetItem();

        view.say();
        view.say("──── Budget Item Details ────");
        view.say("Category: " + item.getCategory());
        view.say("Payee: " + item.getPayee());
        view.say("Memo: " + (item.getMemo() != null && !item.getMemo().isEmpty() ? item.getMemo() : "(none)"));
        view.say("Amount: $" + item.getAmount() + " " + item.getPeriod());
        view.say("Budget: " + item.getBudget().getName());
        if (bim.getAmount() != 0.0) {
            view.say("Assigned Amount for this Merchant: $" + bim.getAmount());
        }
        if (bim.getPercentage() != 0) {
            view.say("Assigned Percentage for this Merchant: " + bim.getPercentage() + "%");
        }
        view.say();
    }

    /**
     * View details of a merchant in an association.
     *
     * @param bim The budget item-merchant association
     * @throws Exception if any error occurs
     */
    private void viewMerchantDetails(BudgetItemMerchant bim) throws Exception {
        Merchant merchant = Merchant.getById(bim.getIdMerchant());

        view.say();
        view.say("──── Merchant Details ────");
        view.say("Name: " + merchant.getName());
        view.say("Ask Always: " + (merchant.isAskAlways() ? "yes" : "no"));
        if (bim.getAmount() != 0.0) {
            view.say("Assigned Amount for this Budget Item: $" + bim.getAmount());
        }
        if (bim.getPercentage() != 0) {
            view.say("Assigned Percentage for this Budget Item: " + bim.getPercentage() + "%");
        }
        view.say();
    }

    /**
     * Update the amount or percentage for an association.
     *
     * @param merchant The merchant (if managing from merchant side)
     * @param budgetItem The budget item (if managing from budget item side)
     * @param bim The association
     * @throws Exception if any error occurs
     */
    private void updateAssociation(Merchant merchant, BudgetItem budgetItem,
                                   BudgetItemMerchant bim) throws Exception {
        view.say();
        view.say("Current Amount: " + (bim.getAmount() != 0.0 ? "$" + bim.getAmount() : "not set"));
        view.say("Current Percentage: " + (bim.getPercentage() != 0 ? bim.getPercentage() + "%" : "not set"));
        view.say();

        String choice = view.selectFromMenu("What would you like to update?",
                List.of("amount", "percentage", "both", "clear both"),
                DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

        double newAmount = bim.getAmount();
        int newPercentage = bim.getPercentage();

        switch (choice) {
            case "a":  // amount
                String amountStr = view.getResponseString("Enter amount (or 0 to clear):",
                        String.valueOf(bim.getAmount()), ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                newAmount = Double.parseDouble(amountStr);
                break;

            case "p":  // percentage
                newPercentage = view.getResponseNatural("Enter percentage (or 0 to clear):",
                        bim.getPercentage(), ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                if (newPercentage > 100) {
                    view.say("Percentage cannot exceed 100. Setting to 100.");
                    newPercentage = 100;
                }
                break;

            case "b":  // both
                amountStr = view.getResponseString("Enter amount (or 0 to clear):",
                        String.valueOf(bim.getAmount()), ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                newAmount = Double.parseDouble(amountStr);

                newPercentage = view.getResponseNatural("Enter percentage (or 0 to clear):",
                        bim.getPercentage(), ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                if (newPercentage > 100) {
                    view.say("Percentage cannot exceed 100. Setting to 100.");
                    newPercentage = 100;
                }
                break;

            case "c":  // clear both
                newAmount = 0.0;
                newPercentage = 0;
                break;

            default:
                return;  // Cancelled
        }

        // Get the item and merchant for the update
        BudgetItem item = bim.getBudgetItem();
        if (merchant == null) {
            merchant = Merchant.getById(bim.getIdMerchant());
        }

        // Delete old association and create new one with updated values
        BudgetItemMerchant.deleteByItemAndMerchant(item, merchant);
        BudgetItemMerchant newBim = new BudgetItemMerchant(item, merchant, newAmount, newPercentage);
        newBim.save();
        view.say("Association successfully updated.");
    }

    /**
     * Remove an association.
     *
     * @param merchant The merchant (if managing from merchant side)
     * @param budgetItem The budget item (if managing from budget item side)
     * @param bim The association
     * @throws Exception if any error occurs
     */
    private void removeAssociation(Merchant merchant, BudgetItem budgetItem,
                                   BudgetItemMerchant bim) throws Exception {
        BudgetItem item = bim.getBudgetItem();
        if (merchant == null) {
            merchant = Merchant.getById(bim.getIdMerchant());
        }

        view.say();
        view.say("You are about to remove the association between:");
        view.say("  Budget Item: " + item.getDisplayString());
        view.say("  Merchant: " + merchant.getName());
        view.say();

        if (view.getYesOrNo("Are you sure you want to remove this association?")) {
            BudgetItemMerchant.deleteByItemAndMerchant(item, merchant);
            view.say("Association successfully removed.");
        } else {
            view.say("Removal cancelled.");
        }
    }

    /**
     * Add a budget item to a merchant.
     *
     * @param merchant The merchant
     * @throws Exception if any error occurs
     */
    private void addBudgetItemToMerchant(Merchant merchant) throws Exception {
        view.say();
        view.say("──── Add Budget Item to " + merchant.getName() + " ────");

        // Get a budget to search in
        Budget selectedBudget = sessionController.getBudgetFromUser();
        if (selectedBudget == null) {
            return;  // User cancelled
        }

        // Use BudgetController's method to select a budget item
        BudgetController budgetController = new BudgetController(sessionController, view);
        BudgetItem selectedItem = budgetController.selectBudgetItem(selectedBudget);

        if (selectedItem == null) {
            return;  // User cancelled
        }

        // Check if already associated
        BudgetItemMerchant existing = BudgetItemMerchant.getByItemAndMerchant(selectedItem, merchant);
        if (existing != null) {
            view.say("This budget item is already associated with this merchant.");
            return;
        }

        // Get amount/percentage if desired
        double amount = 0.0;
        int percentage = 0;
        boolean setAmountOrPercentage = view.getYesOrNo(
            "Do you want to assign a fixed amount or percentage to this budget item when associated with this merchant?");

        if (setAmountOrPercentage) {
            Object[] result = getAmountAndPercentage();
            amount = (Double) result[0];
            percentage = (Integer) result[1];
        }

        // Create and save the association
        BudgetItemMerchant newBim = new BudgetItemMerchant(selectedItem, merchant, amount, percentage);
        newBim.save();
        view.say("Budget item successfully added to merchant.");
    }

    /**
     * Add a merchant to a budget item.
     *
     * @param budgetItem The budget item
     * @throws Exception if any error occurs
     */
    private void addMerchantToBudgetItem(BudgetItem budgetItem) throws Exception {
        view.say();
        view.say("──── Add Merchant to " + budgetItem.getPayee() + " ────");

        // Use MerchantController's method to select a merchant
        MerchantController merchantController = new MerchantController(sessionController, view, notificationService);
        Merchant selectedMerchant = merchantController.selectMerchantPublic(ALLOW_CREATE);

        if (selectedMerchant == null) {
            return;  // User cancelled
        }

        // Check if already associated
        BudgetItemMerchant existing = BudgetItemMerchant.getByItemAndMerchant(budgetItem, selectedMerchant);
        if (existing != null) {
            view.say("This merchant is already associated with this budget item.");
            return;
        }

        // Get amount/percentage if desired
        double amount = 0.0;
        int percentage = 0;
        boolean setAmountOrPercentage = view.getYesOrNo(
            "Do you want to assign a fixed amount or percentage to this merchant when associated with this budget item?");

        if (setAmountOrPercentage) {
            Object[] result = getAmountAndPercentage();
            amount = (Double) result[0];
            percentage = (Integer) result[1];
        }

        // Create and save the association
        BudgetItemMerchant newBim = new BudgetItemMerchant(budgetItem, selectedMerchant, amount, percentage);
        newBim.save();
        view.say("Merchant successfully added to budget item.");
    }

    /**
     * Get amount and/or percentage from the user.
     *
     * @return Array with [amount (Double), percentage (Integer)]
     * @throws Exception if any error occurs
     */
    private Object[] getAmountAndPercentage() throws Exception {
        double amount = 0.0;
        int percentage = 0;

        String choice = view.selectFromMenu("What would you like to set?",
                List.of("amount", "percentage", "both"),
                DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

        switch (choice) {
            case "a":  // amount
                String amountStr = view.getResponseString("Enter amount:",
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                amount = Double.parseDouble(amountStr);
                break;

            case "p":  // percentage
                percentage = view.getResponseNatural("Enter percentage:",
                        0, ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                if (percentage > 100) {
                    view.say("Percentage cannot exceed 100. Setting to 100.");
                    percentage = 100;
                }
                break;

            case "b":  // both
                amountStr = view.getResponseString("Enter amount:",
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                amount = Double.parseDouble(amountStr);

                percentage = view.getResponseNatural("Enter percentage:",
                        0, ALLOW_NONE, DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);
                if (percentage > 100) {
                    view.say("Percentage cannot exceed 100. Setting to 100.");
                    percentage = 100;
                }
                break;
        }

        return new Object[]{amount, percentage};
    }
}

