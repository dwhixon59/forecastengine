package com.hixon.financial.controller;

import com.hixon.financial.Utility;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.forecast.Forecast;
import com.hixon.financial.model.forecast.ForecastEngine;
import com.hixon.financial.model.forecast.LongTermForecast;
import com.hixon.financial.model.forecast.ShortTermForecast;
import com.hixon.financial.model.register.RegisterException;
import com.hixon.financial.view.ForecastView;
import com.hixon.financial.view.ViewException;
import com.hixon.financial.view.excel.excelView;
import com.hixon.financial.view.register.ExcelRegisterView;
import com.hixon.financial.view.register.RegisterView;
import com.hixon.financial.view.register.TransactionResolver;
import com.hixon.financial.view.register.TransactionResolverCmdLine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Scanner;

import static java.util.Calendar.NOVEMBER;

/**
 * Hello world!
 */
public class Controller {
    public static void main(String[] args) throws Exception, BudgetException, ControllerException, RegisterException, ViewException, EntityException {

        boolean importBudgetItemsFile = false;
        boolean importCsvTransactionFile = false;
        boolean renderRegister = false;
        boolean createShortTermForecast = false;
        boolean renderShortTermForecast = false;
        boolean saveShortTermForecast = false;
        boolean createLongTermForecast = false;
        boolean renderLongTermForecast = false;
        boolean saveLongTermForecast = false;
        boolean renderSpendingReportMTD = false;

        Connection dbConnection = null;
        ShortTermForecast shortTermForecast = null;
        LongTermForecast longTermForecast = null;
        RegisterView registerView = null;

        // Convert the input parameters into goals:
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "importBudgetItemsFile":
                    importBudgetItemsFile = true;
                    break;
                case "importCsvTransactionFile":
                    importCsvTransactionFile = true;
                    break;
                case "renderRegister":
                    renderRegister = true;
                    registerView = new ExcelRegisterView();
                    break;
                case "createShortTermForecast":
                    createShortTermForecast = true;
                    break;
                case "renderShortTermForecast":
                    renderShortTermForecast = true;
                    break;
                case "saveShortTermForecast":
                    saveShortTermForecast = true;
                    break;
                case "createLongTermForecast":
                    createLongTermForecast = true;
                    break;
                case "renderLongTermForecast":
                    renderLongTermForecast = true;
                    break;
                case "saveLongTermForecast":
                    saveLongTermForecast = true;
                    break;
                case "renderSpendingReportMTD":
                    renderSpendingReportMTD = true;
                    break;
                case "all":
                    importCsvTransactionFile = true;
                    renderRegister = true;
                    registerView = new ExcelRegisterView();
                    createShortTermForecast = true;
                    renderShortTermForecast = true;
                    saveShortTermForecast = true;
                    createLongTermForecast = true;
                    renderLongTermForecast = true;
                    saveLongTermForecast = true;
                    break;
                default:
                    throw new ControllerException("Unrecognized goal '" + args[i] + "' (parameter " + i +
                            ") in Controller.");
            }
        }

        // Process the goals:
        try {
            System.out.println("Connect to the database.");
            dbConnection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/ForecastDatabase", "root", "***REMOVED-CREDENTIAL***");
            Utility.setDbConnection(dbConnection);

            // Import new budget items into the budget:
            if (importBudgetItemsFile) {
                Importer importer = new Importer();
                importer.importCsvBudgetItemFile("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\" +
                        "Finances\\Expenses\\BudgetItems.csv");
            }

            // Import new transactions into the register:
            if (importCsvTransactionFile) {
                Importer importer = new Importer();
                Forecast forecast = (longTermForecast != null) ? longTermForecast : LongTermForecast.getMostRecent();
                String filename = "C:\\Users\\dwhix\\Downloads\\Checking2.csv";
                //String filename = "C:\\Users\\dwhix\\Downloads\\2019-10-24.csv";
                boolean inSync = importer.importCsvTransactionFile(filename,"Wells Fargo Bank",
                        "Bill Pay Account", forecast);
                if (!inSync && !createLongTermForecast)  {
                    TransactionResolver resolver = new TransactionResolverCmdLine();
                    resolver.askRegenerateForecast();
                }
            }

            // Export the transactions to Excel beginning on a particular date:
            if (renderRegister) {
                //Calendar startDate = getStartDate();
                Calendar startDate = Utility.stringDateDashToCalendarDate("09-28-19");
                registerView.renderFrom(startDate);
            }

            // Create the short term forecast:
            if (createShortTermForecast) {
                shortTermForecast = createShortTermForecast();
            }

            // Render the short term forecast:
            if (shortTermForecast != null && renderShortTermForecast) {
                renderShortTermForecast(shortTermForecast);
            }

            // Save the short term forecast:
            if (shortTermForecast != null && saveShortTermForecast) {
                shortTermForecast.saveAll(dbConnection);
                System.out.println("The forecast was successfully saved to the database.");
            }

            // Create the long term forecast:
            if (createLongTermForecast) {
                longTermForecast = createLongTermForecast(dbConnection);
            }

            // Render the long term forecast:
            if (renderLongTermForecast) {
                if (longTermForecast == null) longTermForecast = LongTermForecast.getMostRecent();
                renderLongTermForecast(longTermForecast);
            }

            // Save the long term forecast:
            if (longTermForecast != null && saveLongTermForecast) {
                longTermForecast.saveAll(dbConnection);
                System.out.println("The forecast was successfully saved to the database.");
            }

            // Render the month-to-date spending report:
            if (renderSpendingReportMTD) {
                ForecastView forecastView = new excelView();
                TransactionResolver resolver = new TransactionResolverCmdLine();
                forecastView.renderSpendingReportMTD(resolver);
            }

            // Close the connection to the database:
            System.out.println("\n\nClose the connection to the database.");
            dbConnection.close();

        } catch (Exception e) {
            if (dbConnection != null) {
                dbConnection.close();
            }
            e.printStackTrace();
        }

    }  // End main().

    private static Calendar getStartDate() throws QuitException {
        Calendar startDate = null;
        boolean stop = false;
        while (!stop) {
            System.out.print("Enter the starting date (MM-DD-YY) of the register export: ");
            Scanner in = new Scanner(System.in);
            String line = in.nextLine();
            try {
                startDate = Utility.stringDateDashToCalendarDate(line);
                stop = true;

            } catch (ParseException e) {
                if (line.equalsIgnoreCase("quit")) {
                    throw new QuitException("User requested to quit.");
                } else {
                    System.out.println("Invalid date.  Please re-enter or type 'quit' to quit.");
                }
            }
        }
        return startDate;
    }

    private static ShortTermForecast createShortTermForecast() throws Exception, BudgetException {
        System.out.println("Create the short term forecast.");
        ForecastEngine forecastEngine = new ForecastEngine();
        double startingBalance = 0;
        double minimumBalance = 1000;
        String budgetName = "Bill Pay Account";
        ShortTermForecast shortTermForecast = new ShortTermForecast(budgetName, startingBalance, minimumBalance);
        System.out.println("\n\nGenerate the short term forecast.");
        forecastEngine.generateShortTermForecast(shortTermForecast);
        System.out.println("The short term forecast was successfully generated");
        return shortTermForecast;
    }

    private static void renderShortTermForecast(ShortTermForecast forecast) throws Exception, EntityException, BudgetException {
        System.out.println("\n\nRender the short term forecast.");
        ForecastView forecastView = new excelView();
        //forecastView.setForecast((Forecast) forecast);
        forecastView.renderShortTermForecast("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                        "ShortTermForecast.tsv", "UTF-8");
        System.out.println("The short term forecast was successfully rendered.");
     }

    // Create the long term forecast:
    public static LongTermForecast createLongTermForecast(Connection dbConnection) throws Exception, BudgetException, EntityException {
        System.out.println("Create the long term forecast.");
        ForecastEngine forecastEngine = new ForecastEngine();
        Calendar startDate = new GregorianCalendar();
        startDate.set(2019, NOVEMBER, 1, 0, 0, 0);
        double startingBalance = 0;
        int numberOfMonths = 12;
        double minimumBalance = 1000;
        String budgetName = "Bill Pay Account";
        LongTermForecast forecast = new LongTermForecast(budgetName, startDate, startingBalance, numberOfMonths,
                minimumBalance);
        forecastEngine.generateLongTermForecast(forecast);
        System.out.println("The long term forecast was successfully generated");
        return forecast;
    } // End createLongTermForecast(Connection dbConnections).

    // Render the long term longTermForecast:
    public static void renderLongTermForecast(LongTermForecast longTermForecast) throws Exception, EntityException, BudgetException {
        System.out.println("\n\nRender the long term forecast.");
        ForecastView forecastView = new excelView();
        forecastView.setLongTermForecast(longTermForecast);
        String filename = "C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "LongTermForecast.tsv";
        forecastView.renderLongTermForecast(filename, "UTF-8");
        System.out.println("The long term forecast was successfully rendered to the file " + filename);
    }

}  // End class App.
