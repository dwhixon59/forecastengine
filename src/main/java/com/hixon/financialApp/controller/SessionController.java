package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.financialinstitution.FinancialInstitutionInt;
import com.hixon.financialApp.model.financialinstitution.WellsFargoBank;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.FinancialAppException;
import com.hixon.financialApp.view.base.BudgetViewInt;
import com.hixon.financialApp.view.base.ForecastViewInt;
import com.hixon.financialApp.view.base.RegisterViewInt;
import com.hixon.financialApp.view.base.ViewInt;
import com.hixon.financialApp.view.excel.ExcelForecastView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlBudgetView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlForecastView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlRegisterView;

import java.util.List;

/**
 * SessionController manages the shared application state including register, budget, and forecast objects.
 * This controller provides centralized access to these core model objects and ensures they are properly
 * initialized and associated with their respective views.
 *
 * This controller can be used across multiple controllers that need access to the current session's
 * register, budget, and forecast data.
 */
public class SessionController {

    /*
     * Member variables:
     */
    private Register register = null;
    private Budget budget = null;
    private Forecast forecast = null;
    private FinancialInstitutionInt financialInstitution = null;
    private ViewInt view;
    private NotificationServiceInt notificationService;

    // Controllers:
    private BudgetController budgetController;
    private MerchantController merchantController;
    private ForecastController forecastController;

    // Specialized views for interfacing with external agents:
    private RegisterViewInt registerView;
    private BudgetViewInt budgetView;
    private ForecastViewInt forecastView;


    /*
     * Constructors:
     */

    /**
     * Creates a new SessionController with the specified view and notification service.
     *
     * @param view The view interface for user interaction
     * @param notificationService The notification service for sending notifications
     */
    public SessionController(ViewInt view, NotificationServiceInt notificationService) {
        this.view = view;
        this.notificationService = notificationService;
    }

    /**
     * Creates a new SessionController with pre-initialized register, budget, and forecast objects.
     *
     * @param register The register to use in this session
     * @param budget The budget to use in this session
     * @param forecast The forecast to use in this session
     * @param view The view interface for user interaction
     * @param notificationService The notification service for sending notifications
     */
    public SessionController(Register register, Budget budget, Forecast forecast, ViewInt view,
                           NotificationServiceInt notificationService) {
        this.register = register;
        this.budget = budget;
        this.forecast = forecast;
        this.view = view;
        this.notificationService = notificationService;
    }


    /*
     * Getters and setters:
     */

    public Register getRegister() {
        return register;
    }

    public void setRegister(Register register) {
        this.register = register;
    }

    public Budget getBudget() {
        return budget;
    }

    public void setBudget(Budget budget) {
        this.budget = budget;
    }

    public Forecast getForecast() {
        return forecast;
    }

    public void setForecast(Forecast forecast) {
        this.forecast = forecast;
    }

    public FinancialInstitutionInt getFinancialInstitution() {
        return financialInstitution;
    }

    public void setFinancialInstitution(FinancialInstitutionInt financialInstitution) {
        this.financialInstitution = financialInstitution;
    }

    public ViewInt getView() {
        return view;
    }

    public void setView(ViewInt view) {
        this.view = view;
    }

    public NotificationServiceInt getNotificationService() {
        return notificationService;
    }

    public void setNotificationService(NotificationServiceInt notificationService) {
        this.notificationService = notificationService;
    }

    public BudgetController getBudgetController() {
        return budgetController;
    }

    public void setBudgetController(BudgetController budgetController) {
        this.budgetController = budgetController;
    }

    public MerchantController getMerchantController() {
        return merchantController;
    }

    public void setMerchantController(MerchantController merchantController) {
        this.merchantController = merchantController;
    }

    public ForecastController getForecastController() {
        return forecastController;
    }

    public void setForecastController(ForecastController forecastController) {
        this.forecastController = forecastController;
    }

    public RegisterViewInt getRegisterView() {
        return registerView;
    }

    public void setRegisterView(RegisterViewInt registerView) {
        this.registerView = registerView;
    }

    public BudgetViewInt getBudgetView() {
        return budgetView;
    }

    public void setBudgetView(BudgetViewInt budgetView) {
        this.budgetView = budgetView;
    }

    public ForecastViewInt getForecastView() {
        return forecastView;
    }

    public void setForecastView(ForecastViewInt forecastView) {
        this.forecastView = forecastView;
    }


    /*
     * Public methods:
     */

