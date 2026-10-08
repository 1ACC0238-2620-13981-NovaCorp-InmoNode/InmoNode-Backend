package com.novacorp.inmonode.inmonodebackend.iam.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.RefreshToken;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence abstraction for the {@link RefreshToken} aggregate.
 */
public interface RefreshTokenRepository {

    /**
     * @return the persisted token, with its generated id
     */
    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every token of the user that is still unrevoked (sign-out everywhere, or reuse of a
     * rotated token).
     *
     * @return the number of tokens revoked
     */
    int revokeAllActiveByUserId(Long userId, Instant now);
}
