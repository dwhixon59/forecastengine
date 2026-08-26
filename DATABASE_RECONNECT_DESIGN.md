# Database Reconnection Design

## Problem

The application opens a single MySQL JDBC `Connection` at startup and stores it as a static field in `Utility.dbConnection`.  MySQL servers close idle connections after a configurable timeout (the default `wait_timeout` is 8 hours).  If the app is left running across that boundary – or if the network briefly drops – the next SQL statement will throw a `CommunicationsException` or `"The last packet successfully received from the server was X milliseconds ago"` error and the session dies.

---

## Current Architecture

```
Main.java
  └─ DriverManager.getConnection(url, user, pw)  →  Connection
       └─ MainController(…, dbConnection, …)
            └─ Utility.setDbConnection(dbConnection)   ← global singleton
                 └─ Every model/entity class calls Utility.getDbConnection()
```

All SQL work is performed through `Utility.getDbConnection()`.  There is **one** place to fix.

---

## Proposed Solution: `DatabaseConnectionManager`

Introduce a new class `com.hixon.financialApp.utility.DatabaseConnectionManager` that:

1. Stores the JDBC URL, username and password.
2. Exposes a `getConnection()` method that **validates** the existing connection before returning it, and **reconnects silently** if it is stale or closed.
3. Replaces the raw `Connection` field in `Utility` so that all callers continue to call `Utility.getDbConnection()` without change – but internally that method now delegates to the manager.

### Reconnect Logic (inside `getConnection()`)

```
if connection == null
   OR connection.isClosed()
   OR NOT connection.isValid(timeoutSeconds = 3)
then
    connection = DriverManager.getConnection(url, user, password)
    log "Reconnected to database"
return connection
```

`Connection.isValid(int timeout)` sends a lightweight ping to the server and returns `false` if the server no longer recognises the connection.  It is the standard JDBC 4.0 mechanism for liveness checking.

---

## Files to Create / Modify

| File | Change |
|------|--------|
| `src/main/java/…/utility/DatabaseConnectionManager.java` | **New** – holds credentials, reconnect logic |
| `src/main/java/…/utility/Utility.java` | Replace raw `Connection` field + setter/getter with a `DatabaseConnectionManager` |
| `src/main/java/…/Main.java` | Pass credentials to `DatabaseConnectionManager` instead of an open `Connection` |

---

## Detailed Design

### `DatabaseConnectionManager.java`

```java
public class DatabaseConnectionManager {

    private final String url;
    private final String username;
    private final String password;
    private Connection connection;

    public DatabaseConnectionManager(String url, String username, String password)
            throws SQLException {
        this.url = url;
        this.username = username;
        this.password = password;
        this.connection = openConnection();   // eager – fail fast at startup
    }

    /**
     * Returns a live connection, reconnecting transparently if the current
     * connection has timed out or been closed by the server.
     */
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            logger.warn("Database connection lost – reconnecting…");
            connection = openConnection();
            logger.info("Reconnected to database successfully.");
        }
        return connection;
    }

    public synchronized void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
```

### `Utility.java` changes

* Replace `private static Connection dbConnection` with `private static DatabaseConnectionManager connectionManager`.
* `setDbConnection(Connection)` is removed (no longer needed from `Main`).
* Add `setConnectionManager(DatabaseConnectionManager)`.
* `getDbConnection()` becomes:

```java
public static Connection getDbConnection() throws SQLException {
    return connectionManager.getConnection();   // reconnects if needed
}
```

* Because `getDbConnection()` will now declare `throws SQLException`, any call site that already handles `SQLException` (virtually all model/entity code) will compile without changes.  The few places that do not currently handle it will need a `throws` clause or a `try/catch` added.

### `Main.java` changes

```java
DatabaseConnectionManager mgr = DatabaseConnectionManager.fromProperties();

MainController mainController =
    new MainController("dwhixon", mgr, new ViewCmdline(),
                       new fileBasedNotificationService());
```

`MainController` stores the manager in `Utility` instead of a bare `Connection`.  In the `finally` block, `mgr.close()` replaces `dbConnection.close()`.

---

## Why Not a Connection Pool?

Libraries such as HikariCP or Apache DBCP provide pooling **and** reconnection.  For a single-user, single-threaded desktop application a full pool is unnecessary complexity.  The lightweight `DatabaseConnectionManager` above achieves the same reliability goal with zero new dependencies.

If the application later becomes multi-user or concurrent, migrating to HikariCP would be straightforward: replace `DatabaseConnectionManager.getConnection()` with a pool `dataSource.getConnection()`.

---

## Testing Strategy

1. **Unit test** – mock the `Connection` so that `isValid()` returns `false` on the first call, then verify that `getConnection()` calls `DriverManager.getConnection()` again and returns a fresh connection.
2. **Manual integration test** – start the app, let the MySQL server's `wait_timeout` expire (or run `SET SESSION wait_timeout = 5` in MySQL to shorten it to 5 seconds), wait, then perform any operation and confirm it succeeds without crashing.

---

## Implementation Steps

1. Create `DatabaseConnectionManager.java`.
2. Update `Utility.java` (swap field + getter).
3. Update `MainController.java` constructor signature.
4. Update `Main.java` to build the manager.
5. Compile and run unit tests (`mvn test`).
6. Perform manual timeout integration test.

