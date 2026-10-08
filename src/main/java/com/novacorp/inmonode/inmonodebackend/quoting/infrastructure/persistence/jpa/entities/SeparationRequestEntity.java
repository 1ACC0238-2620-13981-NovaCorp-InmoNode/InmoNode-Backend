package com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.SeparationStatus;
import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The quotation is referenced by id, not by association: both are separate aggregates.
 */
@Getter
@Setter
@Entity
@Table(name = "separation_requests", schema = "quoting_reservation")
public class SeparationRequestEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false, unique = true)
    private UUID transactionId;

    @Column(nullable = false)
    private Long lotId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long quotationId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal initialAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SeparationStatus status;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant lockExpiresAt;

    @Column(length = 30)
    private String rejectionReason;
}
