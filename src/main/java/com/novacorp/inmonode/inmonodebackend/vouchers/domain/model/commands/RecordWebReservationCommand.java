package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.commands;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Records a separation a buyer requested from the web portal, so their payment voucher can be accepted (US-20).
 *
 * @param reservationId the request's transaction id, shared by every context
 * @param evidenceDueAt until when the lot waits for the payment evidence
 */
public record RecordWebReservationCommand(UUID reservationId, Long buyerId, Long lotId, BigDecimal initialAmount,
                                          Instant requestedAt, Instant evidenceDueAt) {
}
