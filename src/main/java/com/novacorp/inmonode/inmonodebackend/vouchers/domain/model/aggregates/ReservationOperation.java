package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.OperationChannel;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * "Operación de separación" (vouchers canvas): a reservation this context accepts a payment voucher for. The
 * field and web reservations reach this context as different events; its anti-corruption layer turns both into
 * this single concept, so a voucher always belongs to one operation.
 */
public class ReservationOperation {

    private final @Nullable Long id;
    private final UUID reservationId;
    private final OperationChannel channel;
    private final Long ownerId;
    private final Long lotId;
    private final BigDecimal initialAmount;
    private final Instant reservedAt;
    private final @Nullable Instant evidenceDueAt;

    private ReservationOperation(@Nullable Long id, UUID reservationId, OperationChannel channel, Long ownerId,
                                 Long lotId, BigDecimal initialAmount, Instant reservedAt,
                                 @Nullable Instant evidenceDueAt) {
        this.id = id;
        this.reservationId = reservationId;
        this.channel = channel;
        this.ownerId = ownerId;
        this.lotId = lotId;
        this.initialAmount = initialAmount;
        this.reservedAt = reservedAt;
        this.evidenceDueAt = evidenceDueAt;
    }

    /**
     * A reservation an agent made in the field: the agent is its owner, the only one who may send its voucher.
     *
     * @param evidenceDueAt until when the lot waits for the payment evidence, when known
     */
    public static ReservationOperation fromFieldReservation(UUID reservationId, Long agentId, Long lotId,
                                                            BigDecimal initialAmount, Instant reservedAt,
                                                            @Nullable Instant evidenceDueAt) {
        if (reservationId == null || agentId == null || lotId == null || initialAmount == null || reservedAt == null) {
            throw new IllegalArgumentException("a field reservation needs its id, agent, lot, amount and date");
        }
        return new ReservationOperation(null, reservationId, OperationChannel.FIELD, agentId, lotId, initialAmount,
                reservedAt, evidenceDueAt);
    }

    /** Rebuilds an already persisted operation. */
    public static ReservationOperation restore(Long id, UUID reservationId, OperationChannel channel, Long ownerId,
                                               Long lotId, BigDecimal initialAmount, Instant reservedAt,
                                               @Nullable Instant evidenceDueAt) {
        return new ReservationOperation(id, reservationId, channel, ownerId, lotId, initialAmount, reservedAt,
                evidenceDueAt);
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId.equals(userId);
    }

    public @Nullable Long getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public OperationChannel getChannel() { return channel; }
    public Long getOwnerId() { return ownerId; }
    public Long getLotId() { return lotId; }
    public BigDecimal getInitialAmount() { return initialAmount; }
    public Instant getReservedAt() { return reservedAt; }
    public @Nullable Instant getEvidenceDueAt() { return evidenceDueAt; }
}
