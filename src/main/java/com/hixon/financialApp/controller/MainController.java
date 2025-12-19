package com.hixon.financialApp.controller;


import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastEngine;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.base.NotificationServiceInt;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.base.ViewInt;
import com.hixon.financialApp.view.excel.EnvelopeReport;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Calendar;


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
    private SessionController sessionController;
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

        this.view = view;
        this.notificationService = notificationService;
        this.sessionController = new SessionController(view, notificationService);
    }


    /*
     * Helper methods:
     */

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
                        sessionController.getRegisterBudgetForecast();
                        registerController = new RegisterController(sessionController.getRegister(),
                                sessionController.getFinancialInstitution(), sessionController.getBudget(),
                                sessionController.getForecast(), view, notificationService);
                        forecastController = new ForecastController(
                                sessionController);

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
                        sessionController.getRegisterBudgetForecast();
                        registerController = new RegisterController(sessionController.getRegister(),
                                sessionController.getFinancialInstitution(), sessionController.getBudget(),
                                sessionController.getForecast(), view, notificationService);
                        forecastController = new ForecastController(
                                sessionController);

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
                        sessionController.getRegisterBudgetForecast();
                        importController = new ImportController(sessionController);

                        // Import the register transactions:
                        inSync = importController.importRegisterTransactionFile();
                        if (!inSync) {
                            forecastController = new ForecastController(
                                    sessionController);
                            forecastController.updateForecast();
                            view.say("\nThe long term forecast was successfully updated.");
                        }
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "importProvisionalRegisterTransactions":
                        view.say("\n\n========================================================================");
                        view.say("IMPORT PROVISIONAL TRANSACTIONS");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();
                        importController = new ImportController(sessionController);


                        // Import the provisional transactions:
                        inSync = importController.importCsvProvisionalTransactionFile();
                        view.say("The provisional transactions were successfully imported.");
                        if (!inSync) {
                            forecastController = new ForecastController(
                                    sessionController);
                            forecastController.updateForecast();
                            view.say("The long term forecast was successfully updated.");
                        }
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "verifyRegisterBalance":
                        view.say("\n\n========================================================================");
                        view.say("VERIFY REGISTER BALANCE");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();
                        registerController = new RegisterController(sessionController.getRegister(),
                                sessionController.getFinancialInstitution(), sessionController.getBudget(),
                                sessionController.getForecast(), view, notificationService);

                        // Verify the register balance:
                        if (!registerController.verifyRegisterBalance(sessionController.getRegister())) {
                            view.say("The balance of the register " + sessionController.getRegister().getName() + " was " +
                                    "successfully updated.");
                        }
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "importBudgetItems":
                        view.say("\n\n========================================================================");
                        view.say("IMPORT BUDGET ITEMS");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();
                        importController = new ImportController(sessionController);


                        // Import the budget items:
                        importController.importCsvBudgetItemFile("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\" +
                                "Finances\\Expenses\\BudgetItems.csv");
                        view.say("The budget items were successfully imported.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "manageData":
                        view.say("\n\n========================================================================");
                        view.say("MANAGE DATA");

                        // The DataManagerController will prompt for register/budget/forecast only when needed
                        // based on the type of entity being managed (e.g., transactions need register,
                        // budget items need budget, but merchants are global)
                        dataManagerController = new DataManagerController(null, null, null, view,
                                notificationService);
                        inSync = dataManagerController.manageData();

                        // Update the forecast if necessary:
                        if (!inSync) {
                            // If we need to update the forecast, ensure we have the necessary context
                            if (!sessionController.hasRegister()) {
                                sessionController.getRegisterBudgetForecast();
                            }
                            // Set up the objects we need for forecast update:
                            if (!sessionController.isFullyInitialized()) {
                                sessionController.getRegisterBudgetForecast();
                            }
                            forecastController = new ForecastController(
                                    sessionController);

                            forecastController.updateForecast();
                            view.say("The long term forecast was successfully updated.");
                        }
                        view.say("Manage entities complete.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderBudgetSummaryReport":
                        view.say("\n\n========================================================================");
                        view.say("RENDER THE BUDGET SUMMARY REPORT.");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();

                        // Render the budget summary report:
                        sessionController.getBudgetView().renderBudgetSummaryReport();
                        view.say("Successfully rendered the New Budget Summary Report.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderRegister":
                        view.say("\n\n========================================================================");
                        view.say("RENDER A REGISTER.");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();

                        // Render the register:
                        startDate = view.getStartDate();
                        sessionController.getRegisterView().renderTransactionReport(startDate);
                        view.say("The register was successfully rendered");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderSpendingReport":
                        view.say("\n\n========================================================================");
                        view.say("RENDER THE SPENDING REPORT.");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();

                        // Render the spending report:
                        sessionController.getBudgetView().renderSpendingReportForMonth(Calendar.getInstance(), sessionController.getBudget());
                        view.say("The spending report was successfully rendered");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderSpendingReportForMonth":
                        view.say("\n\n========================================================================");
                        view.say("RENDER THE SPENDING REPORT FOR A SPECIFIED MONTH.");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();
                        budgetController = new BudgetController(sessionController.getRegister(),
                                sessionController.getBudget(), sessionController.getForecast(), view, notificationService);

                        // Render the spending report for the specified month
                        Calendar month = budgetController.getSpendingReportMonth();
                        sessionController.getBudgetView().renderSpendingReportForMonth(month, sessionController.getBudget());
                        view.say("The spending report was successfully rendered");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "createForecast":
                        view.say("\n\n========================================================================");
                        view.say("CREATE A FORECAST");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();

                        // Create the forecast:
                        ForecastEngine forecastEngine = new ForecastEngine();
                        forecastController = new ForecastController(
                                sessionController);
                        startDate = forecastController.askStartDate();
                        double startingBalance = 0;
                        int numberOfMonths = 12;
                        int minimumBalance = 1000;
                        Forecast forecast = new Forecast(sessionController.getBudget(), startDate, numberOfMonths, startingBalance, minimumBalance);
                        forecastEngine.generateForecast(forecast, startDate);
                        sessionController.setForecast(forecast);
                        view.say("The forecast was successfully generated");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "updateFromExternalSource":
                        view.say("\n\n========================================================================");
                        view.say("UPDATE THE REGISTER FROM AN EXTERNAL SOURCE.");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();
                        forecastController = new ForecastController(
                                sessionController);

                        // Update the forecast from an external source:
                        if (sessionController.getForecast() != null) {
                            forecastController.updateFromExternalSource();
                        } else {
                            view.say("There is no forecast to update.");
                        }
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "saveForecast":
                        view.say("\n\n========================================================================");
                        view.say("SAVE THE FORECAST");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();

                        // Save the forecast:
                        sessionController.getForecast().saveAll();
                        view.say("The forecast was successfully saved to the database.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "updateForecast":
                        view.say("\n\n========================================================================");
                        view.say("UPDATE A FORECAST");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();
                        forecastController = new ForecastController(
                                sessionController);

                        // Update the forecast:
                        forecastController.updateForecast();
                        view.say("\nThe forecast was successfully updated.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderShortTermForecast":
                        view.say("\n\n========================================================================");
                        view.say("REMDER THE SHORT TERM FORECAST.");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();

                        // Render the short term forecast:
                        sessionController.getForecastView().renderShortTermForecast(sessionController.getForecast());
                        view.say("Successfully rendered the short term forecast.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderLongTermForecast":
                        view.say("\n\n========================================================================");
                        view.say("RENDER A LONG TERM FORECAST");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();

                        // Render the long term forecast:
                        sessionController.getForecastView().renderLongTermForecast(sessionController.getForecast());
                        view.say("\nSuccessfully rendered the long term forecast.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderEnvelopeReport":
                        view.say("\n\n========================================================================");
                        view.say("RENDER AN ENVELOPE REPORT");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();

                        // Render the items of interest report:
                        notificationService.sendEnvelopeReport(sessionController.getForecast());
                        view.say("Successfully rendered the Envelope Report.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderItemsOfInterestReport":
                        view.say("\n\n========================================================================");
                        view.say("RENDER THE ITEMS OF INTEREST REPORT");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();

                        // Render the items of interest report:
                        notificationService.sendItemsOfInterestReport(sessionController.getForecast());
                        view.say("Successfully rendered the Items of Interest Report.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderOverdueAndUpcomingItemsReport":
                        view.say("\n\n========================================================================");
                        view.say("RENDER THE OVERDUE AND UPCOMING ITEMS REPORT");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();

                        // Render the overdue and upcoming items report:
                        notificationService.sendOverdueAndUpcomingItemsReport(sessionController.getForecast());
                        view.say("Successfully rendered the Overdue and Upcoming Items Report.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "renderNewTransactionSummaryReport":
                        view.say("\n\n========================================================================");
                        view.say("RENDER THE NEW TRANSACTION SUMMARY REPORT");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();

                        // Render the new transaction summary report:
                        notificationService.sendNewTransactionSummaryReport(sessionController.getRegister());
                        view.say("Successfully rendered the New Transaction Summary Report.");
                        view.say("------------------------------------------------------------------------");
                        break;

                    case "dailyUpdate":
                        view.sayH1("PERFORM THE DAILY UPDATE");

                        // Set up the objects we need:
                        sessionController.getRegisterBudgetForecast();
                        DailyUpdateController dailyUpdateController = new DailyUpdateController(sessionController);

                        // Perform the daily update:
                        if (dailyUpdateController.run()) {
                            view.sayH4("The daily update succeeded.");
                        } else {
                            view.sayH4("The daily update failed.");
                        }
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

        } catch (CancelException ce) {
            if (Utility.getDbConnection() != null) {
                Utility.getDbConnection().close();
            }
            view.say("\nOperation cancelled by user.");

        } catch (Exception e) {
            if (Utility.getDbConnection() != null) {
                Utility.getDbConnection().close();
            }
            e.printStackTrace();
        }
    }  // End main().
}  // End class App.
