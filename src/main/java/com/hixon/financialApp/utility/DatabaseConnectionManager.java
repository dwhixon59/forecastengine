package com.hixon.financialApp.utility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages a single JDBC connection with automatic reconnection support.
 *
 * <p>MySQL (and most other databases) close idle connections after a configurable timeout
 * (MySQL default: 8 hours via {@code wait_timeout}).  This class validates the connection
 * before every use and reconnects transparently if the server has closed it, preventing
 * {@code CommunicationsException} errors in long-running sessions.
 *
 * <p>Usage:
 * <pre>
 *     DatabaseConnectionManager mgr = DatabaseConnectionManager.fromProperties();
 *     Utility.setConnectionManager(mgr);
 *     // ... later, in any model class:
 *     Connection conn = Utility.getDbConnection();   // always live
 *     // ... at application shutdown:
 *     mgr.close();
 * </pre>
 */
public class DatabaseConnectionManager {

    private static final Logger logger = LogManager.getLogger(DatabaseConnectionManager.class);

    /** Timeout in seconds used by {@link Connection#isValid(int)} to ping the server. */
    private static final int VALIDITY_CHECK_TIMEOUT_SECONDS = 3;

    private final String url;
    private final String username;
    private final String password;
    private Connection connection;

    /**
     * Creates a new manager and opens an initial connection immediately (fail-fast at startup).
     *
     * @param url      JDBC URL, e.g. {@code "jdbc:mysql://localhost:3306/ForecastDatabase"}
     * @param username database username
     * @param password database password
     * @throws SQLException if the initial connection cannot be established
     */
    public DatabaseConnectionManager(String url, String username, String password)
            throws SQLException {
        this.url = url;
        this.username = username;
        this.password = password;
        this.connection = openConnection();
        logger.info("Initial database connection established.");
    }

    /**
     * Creates a manager from the credentials in {@code db.properties} on the classpath.
     *
     * <p>Credentials must never be hardcoded in source.  {@code db.properties} is excluded from
     * version control; see {@code src/main/resources/db.properties.example} for the format.
     *
     * @return a manager connected using the configured credentials
     * @throws IOException  if db.properties is missing or unreadable
     * @throws SQLException if the initial connection cannot be established
     */
    public static DatabaseConnectionManager fromProperties() throws IOException, SQLException {
        Properties dbProps = new Properties();
        try (InputStream in = DatabaseConnectionManager.class.getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new IOException(
                        "db.properties not found on the classpath.  " +
                        "Copy src/main/resources/db.properties.example to " +
                        "src/main/resources/db.properties and fill in your credentials.");
            }
            dbProps.load(in);
        }
        return new DatabaseConnectionManager(dbProps.getProperty("db.url"),
                dbProps.getProperty("db.username"),
                dbProps.getProperty("db.password"));
    }

    /**
     * Returns a live database connection, reconnecting transparently if the current connection
     * has timed out or been closed by the server.
     *
     * <p>This method is {@code synchronized} so it is safe for the (unlikely) case of concurrent
     * access in a future multi-threaded scenario.
     *
     * @return a valid, open {@link Connection}
     * @throws SQLException if reconnection fails
     */
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()
                || !connection.isValid(VALIDITY_CHECK_TIMEOUT_SECONDS)) {
            logger.warn("Database connection lost or stale – reconnecting…");
            connection = openConnection();
            logger.info("Reconnected to database successfully.");
        }
        return connection;
    }

    /**
     * Returns {@code true} if this manager currently holds an open, valid connection.
     * This is a lightweight check (no network round-trip) used mainly in guard clauses.
     */
    public synchronized boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Closes the underlying connection. Should be called once at application shutdown.
     *
     * @throws SQLException if closing fails
     */
    public synchronized void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            logger.info("Database connection closed.");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}

