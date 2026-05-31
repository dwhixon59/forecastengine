package com.hixon.financialApp.test;


import com.hixon.financialApp.controller.SessionController;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.financialinstitution.FinancialInstitutionInt;
import com.hixon.financialApp.model.financialinstitution.WellsFargoBank;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.DatabaseConnectionManager;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlBudgetView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlForecastView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlRegisterView;

import java.sql.SQLException;


/**
 * Test controller for setting up test fixtures with register, budget, and forecast objects.
 */
public class TestController {
    /*
     * Statics and constants:
     */
    //private static final Logger LOGGER = LogManager.getLogger(MainController.class);


    /*
     * Member variables:
     */
    private SessionController sessionController;

    /*
     * Getters and setters:
     */

    public ViewInt getView() {
        return sessionController.getView();
    }

    public SessionController getSessionController() {
        return sessionController;
    }

    public Register getRegister() {
        return sessionController.getRegister();
    }

    public Budget getBudget() {
        return sessionController.getBudget();
    }

    public Forecast getForecast() {
        return sessionController.getForecast();
    }

    public FinancialInstitutionInt getFinancialInstitution() {
        return sessionController.getFinancialInstitution();
    }

    public NotificationServiceInt getNotificationService() {
        return sessionController.getNotificationService();
    }


    /*
     * Constructors:
     */
    public TestController(String username, String register, String budget, String forecast, ViewInt view,
                          NotificationServiceInt notificationService) throws Exception {

        // Set up the user, the database connection manager, and the view:
        DatabaseConnectionManager mgr = new DatabaseConnectionManager(
                "jdbc:mysql://localhost:3306/ForecastDatabase", "root", "***REMOVED-CREDENTIAL***");
        Utility.setConnectionManager(mgr);
        Utility.setUser(User.getByName(username));
        Utility.setView(view);

        // Create the session controller:
        this.sessionController = new SessionController(view, notificationService);

        // Get the register associated with the test:
        Register testRegister = Register.getByName(register);
        sessionController.setRegister(testRegister);
        sessionController.setRegisterView(new SpreadsheetXmlRegisterView(testRegister));

        // Get the budget associated with the test:
        Budget testBudget = Budget.getByName(budget);
        sessionController.setBudget(testBudget);
        sessionController.setBudgetView(new SpreadsheetXmlBudgetView(testBudget));

        // Get the forecast associated with the test:
        Forecast testForecast = Forecast.getByName(forecast);
        sessionController.setForecast(testForecast);
        sessionController.setForecastView(new SpreadsheetXmlForecastView(testForecast));

        // Set the financial institution associated with the test:
        // Create a temporary SessionController for WellsFargoBank initialization
        SessionController tempSession = new SessionController(testRegister, testBudget, testForecast, view, notificationService);
        FinancialInstitutionInt testFinancialInstitution = new WellsFargoBank(tempSession);
        sessionController.setFinancialInstitution(testFinancialInstitution);
    }


    /*
     * Helper methods:
     */

    // Close the connection to the database:
    public void closeDbConnection() throws SQLException {

        // Close the connection to the database via the manager:
        System.out.println("\nClose the connection to the database.");
        Utility.closeConnectionManager();
    }

}  // End class TestController.
