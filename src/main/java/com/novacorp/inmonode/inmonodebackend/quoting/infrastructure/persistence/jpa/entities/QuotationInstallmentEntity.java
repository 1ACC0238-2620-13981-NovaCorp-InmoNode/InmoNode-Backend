package com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Part of the quotation aggregate: saved only through its {@link QuotationEntity}.
 */
@Getter
@Setter
@Entity
@Table(name = "quotation_installments", schema = "quoting_reservation")
public class QuotationInstallmentEntity extends AuditableAbstractPersistenceEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private QuotationEntity quotation;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal principal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal interest;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;
}
