package com.hixon.financialApp;

import com.hixon.financialApp.controller.MainController;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.notification.async.file.fileBasedNotificationService;
import com.hixon.financialApp.view.cmdLine.ViewCmdline;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] goals) {
        Connection dbConnection = null;
        try {
            // Load the MySQL driver
            //Class.forName("com.mysql.cj.jdbc.Driver");

            // Use a MySQL database for persistence:
            dbConnection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/ForecastDatabase", "root", "***REMOVED-CREDENTIAL***");

//          dbConnection = DriverManager.getConnection(
//          "jdbc:mysql://financialappinstance1.ctgwj8jkemeb.us-east-1.rds.amazonaws.com:3306/forecastdatabase",
//          "admin", "***REMOVED-CREDENTIAL***59");

            // Create a mainController with the user 'dwhixon', the database connection, the command line transaction
            // resolver and the file-based notification service:
            MainController mainController = new MainController("dwhixon", dbConnection, new ViewCmdline(),
                    new fileBasedNotificationService());

            // Start the mainController and give it a list of goals to work on:
            mainController.run(goals);
//        } catch (ClassNotFoundException e) {
//            logger.error("MySQL driver not found", e);
        } catch (SQLException | EntityException e) {
            logger.error("An error occurred while connecting to the database", e);
        } finally {
            if (dbConnection != null) {
                try {
                    dbConnection.close();
                } catch (SQLException e) {
                    logger.error("An error occurred while closing the database connection", e);
                }
            }
        }
    }
}