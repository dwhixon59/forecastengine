package com.hixon.financialApp;

import com.hixon.financialApp.controller.MainController;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.notification.async.file.fileBasedNotificationService;
import com.hixon.financialApp.utility.DatabaseConnectionManager;
import com.hixon.financialApp.view.cmdLine.ViewCmdline;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] goals) {
        DatabaseConnectionManager mgr = null;
        try {
            // Create a connection manager – opens the initial connection eagerly (fail-fast)
            // and will reconnect automatically if the MySQL server times out the idle connection.
            mgr = new DatabaseConnectionManager(
                    "jdbc:mysql://localhost:3306/ForecastDatabase", "root", "***REMOVED-CREDENTIAL***");

//          mgr = new DatabaseConnectionManager(
//              "jdbc:mysql://financialappinstance1.ctgwj8jkemeb.us-east-1.rds.amazonaws.com:3306/forecastdatabase",
//              "admin", "***REMOVED-CREDENTIAL***59");

            // Create a mainController with the user 'dwhixon', the connection manager, the command line
            // view and the file-based notification service:
            MainController mainController = new MainController("dwhixon", mgr, new ViewCmdline(),
                    new fileBasedNotificationService());

            // Start the mainController and give it a list of goals to work on:
            mainController.run(goals);

        } catch (SQLException | EntityException e) {
            logger.error("An error occurred while connecting to the database", e);
        } finally {
            if (mgr != null) {
                try {
                    mgr.close();
                } catch (SQLException e) {
                    logger.error("An error occurred while closing the database connection", e);
                }
            }
        }
    }
}