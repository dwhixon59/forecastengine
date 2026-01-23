package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.budget.BudgetUtilities;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static com.hixon.financialApp.view.base.ViewInt.*;

/**
 * Controller for managing budgets - creating, viewing, updating, and deleting budgets.
 *
 * <p>This controller provides comprehensive budget management capabilities including:
 * <ul>
 *   <li>Creating new budgets</li>
 *   <li>Viewing budget details including associated registers</li>
 *   <li>Updating budget names</li>
 *   <li>Deleting budgets (with appropriate safeguards)</li>
 * </ul>
 */
public class BudgetManagementController {

    private final SessionController sessionController;
    private final ViewInt view;
    private final NotificationServiceInt notificationService;

    /**
     * Creates a new BudgetManagementController.
     *
     * @param sessionController The session controller for accessing view and notification services
     */
    public BudgetManagementController(SessionController sessionController) {
        this.sessionController = sessionController;
        this.view = sessionController.getView();
        this.notificationService = sessionController.getNotificationService();
    }

    /**
     * Main entry point for budget management.
     * Provides a menu for creating, viewing, updating, and deleting budgets.
     *
     * @throws Exception if any error occurs during budget management
     */
    public void manageBudgets() throws Exception {
        boolean done = false;

        while (!done) {
            try {
                // Step 1: Ask if user wants to select existing or create new
                view.sayH1("Manage Budgets");
                String mainAction = view.selectFromMenu("What would you like to do?",
                        List.of("select existing budget", "create new budget"),
                        DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP,
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                Budget selectedBudget = null;

                if (mainAction.equals("s")) {
                    // Select existing budget
                    selectedBudget = selectBudgetForManagement();
                    if (selectedBudget == null) {
                        // User cancelled selection
                        continue;
                    }
                } else if (mainAction.equals("c")) {
                    // Create new budget
                    selectedBudget = createNewBudget();
                    if (selectedBudget == null) {
                        // User cancelled creation
                        continue;
                    }
                    // After creating, don't show the action menu - just loop back
                    continue;
                }

                if (selectedBudget == null) {
                    // User cancelled - exit
                    done = true;
                    continue;
                }

                // Step 2: Show action menu for the selected budget
                boolean actionComplete = false;
                while (!actionComplete) {
                    // Display the selected budget
                    view.say();
                    view.say("Selected budget:");
                    view.say("  " + selectedBudget.getName());

                    // Ask what to do with this budget
                    String action = view.selectFromMenu("What would you like to do with this budget?",
                            List.of("view details", "update this budget", "delete this budget", "select another budget"),
                            DO_NOT_ALLOW_NONE, SHOW_CANCEL_QUIT_SKIP,
                            ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);

                    switch (action) {
                        case "v":  // view details
                            view.say();
                            view.say("Budget Details:");
                            view.say("──────────────────────────────────────");
                            displayBudgetDetails(selectedBudget);
                            view.say("──────────────────────────────────────");
                            break;

                        case "u":  // update this budget
                            Budget updatedBudget = updateBudgetFromUser(selectedBudget);
                            if (updatedBudget != null) {
                                updatedBudget.update();
                                view.say("✓ Budget successfully updated.");
                                // Update the selected budget reference for the next iteration
                                selectedBudget = updatedBudget;
                            }
                            break;

                        case "d":  // delete this budget
                            view.say("\nYou are about to delete:");
                            view.say("  Budget: " + selectedBudget.getName());

                            // Check if budget has associated registers
                            List<Register> registers = selectedBudget.getRegisters();
                            if (registers != null && !registers.isEmpty()) {
                                view.say("\nWarning: This budget has " + registers.size() + " associated register(s):");
                                for (Register reg : registers) {
                                    view.say("  - " + reg.getName());
                                }
                                view.say("\nYou must reassign or delete these registers before deleting this budget.");
                                break;
                            }

                            if (view.getYesOrNo("Are you sure you want to delete this budget? This action cannot be undone.")) {
                                try {
                                    selectedBudget.delete();
                                    view.say("✓ Budget successfully deleted.");
                                    actionComplete = true;  // Exit to budget selection
                                } catch (Exception e) {
                                    view.say("Error deleting budget: " + e.getMessage());
                                }
                            } else {
                                view.say("Deletion cancelled.");
                            }
                            break;

                        case "s":  // select another budget
                            actionComplete = true;  // Exit to budget selection
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
                // Exit the manage budgets loop
                done = true;
            }
        }
    }

    /**
     * Prompts the user to select an existing budget for management.
     *
     * @return The selected Budget, or null if user cancelled
     * @throws Exception if an error occurs during selection
     */
    private Budget selectBudgetForManagement() throws Exception {
        List<Budget> budgets = BudgetUtilities.getAllBudgets();

        if (budgets.isEmpty()) {
            view.say("No budgets exist. Please create a budget first.");
            return null;
        }

        try {
            return view.selectByNameFromList("Select a budget to manage", budgets,
                    DO_NOT_ALLOW_NONE, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
        } catch (CancelException e) {
            return null;
        }
    }

    /**
     * Creates a new budget by prompting the user for budget details.
     *
     * @return The newly created Budget, or null if user cancelled
     * @throws Exception if an error occurs during creation
     */
    private Budget createNewBudget() throws Exception {
        view.sayH1("Create New Budget");
        view.say("Let's create a new budget. You'll be asked to provide details.");
        view.say();

        Budget newBudget = getBudgetFromUser(null);

        if (newBudget == null) {
            // User cancelled
            return null;
        }

        // Confirm before creating
        view.say();
        view.say("Please review the budget details:");
        view.say("──────────────────────────────────────");
        view.say("Budget Name: " + newBudget.getName());
        view.say("──────────────────────────────────────");

        if (view.getYesOrNo("Is this information correct? The budget will be created.")) {
            try {
                newBudget.setId(UUID.randomUUID());
                newBudget.insert();
                view.say("✓ Budget '" + newBudget.getName() + "' created successfully.");
                return newBudget;
            } catch (Exception e) {
                view.say("Error creating budget: " + e.getMessage());
                e.printStackTrace();
                throw new ControllerException("Failed to create budget");
            }
        } else {
            view.say("Budget creation cancelled.");
            return null;
        }
    }

    /**
     * Prompts the user to enter or update budget information.
     *
     * @param existingBudget The budget to update (null if creating new)
     * @return A Budget object with the user's input, or null if user cancelled
     * @throws Exception if an error occurs during input
     */
    private Budget getBudgetFromUser(Budget existingBudget) throws Exception {
        view.sayH1("Budget Entry");
        view.say("Please enter the details for the budget. You can cancel or quit at any time by entering 'C' or 'Q'.");
        view.say("Press <enter> to accept the default value shown in brackets [].");
        view.say();

        Budget budget = (existingBudget != null) ? existingBudget : new Budget();

        // Get budget name
        view.sayH2("Basic Information");
        String defaultName = (existingBudget != null) ? existingBudget.getName() : "";
        String budgetName = view.getResponseString("Budget Name", defaultName, DO_NOT_ALLOW_NONE,
                DO_NOT_SHOW_CANCEL_QUIT_SKIP, ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP, null);

        if (budgetName == null || budgetName.trim().isEmpty()) {
            view.say("Budget name is required.");
            return null;
        }

        budget.setBudgetName(budgetName.trim());

        return budget;
    }

    /**
     * Updates an existing budget by prompting for new values.
     *
     * @param budget The budget to update
     * @return The updated Budget, or null if user cancelled
     * @throws Exception if an error occurs during update
     */
    private Budget updateBudgetFromUser(Budget budget) throws Exception {
        Budget updatedBudget = getBudgetFromUser(budget);

        if (updatedBudget == null) {
            return null;
        }

        // Confirm before updating
        view.say();
        view.say("Please review the updated budget details:");
        view.say("──────────────────────────────────────");
        view.say("Budget Name: " + updatedBudget.getName());
        view.say("──────────────────────────────────────");

        if (view.getYesOrNo("Is this information correct? The budget will be updated.")) {
            return updatedBudget;
        } else {
            view.say("Update cancelled.");
            return null;
        }
    }

    /**
     * Displays detailed information about a budget.
     *
     * @param budget The budget to display
     * @throws Exception if an error occurs retrieving budget details
     */
    private void displayBudgetDetails(Budget budget) throws Exception {
        view.say("Budget Name: " + budget.getName());
        view.say("Budget ID: " + budget.getId());

        // Display associated registers
        try {
            List<Register> registers = budget.getRegisters();
            view.say();
            if (registers != null && !registers.isEmpty()) {
                view.say("Associated Registers (" + registers.size() + "):");
                for (Register register : registers) {
                    view.say("  - " + register.getName() + " (" + register.getNickname() + ")");
                }
            } else {
                view.say("No registers associated with this budget.");
            }
        } catch (Exception e) {
            view.say("Error retrieving registers: " + e.getMessage());
        }
    }
}

