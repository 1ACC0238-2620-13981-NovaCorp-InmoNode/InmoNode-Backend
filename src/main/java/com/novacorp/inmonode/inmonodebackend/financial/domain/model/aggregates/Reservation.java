package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consolidated reservation of a {@link Lot}, from the field or the web, referenced by id.
 *
 * <p>A field reservation keeps the ids generated on the device: {@code sourceEventId} identifies the
 * reservation itself, so a re-sent one is recognized, and {@code prospectId} the prospect it was made for,
 * who lives in the field context.</p>
 *
 * <p>It keeps the {@link PaymentEvidence payment evidences} it received, which the back office verifies.</p>
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
    private final List<PaymentEvidence> evidences;

    private Reservation(@Nullable Long id, Long lotId, ReservationChannel channel, Long requesterId,
                        @Nullable UUID prospectId, @Nullable UUID sourceEventId, Money initialAmount,
                        Instant reservedAt, ReservationStatus status, List<PaymentEvidence> evidences) {
        this.id = id;
        this.lotId = lotId;
        this.channel = channel;
        this.requesterId = requesterId;
        this.prospectId = prospectId;
        this.sourceEventId = sourceEventId;
        this.initialAmount = initialAmount;
        this.reservedAt = reservedAt;
        this.status = status;
        this.evidences = new ArrayList<>(evidences);
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

    /**
     * US-19: a buyer's separation request from the web portal that found the lot available. It holds the lot
     * ({@code BLOCKED}) while the payment evidence arrives; a web request is never stored as a conflict, the requesting
     * context keeps the rejection instead.
     *
     * @param sourceEventId the request's transaction id, shared by every context
     * @param initialAmount the down payment of the quotation the buyer accepted
     */
    public static Reservation fromWebRequest(Long lotId, Long buyerId, UUID sourceEventId, Money initialAmount,
                                             Instant requestedAt) {
        if (lotId == null || buyerId == null || sourceEventId == null || initialAmount == null || requestedAt == null) {
            throw new IllegalArgumentException("a web reservation needs its lot, buyer, id, amount and date");
        }
        return new Reservation(null, lotId, ReservationChannel.WEB, buyerId, null, sourceEventId, initialAmount,
                requestedAt, ReservationStatus.BLOCKED, List.of());
    }

    /** Rebuilds an already persisted reservation, with the payment evidences it received. */
    public static Reservation restore(Long id, Long lotId, ReservationChannel channel, Long requesterId,
                                      @Nullable UUID prospectId, @Nullable UUID sourceEventId, Money initialAmount,
                                      Instant reservedAt, ReservationStatus status,
                                      List<PaymentEvidence> evidences) {
        return new Reservation(id, lotId, channel, requesterId, prospectId, sourceEventId, initialAmount,
                reservedAt, status, evidences);
    }

    private static Reservation field(Long lotId, Long agentId, UUID prospectId, UUID sourceEventId,
                                     Money initialAmount, Instant reservedAt, ReservationStatus status) {
        if (lotId == null || agentId == null || prospectId == null || sourceEventId == null
                || initialAmount == null || reservedAt == null) {
            throw new IllegalArgumentException("a field reservation needs its lot, agent, prospect, id, amount and date");
        }
        return new Reservation(null, lotId, ReservationChannel.FIELD, agentId, prospectId, sourceEventId,
                initialAmount, reservedAt, status, List.of());
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

    /**
     * Receives a payment evidence (2.6.4.1). While the reservation holds its lot ({@code BLOCKED}) the evidence is on
     * time and the reservation waits for verification ({@code PENDING_VERIFICATION}); the caller moves the lot along.
     * When it no longer holds it (expired or lost in a conflict) the evidence is kept as late for the back office and
     * the reservation stays as it is. Any other evidence is kept as one more for the same verification.
     *
     * @return whether the reservation moved to {@code PENDING_VERIFICATION}
     * @throws IllegalStateException when an evidence with the same reference was already received
     */
    public boolean attachEvidence(PaymentEvidence evidence) {
        if (findEvidence(evidence.getReference()).isPresent()) {
            throw new IllegalStateException("payment evidence %s was already received".formatted(evidence.getReference()));
        }
        if (status == ReservationStatus.BLOCKED) {
            evidences.add(evidence);
            status = ReservationStatus.PENDING_VERIFICATION;
            return true;
        }
        var heldNoLot = status == ReservationStatus.EXPIRED || status == ReservationStatus.CANCELLED_BY_CONFLICT;
        evidences.add(heldNoLot ? evidence.markedLate() : evidence);
        return false;
    }

    public Optional<PaymentEvidence> findEvidence(UUID reference) {
        return evidences.stream().filter(evidence -> evidence.getReference().equals(reference)).findFirst();
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
    public List<PaymentEvidence> getEvidences() { return List.copyOf(evidences); }
}
