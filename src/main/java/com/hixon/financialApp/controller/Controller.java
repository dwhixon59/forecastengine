package com.hixon.financialApp.controller;

import com.hixon.financialApp.model.budget.BudgetException;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.model.forecast.Forecast;
import com.hixon.financialApp.model.forecast.ForecastEngine;
import com.hixon.financialApp.model.register.RegisterException;
import com.hixon.financialApp.utility.Utility;
import com.hixon.financialApp.view.ViewException;
import com.hixon.financialApp.view.base.BudgetViewInt;
import com.hixon.financialApp.view.base.ForecastViewInt;
import com.hixon.financialApp.view.base.RegisterViewInt;
import com.hixon.financialApp.view.cmdLine.TransactionResolverCmdLine;
import com.hixon.financialApp.view.excel.SpreadsheetBudgetView;
import com.hixon.financialApp.view.excel.SpreadsheetForecastView;
import com.hixon.financialApp.view.excel.SpreadsheetRegisterView;

import java.sql.DriverManager;
import java.util.Calendar;

/**
 * Hello world!
 */
public class Controller {

   public static void main(String[] args) throws Exception, BudgetException, ControllerException, RegisterException, ViewException, EntityException {

      // Variables that are used throughout the goal processing:
      Calendar startDate = null;
      Forecast forecast = null;

      // Interact with the user via the command line for server operations:
      System.out.println("Create a command line transaction Utility.getResolver().");
      Utility.setResolver(new TransactionResolverCmdLine());

      // Use Microsoft Excel as the view of the model:
      RegisterViewInt registerView = new SpreadsheetRegisterView();
      BudgetViewInt budgetView = new SpreadsheetBudgetView();
      ForecastViewInt forecastView = new SpreadsheetForecastView();

      // Create the Importer:
      Importer importer = new Importer();

      try {
         // Use a MySQL database for persistence:
         Utility.getResolver().say("Connect to the database.");
         com.hixon.financialApp.utility.Utility.setDbConnection(DriverManager.getConnection(
                 "jdbc:mysql://localhost:3306/ForecastDatabase", "root", "***REMOVED-CREDENTIAL***"));

         // Process the goals:
         for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
               case "importTransactions":
                  Utility.getResolver().say("Importing the transactions.");
                  if (forecast == null) forecast = Forecast.getMostRecent();
                  String filename = "C:\\Users\\dwhix\\Downloads\\Checking2.csv";
                  //String filename = "C:\\Users\\dwhix\\Downloads\\2019-10-24.csv";
                  boolean inSync = importer.importCsvTransactionFile(filename, "Wells Fargo Bank",
                          "Bill Pay Account", forecast);
                  Utility.getResolver().say("The transactions were successfully imported.");
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
                  registerView.renderTransactionReport(startDate);
                  Utility.getResolver().say("The register was successfully rendered");
                  break;
               case "renderSpendingReport":
                  Utility.getResolver().say("Rendering the spending report.");
                  startDate = Utility.askStartDate();
                  budgetView.renderSpendingReport(startDate);
                  Utility.getResolver().say("The spending report was successfully rendered");
                  break;
               case "createForecast":
                  Utility.getResolver().say("Create the forecast.");
                  ForecastEngine forecastEngine = new ForecastEngine();
                  startDate = Utility.askStartDate();
                  double startingBalance = 0;
                  int numberOfMonths = 12;
                  int minimumBalance = 1000;
                  String budgetName = "Bill Pay Account";
                  forecast = new Forecast(budgetName, startDate, numberOfMonths, startingBalance, minimumBalance);
                  forecastEngine.generateForecast(forecast, startDate);
                  Utility.getResolver().say("The forecast was successfully generated");
                  break;
               case "saveForecast":
                  if (forecast == null) {
                     Utility.getResolver().say("You requested to save the forecast, but there isn't a forecast to save.");
                  } else {
                     Utility.getResolver().say("Saving the forecast.");
                     forecast.saveAll();
                     Utility.getResolver().say("The forecast was successfully saved to the database.");
                  }
                  break;
               case "updateForecast":
                  Utility.getResolver().say("Updating the forecast.");
                  if (forecast == null) forecast = Forecast.getMostRecent();
                  forecast.updateForecast();
                  Utility.getResolver().say("The forecast was successfully updated.");
                  break;
               case "renderShortTermForecast":
                  Utility.getResolver().say("Rendering the short term forecast.");
                  if (forecast == null) forecast = Forecast.getMostRecent();
                  forecastView.renderShortTermForecast(forecast);
                  Utility.getResolver().say("Successfully rendered the short term forecast.");
                  break;
               case "renderLongTermForecast":
                  Utility.getResolver().say("Rendering the long term forecast.");
                  if (forecast == null) forecast = Forecast.getMostRecent();
                  forecastView.renderLongTermForecast(forecast);
                  Utility.getResolver().say("Successfully rendered the long term forecast.");
                  break;
               default:
                  throw new ControllerException("Unrecognized goal '" + args[i] + "' (parameter " + i + ") in Controller.");
            }
         }

         // Close the connection to the database:
         System.out.println("\nClose the connection to the database.");
         Utility.getDbConnection().close();

      } catch (Exception | QuitException e) {
         if (Utility.getDbConnection() != null) {
            Utility.getDbConnection().close();
         }
         e.printStackTrace();
      }

   }  // End main().

}  // End class App.
