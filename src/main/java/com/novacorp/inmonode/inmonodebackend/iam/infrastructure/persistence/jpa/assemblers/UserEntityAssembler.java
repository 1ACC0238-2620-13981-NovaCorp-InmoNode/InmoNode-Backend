package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.aggregates.User;
import com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.entities.UserEntity;

/**
 * Translates between the {@link User} aggregate and its JPA persistence entity.
 */
public final class UserEntityAssembler {

    private UserEntityAssembler() {}

    public static User toDomain(UserEntity entity) {
        return User.restore(entity.getId(), entity.getEmail(), entity.getPasswordHash(), entity.getRole(),
                entity.getStatus(), entity.getVerificationToken(), entity.getVerificationSentAt(),
                entity.getFailedAttempts(), entity.getLockedUntil());
    }

    /** Copies the aggregate state onto the entity; audit columns and id stay untouched. */
    public static UserEntity copyToEntity(User user, UserEntity entity) {
        entity.setEmail(user.getEmail());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setRole(user.getRole());
        entity.setStatus(user.getStatus());
        entity.setVerificationToken(user.getVerificationToken());
        entity.setVerificationSentAt(user.getVerificationSentAt());
        entity.setFailedAttempts(user.getFailedAttempts());
        entity.setLockedUntil(user.getLockedUntil());
        return entity;
    }
}
