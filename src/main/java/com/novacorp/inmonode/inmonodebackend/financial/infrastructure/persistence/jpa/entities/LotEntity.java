package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
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

/**
 * The project is referenced by id, not by association: {@code Project} and {@code Lot} are separate aggregates.
 */
@Getter
@Setter
@Entity
@Table(name = "lots", schema = "financial_document_control")
public class LotEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal area;

    @Column(precision = 10, scale = 2)
    private BigDecimal front;

    @Column(precision = 10, scale = 2)
    private BigDecimal depth;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal priceAmount;

    @Column(nullable = false, length = 3)
    private String priceCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LotStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String boundaryWkt;

    private Long currentReservationId;

    private Instant blockedUntil;
}
