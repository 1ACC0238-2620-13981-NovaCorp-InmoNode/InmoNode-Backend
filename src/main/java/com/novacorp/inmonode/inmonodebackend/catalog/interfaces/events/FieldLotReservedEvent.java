package com.novacorp.inmonode.inmonodebackend.catalog.interfaces.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * "Lote separado" (Context Map): a reservation an agent made offline now holds its lot on the server. Published
 * once per reservation, when its synchronization answers SYNCED; Gestión de Comprobantes uses it to accept the
 * payment voucher of that reservation.
 *
 * @param reservationId id the device generated for the reservation, shared by every context
 * @param agentId       the agent who made it, the only one who may upload its voucher
 * @param initialAmount down payment agreed in the field, in soles
 * @param reservedAt    when the agent registered it on the device
 * @param blockedUntil  until when the lot waits for the payment evidence
 */
public record FieldLotReservedEvent(UUID reservationId, Long agentId, Long lotId, BigDecimal initialAmount,
                                    Instant reservedAt, Instant blockedUntil) {
}
