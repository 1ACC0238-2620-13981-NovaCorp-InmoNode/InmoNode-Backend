package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceSource;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Part of the reservation aggregate: saved and removed only through its {@link ReservationEntity}.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_evidences", schema = "financial_document_control")
public class PaymentEvidenceEntity extends AuditableAbstractPersistenceEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private ReservationEntity reservation;

    @Column(nullable = false, unique = true)
    private UUID reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentEvidenceSource source;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private LocalDate operationDate;

    @Column(nullable = false, length = 50)
    private String operationCode;

    @Column(nullable = false)
    private boolean manuallyCorrected;

    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentEvidenceStatus status;

    @Column(nullable = false)
    private boolean late;

    @Column(nullable = false)
    private Instant submittedAt;

    private Long reviewerId;

    @Column(length = 500)
    private String reviewerNote;

    private Instant reviewedAt;
}
