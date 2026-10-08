package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
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
 * The lot is referenced by id, not by association: {@code Lot} and {@code Reservation} are separate aggregates.
 */
@Getter
@Setter
@Entity
@Table(name = "reservations", schema = "financial_document_control")
public class ReservationEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false)
    private Long lotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReservationChannel channel;

    @Column(nullable = false)
    private Long requesterId;

    private UUID prospectId;

    @Column(unique = true)
    private UUID sourceEventId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal initialAmount;

    @Column(nullable = false, length = 3)
    private String initialAmountCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    @Column(nullable = false)
    private Instant reservedAt;
}
