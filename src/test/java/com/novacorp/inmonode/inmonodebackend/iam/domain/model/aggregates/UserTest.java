package com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.AccountLockedEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.PasswordResetEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.PasswordResetRequestedEvent;
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
    private static final Duration LINK_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofMinutes(30);

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

        assertFalse(user.verifyEmail("wrong", NOW, LINK_TTL));
        assertFalse(user.isActive());
        assertTrue(user.verifyEmail(token, NOW, LINK_TTL));
        assertTrue(user.isActive());
        assertNull(user.getVerificationToken());
        assertFalse(user.verifyEmail(token, NOW, LINK_TTL));
    }

    @Test
    void verificationLinkWorksUntilItsTimeToLiveEnds() {
        var user = User.register("a@mail.com", "hash", Role.BUYER, NOW);

        assertTrue(user.verifyEmail(user.getVerificationToken(), NOW.plus(LINK_TTL).minusSeconds(1), LINK_TTL));
    }

    @Test
    void expiredLinkIsRejectedUntilANewOneIsSent() {
        var user = User.register("a@mail.com", "hash", Role.BUYER, NOW);
        var expired = user.getVerificationToken();
        var dayLater = NOW.plus(LINK_TTL);

        assertFalse(user.verifyEmail(expired, dayLater, LINK_TTL));
        assertFalse(user.isActive());
        assertEquals(expired, user.getVerificationToken());

        assertTrue(user.renewVerificationToken(dayLater, COOLDOWN));
        assertTrue(user.verifyEmail(user.getVerificationToken(), dayLater, LINK_TTL));
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
        assertFalse(user.verifyEmail(oldToken, later, LINK_TTL));
        assertTrue(user.verifyEmail(newToken, later, LINK_TTL));
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

    @Test
    void passwordResetRequestKeepsOnlyTheDigestAndHandsTheTokenToTheEmail() {
        var user = activeUser();

        assertTrue(user.requestPasswordReset("t1", "sha:t1", NOW, COOLDOWN));

        assertEquals("sha:t1", user.getPasswordResetTokenHash());
        assertEquals(NOW, user.getPasswordResetRequestedAt());
        assertTrue(user.domainEvents().contains(new PasswordResetRequestedEvent("a@mail.com", "t1")));
    }

    @Test
    void passwordResetRequestIsIgnoredForPendingAccountsAndWithinTheCooldown() {
        var pending = User.register("p@mail.com", "hash", Role.BUYER, NOW);
        assertFalse(pending.requestPasswordReset("t1", "sha:t1", NOW, COOLDOWN));
        assertNull(pending.getPasswordResetTokenHash());

        var user = activeUser();
        user.requestPasswordReset("t1", "sha:t1", NOW, COOLDOWN);

        assertFalse(user.requestPasswordReset("t2", "sha:t2", NOW.plus(COOLDOWN).minusSeconds(1), COOLDOWN));
        assertEquals("sha:t1", user.getPasswordResetTokenHash());
        assertEquals(NOW, user.getPasswordResetRequestedAt());
        assertEquals(1, user.domainEvents().stream().filter(PasswordResetRequestedEvent.class::isInstance).count());
    }

    @Test
    void aNewResetLinkReplacesThePreviousOne() {
        var user = activeUser();
        var later = NOW.plus(COOLDOWN);
        user.requestPasswordReset("t1", "sha:t1", NOW, COOLDOWN);

        assertTrue(user.requestPasswordReset("t2", "sha:t2", later, COOLDOWN));

        assertFalse(user.resetPassword("sha:t1", "new-hash", later, RESET_TTL));
        assertTrue(user.resetPassword("sha:t2", "new-hash", later, RESET_TTL));
    }

    @Test
    void resettingThePasswordReplacesItLiftsTheLockAndTheLinkIsSingleUse() {
        var user = activeUser();
        for (int i = 0; i < User.MAX_FAILED_ATTEMPTS; i++) {
            user.recordFailedAttempt(NOW);
        }
        // Locking restarts the count, so one more failure leaves both a lock and a pending count.
        user.recordFailedAttempt(NOW);
        assertTrue(user.isLocked(NOW));
        assertEquals(1, user.getFailedAttempts());
        user.requestPasswordReset("t1", "sha:t1", NOW, COOLDOWN);

        assertTrue(user.resetPassword("sha:t1", "new-hash", NOW, RESET_TTL));

        assertEquals("new-hash", user.getPasswordHash());
        assertNull(user.getPasswordResetTokenHash());
        assertFalse(user.isLocked(NOW));
        assertNull(user.getLockedUntil());
        assertEquals(0, user.getFailedAttempts());
        assertTrue(user.domainEvents().contains(new PasswordResetEvent("a@mail.com")));
        assertFalse(user.resetPassword("sha:t1", "other-hash", NOW, RESET_TTL));
        assertEquals("new-hash", user.getPasswordHash());
    }

    @Test
    void resetLinkWorksUntilItsThirtyMinutesEnd() {
        var user = activeUser();
        user.requestPasswordReset("t1", "sha:t1", NOW, COOLDOWN);

        assertFalse(user.resetPassword("sha:t1", "new-hash", NOW.plus(RESET_TTL), RESET_TTL));
        assertEquals("hash", user.getPasswordHash());
        assertTrue(user.resetPassword("sha:t1", "new-hash", NOW.plus(RESET_TTL).minusSeconds(1), RESET_TTL));
    }

    @Test
    void resetWithoutALinkOrWithAnotherTokenChangesNothing() {
        var user = activeUser();

        assertFalse(user.resetPassword("sha:t1", "new-hash", NOW, RESET_TTL));
        user.requestPasswordReset("t1", "sha:t1", NOW, COOLDOWN);
        assertFalse(user.resetPassword("sha:other", "new-hash", NOW, RESET_TTL));

        assertEquals("hash", user.getPasswordHash());
        assertEquals("sha:t1", user.getPasswordResetTokenHash());
        assertFalse(user.domainEvents().stream().anyMatch(PasswordResetEvent.class::isInstance));
    }

    private static User activeUser() {
        return User.restore(1L, "a@mail.com", "hash", Role.BUYER, UserStatus.ACTIVE, null, null, 0, null);
    }
}
