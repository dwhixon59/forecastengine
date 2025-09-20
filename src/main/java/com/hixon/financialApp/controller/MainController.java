package com.hixon.financialApp.controller;


import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastEngine;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.FinancialAppException;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import com.hixon.financialApp.view.excel.EnvelopeReport;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlBudgetView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlForecastView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlRegisterView;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Calendar;

import static com.hixon.financialApp.utility.Utility.getBudgetController;
import static com.hixon.financialApp.utility.Utility.getBudgetView;


/**
 * Main controller for the command line version of the product:
 */
public class MainController {
    /*
     * Statics and constants:
     */
    //private static final Logger LOGGER = LogManager.getLogger(MainController.class);


    /*
     * Member variables:
     */
    private Register register = null;
    private Budget budget = null;
    private Forecast forecast = null;
    private FinancialInstitutionInt financialInstitution = null;
    private ViewInt view;
    private NotificationServiceInt notificationService;


    /*
     * Constructors:
     */
    public MainController(String username, Connection dbConnection, ViewInt view,
                          NotificationServiceInt notificationService) throws SQLException, EntityException {

        Utility.setDbConnection(dbConnection);
        Utility.setUser(User.getByName(username));
        Utility.setView(view);
        Utility.setNotificationService(notificationService);

        this.view = view;
        this.notificationService = notificationService;
    }


    /*
     * Helper methods:
     */
    /**
     * Get the register, budget, and forecast associated with the selected register that the user wants to work with:
     *
     * @throws FinancialAppException Throws any of the app exceptions that can be thrown by the methods called by this.
     * @throws Exception May throw various low level exceptions like SQLException, etc.
     */
    private void getRegisterBudgetForecast() throws FinancialAppException, Exception {

        // If a register has not been selected:
        if (register == null) {

            // then get the register associated with the selected register that the user wants to work with:
            register = RegisterController.selectRegister(view);
            Utility.setRegisterView(new SpreadsheetXmlRegisterView(register));

            // and set the financial institution associated with the selected register
            financialInstitution = new WellsFargoBankController(register, budget, forecast, view, notificationService);

            // and get the budget associated with the selected register that the user wants to work with:
            budget = Budget.getById(register.getBudgetID());
            Utility.setBudgetView(new SpreadsheetXmlBudgetView(budget));

            // and get the forecast associated with the selected register that the user wants to work with:
            forecast = Forecast.selectForecast(budget);
            Utility.setForecastView(new SpreadsheetXmlForecastView(forecast));
        }
        else {
            // If the budget is null, then get the budget associated with the selected register the user wants to work
            // with:
            if (budget == null) {
                budget = Budget.getById(register.getBudgetID());
                Utility.setBudgetView(new SpreadsheetXmlBudgetView(budget));
            }

            // If the forecast is null, then get the forecast associated with the selected register that the user wants
            // to work with:
            if (forecast == null) {
                forecast = Forecast.selectForecast(budget);
                Utility.setForecastView(new SpreadsheetXmlForecastView(forecast));
            }
        }
    }

