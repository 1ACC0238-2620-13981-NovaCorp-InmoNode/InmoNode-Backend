package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.persistence.jpa.entities.AuditableAbstractPersistenceEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * The lot is referenced by id, not by association: {@code Lot} and {@code Reservation} are separate aggregates. The
 * payment evidences belong to this aggregate, so they are mapped as its children.
 */
@Getter
@Setter
@Entity
@Table(name = "reservations", schema = "financial_document_control")
public class ReservationEntity extends AuditableAbstractPersistenceEntity {
    @Column(name = "co_owner_full_name", length = 200)
    private String coOwnerFullName;
    @Column(name = "co_owner_document_type", length = 20)
    private String coOwnerDocumentType;
    @Column(name = "co_owner_document_number", length = 20)
    private String coOwnerDocumentNumber;


    @Column(nullable = false)
    private Long lotId;

    private Long quotationId;

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

    private Instant resubmissionDeadline;

    private Instant verifiedAt;

    /** Price of the lot when a web reservation blocked it; with the term and the rate, its financing plan. */
    @Column(precision = 14, scale = 2)
    private BigDecimal lotPrice;

    private Integer termMonths;

    @Column(precision = 7, scale = 4)
    private BigDecimal annualInterestRate;

    /** A reservation receives few evidences and needs them all to be rebuilt, so they are loaded with it. */
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<PaymentEvidenceEntity> evidences = new ArrayList<>();
}
