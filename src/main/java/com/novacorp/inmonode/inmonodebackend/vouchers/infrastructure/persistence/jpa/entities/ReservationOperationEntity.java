package com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.OperationChannel;
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

@Getter
@Setter
@Entity
@Table(name = "reservation_operations", schema = "voucher_management")
public class ReservationOperationEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false, unique = true)
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OperationChannel channel;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long lotId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal initialAmount;

    @Column(nullable = false)
    private Instant reservedAt;

    private Instant evidenceDueAt;
}
