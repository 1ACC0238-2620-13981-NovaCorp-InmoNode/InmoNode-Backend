package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractDocument;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.CoOwner;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Preliminary purchase contract of a reservation (2.6.4.1, US-21), issued by the back office once the payment was
 * verified. It refers to its {@link Reservation} by id, one contract per reservation, and keeps the PDF in the file
 * repository. For now only web reservations get one: their buyer has an account to read it.
 */
public class Contract {

    private @Nullable CoOwner coOwner;
    private final @Nullable Long id;
    private final Long reservationId;
    private final UUID transactionId;
    private final Long buyerId;
    private final Long lotId;
    private final UUID documentId;
    private final String objectKey;
    private final long sizeBytes;
    private final ContractStatus status;
    private final Instant issuedAt;
    private final Long issuedBy;
    private @Nullable Instant buyerAcknowledgedAt;

    private Contract(@Nullable Long id, Long reservationId, UUID transactionId, Long buyerId, Long lotId,
                     UUID documentId, String objectKey, long sizeBytes, ContractStatus status, Instant issuedAt,
                     Long issuedBy, @Nullable Instant buyerAcknowledgedAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.transactionId = transactionId;
        this.buyerId = buyerId;
        this.lotId = lotId;
        this.documentId = documentId;
        this.objectKey = objectKey;
        this.sizeBytes = sizeBytes;
        this.status = status;
        this.issuedAt = issuedAt;
        this.issuedBy = issuedBy;
        this.buyerAcknowledgedAt = buyerAcknowledgedAt;
    }

    /**
     * Why the reservation cannot get a contract yet, or ever in this version; empty when it can.
     */
    public static Optional<String> issuingObstacle(Reservation reservation) {
        if (reservation.getChannel() != ReservationChannel.WEB) {
            return Optional.of("only web reservations get a contract for now; this one was made in the field");
        }
        if (reservation.getStatus() != ReservationStatus.VERIFIED) {
            return Optional.of("the reservation is %s; its payment must be verified first"
                    .formatted(reservation.getStatus()));
        }
        return Optional.empty();
    }

    /**
     * The back office issues the contract with the PDF it uploaded.
     *
     * @throws IllegalStateException when {@link #issuingObstacle} finds an obstacle
     */
    public static Contract issue(Reservation reservation, ContractDocument document, Long issuedBy, Instant now) {
        issuingObstacle(reservation).ifPresent(obstacle -> {
            throw new IllegalStateException(obstacle);
        });
        if (reservation.getId() == null || reservation.getSourceEventId() == null
                || !reservation.getSourceEventId().equals(document.transactionId())) {
            throw new IllegalArgumentException("the document must belong to this saved reservation");
        }
        var contract = new Contract(null, reservation.getId(), reservation.getSourceEventId(), reservation.getRequesterId(),
                reservation.getLotId(), document.documentId(), document.objectKey(), document.sizeBytes(),
                ContractStatus.ISSUED, now, issuedBy, null);
        contract.coOwner = reservation.getCoOwner();
        return contract;
    }

    /**
     * US-22: the buyer gives their preliminary agreement with the terms. Only the first one counts, so repeating it
     * keeps the original date.
     *
     * @return whether it was registered now
     * @throws IllegalArgumentException when the user is not the buyer of the contract
     */
    public boolean registerBuyerAcknowledgment(Long userId, Instant now) {
        if (!belongsTo(userId)) {
            throw new IllegalArgumentException("only the buyer of the contract can acknowledge it");
        }
        if (buyerAcknowledgedAt != null) {
            return false;
        }
        buyerAcknowledgedAt = now;
        return true;
    }

    public boolean belongsTo(Long userId) {
        return buyerId.equals(userId);
    }

    /** Rebuilds an already persisted contract. */
    public static Contract restore(Long id, Long reservationId, UUID transactionId, Long buyerId, Long lotId,
                                   UUID documentId, String objectKey, long sizeBytes, ContractStatus status,
                                   Instant issuedAt, Long issuedBy, @Nullable Instant buyerAcknowledgedAt) {
        return new Contract(id, reservationId, transactionId, buyerId, lotId, documentId, objectKey, sizeBytes, status,
                issuedAt, issuedBy, buyerAcknowledgedAt);
    }

    public static Contract restore(Long id, Long reservationId, UUID transactionId, Long buyerId, Long lotId,
                                   UUID documentId, String objectKey, long sizeBytes, ContractStatus status,
                                   Instant issuedAt, Long issuedBy, @Nullable Instant buyerAcknowledgedAt,
                                   @Nullable CoOwner coOwner) {
        var contract = restore(id, reservationId, transactionId, buyerId, lotId, documentId, objectKey, sizeBytes,
                status, issuedAt, issuedBy, buyerAcknowledgedAt);
        contract.coOwner = coOwner;
        return contract;
    }

    public @Nullable CoOwner getCoOwner() { return coOwner; }

    public @Nullable Long getId() { return id; }
    public Long getReservationId() { return reservationId; }
    public UUID getTransactionId() { return transactionId; }
    public Long getBuyerId() { return buyerId; }
    public Long getLotId() { return lotId; }
    public UUID getDocumentId() { return documentId; }
    public String getObjectKey() { return objectKey; }
    public long getSizeBytes() { return sizeBytes; }
    public ContractStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Long getIssuedBy() { return issuedBy; }
    public @Nullable Instant getBuyerAcknowledgedAt() { return buyerAcknowledgedAt; }
}
