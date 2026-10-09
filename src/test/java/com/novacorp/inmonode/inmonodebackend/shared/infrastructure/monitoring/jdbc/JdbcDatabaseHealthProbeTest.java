package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.monitoring.jdbc;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcDatabaseHealthProbeTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final JdbcDatabaseHealthProbe probe = new JdbcDatabaseHealthProbe(dataSource, 2);

    @Test
    void aValidConnectionIsUpAndIsReturnedToThePool() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        assertTrue(probe.isAvailable());

        verify(connection).close();
    }

    @Test
    void anInvalidConnectionIsDownAndIsReturnedToThePool() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);

        assertFalse(probe.isAvailable());

        verify(connection).close();
    }

    @Test
    void aPoolTimeoutIsDownInsteadOfAnUnhandledError() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLTimeoutException("connection pool exhausted"));

        assertFalse(probe.isAvailable());
    }

    @Test
    void aValidationTimeoutIsDownAndStillReturnsTheConnection() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenThrow(new SQLTimeoutException("database did not respond"));

        assertFalse(probe.isAvailable());

        verify(connection).close();
    }

    @Test
    void theNextProbeReflectsDatabaseRecovery() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("database offline")).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        assertFalse(probe.isAvailable());
        assertTrue(probe.isAvailable());

        verify(connection).close();
    }

    @Test
    void theConfiguredTimeoutIsPassedToTheDriver() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(true);

        assertTrue(new JdbcDatabaseHealthProbe(dataSource, 5).isAvailable());
    }

    @Test
    void anUnlimitedOrNegativeValidationTimeoutIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JdbcDatabaseHealthProbe(dataSource, 0));
        assertThrows(IllegalArgumentException.class, () -> new JdbcDatabaseHealthProbe(dataSource, -1));
    }
}
