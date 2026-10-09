package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.RefreshToken;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.entities.RefreshTokenEntity;

/**
 * Translates between the {@link RefreshToken} aggregate and its JPA persistence entity.
 */
public final class RefreshTokenEntityAssembler {

    private RefreshTokenEntityAssembler() {}

    public static RefreshToken toDomain(RefreshTokenEntity entity) {
        return RefreshToken.restore(entity.getId(), entity.getUserId(), entity.getTokenHash(),
                entity.getExpiresAt(), entity.getRevokedAt());
    }

    /** Copies the aggregate state onto the entity; audit columns and id stay untouched. */
    public static RefreshTokenEntity copyToEntity(RefreshToken refreshToken, RefreshTokenEntity entity) {
        entity.setUserId(refreshToken.getUserId());
        entity.setTokenHash(refreshToken.getTokenHash());
        entity.setExpiresAt(refreshToken.getExpiresAt());
        entity.setRevokedAt(refreshToken.getRevokedAt());
        return entity;
    }
}
