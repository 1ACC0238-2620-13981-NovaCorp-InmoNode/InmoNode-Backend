package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The contract and the reservation are referenced by id; the installments belong to this aggregate, so they are
 * mapped as its children.
 */
@Getter
@Setter
@Entity
@Table(name = "account_statements", schema = "financial_document_control")
public class AccountStatementEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false, unique = true)
    private Long contractId;

    @Column(nullable = false, unique = true)
    private Long reservationId;

    @Column(nullable = false)
    private UUID transactionId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long lotId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lotPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal initialPayment;

    @Column(nullable = false)
    private int termMonths;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal annualInterestRate;

    @Column(nullable = false)
    private Instant openedAt;

    /** A statement needs its whole schedule to be rebuilt, so it is loaded with it. */
    @OneToMany(mappedBy = "accountStatement", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("number")
    private List<InstallmentEntity> installments = new ArrayList<>();
}
