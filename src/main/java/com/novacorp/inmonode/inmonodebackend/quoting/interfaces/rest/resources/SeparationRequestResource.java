package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param transactionId  send it as {@code reservationId} to upload the payment voucher
 * @param status         BLOCKED: the lot is held for the buyer until {@code lockExpiresAt}
 * @param initialAmount  the down payment of the quotation, in {@code currency}
 * @param lockExpiresAt  the payment voucher must be registered before this instant
 */
public record SeparationRequestResource(Long id, UUID transactionId, Long lotId, Long quotationId, String status,
                                        BigDecimal initialAmount, String currency, Instant requestedAt,
                                        Instant lockExpiresAt) {
}
