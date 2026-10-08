package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
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

/**
 * Part of the account statement aggregate: saved only through its {@link AccountStatementEntity}.
 */
@Getter
@Setter
@Entity
@Table(name = "installments", schema = "financial_document_control")
public class InstallmentEntity extends AuditableAbstractPersistenceEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_statement_id", nullable = false)
    private AccountStatementEntity accountStatement;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentStatus status;

    private Instant paidAt;

    @Column(precision = 14, scale = 2)
    private BigDecimal paidAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal penalty;

    private Instant reminderSentAt;

    private Instant overdueNotifiedAt;
}
