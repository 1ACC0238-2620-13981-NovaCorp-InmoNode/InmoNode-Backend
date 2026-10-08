package com.novacorp.inmonode.inmonodebackend.catalog.interfaces.events;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * "Lote separado" (Context Map): a reservation an agent made offline now holds its lot on the server. Published when
 * its synchronization answers SYNCED and again on every re-send of it (at-least-once), so listeners must be
 * idempotent by {@code reservationId}; Gestión de Comprobantes uses it to accept the payment voucher of that
 * reservation.
 *
 * @param reservationId id the device generated for the reservation, shared by every context
 * @param agentId       the agent who made it, the only one who may upload its voucher
 * @param initialAmount down payment agreed in the field, in soles
 * @param reservedAt    when the agent registered it on the device
 * @param blockedUntil  until when the lot waits for the payment evidence; {@code null} on a re-send once the
 *                      reservation no longer holds its lot
 */
public record FieldLotReservedEvent(UUID reservationId, Long agentId, Long lotId, BigDecimal initialAmount,
                                    Instant reservedAt, @Nullable Instant blockedUntil) {
}
