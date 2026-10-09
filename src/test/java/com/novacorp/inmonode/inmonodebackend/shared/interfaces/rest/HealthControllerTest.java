package com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.configuration.WebSecurityConfiguration;
import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.monitoring.jdbc.JdbcDatabaseHealthProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLTimeoutException;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the actual security chain and JDBC adapter without requiring Docker or a running database. */
@WebMvcTest(controllers = HealthController.class,
        properties = {"authorization.cors.allowed-origins=http://localhost:5173",
                "monitoring.health.validation-timeout-seconds=2"})
@Import({WebSecurityConfiguration.class, JdbcDatabaseHealthProbe.class})
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private DataSource dataSource;

    @Test
    void aLoadBalancerWithoutTokenGetsUpWhenTheDatabaseResponds() throws Exception {
        var connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.services.api").value("UP"))
                .andExpect(jsonPath("$.services.database").value("UP"));

        verify(connection).close();
    }

    @Test
    void aDatabaseTimeoutReturnsServiceUnavailableWithoutConnectionDetails() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLTimeoutException("private-host password=secret"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.services.api").value("UP"))
                .andExpect(jsonPath("$.services.database").value("DOWN"))
                .andExpect(jsonPath("$.message").value("The main database is unavailable"))
                .andExpect(content().string(not(containsString("private-host"))))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void aFailedPingReturnsServiceUnavailable() throws Exception {
        var connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);

        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.services.database").value("DOWN"));
    }

    @Test
    void openingTheHealthGetDoesNotOpenOtherMethodsOrProtectedRoutes() throws Exception {
        mockMvc.perform(post("/health")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/health/private")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/account-statements")).andExpect(status().isUnauthorized());

        verifyNoInteractions(dataSource);
    }
}
