package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.monitoring.jdbc;

import com.novacorp.inmonode.inmonodebackend.shared.application.monitoring.DatabaseHealthProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;

/** Checks the application's database and always returns the borrowed connection to the pool. */
@Component
public class JdbcDatabaseHealthProbe implements DatabaseHealthProbe {

    private final DataSource dataSource;
    private final int validationTimeoutSeconds;

    public JdbcDatabaseHealthProbe(DataSource dataSource,
                                   @Value("${monitoring.health.validation-timeout-seconds:2}") int validationTimeoutSeconds) {
        if (validationTimeoutSeconds < 1) {
            throw new IllegalArgumentException("health validation timeout must be at least one second");
        }
        this.dataSource = dataSource;
        this.validationTimeoutSeconds = validationTimeoutSeconds;
    }

    @Override
    public boolean isAvailable() {
        // Acquiring a connection is bounded separately by the data source's connection timeout.
        try (var connection = dataSource.getConnection()) {
            return connection.isValid(validationTimeoutSeconds);
        } catch (SQLException ex) {
            // The public health response must never contain JDBC URLs, credentials or driver exception messages.
            return false;
        }
    }
}
