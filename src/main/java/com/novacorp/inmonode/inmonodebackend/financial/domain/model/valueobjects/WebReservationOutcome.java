package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Answer to a web separation request (US-19): whether the buyer's reservation now holds the lot.
 *
 * @param reservationId the stored reservation; {@code null} unless the lot was blocked
 * @param blockedUntil  until when the lot is held for it; {@code null} unless the lot was blocked
 */
public record WebReservationOutcome(Result result, @Nullable Long reservationId, @Nullable Instant blockedUntil) {

    public enum Result {
        /** The reservation holds the lot for one hour, while the payment evidence arrives. */
        BLOCKED,
        /** Another operation holds the lot, or it is no longer for sale (US-19, Scenario 2). */
        LOT_UNAVAILABLE,
        /** There is no such lot, or its project is not published. */
        LOT_NOT_FOUND
    }

    public static WebReservationOutcome blocked(Reservation reservation, Instant blockedUntil) {
        return new WebReservationOutcome(Result.BLOCKED, reservation.getId(), blockedUntil);
    }

    public static WebReservationOutcome unavailable() {
        return new WebReservationOutcome(Result.LOT_UNAVAILABLE, null, null);
    }

    public static WebReservationOutcome lotNotFound() {
        return new WebReservationOutcome(Result.LOT_NOT_FOUND, null, null);
    }
}
