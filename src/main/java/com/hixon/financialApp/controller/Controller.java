package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.Budget;
import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastEngine;
import com.hixon.financialApp.model.register.FinancialInstitution;
import com.hixon.financialApp.model.register.Register;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.model.register.WellsFargoBank;
import com.hixon.financialApp.notification.async.file.fileBasedNotificationService;
import com.hixon.financialApp.utility.FinancialException;
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

   public static void main(String[] args) throws Exception, BudgetException, ControllerException, RegisterException, ViewException, EntityException {

      // Interact with the user via the command line for server operations:
      System.out.println("Create a command line transaction Utility.getResolver().");
      Utility.setResolver(new TransactionResolverCmdLine());

      // Use the file based notification service:
      Utility.setNotificationService(new fileBasedNotificationService());

      // Create the Importer:
      Importer importer = new Importer();

      try {
         // Use a MySQL database for persistence:
         Utility.getResolver().say("Connect to the database.");
//         com.hixon.financialApp.utility.Utility.setDbConnection(DriverManager.getConnection(
//                 "jdbc:mysql://localhost:3306/ForecastDatabase", "root", "***REMOVED-CREDENTIAL***"));
         com.hixon.financialApp.utility.Utility.setDbConnection(DriverManager.getConnection(
                 "jdbc:mysql://financialappinstance1.ctgwj8jkemeb.us-east-1.rds.amazonaws.com:3306/forecastdatabase",
                 "admin", "***REMOVED-CREDENTIAL***59"));

         // Use Spreadsheet XML as the view for the application:
         Utility.setRegisterView(new SpreadsheetXmlRegisterView(Register.getByName("Bill Pay Account")));
         Utility.setBudgetView(new SpreadsheetXmlBudgetView(Budget.getByName("Bill Pay Account")));
         Utility.setForecastView(new SpreadsheetXmlForecastView(Forecast.getMostRecent()));

         // Process the goals:
         Calendar startDate;
         String filename = null;
         boolean inSync = true;
         Register register = Register.getByName("Bill Pay Account");
         FinancialInstitution financialInstitution = null;
         String budgetName = "Bill Pay Account";
         Forecast forecast = Forecast.getMostRecent();
         for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
               case "processSkippedTransactions":
                  if (register == null) register = Register.getByName("Bill Pay Account");
                  if (financialInstitution == null) financialInstitution = new WellsFargoBank(register);
                  inSync = register.processSkippedTransactions(forecast);
                  if (!inSync) {
                     forecast.updateForecast();
                     Utility.getResolver().say("The long term forecast was successfully updated.");
                  }
                  break;

               case "importRegisterTransactions":
                  Utility.getResolver().say("Importing the register transactions.");
                  inSync = importer.importCsvRegisterTransactionFile("Wells Fargo Bank",
                          "Bill Pay Account", forecast);
                  Utility.getResolver().say("The transactions were successfully imported.");
                  if (!inSync) {
                     forecast.updateForecast();
                     Utility.getResolver().say("The long term forecast was successfully updated.");
                  }
                  break;

               case "importProvisionalRegisterTransactions":
                  inSync = importer.importCsvProvisionalTransactionFile("Wells Fargo Bank",
                          "Bill Pay Account", forecast);
                  Utility.getResolver().say("The provisional transactions were successfully imported.");
                  if (!inSync) {
                     forecast.updateForecast();
                     Utility.getResolver().say("The long term forecast was successfully updated.");
                  }
                  break;

               case "importBudgetItems":
                  Utility.getResolver().say("Importing the budget items.");
                  importer.importCsvBudgetItemFile("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\" +
                          "Finances\\Expenses\\BudgetItems.csv");
                  Utility.getResolver().say("The budget items were successfully imported.");
                  break;

               case "renderRegister":
                  Utility.getResolver().say("Rendering the register.");
                  startDate = Utility.askStartDate();
                  Utility.getRegisterView().renderTransactionReport(startDate);
                  Utility.getResolver().say("The register was successfully rendered");
                  break;

               case "renderSpendingReport":
                  Utility.getResolver().say("Rendering the spending report.");
                  startDate = Utility.getResolver().getSpendingReportMonth();
                  Utility.getBudgetView().renderPlannedVsActualReport(startDate);
                  Utility.getResolver().say("The spending report was successfully rendered");
                  break;

               case "createForecast":
                  Utility.getResolver().say("Create the forecast.");
                  ForecastEngine forecastEngine = new ForecastEngine();
                  startDate = Utility.askStartDate();
                  double startingBalance = 0;
                  int numberOfMonths = 12;
                  int minimumBalance = 1000;
                  forecast = new Forecast(budgetName, startDate, numberOfMonths, startingBalance, minimumBalance);
                  forecastEngine.generateForecast(forecast, startDate);
                  Utility.getResolver().say("The forecast was successfully generated");
                  break;

               case "updateFromExternalSource":
                  Utility.getResolver().say("Updating the forecast from an external source.");
                  if (forecast != null) {
                     Utility.getForecastView().updateFromExternalSource();
                  } else {
                     Utility.getResolver().say("There is no forecast to update.");
                  }
                  break;

               case "saveForecast":
                  Utility.getResolver().say("Saving the forecast.");
                  forecast.saveAll();
                  Utility.getResolver().say("The forecast was successfully saved to the database.");
                  break;

               case "updateForecast":
                  Utility.getResolver().say("Updating the forecast.");
                  forecast.updateForecast();
                  Utility.getResolver().say("The forecast was successfully updated.");
                  break;

               case "renderShortTermForecast":
                  Utility.getResolver().say("Rendering the short term forecast.");
                  Utility.getForecastView().renderShortTermForecast(forecast);
                  Utility.getResolver().say("Successfully rendered the short term forecast.");
                  break;

               case "renderLongTermForecast":
                  Utility.getResolver().say("Rendering the long term forecast.");
                  Utility.getForecastView().renderLongTermForecast(forecast);
                  Utility.getResolver().say("Successfully rendered the long term forecast.");
                  break;

               case "renderItemsOfInterestReport":
                  Utility.getResolver().say("Rendering the Items of Interest Report.");
                  Utility.getNotificationService().sendItemsOfInterestReport();
                  Utility.getResolver().say("Successfully rendered the Items of Interest Report.");
                  break;

               case "renderNewTransactionSummaryReport":
                  Utility.getResolver().say("Rendering the New Transaction Summary Report.");
                  Utility.getNotificationService().sendNewTransactionSummaryReport(register);
                  Utility.getResolver().say("Successfully rendered the New Transaction Summary Report.");
                  break;

               default:
                  throw new ControllerException("Unrecognized goal '" + args[i] + "' (parameter " + i + ") in Controller.");
            }
         }

         // Close the connection to the database:
         System.out.println("\nClose the connection to the database.");
         Utility.getDbConnection().close();

      } catch (Exception | FinancialException e) {
         if (Utility.getDbConnection() != null) {
            Utility.getDbConnection().close();
         }
         e.printStackTrace();
      }
   }  // End main().
}  // End class App.
