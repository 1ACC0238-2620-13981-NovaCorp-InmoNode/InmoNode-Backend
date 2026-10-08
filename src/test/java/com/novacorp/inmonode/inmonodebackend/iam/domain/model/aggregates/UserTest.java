package com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.AccountLockedEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.UserRegisteredEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.VerificationTokenRenewedEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    @Test
    void registerCreatesInactiveUserWithTokenAndEvent() {
        var user = User.register("  Ana@Mail.com ", "hash", Role.BUYER, NOW);

        assertEquals("ana@mail.com", user.getEmail());
        assertEquals(UserStatus.INACTIVE, user.getStatus());
        assertNotNull(user.getVerificationToken());
        assertEquals(NOW, user.getVerificationSentAt());
        assertTrue(user.domainEvents().stream().anyMatch(e ->
                e instanceof UserRegisteredEvent ev && ev.verificationToken().equals(user.getVerificationToken())));
    }

    @Test
    void verifyEmailActivatesAccountOnlyWithMatchingTokenAndIsSingleUse() {
        var user = User.register("a@mail.com", "hash", Role.BUYER, NOW);
        var token = user.getVerificationToken();

        assertFalse(user.verifyEmail("wrong"));
        assertFalse(user.isActive());
        assertTrue(user.verifyEmail(token));
        assertTrue(user.isActive());
        assertNull(user.getVerificationToken());
        assertFalse(user.verifyEmail(token));
    }

    @Test
    void renewingTheVerificationTokenReplacesItAndRaisesAnEvent() {
        var user = User.register("a@mail.com", "hash", Role.BUYER, NOW);
        var oldToken = user.getVerificationToken();
        var later = NOW.plus(COOLDOWN);

        assertTrue(user.renewVerificationToken(later, COOLDOWN));

        var newToken = user.getVerificationToken();
        assertNotNull(newToken);
        assertNotEquals(oldToken, newToken);
        assertEquals(later, user.getVerificationSentAt());
        assertTrue(user.domainEvents().stream().anyMatch(e ->
                e instanceof VerificationTokenRenewedEvent ev && ev.verificationToken().equals(newToken)));
        assertFalse(user.verifyEmail(oldToken));
        assertTrue(user.verifyEmail(newToken));
    }

    @Test
    void renewalIsIgnoredWithinTheCooldownAndForActiveAccounts() {
        var user = User.register("a@mail.com", "hash", Role.BUYER, NOW);
        var token = user.getVerificationToken();

        assertFalse(user.renewVerificationToken(NOW.plus(COOLDOWN).minusSeconds(1), COOLDOWN));
        assertEquals(token, user.getVerificationToken());
        assertEquals(NOW, user.getVerificationSentAt());
        assertFalse(activeUser().renewVerificationToken(NOW.plus(Duration.ofDays(1)), COOLDOWN));
    }

    @Test
    void pendingAccountWithoutSendTimeCanRenewRightAway() {
        var user = User.restore(1L, "a@mail.com", "hash", Role.BUYER, UserStatus.INACTIVE, "old", null, 0, null);

        assertTrue(user.renewVerificationToken(NOW, COOLDOWN));
        assertEquals(NOW, user.getVerificationSentAt());
    }

    @Test
    void fifthConsecutiveFailedAttemptLocksAccountForFifteenMinutes() {
        var user = activeUser();
        for (int i = 0; i < 4; i++) {
            user.recordFailedAttempt(NOW);
            assertFalse(user.isLocked(NOW));
        }

        user.recordFailedAttempt(NOW);

        assertTrue(user.isLocked(NOW));
        assertTrue(user.isLocked(NOW.plusSeconds(14 * 60)));
        assertFalse(user.isLocked(NOW.plusSeconds(15 * 60)));
        assertTrue(user.domainEvents().stream().anyMatch(AccountLockedEvent.class::isInstance));
    }

    @Test
    void successfulSignInResetsFailedAttempts() {
        var user = activeUser();
        user.recordFailedAttempt(NOW);
        user.recordFailedAttempt(NOW);

        user.recordSuccessfulSignIn();

        assertEquals(0, user.getFailedAttempts());
    }

    private static User activeUser() {
        return User.restore(1L, "a@mail.com", "hash", Role.BUYER, UserStatus.ACTIVE, null, null, 0, null);
    }
}
