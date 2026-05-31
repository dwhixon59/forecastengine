package com.hixon.financialApp.utility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
 *     DatabaseConnectionManager mgr =
 *         new DatabaseConnectionManager("jdbc:mysql://localhost:3306/MyDB", "user", "pass");
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

