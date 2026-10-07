package com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.AccountLockedEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.UserRegisteredEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    @Test
    void registerCreatesInactiveUserWithTokenAndEvent() {
        var user = User.register("  Ana@Mail.com ", "hash", Role.BUYER);

        assertEquals("ana@mail.com", user.getEmail());
        assertEquals(UserStatus.INACTIVE, user.getStatus());
        assertNotNull(user.getVerificationToken());
        assertTrue(user.domainEvents().stream().anyMatch(e ->
                e instanceof UserRegisteredEvent ev && ev.verificationToken().equals(user.getVerificationToken())));
    }

    @Test
    void verifyEmailActivatesAccountOnlyWithMatchingTokenAndIsSingleUse() {
        var user = User.register("a@mail.com", "hash", Role.BUYER);
        var token = user.getVerificationToken();

        assertFalse(user.verifyEmail("wrong"));
        assertFalse(user.isActive());
        assertTrue(user.verifyEmail(token));
        assertTrue(user.isActive());
        assertNull(user.getVerificationToken());
        assertFalse(user.verifyEmail(token));
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
        return User.restore(1L, "a@mail.com", "hash", Role.BUYER, UserStatus.ACTIVE, null, 0, null);
    }
}
