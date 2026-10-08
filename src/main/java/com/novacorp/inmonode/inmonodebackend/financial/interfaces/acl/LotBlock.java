package com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Answer of {@link LotAvailabilityFacade#blockLot} in plain values.
 *
 * @param result        {@code BLOCKED}, {@code LOT_UNAVAILABLE} (another operation holds it) or {@code LOT_NOT_FOUND}
 * @param reservationId id of the reservation that holds the lot; {@code null} unless it was blocked
 * @param blockedUntil  until when the lot is held for the request; {@code null} unless it was blocked
 */
public record LotBlock(String result, @Nullable Long reservationId, @Nullable Instant blockedUntil) {
}
