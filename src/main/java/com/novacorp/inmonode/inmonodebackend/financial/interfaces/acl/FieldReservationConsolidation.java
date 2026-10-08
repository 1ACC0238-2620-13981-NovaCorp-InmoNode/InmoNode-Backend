package com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Answer of {@link FieldReservationConsolidationFacade} for one reservation, in plain values.
 *
 * @param result            {@code SYNCED}, {@code CONFLICT} or {@code DUPLICATE}
 * @param reservationId     id of the stored reservation; {@code null} when the lot does not exist
 * @param reservationStatus current status of the stored reservation, e.g. {@code BLOCKED}
 * @param blockedUntil      until when the lot is held for this reservation; {@code null} when it does not hold it
 * @param conflictReason    {@code LOT_UNAVAILABLE} or {@code LOT_NOT_FOUND}; {@code null} unless it is a conflict
 * @param originalResult    for {@code DUPLICATE}, what the first consolidation answered
 */
public record FieldReservationConsolidation(String result, @Nullable Long reservationId,
                                            @Nullable String reservationStatus, @Nullable Instant blockedUntil,
                                            @Nullable String conflictReason, @Nullable String originalResult) {
}