    /**
     * Get the register, budget, and forecast associated with the selected register that the user wants to work with.
     * This method ensures that all three core model objects (register, budget, forecast) are properly initialized
     * and associated with their respective views.
     *
     * If a register has not been selected, this method will:
     * 1. Prompt the user to select a register
     * 2. Initialize the financial institution controller
     * 3. Load the budget associated with the selected register
     * 4. Load the forecast associated with the budget
     * 5. Set up the appropriate views for each object
     *
     * If a register has already been selected, this method will only initialize budget and/or forecast
     * if they are null.
     *
     * @throws FinancialAppException Throws any of the app exceptions that can be thrown by the methods called by this.
     * @throws Exception May throw various low level exceptions like SQLException, etc.
     */
    public void getRegisterBudgetForecast() throws FinancialAppException, Exception {

        // If a register has not been selected:
        if (register == null) {

            // then get the register associated with the selected register that the user wants to work with:
            register = RegisterController.selectRegister(view);
            registerView = new SpreadsheetXmlRegisterView(register);

            // and get the budget associated with the selected register that the user wants to work with:
            budget = Budget.getById(register.getBudgetID());
            budgetView = new SpreadsheetXmlBudgetView(budget);

            // and get the forecast associated with the selected register that the user wants to work with:
            forecast = Forecast.selectForecast(budget);
            //forecastView = new SpreadsheetXmlForecastView(forecast);
            forecastView = new ExcelForecastView(forecast);

            // and set the financial institution associated with the selected register (after forecast is retrieved)
            financialInstitution = new WellsFargoBank(register, budget, forecast, view, notificationService);
        }
        else {
            // If the budget is null, then get the budget associated with the selected register the user wants to work
            // with:
            if (budget == null) {
                budget = Budget.getById(register.getBudgetID());
                budgetView = new SpreadsheetXmlBudgetView(budget);
            }

            // If the forecast is null, then get the forecast associated with the selected register that the user wants
            // to work with:
            if (forecast == null) {
                forecast = Forecast.selectForecast(budget);
                forecastView = new SpreadsheetXmlForecastView(forecast);
            }
        }
    }

    /**
     * Clears the current session data, resetting register, budget, forecast, and financial institution to null.
     * This is useful when switching between different registers or starting a new session.
     */
    public void clearSession() {
        this.register = null;
        this.budget = null;
        this.forecast = null;
        this.financialInstitution = null;
    }

    /**
     * Checks if a register has been selected for the current session.
     *
     * @return true if a register is selected, false otherwise
     */
    public boolean hasRegister() {
        return register != null;
    }

    /**
     * Checks if a budget has been loaded for the current session.
     *
     * @return true if a budget is loaded, false otherwise
     */
    public boolean hasBudget() {
        return budget != null;
    }

    /**
     * Checks if a forecast has been loaded for the current session.
     *
     * @return true if a forecast is loaded, false otherwise
     */
    public boolean hasForecast() {
        return forecast != null;
    }

    /**
     * Checks if all core model objects (register, budget, forecast) are initialized.
     *
     * @return true if register, budget, and forecast are all non-null, false otherwise
     */
    public boolean isFullyInitialized() {
        return register != null && budget != null && forecast != null;
    }

    /**
     * Gets all budgets available to the current user.
     * This method retrieves all budgets from the database.
     *
     * @return List of all Budget objects
     * @throws Exception if an error occurs retrieving budgets
     */
    public List<Budget> getUserBudgets() throws Exception {
        return com.hixon.financialApp.model.budget.BudgetUtilities.getAllBudgets();
    }

    /**
     * Prompts the user to select a budget from available budgets.
     * Uses the view interface to present budget options to the user.
     *
     * @return The selected Budget, or null if cancelled
     * @throws Exception if an error occurs during budget selection
     */
    public Budget getBudgetFromUser() throws Exception {
        try {
            List<Budget> budgets = getUserBudgets();
            if (budgets.isEmpty()) {
                view.say("No budgets available.");
                return null;
            }

            if (budgets.size() == 1) {
                return budgets.get(0);
            }

            Budget defaultBudget = budget != null ? budget : budgets.get(0);
            return view.selectByNameFromList("Select Budget", budgets, defaultBudget,
                    ViewInt.DO_NOT_ALLOW_NONE, ViewInt.DO_NOT_SHOW_CANCEL_QUIT_SKIP,
                    ViewInt.ALLOW_CANCEL, ViewInt.ALLOW_QUIT, ViewInt.DO_NOT_ALLOW_SKIP,
                    () -> "Select a budget to work with");
        } catch (CancelException | QuitException e) {
            return null;
        }
    }
}
