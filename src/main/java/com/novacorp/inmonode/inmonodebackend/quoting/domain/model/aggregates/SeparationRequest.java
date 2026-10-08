package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.SeparationStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A buyer's formal request to separate a lot from the web portal (US-19), backed by one of their quotations. It refers
 * to the quotation by id only, so both keep independent life cycles.
 *
 * <p>Its {@code transactionId} is the id every context uses for this separation: Control Financiero y Documental
 * stores the reservation under it and Gestión de Comprobantes accepts the payment voucher against it.</p>
 */
public class SeparationRequest {

    /** Why a request was rejected: the lot was already taken. */
    public static final String LOT_UNAVAILABLE = "LOT_UNAVAILABLE";

    private final @Nullable Long id;
    private final UUID transactionId;
    private final Long lotId;
    private final Long buyerId;
    private final Long quotationId;
    private final Money initialAmount;
    private final SeparationStatus status;
    private final Instant requestedAt;
    private final @Nullable Instant lockExpiresAt;
    private final @Nullable String rejectionReason;

    private SeparationRequest(@Nullable Long id, UUID transactionId, Long lotId, Long buyerId, Long quotationId,
                              Money initialAmount, SeparationStatus status, Instant requestedAt,
                              @Nullable Instant lockExpiresAt, @Nullable String rejectionReason) {
        this.id = id;
        this.transactionId = transactionId;
        this.lotId = lotId;
        this.buyerId = buyerId;
        this.quotationId = quotationId;
        this.initialAmount = initialAmount;
        this.status = status;
        this.requestedAt = requestedAt;
        this.lockExpiresAt = lockExpiresAt;
        this.rejectionReason = rejectionReason;
    }

    /**
     * US-19, Scenario 1: the lot is held for the buyer until {@code lockExpiresAt}; the payment voucher can be
     * uploaded against {@code transactionId}. Its amount is the down payment of the quotation.
     */
    public static SeparationRequest blocked(UUID transactionId, Quotation quotation, Instant requestedAt,
                                            Instant lockExpiresAt) {
        if (lockExpiresAt == null || !lockExpiresAt.isAfter(requestedAt)) {
            throw new IllegalArgumentException("a blocked request needs a lock that ends after it was requested");
        }
        return request(transactionId, quotation, SeparationStatus.BLOCKED, requestedAt, lockExpiresAt, null);
    }

    /** US-19, Scenario 2: another operation took the lot first; kept to trace the attempt. */
    public static SeparationRequest rejectedUnavailable(UUID transactionId, Quotation quotation, Instant requestedAt) {
        return request(transactionId, quotation, SeparationStatus.REJECTED_UNAVAILABLE, requestedAt, null,
                LOT_UNAVAILABLE);
    }

    /** Rebuilds an already persisted request. */
    public static SeparationRequest restore(Long id, UUID transactionId, Long lotId, Long buyerId, Long quotationId,
                                            Money initialAmount, SeparationStatus status, Instant requestedAt,
                                            @Nullable Instant lockExpiresAt, @Nullable String rejectionReason) {
        return new SeparationRequest(id, transactionId, lotId, buyerId, quotationId, initialAmount, status,
                requestedAt, lockExpiresAt, rejectionReason);
    }

    private static SeparationRequest request(UUID transactionId, Quotation quotation, SeparationStatus status,
                                             Instant requestedAt, @Nullable Instant lockExpiresAt,
                                             @Nullable String rejectionReason) {
        if (transactionId == null || quotation == null || quotation.getId() == null || requestedAt == null) {
            throw new IllegalArgumentException("a separation request needs its id, a saved quotation and its date");
        }
        return new SeparationRequest(null, transactionId, quotation.getLotId(), quotation.getBuyerId(),
                quotation.getId(), quotation.getInitialPayment(), status, requestedAt, lockExpiresAt,
                rejectionReason);
    }

    /** Whether it still holds the lot, as far as this context knows: blocked and within the hour. */
    public boolean isActive(Instant now) {
        return status == SeparationStatus.BLOCKED && lockExpiresAt != null && now.isBefore(lockExpiresAt);
    }

    public @Nullable Long getId() { return id; }
    public UUID getTransactionId() { return transactionId; }
    public Long getLotId() { return lotId; }
    public Long getBuyerId() { return buyerId; }
    public Long getQuotationId() { return quotationId; }
    public Money getInitialAmount() { return initialAmount; }
    public SeparationStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public @Nullable Instant getLockExpiresAt() { return lockExpiresAt; }
    public @Nullable String getRejectionReason() { return rejectionReason; }
}
