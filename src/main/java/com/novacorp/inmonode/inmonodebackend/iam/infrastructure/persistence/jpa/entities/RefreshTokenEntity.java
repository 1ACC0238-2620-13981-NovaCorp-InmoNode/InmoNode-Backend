package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * The owner is referenced by id, not by association: {@code User} and {@code RefreshToken} are separate aggregates.
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_tokens", schema = "identity_access")
public class RefreshTokenEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;
}
