package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Answer to one consolidated field reservation (US-32): the field context turns it into the result the device shows.
 *
 * @param reservationId     {@code null} only when the lot does not exist
 * @param reservationStatus current status of the stored reservation
 * @param blockedUntil      until when the lot is held for this reservation; {@code null} when it does not hold it
 * @param conflictReason    why the reservation could not take the lot; {@code null} unless it is a conflict
 * @param originalResult    for a re-send, what the first consolidation answered ({@code SYNCED} or {@code CONFLICT})
 */
public record FieldReservationOutcome(Result result, @Nullable Long reservationId,
                                      @Nullable ReservationStatus reservationStatus, @Nullable Instant blockedUntil,
                                      @Nullable ConflictReason conflictReason, @Nullable Result originalResult) {

    public enum Result {
        /** The reservation holds the lot. */
        SYNCED,
        /** The lot was already taken by an operation that reached the server first (US-12). */
        CONFLICT,
        /** The device re-sent a reservation already consolidated; nothing new was stored. */
        DUPLICATE
    }

    public enum ConflictReason {
        LOT_UNAVAILABLE,
        LOT_NOT_FOUND
    }

    public static FieldReservationOutcome synced(Reservation reservation, Instant blockedUntil) {
        return new FieldReservationOutcome(Result.SYNCED, reservation.getId(), reservation.getStatus(), blockedUntil,
                null, null);
    }

    public static FieldReservationOutcome conflict(@Nullable Reservation reservation, ConflictReason reason) {
        return new FieldReservationOutcome(Result.CONFLICT, reservation == null ? null : reservation.getId(),
                reservation == null ? null : reservation.getStatus(), null, reason, null);
    }

    /**
     * @param blockedUntil the block the reservation still holds, if any
     */
    public static FieldReservationOutcome duplicateOf(Reservation reservation, @Nullable Instant blockedUntil) {
        var conflicted = reservation.isCancelledByConflict();
        return new FieldReservationOutcome(Result.DUPLICATE, reservation.getId(), reservation.getStatus(),
                blockedUntil, conflicted ? ConflictReason.LOT_UNAVAILABLE : null,
                conflicted ? Result.CONFLICT : Result.SYNCED);
    }
}
