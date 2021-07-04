package com.hixon.financialApp.controller;


import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastEngine;
import com.hixon.financialApp.model.register.FinancialInstitutionInt;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.WellsFargoBank;
import com.hixon.financialApp.notification.async.file.fileBasedNotificationService;
import com.hixon.financialApp.utility.FinancialAppException;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.cmdLine.TransactionResolverCmdLine;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlBudgetView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlForecastView;
import com.hixon.financialApp.view.spreadsheetXml.SpreadsheetXmlRegisterView;

import java.sql.DriverManager;
import java.util.Calendar;


/**
 * Main controller for the command line version of the product:
 */
public class Controller {

    //private static final Logger LOGGER = LogManager.getLogger(Controller.class);

    public static void main(String[] args) throws Exception, BudgetException, ControllerException, RegisterException, ViewException, EntityException {

        //LOGGER.debug("Enter method Main().");

        // Interact with the user via the command line for server operations:
        Utility.setResolver(new TransactionResolverCmdLine());

        // Use the file based notification service:
        Utility.setNotificationService(new fileBasedNotificationService());

        // Create the Importer:
        Importer importer = new Importer();

        try {
            // Use a MySQL database for persistence:
            com.hixon.financialApp.utility.Utility.setDbConnection(DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/ForecastDatabase", "root", "***REMOVED-CREDENTIAL***"));
//         com.hixon.financialApp.utility.Utility.setDbConnection(DriverManager.getConnection(
//                 "jdbc:mysql://financialappinstance1.ctgwj8jkemeb.us-east-1.rds.amazonaws.com:3306/forecastdatabase",
//                 "admin", "***REMOVED-CREDENTIAL***59"));

            // Use Spreadsheet XML as the view for the application:
            Utility.setRegisterView(new SpreadsheetXmlRegisterView(Register.getByName("Bill Pay Account")));
            Utility.setBudgetView(new SpreadsheetXmlBudgetView(Budget.getByName("Bill Pay Account")));
            Utility.setForecastView(new SpreadsheetXmlForecastView(Forecast.getMostRecent()));

            // Process the goals:
            Calendar startDate;
            String filename = null;
            boolean inSync = true;
            Register register = Register.getByName("Bill Pay Account");
            FinancialInstitutionInt financialInstitution = new WellsFargoBank(register);
            ;
            Budget budget = Budget.getByName("Bill Pay Account");
            Forecast forecast = Forecast.getMostRecent();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "processSkippedTransactions":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("REPROCESS SKIPPED TRANSACTIONS");
                        inSync = register.processSkippedTransactions(financialInstitution, register, forecast);
                        if (!inSync) {
                            forecast.updateForecast();
                            Utility.getResolver().say("\nThe long term forecast was successfully updated.");
                        }
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "importRegisterTransactions":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("IMPORT REGISTER TRANSACTIONS");
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
                        Utility.getResolver().say("IMPORT PROVISIONAL TRANSACTIONS\n");
                        inSync = importer.importCsvProvisionalTransactionFile(financialInstitution,
                                register, forecast);
                        Utility.getResolver().say("\nThe provisional transactions were successfully imported.");
                        if (!inSync) {
                            forecast.updateForecast();
                            Utility.getResolver().say("\nThe long term forecast was successfully updated.");
                        }
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "verifyRegisterBalance":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Verify register balance and update if necessary.");
                        if (!Utility.getRegisterView().verifyRegisterBalance(register)) {
                            Utility.getResolver().say("The balance of the register " + register.getRegisterName() + " was " +
                                    "successfully updated.");
                        }
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "importBudgetItems":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Importing the budget items.");
                        importer.importCsvBudgetItemFile("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\" +
                                "Finances\\Expenses\\BudgetItems.csv");
                        Utility.getResolver().say("The budget items were successfully imported.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderBudgetSummaryReport":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the Budget Summary Report.");
                        Utility.getBudgetView().renderBudgetSummaryReport();
                        Utility.getResolver().say("Successfully rendered the New Budget Summary Report.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderRegister":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the register.");
                        startDate = Utility.askStartDate();
                        Utility.getRegisterView().renderTransactionReport(startDate);
                        Utility.getResolver().say("The register was successfully rendered");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderSpendingReport":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the spending report.");
                        Utility.getBudgetView().renderSpendingReportForMonth(Calendar.getInstance());
                        Utility.getResolver().say("The spending report was successfully rendered");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderSpendingReportForMonth":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the spending report.");
                        Calendar month = Utility.getResolver().getSpendingReportMonth();
                        Utility.getBudgetView().renderSpendingReportForMonth(month);
                        Utility.getResolver().say("The spending report was successfully rendered");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "createForecast":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Create the forecast.");
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
                        forecast.saveAll();
                        Utility.getResolver().say("The forecast was successfully saved to the database.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "updateForecast":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Updating the forecast.");
                        forecast.updateForecast();
                        Utility.getResolver().say("\nThe forecast was successfully updated.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderShortTermForecast":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the short term forecast.");
                        Utility.getForecastView().renderShortTermForecast(forecast);
                        Utility.getResolver().say("Successfully rendered the short term forecast.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderLongTermForecast":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the long term forecast.");
                        Utility.getForecastView().renderLongTermForecast(forecast);
                        Utility.getResolver().say("Successfully rendered the long term forecast.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderItemsOfInterestReport":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the Items of Interest Report.\n");
                        Utility.getNotificationService().sendItemsOfInterestReport(forecast);
                        Utility.getResolver().say("Successfully rendered the Items of Interest Report.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderOverdueAndUpcomingItemsReport":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the Overdue and Upcoming Items Report.");
                        Utility.getNotificationService().sendOverdueAndUpcomingItemsReport(forecast);
                        Utility.getResolver().say("Successfully rendered the Overdue and Upcoming Items Report.");
                        Utility.getResolver().say("------------------------------------------------------------------------");
                        break;

                    case "renderNewTransactionSummaryReport":
                        Utility.getResolver().say("\n\n========================================================================");
                        Utility.getResolver().say("Rendering the New Transaction Summary Report.");
                        Utility.getNotificationService().sendNewTransactionSummaryReport(register);
                        Utility.getResolver().say("Successfully rendered the New Transaction Summary Report.");
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
            Utility.getResolver().say("\nProcessing aborted at user's request.");

        } catch (Exception | FinancialAppException e) {
            if (Utility.getDbConnection() != null) {
                Utility.getDbConnection().close();
            }
            e.printStackTrace();
        }
    }  // End main().
}  // End class App.
