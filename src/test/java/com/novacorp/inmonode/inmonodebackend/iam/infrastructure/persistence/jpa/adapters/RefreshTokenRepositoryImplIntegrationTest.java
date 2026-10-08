package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.RefreshToken;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.RefreshTokenRepository;
import com.novacorp.inmonode.inmonodebackend.iam.domain.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Refresh token persistence against a real PostgreSQL: hash lookup, the foreign key to users and
 * the bulk revocation.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefreshTokenRepositoryImplIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final Duration THIRTY_DAYS = Duration.ofDays(30);

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void savedTokenIsFoundByItsHash() {
        var userId = persistedUserId();
        var hash = uniqueHash();

        var saved = refreshTokenRepository.save(RefreshToken.issue(userId, hash, NOW, THIRTY_DAYS));
        var found = refreshTokenRepository.findByTokenHash(hash).orElseThrow();

        assertNotNull(saved.getId());
        assertEquals(saved.getId(), found.getId());
        assertEquals(userId, found.getUserId());
        assertEquals(NOW.plus(THIRTY_DAYS), found.getExpiresAt());
        assertFalse(found.isRevoked());
        assertTrue(refreshTokenRepository.findByTokenHash(uniqueHash()).isEmpty());
    }

    @Test
    void revocationIsPersisted() {
        var hash = uniqueHash();
        var token = refreshTokenRepository.save(RefreshToken.issue(persistedUserId(), hash, NOW, THIRTY_DAYS));

        token.revoke(NOW.plusSeconds(60));
        refreshTokenRepository.save(token);

        assertEquals(NOW.plusSeconds(60), refreshTokenRepository.findByTokenHash(hash).orElseThrow().getRevokedAt());
    }

    @Test
    void revokeAllActiveByUserIdOnlyTouchesThatUsersUnrevokedTokens() {
        var userId = persistedUserId();
        var otherUserId = persistedUserId();
        var first = uniqueHash();
        var second = uniqueHash();
        var alreadyRevoked = uniqueHash();
        var otherUsers = uniqueHash();
        refreshTokenRepository.save(RefreshToken.issue(userId, first, NOW, THIRTY_DAYS));
        refreshTokenRepository.save(RefreshToken.issue(userId, second, NOW, THIRTY_DAYS));
        var revoked = RefreshToken.issue(userId, alreadyRevoked, NOW, THIRTY_DAYS);
        revoked.revoke(NOW.plusSeconds(10));
        refreshTokenRepository.save(revoked);
        refreshTokenRepository.save(RefreshToken.issue(otherUserId, otherUsers, NOW, THIRTY_DAYS));

        var count = refreshTokenRepository.revokeAllActiveByUserId(userId, NOW.plusSeconds(60));

        assertEquals(2, count);
        assertEquals(NOW.plusSeconds(60), refreshTokenRepository.findByTokenHash(first).orElseThrow().getRevokedAt());
        assertEquals(NOW.plusSeconds(60), refreshTokenRepository.findByTokenHash(second).orElseThrow().getRevokedAt());
        assertEquals(NOW.plusSeconds(10),
                refreshTokenRepository.findByTokenHash(alreadyRevoked).orElseThrow().getRevokedAt());
        assertFalse(refreshTokenRepository.findByTokenHash(otherUsers).orElseThrow().isRevoked());
    }

    /** Persists a user without its registration event, so no verification email is attempted. */
    private Long persistedUserId() {
        var user = User.register("user-" + UUID.randomUUID() + "@mail.com", "hash", Role.BUYER, NOW);
        user.clearDomainEvents();
        return userRepository.save(user).getId();
    }

    private static String uniqueHash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }
}
