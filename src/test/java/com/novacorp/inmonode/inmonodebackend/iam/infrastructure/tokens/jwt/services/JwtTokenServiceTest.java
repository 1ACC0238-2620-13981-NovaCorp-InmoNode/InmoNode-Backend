package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.tokens.jwt.services;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final User USER =
            User.restore(7L, "ana@mail.com", "hash", Role.FIELD_AGENT, UserStatus.ACTIVE, null, null, 0, null);

    private static JwtTokenService serviceAt(Instant instant, String secret) {
        return new JwtTokenService(secret, 3600, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void generatedTokenRoundTripsClaims() {
        var service = serviceAt(NOW, SECRET);

        var claims = service.parseToken(service.generateToken(USER)).orElseThrow();

        assertEquals(7L, claims.userId());
        assertEquals("ana@mail.com", claims.email());
        assertEquals(Role.FIELD_AGENT, claims.role());
    }

    @Test
    void expiredTokenIsRejected() {
        var token = serviceAt(NOW, SECRET).generateToken(USER);

        var later = serviceAt(NOW.plus(Duration.ofHours(2)), SECRET);

        assertTrue(later.parseToken(token).isEmpty());
    }

    @Test
    void tamperedOrForeignOrMalformedTokensAreRejected() {
        var service = serviceAt(NOW, SECRET);
        var token = service.generateToken(USER);
        var foreign = serviceAt(NOW, "another-secret-another-secret-123456").generateToken(USER);

        assertTrue(service.parseToken(token + "x").isEmpty());
        assertTrue(service.parseToken(foreign).isEmpty());
        assertTrue(service.parseToken("not-a-jwt").isEmpty());
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThrows(IllegalStateException.class, () -> serviceAt(NOW, "short"));
    }
}
