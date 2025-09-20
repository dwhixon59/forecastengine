package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.view.base.ViewInt;

/*
 * This class enables the user to manage budget items, e.g. add, update, delete, etc.
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
     * Create a data manager controller:
     *
     * @param register
     * @param budget
     * @param forecast
     * @param view
     * @param notificationService
     * @return
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
     *
     * @return true if management completed successfully
     * @throws Exception
     */
    public boolean manageEntities() throws Exception {
        boolean done = false;
        while (!done) {
            view.say();
            String prompt = "Which entity would you like to manage? (b-budget items, m-merchants, t-transactions, f-forecast transactions, i-financial institutions, q-quit)";
            String option = view.selectFromFirstLetterList(prompt, "b,m,t,f,i,q");
            switch(option) {
                case "b":
                    // Delegate to BudgetController
                    budgetController = new BudgetController(register, budget, forecast, view, notificationService);
                    budgetController.manageBudgetItems();
                    break;
                case "m":
                    // TODO: Delegate to MerchantController
                    // merchantController.manageMerchants();
                    view.say("Merchant management not yet implemented.");
                    break;
                case "t":
                    // TODO: Delegate to TransactionController
                    // transactionController.manageTransactions();
                    view.say("Transaction management not yet implemented.");
                    break;
                case "f":
                    // TODO: Delegate to ForecastTransactionController
                    // forecastTransactionController.manageForecastTransactions();
                    view.say("Forecast transaction management not yet implemented.");
                    break;
                case "i":
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
        return true;
    }
}
