package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.configuration;

import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.tokens.TokenService;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.authorization.sfs.model.AuthenticatedUser;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.tokens.jwt.services.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JWT (US-31) and CORS (US-38) rules of the single security filter chain. No business module exposes
 * a protected endpoint yet, so a test-only {@link ProbeController} stands in for one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, WebSecurityConfigurationIntegrationTest.ProbeController.class})
class WebSecurityConfigurationIntegrationTest {

    private static final String PROTECTED_PATH = "/api/v1/security-probe/me";
    private static final String CATALOG_ADMIN_PATH = "/api/v1/security-probe/catalog-admin";
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final User BUYER =
            User.restore(42L, "buyer@mail.com", "hash", Role.BUYER, UserStatus.ACTIVE, null, null, 0, null);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Value("${authorization.jwt.secret}")
    private String jwtSecret;

    @Test
    void protectedRouteWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedRouteWithMalformedTokenIsUnauthorized() throws Exception {
        getWithBearer(PROTECTED_PATH, "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedRouteWithExpiredTokenIsUnauthorized() throws Exception {
        var twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        var expiredToken = new JwtTokenService(jwtSecret, 3600, twoHoursAgo).generateToken(BUYER);

        getWithBearer(PROTECTED_PATH, expiredToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedRouteWithValidTokenExposesTheCaller() throws Exception {
        getWithBearer(PROTECTED_PATH, tokenService.generateToken(BUYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.role").value("BUYER"));
    }

    @Test
    void roleNotAdmittedByPreAuthorizeIsForbidden() throws Exception {
        mockMvc.perform(get(CATALOG_ADMIN_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.generateToken(BUYER))
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta operación"));
    }

    @Test
    void preflightFromPortalOriginIsAllowed() throws Exception {
        preflight(ALLOWED_ORIGIN)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    void preflightFromUnknownOriginIsRejected() throws Exception {
        preflight("https://evil.example.com")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private ResultActions getWithBearer(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions preflight(String origin) throws Exception {
        return mockMvc.perform(options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"));
    }

    @RestController
    @RequestMapping("/api/v1/security-probe")
    static class ProbeController {

        @GetMapping("/me")
        Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser caller) {
            return Map.of("userId", caller.userId(), "role", caller.role().name());
        }

        @GetMapping("/catalog-admin")
        @PreAuthorize("hasRole('CATALOG_ADMIN')")
        Map<String, Object> catalogAdmin() {
            return Map.of("ok", true);
        }
    }
}
