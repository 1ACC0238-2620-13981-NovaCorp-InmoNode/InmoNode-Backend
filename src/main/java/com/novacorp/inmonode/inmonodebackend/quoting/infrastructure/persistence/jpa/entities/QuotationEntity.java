package com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.entities;

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

/**
 * The installments belong to this aggregate, so they are mapped as its children.
 */
@Getter
@Setter
@Entity
@Table(name = "quotations", schema = "quoting_reservation")
public class QuotationEntity extends AuditableAbstractPersistenceEntity {

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long lotId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 30)
    private String lotCode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lotPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal initialPayment;

    @Column(nullable = false)
    private int termMonths;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(nullable = false)
    private Instant generatedAt;

    @Column(nullable = false)
    private Instant validUntil;

    /** A quotation needs its whole schedule to be rebuilt, so it is loaded with it. */
    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("number")
    private List<QuotationInstallmentEntity> installments = new ArrayList<>();
}
