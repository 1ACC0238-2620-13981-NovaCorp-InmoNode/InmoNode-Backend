package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Consolidated reservation of a {@link Lot}, from the field or the web, referenced by id.
 *
 * <p>A field reservation keeps the ids generated on the device: {@code sourceEventId} identifies the
 * reservation itself, so a re-sent one is recognized, and {@code prospectId} the prospect it was made for,
 * who lives in the field context.</p>
 */
public class Reservation {

    private final @Nullable Long id;
    private final Long lotId;
    private final ReservationChannel channel;
    private final Long requesterId;
    private final @Nullable UUID prospectId;
    private final @Nullable UUID sourceEventId;
    private final Money initialAmount;
    private final Instant reservedAt;
    private ReservationStatus status;

    private Reservation(@Nullable Long id, Long lotId, ReservationChannel channel, Long requesterId,
                        @Nullable UUID prospectId, @Nullable UUID sourceEventId, Money initialAmount,
                        Instant reservedAt, ReservationStatus status) {
        this.id = id;
        this.lotId = lotId;
        this.channel = channel;
        this.requesterId = requesterId;
        this.prospectId = prospectId;
        this.sourceEventId = sourceEventId;
        this.initialAmount = initialAmount;
        this.reservedAt = reservedAt;
        this.status = status;
    }

    /**
     * US-11: a reservation made offline that reached the server while the lot was available. It holds the lot
     * ({@code BLOCKED}) while the payment evidence arrives.
     *
     * @param reservedAt when the agent registered it on the device, kept for traceability
     */
    public static Reservation fromFieldSync(Long lotId, Long agentId, UUID prospectId, UUID sourceEventId,
                                            Money initialAmount, Instant reservedAt) {
        return field(lotId, agentId, prospectId, sourceEventId, initialAmount, reservedAt, ReservationStatus.BLOCKED);
    }

    /**
     * US-12: a reservation made offline that reached the server after another operation took the lot. It is kept,
     * so that a re-send of it is answered with the same conflict.
     */
    public static Reservation cancelledByConflict(Long lotId, Long agentId, UUID prospectId, UUID sourceEventId,
                                                  Money initialAmount, Instant reservedAt) {
        return field(lotId, agentId, prospectId, sourceEventId, initialAmount, reservedAt,
                ReservationStatus.CANCELLED_BY_CONFLICT);
    }

    /** Rebuilds an already persisted reservation. */
    public static Reservation restore(Long id, Long lotId, ReservationChannel channel, Long requesterId,
                                      @Nullable UUID prospectId, @Nullable UUID sourceEventId, Money initialAmount,
                                      Instant reservedAt, ReservationStatus status) {
        return new Reservation(id, lotId, channel, requesterId, prospectId, sourceEventId, initialAmount,
                reservedAt, status);
    }

    private static Reservation field(Long lotId, Long agentId, UUID prospectId, UUID sourceEventId,
                                     Money initialAmount, Instant reservedAt, ReservationStatus status) {
        if (lotId == null || agentId == null || prospectId == null || sourceEventId == null
                || initialAmount == null || reservedAt == null) {
            throw new IllegalArgumentException("a field reservation needs its lot, agent, prospect, id, amount and date");
        }
        return new Reservation(null, lotId, ReservationChannel.FIELD, agentId, prospectId, sourceEventId,
                initialAmount, reservedAt, status);
    }

    /**
     * The lot block ran out without payment evidence (Lot Block).
     *
     * @return {@code false} when the reservation no longer held the lot, so nothing changed
     */
    public boolean expire() {
        if (status != ReservationStatus.BLOCKED) {
            return false;
        }
        status = ReservationStatus.EXPIRED;
        return true;
    }

    public boolean isCancelledByConflict() {
        return status == ReservationStatus.CANCELLED_BY_CONFLICT;
    }

    public @Nullable Long getId() { return id; }
    public Long getLotId() { return lotId; }
    public ReservationChannel getChannel() { return channel; }
    public Long getRequesterId() { return requesterId; }
    public @Nullable UUID getProspectId() { return prospectId; }
    public @Nullable UUID getSourceEventId() { return sourceEventId; }
    public Money getInitialAmount() { return initialAmount; }
    public Instant getReservedAt() { return reservedAt; }
    public ReservationStatus getStatus() { return status; }
}
