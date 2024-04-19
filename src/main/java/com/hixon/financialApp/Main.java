package com.hixon.financialApp;

import com.hixon.financialApp.controller.MainController;
import com.hixon.financialApp.notification.async.file.fileBasedNotificationService;
import com.hixon.financialApp.view.cmdLine.ViewCmdline;

import java.sql.DriverManager;

public class Main {

    public static void main(String[] goals) throws Exception {

        // Use a MySQL database for persistence:
        java.sql.Connection dbConnection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/ForecastDatabase", "root", "***REMOVED-CREDENTIAL***");
//         java.sql.Connection dbConnection = DriverManager.getConnection(
//                 "jdbc:mysql://financialappinstance1.ctgwj8jkemeb.us-east-1.rds.amazonaws.com:3306/forecastdatabase",
//                 "admin", "***REMOVED-CREDENTIAL***59"));

        // Create a mainController with the user 'dwhixon', the database connection, the command line transaction
        // resolver and the file-based notification service:
        MainController mainController = new MainController("dwhixon", dbConnection, new ViewCmdline(),
                new fileBasedNotificationService());

        // Start the mainController and give it a list of goals to work on:
        mainController.run(goals);
    }
}
