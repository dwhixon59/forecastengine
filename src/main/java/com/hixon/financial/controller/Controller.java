package com.hixon.financial.controller;

import com.hixon.financial.Utility;
import com.hixon.financial.model.EntityException;
import com.hixon.financial.model.budget.BudgetException;
import com.hixon.financial.model.forecast.*;
import com.hixon.financial.model.register.RegisterException;
import com.hixon.financial.view.ForecastView;
import com.hixon.financial.view.ViewException;
import com.hixon.financial.view.excel.excelView;
import com.hixon.financial.view.register.ExcelRegisterView;
import com.hixon.financial.view.register.RegisterView;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Scanner;

/**
 * Hello world!
 */
public class Controller {
    public static void main(String[] args) throws Exception, BudgetException, ControllerException, RegisterException, ViewException, EntityException {

        boolean importCsvTransactionFile = false;
        boolean renderRegister = false;
        boolean createShortTermForecast = false;
        boolean renderShortTermForecast = false;
        boolean saveShortTermForecast = false;
        boolean createLongTermForecast = false;
        boolean renderLongTermForecast = false;
        boolean saveLongTermForecast = false;

        Connection dbConnection = null;
        ShortTermForecast shortTermForecast = null;
        LongTermForecast longTermForecast = null;
        RegisterView registerView = null;

        // Convert the input parameters into goals:
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
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

            // Import new transactions into the register:
            if (importCsvTransactionFile) {
                Importer importer = new Importer(dbConnection);
                int numImportedTrxs = importer.importCsvTransactionFile("C:\\Users\\dwhix\\Dropbox\\Hixon " +
                        "Family Personal Business\\Finances\\Expenses\\Transactions.csv",
                        "Wells Fargo Bank", "Bill Pay Account");
            }

            // Export the transactions to Excel beginning on a particular date:
            if (renderRegister) {
                Calendar startDate = getStartDate();
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
                shortTermForecast.save(dbConnection);
                System.out.println("The forecast was successfully saved to the database.");
            }

            // Create the long term forecast:
            if (createLongTermForecast) {
                longTermForecast = createLongTermForecast(dbConnection);
            }

            // Render the long term forecast:
            if (longTermForecast != null && renderLongTermForecast) {
                renderLongTermForecast(longTermForecast);
            }

            // Save the long term forecast:
            if (longTermForecast != null && saveLongTermForecast) {
                longTermForecast.save(dbConnection);
                System.out.println("The forecast was successfully saved to the database.");
            }

            // Close the connection to the database:
            System.out.println("\n\nClose the connection to the database.");
            dbConnection.close();

        } catch (Exception e) {
            if (dbConnection != null) {
                dbConnection.close();
            }
            e.printStackTrace();
        } catch (QuitException e) {
            System.out.println(e.getMessage());
            dbConnection.close();
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

    private static ShortTermForecast createShortTermForecast() throws Exception {
        System.out.println("Create the short term forecast.");
        ForecastEngine forecastEngine = new ForecastEngine();
        double startingBalance = 0;
        double minimumBalance = 1000;
        String budgetName = "Bill Pay Account";
        ShortTermForecast shortTermForecast = new ShortTermForecast(budgetName, startingBalance, minimumBalance);
        System.out.println("\n\nGenerate the short term forecast.");
        forecastEngine.generateShortTermForcast(shortTermForecast);
        System.out.println("The short term forecast was successfully generated");
        return shortTermForecast;
    }

    private static void renderShortTermForecast(ShortTermForecast forecast) throws FileNotFoundException, ForecastException,
            UnsupportedEncodingException {
        System.out.println("\n\nRender the short term forecast.");
        ForecastView forecastView = new excelView();
        //forecastView.setForecast((Forecast) forecast);
        forecastView.render("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                        "ShortTermForecast.tsv", "UTF-8");
        System.out.println("The short term forecast was successfully rendered.");
     }

    // Create the long term forecast:
    public static LongTermForecast createLongTermForecast(Connection dbConnection) throws Exception {
        System.out.println("Create the long term forecast.");
        ForecastEngine forecastEngine = new ForecastEngine();
        Calendar startDate = new GregorianCalendar();
        startDate.set(2019, Calendar.AUGUST, 1, 0, 0, 0);
        double startingBalance = 0;
        int numberOfMonths = 12;
        double minimumBalance = 1000;
        String budgetName = "Bill Pay Account";
        LongTermForecast forecast = new LongTermForecast(budgetName, startDate, startingBalance, numberOfMonths,
                minimumBalance);
        forecastEngine.generateLongTermForcast(forecast);
        System.out.println("The long term forecast was successfully generated");
        return forecast;
    } // End createLongTermForecast(Connection dbConnections).

    // Render the long term forecast:
    public static void renderLongTermForecast(LongTermForecast forecast) throws ForecastException, FileNotFoundException,
            UnsupportedEncodingException {
        System.out.println("\n\nRender the long term forecast.");
        ForecastView forecastView = new excelView();
        forecastView.setForecast(forecast);
        forecastView.render("C:\\Users\\dwhix\\Dropbox\\Hixon Family Personal Business\\Finances\\Expenses\\" +
                "LongTermForecast.tsv", "UTF-8");
        System.out.println("The long term forecast was successfully rendered.");
    }

}  // End class App.
