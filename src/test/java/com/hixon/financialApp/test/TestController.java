package com.hixon.financialApp.test;


import com.hixon.financialApp.controller.FinancialInstitutionInt;
import com.hixon.financialApp.controller.WellsFargoBankController;
import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlBudgetView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlForecastView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlRegisterView;

import java.sql.DriverManager;
import java.sql.SQLException;


/**
 * Main controller for the command line version of the product:
 */
public class TestController {
    /*
     * Statics and constants:
     */
    //private static final Logger LOGGER = LogManager.getLogger(MainController.class);


    /*
     * Member variables:
     */
    private Register register;
    private Budget budget;
    private Forecast forecast;
    private ViewInt view;
    private NotificationServiceInt notificationService;
    private FinancialInstitutionInt financialInstitution;

    /*
     * Getters and setters:
     */

    public ViewInt getView() {
        return view;
    }


    /*
     * Constructors:
     */
    public TestController(String username, String register, String budget, String forecast, ViewInt view,
                          NotificationServiceInt notificationService) throws Exception {

        // Set up the user, the database connection, the view and the notification service:
        java.sql.Connection dbConnection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/ForecastDatabase", "root", "***REMOVED-CREDENTIAL***");
        Utility.setDbConnection(dbConnection);
        Utility.setUser(User.getByName(username));
        this.view = view;
        this.notificationService = notificationService;

        // then get the register associated with the selected register under test:
        this.register = Register.getByName(register);
        Utility.setRegisterView(new SpreadsheetXmlRegisterView(this.register));

        // and get the budget associated with the selected register under test:
        this.budget = Budget.getByName(budget);
        Utility.setBudgetView(new SpreadsheetXmlBudgetView(this.budget));

        // and get the forecast associated with the selected register under test:
        this.forecast = Forecast.getByName(forecast);
        Utility.setForecastView(new SpreadsheetXmlForecastView(this.forecast));

        // and set the financial institution associated with the selected register under test:
        this.financialInstitution = new WellsFargoBankController(this.register, this.budget, this.forecast, this.view,
                this.notificationService);
    }


    /*
     * Helper methods:
     */

    // Close the connection to the database:
    public void closeDbConnection() throws SQLException {

        // Close the connection to the database:
        System.out.println("\nClose the connection to the database.");
        Utility.getDbConnection().close();
    }

    public NotificationServiceInt getNotificationService() {
        return Utility.getNotificationService();
    }

}  // End class TestController.
