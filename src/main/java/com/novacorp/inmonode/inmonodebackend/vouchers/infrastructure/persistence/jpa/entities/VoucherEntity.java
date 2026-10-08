package com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "vouchers", schema = "voucher_management")
public class VoucherEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false, unique = true)
    private UUID voucherId;

    @Column(nullable = false)
    private UUID reservationId;

    @Column(nullable = false)
    private String objectKey;

    /** The media type, such as {@code image/jpeg}. */
    @Column(nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private LocalDate operationDate;

    @Column(nullable = false, length = 50)
    private String operationCode;

    @Column(precision = 4, scale = 3)
    private BigDecimal ocrConfidence;

    @Column(nullable = false)
    private boolean manuallyCorrected;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherStatus status;

    @Column(nullable = false)
    private Instant receivedAt;
}
