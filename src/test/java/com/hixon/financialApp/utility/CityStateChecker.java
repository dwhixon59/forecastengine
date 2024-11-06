package com.hixon.financialApp.utility;

import com.hixon.financialApp.model.entity.EntityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CityStateCheckerTest {

    private Connection mockConnection;
    private PreparedStatement mockPreparedStatement;
    private ResultSet mockResultSet;

    @BeforeEach
    void setUp() throws SQLException {
        mockConnection = mock(Connection.class);
        mockPreparedStatement = mock(PreparedStatement.class);
        mockResultSet = mock(ResultSet.class);

        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    }

    @Test
    void testExists_CityStatePairExists() throws SQLException, EntityException {
        try (MockedStatic<Utility> mockedUtility = Mockito.mockStatic(Utility.class)) {
            mockedUtility.when(Utility::getDbConnection).thenReturn(mockConnection);
            when(mockResultSet.next()).thenReturn(true);

            boolean result = CityStateChecker.exists("New York", "NY");
            assertTrue(result, "The city-state pair should exist.");
        }
    }

    @Test
    void testExists_CityStatePairDoesNotExist() throws SQLException, EntityException {
        try (MockedStatic<Utility> mockedUtility = Mockito.mockStatic(Utility.class)) {
            mockedUtility.when(Utility::getDbConnection).thenReturn(mockConnection);
            when(mockResultSet.next()).thenReturn(false);

            boolean result = CityStateChecker.exists("NonExistentCity", "XX");
            assertFalse(result, "The city-state pair should not exist.");
        }
    }

    @Test
    void testExists_SQLException() throws SQLException {
        try (MockedStatic<Utility> mockedUtility = Mockito.mockStatic(Utility.class)) {
            mockedUtility.when(Utility::getDbConnection).thenReturn(mockConnection);
            when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("Database error"));

            EntityException thrown = assertThrows(EntityException.class, () -> {
                CityStateChecker.exists("New York", "NY");
            });

            assertEquals("Database error occurred trying to retrieve a city-state pair. \nSQL statement was SELECT 1 FROM cities WHERE city_ascii = ? AND state_id = ? LIMIT 1.  \nCity: New York, State: NY", thrown.getMessage());
        }
    }
}