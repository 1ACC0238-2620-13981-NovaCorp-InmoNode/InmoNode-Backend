package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One entry of the verification queue: a payment evidence waiting for a decision and what it pays for.
 *
 * @param reference     the voucher id
 * @param late          it arrived after the reservation stopped holding its lot: it can only be rejected
 * @param transactionId id of the reservation shared by every context
 * @param requesterId   the agent (field) or the buyer (web) who made the reservation
 * @param initialAmount the down payment the evidence must cover, in {@code currency}
 */
public record PendingVerificationResource(Long evidenceId, UUID reference, String source, BigDecimal amount,
                                          String currency, LocalDate operationDate, String operationCode,
                                          boolean manuallyCorrected, boolean late, Instant submittedAt,
                                          Long reservationId, UUID transactionId, String channel, Long requesterId,
                                          String reservationStatus, BigDecimal initialAmount, Long lotId,
                                          String lotCode) {
}