    /**
     * Run the app with the given user, database connection, resolver, notification service and goals:
     */
    public void run(String[] goals) throws SQLException {
        try {
             // Create the various sub controllers:
            RegisterController registerController;
            BudgetController budgetController;
            ForecastController forecastController;
            ForecastTransactionController forecastTransactionController;
            ImportController importController;
            DataManagerController dataManagerController;

            // Process the goals:
            Calendar startDate;
            boolean inSync;
            for (int i = 0; i < goals.length; i++) {
                switch (goals[i]) {
                    case "createEnvelopeReport":
                        view.say("\n\n========================================================================");
                        view.say("CREATE ENVELOPE REPORT");

                        // Call the EnvelopeReport class to create the report:
                        EnvelopeReport envelopeReport = new EnvelopeReport(Utility.getDbConnection());
                        envelopeReport.createReport();

                        // Notify the user that the report was created:
                        view.say("The envelope report was successfully created.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "processUncategorizedTransactions":
                        view.say("\n\n========================================================================");
                        view.say("REPROCESS UNCATEGORIZED TRANSACTIONS");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();
                        registerController = new RegisterController(register, financialInstitution, budget, forecast,
                                view, notificationService);
                        forecastController = new ForecastController(register, budget, forecast, view,
                                notificationService);

                        // Process the uncategorized transactions:
                        inSync = registerController.processUncategorizedTransactions();
                        if (!inSync) {
                            forecastController.updateForecast();
                            view.say("\nThe long term forecast was successfully updated.");
                        }
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "processUnreconciledTransactions":
                        view.say("\n\n========================================================================");
                        view.say("REPROCESS UNRECONCILED TRANSACTIONS");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();
                        registerController = new RegisterController(register, financialInstitution, budget, forecast,
                                view, notificationService);
                        forecastController = new ForecastController(register, budget, forecast, view,
                                notificationService);

                        // Process the unreconciled transactions:
                        inSync = registerController.processUnreconciledTransactions();
                        if (!inSync) {
                            forecastController.updateForecast();
                            view.say("\nThe long term forecast was successfully updated.");
                        }
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "importRegisterTransactions":
                        view.say("\n\n========================================================================");
                        view.say("IMPORT REGISTER TRANSACTIONS");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();
                        importController = new ImportController(register, financialInstitution, budget, forecast, view,
                                notificationService);

                        // Import the register transactions:
                        inSync = importController.importCsvRegisterTransactionFile();
                        if (!inSync) {
                            forecastController = new ForecastController(register, budget, forecast, view,
                                    notificationService);
                            forecastController.updateForecast();
                            view.say("\nThe long term forecast was successfully updated.");
                        }
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "importProvisionalRegisterTransactions":
                        view.say("\n\n========================================================================");
                        view.say("IMPORT PROVISIONAL TRANSACTIONS");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();
                        importController = new ImportController(register, financialInstitution, budget, forecast, view,
                                notificationService);

                        // Import the provisional transactions:
                        inSync = importController.importCsvProvisionalTransactionFile();
                        view.say("The provisional transactions were successfully imported.");
                        if (!inSync) {
                            forecastController = new ForecastController(register, budget, forecast, view,
                                    notificationService);
                            forecastController.updateForecast();
                            view.say("The long term forecast was successfully updated.");
                        }
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "verifyRegisterBalance":
                        view.say("\n\n========================================================================");
                        view.say("Verify register balance and update if necessary.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();
                        registerController = new RegisterController(register, financialInstitution, budget, forecast,
                                view, notificationService);

                        // Verify the register balance:
                        if (!registerController.verifyRegisterBalance(register)) {
                            view.say("The balance of the register " + register.getName() + " was " +
                                    "successfully updated.");
                        }
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "importBudgetItems":
                        view.say("\n\n========================================================================");
                        view.say("Importing the budget items.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();
                        importController = new ImportController(register, financialInstitution, budget, forecast, view,
                                notificationService);

                        // Import the budget items:
                        importController.importCsvBudgetItemFile("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\" +
                                "Finances\\Expenses\\BudgetItems.csv");
                        view.say("The budget items were successfully imported.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "manageData":
                        view.say("\n\n========================================================================");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Manage the budget items:
                        dataManagerController = new DataManagerController(register, budget, forecast, view,
                                notificationService);
                        inSync = dataManagerController.manageEntities();

                        // Update the forecast if necessary:
                        if (!inSync) {
                            forecastController = new ForecastController(register, budget, forecast, view,
                                    notificationService);1

                            forecastController.updateForecast();
                            view.say("The long term forecast was successfully updated.");
                        }
                        view.say("Manage budget items complete.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderBudgetSummaryReport":
                        view.say("\n\n========================================================================");
                        view.say("Rendering the Budget Summary Report.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the budget summary report:
                        getBudgetView().renderBudgetSummaryReport();
                        view.say("Successfully rendered the New Budget Summary Report.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderRegister":
                        view.say("\n\n========================================================================");
                        view.say("Rendering the register.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the register:
                        startDate = view.getStartDate();
                        Utility.getRegisterView().renderTransactionReport(startDate);
                        view.say("The register was successfully rendered");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderSpendingReport":
                        view.say("\n\n========================================================================");
                        view.say("Rendering the spending report.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the spending report:
                        getBudgetView().renderSpendingReportForMonth(Calendar.getInstance(), budget);
                        view.say("The spending report was successfully rendered");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderSpendingReportForMonth":
                        view.say("\n\n========================================================================");
                        view.say("Rendering the spending report.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the spending report for the specified month
                        Calendar month = getBudgetController().getSpendingReportMonth();
                        getBudgetView().renderSpendingReportForMonth(month, budget);
                        view.say("The spending report was successfully rendered");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "createForecast":
                        view.say("\n\n========================================================================");
                        view.say("Create the forecast.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Create the forecast:
                        ForecastEngine forecastEngine = new ForecastEngine();
                        forecastController = new ForecastController(register, budget, forecast, view, notificationService);
                        startDate = forecastController.askStartDate();
                        double startingBalance = 0;
                        int numberOfMonths = 12;
                        int minimumBalance = 1000;
                        forecast = new Forecast(budget, startDate, numberOfMonths, startingBalance, minimumBalance);
                        forecastEngine.generateForecast(forecast, startDate);
                        view.say("The forecast was successfully generated");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "updateFromExternalSource":
                        view.say("\n\n========================================================================");
                        view.say("UPDATE THE FORECAST FROM AN EXTERNAL SOURCE.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();
                        forecastController = new ForecastController(register, budget, forecast, view, notificationService);

                        // Update the forecast from an external source:
                        if (forecast != null) {
                            forecastController.updateFromExternalSource();
                        } else {
                            view.say("There is no forecast to update.");
                        }
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "saveForecast":
                        view.say("\n\n========================================================================");
                        view.say("Saving the forecast.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Save the forecast:
                        forecast.saveAll();
                        view.say("The forecast was successfully saved to the database.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "updateForecast":
                        view.say("\n\n========================================================================");
                        view.say("Updating the forecast.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();
                        forecastController = new ForecastController(register, budget, forecast, view, notificationService);

                        // Update the forecast:
                        forecastController.updateForecast();
                        view.say("\nThe forecast was successfully updated.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderShortTermForecast":
                        view.say("\n\n========================================================================");
                        view.say("Rendering the short term forecast.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the short term forecast:
                        Utility.getForecastView().renderShortTermForecast(forecast);
                        view.say("Successfully rendered the short term forecast.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderLongTermForecast":
                        view.say("\n\n========================================================================");
                        view.say("Rendering the long term forecast.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the long term forecast:
                        Utility.getForecastView().renderLongTermForecast(forecast);
                        view.say("\nSuccessfully rendered the long term forecast.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderEnvelopeReport":
                        view.say("\n\n========================================================================");
                        view.say("Rendering the Envelope Report.\n");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the items of interest report:
                        notificationService.sendEnvelopeReport(forecast);
                        view.say("Successfully rendered the Envelope Report.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderItemsOfInterestReport":
                        view.say("\n\n========================================================================");
                        view.say("Rendering the Items of Interest Report.\n");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the items of interest report:
                        notificationService.sendItemsOfInterestReport(forecast);
                        view.say("Successfully rendered the Items of Interest Report.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderOverdueAndUpcomingItemsReport":
                        view.say("\n\n========================================================================");
                        view.say("Rendering the Overdue and Upcoming Items Report.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the overdue and upcoming items report:
                        notificationService.sendOverdueAndUpcomingItemsReport(forecast);
                        view.say("Successfully rendered the Overdue and Upcoming Items Report.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderNewTransactionSummaryReport":
                        view.say("\n\n========================================================================");
                        view.say("Rendering the New Transaction Summary Report.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the new transaction summary report:
                        notificationService.sendNewTransactionSummaryReport(register);
                        view.say("Successfully rendered the New Transaction Summary Report.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "dailyUpdate":
                        view.say("\n\n========================================================================");
                        view.say("PERFORM THE DAILY UPDATE");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();
                        DailyUpdateController dailyUpdateController = new DailyUpdateController(register,
                                financialInstitution, budget, forecast, view, notificationService);

                        // Perform the daily update:
                        if (dailyUpdateController.run()) {
                            view.say("The daily update succeeded.");
                        } else {
                            view.say("The daily update failed.");
                        }
                        view.say("------------------------------------------------------------------------");
                        break;

                    default:
                        throw new ControllerException("Unrecognized goal '" + goals[i] + "' (parameter " + i + ") in MainController.");
                }
            }

            // Close the connection to the database:
            System.out.println("\nClose the connection to the database.");
            Utility.getDbConnection().close();

        } catch (QuitException qe) {
            if (Utility.getDbConnection() != null) {
                Utility.getDbConnection().close();
            }
            view.say("\nProcessing aborted at user's request.");

        } catch (Exception e) {
            if (Utility.getDbConnection() != null) {
                Utility.getDbConnection().close();
            }
            e.printStackTrace();
        }
    }  // End main().
}  // End class App.
