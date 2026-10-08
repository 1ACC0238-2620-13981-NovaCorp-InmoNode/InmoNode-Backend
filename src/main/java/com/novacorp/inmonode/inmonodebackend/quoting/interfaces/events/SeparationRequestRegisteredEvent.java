package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * "Solicitud de separación registrada" (Context Map): a buyer's web request now holds its lot. Published once, inside
 * the transaction that blocks the lot, so a synchronous listener stores what it needs together with the block or
 * nothing is stored. Gestión de Comprobantes uses it to accept the buyer's payment voucher.
 *
 * @param transactionId id of the separation, shared by every context
 * @param initialAmount the down payment of the buyer's quotation
 * @param lockExpiresAt until when the lot waits for the payment evidence
 */
public record SeparationRequestRegisteredEvent(UUID transactionId, Long lotId, Long buyerId, BigDecimal initialAmount,
                                               String currency, Instant requestedAt, Instant lockExpiresAt) {
}
