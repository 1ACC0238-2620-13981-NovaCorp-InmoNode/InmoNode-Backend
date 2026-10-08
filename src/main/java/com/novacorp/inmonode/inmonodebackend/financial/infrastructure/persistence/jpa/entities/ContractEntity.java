package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The reservation is referenced by id, not by association: {@code Contract} and {@code Reservation} are separate
 * aggregates.
 */
@Getter
@Setter
@Entity
@Table(name = "contracts", schema = "financial_document_control")
public class ContractEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false, unique = true)
    private Long reservationId;

    @Column(nullable = false, unique = true)
    private UUID transactionId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long lotId;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private String objectKey;

    @Column(nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    private Long issuedBy;

    private Instant buyerAcknowledgedAt;
}
