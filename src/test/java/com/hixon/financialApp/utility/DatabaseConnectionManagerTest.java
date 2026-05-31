package com.hixon.financialApp.utility;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DatabaseConnectionManager} and the related changes to {@link Utility}.
 *
 * <p>All tests use Mockito static mocking of {@link DriverManager} so there is no
 * dependency on a real MySQL server.
 *
 * <p>The behaviour under test, per the design document:
 * <ul>
 *   <li>Construction opens an initial connection eagerly (fail-fast).</li>
 *   <li>{@code getConnection()} validates before returning and reconnects silently
 *       when the connection is closed or no longer valid.</li>
 *   <li>{@code isConnected()} is a lightweight guard-clause check.</li>
 *   <li>{@code close()} is idempotent.</li>
 *   <li>{@link Utility} static helpers delegate correctly to the manager.</li>
 * </ul>
 */
@DisplayName("DatabaseConnectionManager Tests")
@ExtendWith(MockitoExtension.class)
class DatabaseConnectionManagerTest {

    private static final String URL      = "jdbc:mysql://localhost:3306/TestDB";
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "testpass";

    /**
     * Restore Utility static state after every test so tests do not interfere
     * with each other through the shared singleton.
     */
    @AfterEach
    void clearUtilityState() {
        Utility.setConnectionManager(null);
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Opens an initial connection eagerly at construction time")
        void constructor_opensInitialConnectionEagerly() throws SQLException {
            Connection mockConn = mock(Connection.class);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(mockConn);

                new DatabaseConnectionManager(URL, USERNAME, PASSWORD);

                dmMock.verify(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD), times(1));
            }
        }

        @Test
        @DisplayName("Throws SQLException when the initial connection cannot be established")
        void constructor_throwsSQLExceptionOnFailure() {
            // Create the exception BEFORE opening the static mock scope so that
            // new SQLException(...) does not accidentally call DriverManager.getLogWriter()
            // inside the mock and confuse Mockito's stubbing state.
            SQLException connectionRefused = new SQLException("Connection refused");

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenThrow(connectionRefused);

                assertThrows(SQLException.class,
                        () -> new DatabaseConnectionManager(URL, USERNAME, PASSWORD),
                        "Constructor should propagate the SQLException from DriverManager");
            }
        }
    }

    // =========================================================================
    // getConnection()
    // =========================================================================

    @Nested
    @DisplayName("getConnection()")
    class GetConnectionTests {

        @Test
        @DisplayName("Returns the existing connection when it is still valid")
        void getConnection_returnsExistingConnectionWhenValid() throws SQLException {
            Connection mockConn = mock(Connection.class);
            when(mockConn.isClosed()).thenReturn(false);
            when(mockConn.isValid(3)).thenReturn(true);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(mockConn);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);
                Connection result = mgr.getConnection();

                assertSame(mockConn, result, "Should return the same connection instance");
                // DriverManager called only once – during construction
                dmMock.verify(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD), times(1));
            }
        }

        @Test
        @DisplayName("Multiple calls return the same instance while connection is live")
        void getConnection_returnsSameInstanceAcrossMultipleCalls() throws SQLException {
            Connection mockConn = mock(Connection.class);
            when(mockConn.isClosed()).thenReturn(false);
            when(mockConn.isValid(3)).thenReturn(true);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(mockConn);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);

                Connection c1 = mgr.getConnection();
                Connection c2 = mgr.getConnection();
                Connection c3 = mgr.getConnection();

                assertSame(c1, c2);
                assertSame(c2, c3);
                // Only one DriverManager call regardless of how many times getConnection() is called
                dmMock.verify(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD), times(1));
            }
        }

        @Test
        @DisplayName("Reconnects transparently when isClosed() returns true (server dropped connection)")
        void getConnection_reconnectsWhenConnectionIsClosed() throws SQLException {
            Connection stale = mock(Connection.class);
            Connection fresh = mock(Connection.class);

            // The stale connection reports itself as closed; the fresh one is fine.
            // NOTE: fresh.isClosed() / fresh.isValid() are NOT stubbed here because
            //       the manager returns the new connection immediately after openConnection()
            //       without re-validating it in the same getConnection() call.
            when(stale.isClosed()).thenReturn(true);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(stale, fresh);   // first call → stale, second → fresh

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);
                Connection result = mgr.getConnection();

                assertSame(fresh, result, "Should return the fresh reconnected connection");
                dmMock.verify(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD), times(2));
            }
        }

        @Test
        @DisplayName("Reconnects transparently when isValid() returns false (MySQL wait_timeout expired)")
        void getConnection_reconnectsWhenConnectionIsNoLongerValid() throws SQLException {
            Connection stale = mock(Connection.class);
            Connection fresh = mock(Connection.class);

            when(stale.isClosed()).thenReturn(false);
            when(stale.isValid(3)).thenReturn(false);  // server timed out the idle connection

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(stale, fresh);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);
                Connection result = mgr.getConnection();

                assertSame(fresh, result, "Should return the fresh reconnected connection");
                dmMock.verify(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD), times(2));
            }
        }

        @Test
        @DisplayName("Throws SQLException when reconnection itself fails")
        void getConnection_throwsSQLExceptionWhenReconnectionFails() throws SQLException {
            Connection stale = mock(Connection.class);
            when(stale.isClosed()).thenReturn(true);

            // Create exception before entering the mock scope to avoid side-effects
            SQLException reconnectFailure = new SQLException("Server unreachable");

            // Use thenAnswer to return the stale connection on the first call and
            // throw on the second, since MockedStatic.Stubber does not support
            // chaining thenReturn(...).thenThrow(...) directly.
            AtomicInteger callCount = new AtomicInteger(0);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenAnswer(inv -> {
                          if (callCount.incrementAndGet() == 1) return stale;
                          throw reconnectFailure;
                      });

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);

                assertThrows(SQLException.class, mgr::getConnection,
                        "Should propagate the reconnection failure");
            }
        }
    }

    // =========================================================================
    // isConnected()
    // =========================================================================

    @Nested
    @DisplayName("isConnected()")
    class IsConnectedTests {

        @Test
        @DisplayName("Returns true when the underlying connection is open")
        void isConnected_returnsTrueWhenOpen() throws SQLException {
            Connection mockConn = mock(Connection.class);
            when(mockConn.isClosed()).thenReturn(false);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(mockConn);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);
                assertTrue(mgr.isConnected());
            }
        }

        @Test
        @DisplayName("Returns false when the underlying connection is closed")
        void isConnected_returnsFalseWhenClosed() throws SQLException {
            Connection mockConn = mock(Connection.class);
            when(mockConn.isClosed()).thenReturn(true);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(mockConn);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);
                assertFalse(mgr.isConnected());
            }
        }

        @Test
        @DisplayName("Returns false and does not throw when isClosed() itself throws SQLException")
        void isConnected_returnsFalseWhenIsClosedThrows() throws SQLException {
            Connection mockConn = mock(Connection.class);
            // Create exception before entering mock scope
            SQLException networkError = new SQLException("Network error");
            when(mockConn.isClosed()).thenThrow(networkError);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(mockConn);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);
                assertFalse(mgr.isConnected(), "Should return false (not throw) when isClosed() throws");
            }
        }
    }

    // =========================================================================
    // close()
    // =========================================================================

    @Nested
    @DisplayName("close()")
    class CloseTests {

        @Test
        @DisplayName("Closes the underlying connection")
        void close_closesConnection() throws SQLException {
            Connection mockConn = mock(Connection.class);
            when(mockConn.isClosed()).thenReturn(false);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(mockConn);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);
                mgr.close();

                verify(mockConn, times(1)).close();
            }
        }

        @Test
        @DisplayName("Does NOT call close() on a connection that is already closed (idempotent)")
        void close_idempotentWhenAlreadyClosed() throws SQLException {
            Connection mockConn = mock(Connection.class);
            when(mockConn.isClosed()).thenReturn(true); // already closed

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(mockConn);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);

                assertDoesNotThrow(mgr::close, "close() on an already-closed connection should not throw");
                verify(mockConn, never()).close(); // close() must not be called again
            }
        }
    }

    // =========================================================================
    // Utility integration
    // =========================================================================

    @Nested
    @DisplayName("Utility integration")
    class UtilityIntegrationTests {

        @Test
        @DisplayName("Utility.getDbConnection() throws SQLException when no manager has been set")
        void utility_getDbConnectionThrowsWhenNoManagerSet() {
            Utility.setConnectionManager(null);

            assertThrows(SQLException.class, Utility::getDbConnection,
                    "Should throw when no connection manager is configured");
        }

        @Test
        @DisplayName("Utility.isConnectionManagerSet() returns false when no manager is set")
        void utility_isConnectionManagerSetReturnsFalseWhenNull() {
            Utility.setConnectionManager(null);
            assertFalse(Utility.isConnectionManagerSet());
        }

        @Test
        @DisplayName("Utility.isConnectionManagerSet() returns true after a manager is registered")
        void utility_isConnectionManagerSetReturnsTrueWhenSet() throws SQLException {
            Connection mockConn = mock(Connection.class);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(mockConn);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);
                Utility.setConnectionManager(mgr);

                assertTrue(Utility.isConnectionManagerSet());
            }
        }

        @Test
        @DisplayName("Utility.getDbConnection() delegates to the manager and returns a live connection")
        void utility_getDbConnectionDelegatesToManager() throws SQLException {
            Connection mockConn = mock(Connection.class);
            when(mockConn.isClosed()).thenReturn(false);
            when(mockConn.isValid(3)).thenReturn(true);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(mockConn);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);
                Utility.setConnectionManager(mgr);

                Connection result = Utility.getDbConnection();
                assertSame(mockConn, result, "Utility.getDbConnection() should return the manager's connection");
            }
        }

        @Test
        @DisplayName("Utility.getDbConnection() triggers reconnection through the manager")
        void utility_getDbConnectionTriggersReconnectionThroughManager() throws SQLException {
            Connection stale = mock(Connection.class);
            Connection fresh = mock(Connection.class);

            when(stale.isClosed()).thenReturn(false);
            when(stale.isValid(3)).thenReturn(false); // server timed out

            // fresh connection stubs not needed here: manager returns it immediately
            // after openConnection() without an extra validation pass.

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(stale, fresh);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);
                Utility.setConnectionManager(mgr);

                Connection result = Utility.getDbConnection();
                assertSame(fresh, result,
                        "Utility.getDbConnection() should transparently return the reconnected connection");
                dmMock.verify(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD), times(2));
            }
        }

        @Test
        @DisplayName("Utility.closeConnectionManager() is safe to call when no manager is set")
        void utility_closeConnectionManagerSafeWhenNull() {
            Utility.setConnectionManager(null);
            assertDoesNotThrow(Utility::closeConnectionManager,
                    "closeConnectionManager() must not throw when no manager has been set");
        }

        @Test
        @DisplayName("Utility.closeConnectionManager() closes the manager's connection")
        void utility_closeConnectionManagerClosesConnection() throws SQLException {
            Connection mockConn = mock(Connection.class);
            when(mockConn.isClosed()).thenReturn(false);

            try (MockedStatic<DriverManager> dmMock = mockStatic(DriverManager.class)) {
                dmMock.when(() -> DriverManager.getConnection(URL, USERNAME, PASSWORD))
                      .thenReturn(mockConn);

                DatabaseConnectionManager mgr = new DatabaseConnectionManager(URL, USERNAME, PASSWORD);
                Utility.setConnectionManager(mgr);

                Utility.closeConnectionManager();

                verify(mockConn, times(1)).close();
            }
        }
    }
}

