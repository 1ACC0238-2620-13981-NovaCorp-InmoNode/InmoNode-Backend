package com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * Long-lived credential that renews the short-lived access token of a {@link User}.
 *
 * <p>Only the hash of the token is kept: the plain value is handed to the client once and never
 * stored. A token is usable until it expires or is revoked; it is revoked when it is rotated,
 * when the user signs out, or when reuse of an already rotated token reveals it was stolen.</p>
 */
public class RefreshToken {

    private final @Nullable Long id;
    private final Long userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private @Nullable Instant revokedAt;

    private RefreshToken(@Nullable Long id, Long userId, String tokenHash, Instant expiresAt,
                         @Nullable Instant revokedAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    /** Issues a new token for the user, valid for {@code timeToLive} from {@code now}. */
    public static RefreshToken issue(Long userId, String tokenHash, Instant now, Duration timeToLive) {
        return new RefreshToken(null, userId, tokenHash, now.plus(timeToLive), null);
    }

    /** Rebuilds an already persisted token. */
    public static RefreshToken restore(Long id, Long userId, String tokenHash, Instant expiresAt,
                                       @Nullable Instant revokedAt) {
        return new RefreshToken(id, userId, tokenHash, expiresAt, revokedAt);
    }

    /** A token can renew the session only while it is neither revoked nor expired. */
    public boolean isUsable(Instant now) {
        return !isRevoked() && now.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Revokes the token; revoking it again keeps the original instant. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public @Nullable Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public @Nullable Instant getRevokedAt() { return revokedAt; }
}
