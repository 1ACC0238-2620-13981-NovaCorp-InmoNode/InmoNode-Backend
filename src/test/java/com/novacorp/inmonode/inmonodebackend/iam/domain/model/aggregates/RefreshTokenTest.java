package com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final Duration THIRTY_DAYS = Duration.ofDays(30);

    @Test
    void issuedTokenIsUsableUntilItExpires() {
        var token = RefreshToken.issue(7L, "hash", NOW, THIRTY_DAYS);

        assertEquals(NOW.plus(THIRTY_DAYS), token.getExpiresAt());
        assertTrue(token.isUsable(NOW));
        assertTrue(token.isUsable(NOW.plus(THIRTY_DAYS).minusSeconds(1)));
        assertFalse(token.isUsable(NOW.plus(THIRTY_DAYS)));
    }

    @Test
    void revokedTokenIsNotUsableAndKeepsTheFirstRevocationInstant() {
        var token = RefreshToken.issue(7L, "hash", NOW, THIRTY_DAYS);

        token.revoke(NOW.plusSeconds(60));
        token.revoke(NOW.plusSeconds(120));

        assertTrue(token.isRevoked());
        assertFalse(token.isUsable(NOW.plusSeconds(90)));
        assertEquals(NOW.plusSeconds(60), token.getRevokedAt());
    }
}
