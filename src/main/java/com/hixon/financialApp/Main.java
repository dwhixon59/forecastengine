package com.hixon.financialApp;

import com.hixon.financialApp.controller.MainController;
import com.hixon.financialApp.model.entity.EntityException;
import com.hixon.financialApp.notification.async.file.fileBasedNotificationService;
import com.hixon.financialApp.utility.DatabaseConnectionManager;
import com.hixon.financialApp.view.cmdLine.ViewCmdline;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Properties;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] goals) {
        DatabaseConnectionManager mgr = null;
        try {
            // Load database credentials from db.properties (excluded from version control).
            // See src/main/resources/db.properties.example for the required format.
            Properties dbProps = new Properties();
            try (InputStream in = Main.class.getClassLoader().getResourceAsStream("db.properties")) {
                if (in == null) {
                    throw new IOException(
                            "db.properties not found on the classpath.  " +
                            "Copy src/main/resources/db.properties.example to " +
                            "src/main/resources/db.properties and fill in your credentials.");
                }
                dbProps.load(in);
            }

            String dbUrl      = dbProps.getProperty("db.url");
            String dbUsername = dbProps.getProperty("db.username");
            String dbPassword = dbProps.getProperty("db.password");

            // Create a connection manager – opens the initial connection eagerly (fail-fast)
            // and will reconnect automatically if the MySQL server times out the idle connection.
            mgr = new DatabaseConnectionManager(dbUrl, dbUsername, dbPassword);

            // Create a mainController with the user 'dwhixon', the connection manager, the command line
            // view and the file-based notification service:
            MainController mainController = new MainController("dwhixon", mgr, new ViewCmdline(),
                    new fileBasedNotificationService());

            // Start the mainController and give it a list of goals to work on:
            mainController.run(goals);

        } catch (IOException e) {
            logger.error("Could not load database credentials", e);
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