package com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.AccountLockedEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.PasswordResetEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.PasswordResetRequestedEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.UserRegisteredEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.VerificationTokenRenewedEvent;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.Role;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.valueobjects.UserStatus;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.aggregates.AbstractDomainAggregateRoot;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Aggregate root of the identity and access context.
 *
 * <p>Holds the credentials and role of an account. The password is received already hashed:
 * hashing is an application concern, not a domain one.</p>
 */
public class User extends AbstractDomainAggregateRoot<User> {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final @Nullable Long id;
    private final String email;
    private String passwordHash;
    private final Role role;
    private UserStatus status;
    private @Nullable String verificationToken;
    private @Nullable Instant verificationSentAt;
    private int failedAttempts;
    private @Nullable Instant lockedUntil;
    private @Nullable String passwordResetTokenHash;
    private @Nullable Instant passwordResetRequestedAt;

    private User(@Nullable Long id, String email, String passwordHash, Role role, UserStatus status,
                 @Nullable String verificationToken, @Nullable Instant verificationSentAt,
                 int failedAttempts, @Nullable Instant lockedUntil,
                 @Nullable String passwordResetTokenHash, @Nullable Instant passwordResetRequestedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.verificationToken = verificationToken;
        this.verificationSentAt = verificationSentAt;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.passwordResetTokenHash = passwordResetTokenHash;
        this.passwordResetRequestedAt = passwordResetRequestedAt;
    }

    /**
     * US-14, Scenario 1: creates the account as {@code INACTIVE} with a single-use verification token,
     * emailed at {@code now}. Duplicate emails are rejected by the application service (Scenario 2).
     */
    public static User register(String email, String passwordHash, Role role, Instant now) {
        var token = newVerificationToken();
        var user = new User(null, normalize(email), passwordHash, role, UserStatus.INACTIVE, token, now, 0, null,
                null, null);
        user.registerDomainEvent(new UserRegisteredEvent(user.email, token));
        return user;
    }

    /** Rebuilds an already persisted account without raising events. */
    public static User restore(Long id, String email, String passwordHash, Role role, UserStatus status,
                               @Nullable String verificationToken, @Nullable Instant verificationSentAt,
                               int failedAttempts, @Nullable Instant lockedUntil,
                               @Nullable String passwordResetTokenHash, @Nullable Instant passwordResetRequestedAt) {
        return new User(id, email, passwordHash, role, status, verificationToken, verificationSentAt,
                failedAttempts, lockedUntil, passwordResetTokenHash, passwordResetRequestedAt);
    }

    /** Rebuilds an already persisted account that was never emailed a password reset link. */
    public static User restore(Long id, String email, String passwordHash, Role role, UserStatus status,
                               @Nullable String verificationToken, @Nullable Instant verificationSentAt,
                               int failedAttempts, @Nullable Instant lockedUntil) {
        return restore(id, email, passwordHash, role, status, verificationToken, verificationSentAt,
                failedAttempts, lockedUntil, null, null);
    }

    private static String newVerificationToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Activates the account when the token matches and was emailed less than {@code timeToLive} ago.
     * The token is single-use; an expired one is kept until a resend replaces it.
     *
     * @return {@code true} if the account was activated
     */
    public boolean verifyEmail(String token, Instant now, Duration timeToLive) {
        if (verificationToken == null || !verificationToken.equals(token)
                || verificationSentAt == null || !now.isBefore(verificationSentAt.plus(timeToLive))) {
            return false;
        }
        status = UserStatus.ACTIVE;
        verificationToken = null;
        return true;
    }

    /**
     * Replaces the verification token of an account that is still {@code INACTIVE}, so a lost or failed
     * email can be sent again; the previous token stops working. Ignored for active accounts and while
     * the previous email was sent less than {@code cooldown} ago, which keeps the endpoint from being
     * used to flood a mailbox.
     *
     * @return {@code true} if a new token was issued and a new email must be sent
     */
    public boolean renewVerificationToken(Instant now, Duration cooldown) {
        if (isActive() || (verificationSentAt != null && now.isBefore(verificationSentAt.plus(cooldown)))) {
            return false;
        }
        verificationToken = newVerificationToken();
        verificationSentAt = now;
        registerDomainEvent(new VerificationTokenRenewedEvent(email, verificationToken));
        return true;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * US-01, Scenario 2: the fifth consecutive failed attempt locks the account for 15 minutes.
     */
    public void recordFailedAttempt(Instant now) {
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedAttempts = 0;
            registerDomainEvent(new AccountLockedEvent(email, lockedUntil));
        }
    }

    public void recordSuccessfulSignIn() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    /**
     * US-52, Scenario 1: issues a single-use link to reset the password of an active account. Only the
     * digest of the token is kept; the token itself travels in the event to the email. The previous link stops
     * working. Ignored for accounts pending verification, which must verify their email first, and while the
     * previous link was emailed less than {@code cooldown} ago, which keeps the endpoint from being used to
     * flood a mailbox.
     *
     * @param token     the token to email, never stored
     * @param tokenHash the digest of {@code token}, by which the link is later looked up
     * @return {@code true} if a new link was issued and must be emailed
     */
    public boolean requestPasswordReset(String token, String tokenHash, Instant now, Duration cooldown) {
        if (!isActive() || (passwordResetRequestedAt != null
                && now.isBefore(passwordResetRequestedAt.plus(cooldown)))) {
            return false;
        }
        passwordResetTokenHash = tokenHash;
        passwordResetRequestedAt = now;
        registerDomainEvent(new PasswordResetRequestedEvent(email, token));
        return true;
    }

    /**
     * US-52: replaces the password when the digest matches the last link and that link was emailed less than
     * {@code timeToLive} ago; otherwise nothing changes (Scenario 2). The link is single-use. The email proved
     * the account is theirs, so a lock from failed sign-ins is lifted too.
     *
     * @return {@code true} if the password was replaced
     */
    public boolean resetPassword(String tokenHash, String newPasswordHash, Instant now, Duration timeToLive) {
        if (passwordResetTokenHash == null || !passwordResetTokenHash.equals(tokenHash)
                || passwordResetRequestedAt == null || !now.isBefore(passwordResetRequestedAt.plus(timeToLive))) {
            return false;
        }
        passwordHash = newPasswordHash;
        passwordResetTokenHash = null;
        failedAttempts = 0;
        lockedUntil = null;
        registerDomainEvent(new PasswordResetEvent(email));
        return true;
    }

    public @Nullable Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public @Nullable String getVerificationToken() { return verificationToken; }
    public @Nullable Instant getVerificationSentAt() { return verificationSentAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public @Nullable Instant getLockedUntil() { return lockedUntil; }
    public @Nullable String getPasswordResetTokenHash() { return passwordResetTokenHash; }
    public @Nullable Instant getPasswordResetRequestedAt() { return passwordResetRequestedAt; }
}
