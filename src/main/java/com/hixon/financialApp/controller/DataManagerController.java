package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetUtilities;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;

import java.util.List;

import static com.hixon.financialApp.view.base.ViewInt.*;

/**
 * This class enables the user to manage various entities such as budget items, merchants,
 * transactions, forecast transactions, and financial institutions.
 *
 * <p>The DataManagerController operates in a flexible context mode where register, budget,
 * and forecast are optional and only required for specific entity types:
 * <ul>
 *   <li>Budget items: Can work across all budgets or within a specific budget context</li>
 *   <li>Merchants: Global entities not tied to a specific register/budget</li>
 *   <li>Transactions: Require a specific register context</li>
 *   <li>Forecast transactions: Require a specific forecast context</li>
 *   <li>Financial institutions: Global entities not tied to a specific register/budget</li>
 * </ul>
 * </p>
 */
public class DataManagerController {

    /*
     * Fields of the DataManagerController:
     */
    private Register register;
    private Budget budget;
    private Forecast forecast;
    private ViewInt view;
    private NotificationServiceInt notificationService;
    private BudgetController budgetController;
    
    
    /*
     * Constructors and destructor for the data manager controller:
     */
    /**
     * Create a data manager controller with optional register, budget, and forecast context.
     * These can be null and will be prompted for only when needed by specific entity operations.
     *
     * @param register The register context (can be null)
     * @param budget The budget context (can be null)
     * @param forecast The forecast context (can be null)
     * @param view The view interface for user interaction (required)
     * @param notificationService The notification service (required)
     */
    DataManagerController(Register register, Budget budget, Forecast forecast, ViewInt view, 
                          NotificationServiceInt notificationService) {
        this.register = register;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
    }
    
    
    /*
     * Main methods of the data manager controller:
     */
    /**
     * This routine enables the user to select which entity they want to manage, and delegates to the appropriate controller.
     * For entity types that require specific context (register, budget, forecast), those will be prompted for
     * only when needed.
     *
     * @return true if management completed successfully
     * @throws Exception if any error occurs during entity management
     */
    public boolean manageData() throws Exception {
        boolean done = false;
        try {
            while (!done) {
                String prompt = "What type of entity would you like to manage?";
                List<String> entityOptions = List.of(
                    "Budget items",
                    "Merchants",
                    "Transactions",
                    "Forecast transactions",
                    "Financial institutions"
                );
                String option = view.selectFromMenu(prompt, entityOptions, DO_NOT_ALLOW_NONE,
                        ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
                switch(option) {
                    case "b":
                        // Delegate to BudgetController
                        budgetController = new BudgetController(register, budget, forecast, view, notificationService);
                        budgetController.manageBudgetItems();
                        break;
                    case "m":
                        // Merchants are global entities - no specific context needed
                        // TODO: Delegate to MerchantController
                        // merchantController.manageMerchants();
                        view.say("Merchant management not yet implemented.");
                        break;
                    case "t":
                        // Transactions require a register context
                        ensureRegisterContext();

                        // TODO: Delegate to TransactionController
                        // transactionController.manageTransactions();
                        view.say("Transaction management not yet implemented.");
                        break;
                    case "f":
                        // Forecast transactions require a forecast context (which includes budget)
                        ensureForecastContext();

                        // TODO: Delegate to ForecastTransactionController
                        // forecastTransactionController.manageForecastTransactions();
                        view.say("Forecast transaction management not yet implemented.");
                        break;
                    case "i":
                        // Financial institutions are global entities - no specific context needed
                        // TODO: Delegate to FinancialInstitutionController
                        // financialInstitutionController.manageFinancialInstitutions();
                        view.say("Financial institution management not yet implemented.");
                        break;
                    case "q":
                        done = true;
                        break;
                    default:
                        throw new InvalidEntryException("selectFromFirstLetterList returned an option that wasn't in the option list.");
                }
            }
        } catch (CancelException e) {
            view.say("Operation cancelled by user.");
        }
        return true;
    }

    /**
     * Ensures that a register, budget, and forecast context are available.
     * Prompts the user to select them if not already set.
     *
     * @throws Exception if an error occurs while selecting the register, budget, or forecast
     */
    private void ensureRegisterContext() throws Exception {
        if (register == null) {
            register = RegisterController.selectRegister(view);
            // Also ensure budget and forecast since register implies those
            ensureBudgetContext();
        }
    }

    /**
     * Ensures that a budget context is available.
     * Prompts the user to select one if not already set.
     *
     * @throws Exception if an error occurs while selecting the budget
     */
    private void ensureBudgetContext() throws Exception {
        if (budget == null && register != null) {
            budget = Budget.getById(register.getBudgetID());
        } else if (budget == null) {
            // No register context, so select budget directly
            view.say("Please select a budget to work with:");
            List<Budget> availableBudgets = BudgetUtilities.getAllBudgets();
            if (availableBudgets.isEmpty()) {
                throw new Exception("No budgets available. Please create a budget first.");
            }
            budget = view.selectByNameFromList("Select Budget", availableBudgets, DO_NOT_ALLOW_NONE,
                    ALLOW_CANCEL, ALLOW_QUIT, DO_NOT_ALLOW_SKIP);
        }
    }

    /**
     * Ensures that a forecast context is available (including budget).
     * Prompts the user to select them if not already set.
     *
     * @throws Exception if an error occurs while selecting the forecast
     */
    private void ensureForecastContext() throws Exception {
        ensureBudgetContext();
        if (forecast == null) {
            forecast = Forecast.selectForecast(budget);
        }
    }
}
