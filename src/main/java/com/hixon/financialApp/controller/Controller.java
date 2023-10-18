package com.hixon.financialApp.controller;


import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastEngine;
import com.hixon.financialApp.model.register.FinancialInstitutionInt;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.WellsFargoBank;
import com.hixon.financialApp.model.user.User;
import com.hixon.financialApp.notification.async.file.fileBasedNotificationService;
import com.hixon.financialApp.utility.FinancialAppException;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.cmdLine.TransactionResolverCmdLine;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlBudgetView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlForecastView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlRegisterView;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Calendar;


/**
 * Main controller for the command line version of the product:
 */
public class Controller {
    /*
     * Statics and constants:
     */
    //private static final Logger LOGGER = LogManager.getLogger(Controller.class);


    /*
     * Member variables:
     */
    private Register register = null;
    private Budget budget = null;
    private Forecast forecast = null;
    private FinancialInstitutionInt financialInstitution = null;


    /*
     * Constructors:
     */
    public Controller(String username, Connection dbConnection, TransactionResolverCmdLine transactionResolverCmdLine,
                      fileBasedNotificationService fileBasedNotificationService) throws SQLException, EntityException {
        Utility.setDbConnection(dbConnection);
        Utility.setUser(User.getByName(username));
        Utility.setResolver(transactionResolverCmdLine);
        Utility.setNotificationService(fileBasedNotificationService);
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
            register = Register.selectRegister();
            Utility.setRegisterView(new SpreadsheetXmlRegisterView(register));

            // and set the financial institution associated with the selected register
            financialInstitution = new WellsFargoBank(register);

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
    public void run(String[] args) throws SQLException {
        try {
             // Create the various sub controllers:
            Importer importer = new Importer();
            BudgetItemManager budgetItemManager = new BudgetItemManager();

            // Process the goals:
            Calendar startDate;
            String filename = null;
            boolean inSync = true;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "processUncategorizedTransactions":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("REPROCESS UNCATEGORIZED TRANSACTIONS");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Process the uncategorized transactions:
                        inSync = register.processUncategorizedTransactions(financialInstitution, register, forecast);
                        if (!inSync) {
                            forecast.updateForecast();
                            Utility.getResolver().say("\nThe long term forecast was successfully updated.");
                        }
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "processUnreconciledTransactions":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("REPROCESS UNRECONCILED TRANSACTIONS");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Process the unreconciled transactions:
                        inSync = register.processUnreconciledTransactions(financialInstitution, register, forecast);
                        if (!inSync) {
                            forecast.updateForecast();
                            Utility.getResolver().say("\nThe long term forecast was successfully updated.");
                        }
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "importRegisterTransactions":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("IMPORT REGISTER TRANSACTIONS");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Import the register transactions:
                        inSync = importer.importCsvRegisterTransactionFile(financialInstitution,
                                register, forecast);
                        if (!inSync) {
                            forecast.updateForecast();
                            Utility.getResolver().say("\nThe long term forecast was successfully updated.");
                        }
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "importProvisionalRegisterTransactions":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("IMPORT PROVISIONAL TRANSACTIONS");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Import the provisional transactions:
                        inSync = importer.importCsvProvisionalTransactionFile(financialInstitution,
                                register, forecast);
                        Utility.getResolver().say("The provisional transactions were successfully imported.");
                        if (!inSync) {
                            forecast.updateForecast();
                            Utility.getResolver().say("The long term forecast was successfully updated.");
                        }
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "verifyRegisterBalance":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Verify register balance and update if necessary.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Verify the register balance:
                        if (!Utility.getRegisterView().verifyRegisterBalance(register)) {
                            Utility.getResolver().say("The balance of the register " + register.getName() + " was " +
                                    "successfully updated.");
                        }
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "importBudgetItems":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Importing the budget items.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Import the budget items:
                        importer.importCsvBudgetItemFile("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\" +
                                "Finances\\Expenses\\BudgetItems.csv");
                        Utility.getResolver().say("The budget items were successfully imported.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "manageBudgetItems":
                        Utility.getResolver().say("\n\n========================================================================");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Manage the budget items:
                        budgetItemManager.manageBudgetItems(forecast);
                        Utility.getResolver().say("Manage budget items complete.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderBudgetSummaryReport":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the Budget Summary Report.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the budget summary report:
                        Utility.getBudgetView().renderBudgetSummaryReport();
                        Utility.getResolver().say("Successfully rendered the New Budget Summary Report.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderRegister":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the register.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the register:
                        startDate = Utility.askStartDate();
                        Utility.getRegisterView().renderTransactionReport(startDate);
                        Utility.getResolver().say("The register was successfully rendered");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderSpendingReport":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the spending report.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the spending report:
                        Utility.getBudgetView().renderSpendingReportForMonth(Calendar.getInstance());
                        Utility.getResolver().say("The spending report was successfully rendered");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderSpendingReportForMonth":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the spending report.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the spending report for the specified month
                        Calendar month = Utility.getResolver().getSpendingReportMonth();
                        Utility.getBudgetView().renderSpendingReportForMonth(month);
                        Utility.getResolver().say("The spending report was successfully rendered");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "createForecast":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Create the forecast.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Create the forecast:
                        ForecastEngine forecastEngine = new ForecastEngine();
                        startDate = Utility.askStartDate();
                        double startingBalance = 0;
                        int numberOfMonths = 12;
                        int minimumBalance = 1000;
                        forecast = new Forecast(budget, startDate, numberOfMonths, startingBalance, minimumBalance);
                        forecastEngine.generateForecast(forecast, startDate);
                        Utility.getResolver().say("The forecast was successfully generated");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "updateFromExternalSource":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("UPDATE THE FORECAST FROM AN EXTERNAL SOURCE.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Update the forecast from an external source:
                        if (forecast != null) {
                            Utility.getForecastView().updateFromExternalSource();
                        } else {
                            Utility.getResolver().say("There is no forecast to update.");
                        }
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "saveForecast":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Saving the forecast.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Save the forecast:
                        forecast.saveAll();
                        Utility.getResolver().say("The forecast was successfully saved to the database.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "updateForecast":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Updating the forecast.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Update the forecast:
                        forecast.updateForecast();
                        Utility.getResolver().say("\nThe forecast was successfully updated.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderShortTermForecast":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the short term forecast.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the short term forecast:
                        Utility.getForecastView().renderShortTermForecast(forecast);
                        Utility.getResolver().say("Successfully rendered the short term forecast.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderLongTermForecast":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the long term forecast.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the long term forecast:
                        Utility.getForecastView().renderLongTermForecast(forecast);
                        Utility.getResolver().say("Successfully rendered the long term forecast.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderItemsOfInterestReport":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the Items of Interest Report.\n");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the items of interest report:
                        Utility.getNotificationService().sendItemsOfInterestReport(forecast);
                        Utility.getResolver().say("Successfully rendered the Items of Interest Report.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderOverdueAndUpcomingItemsReport":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the Overdue and Upcoming Items Report.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the overdue and upcoming items report:
                        Utility.getNotificationService().sendOverdueAndUpcomingItemsReport(forecast);
                        Utility.getResolver().say("Successfully rendered the Overdue and Upcoming Items Report.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderNewTransactionSummaryReport":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the New Transaction Summary Report.");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Render the new transaction summary report:
                        Utility.getNotificationService().sendNewTransactionSummaryReport(register);
                        Utility.getResolver().say("Successfully rendered the New Transaction Summary Report.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "dailyUpdate":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("PERFORM THE DAILY UPDATE");

                        // Set up the objects we need:
                        getRegisterBudgetForecast();

                        // Perform the daily update:
                        DailyUpdate dailyUpdate = new DailyUpdate(register, financialInstitution, budget, forecast);
                        if (dailyUpdate.run()) {
                            Utility.getResolver().say("The daily update succeeded.");
                        } else {
                            Utility.getResolver().say("The daily update failed.");
                        }
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    default:
                        throw new ControllerException("Unrecognized goal '" + args[i] + "' (parameter " + i + ") in Controller.");
                }
            }

            // Close the connection to the database:
            System.out.println("\nClose the connection to the database.");
            Utility.getDbConnection().close();

        } catch (QuitException qe) {
            if (Utility.getDbConnection() != null) {
                Utility.getDbConnection().close();
            }
            Utility.getResolver().say("\nProcessing aborted at user's request.");

        } catch (Exception e) {
            if (Utility.getDbConnection() != null) {
                Utility.getDbConnection().close();
            }
            e.printStackTrace();
        }
    }  // End main().
}  // End class App.
