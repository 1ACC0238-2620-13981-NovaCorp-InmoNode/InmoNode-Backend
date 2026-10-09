package com.novacorp.inmonode.inmonodebackend.catalog.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Keeps a numeric surrogate id, like every entity, and the device id as a unique business key.
 */
@Getter
@Setter
@Entity
@Table(name = "prospects", schema = "catalog_management")
public class ProspectEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false, unique = true)
    private UUID prospectId;

    @Column(nullable = false)
    private Long agentId;

    @Column(nullable = false, length = 12)
    private String document;

    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private Instant registeredAt;
}
