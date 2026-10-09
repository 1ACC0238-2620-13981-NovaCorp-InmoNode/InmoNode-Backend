package com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.configuration.WebSecurityConfiguration;
import com.novacorp.inmonode.inmonodebackend.shared.application.monitoring.DatabaseHealthProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HealthController.class,
        properties = {"api.rate-limit.enabled=true", "api.rate-limit.general-per-minute=2"})
@Import(WebSecurityConfiguration.class)
@ActiveProfiles("test")
class ApiRateLimitSecurityTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private DatabaseHealthProbe databaseHealthProbe;

    @Test
    void deniedAnonymousCallsAreLimitedAfterCorsAndHealthRemainsAvailable() throws Exception {
        mvc.perform(get("/api/v1/private")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/private")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/private").header("Origin", "http://localhost:5173"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().exists("Retry-After"));
        mvc.perform(options("/api/v1/private").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
        when(databaseHealthProbe.isAvailable()).thenReturn(true);
        mvc.perform(get("/health")).andExpect(status().isOk());
    }
}
