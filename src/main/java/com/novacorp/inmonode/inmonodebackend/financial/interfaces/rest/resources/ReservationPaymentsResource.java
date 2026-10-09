package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A reservation with its payment evidences, for the person who made it (US-25).
 *
 * @param status        BLOCKED (waiting for a voucher), PENDING_VERIFICATION, REJECTED (waiting for a substitute),
 *                      VERIFIED, EXPIRED...
 * @param initialAmount the down payment the voucher must cover, in {@code currency}
 * @param waitingUntil  a voucher (or a substitute for a rejected one) must arrive before this instant
 */
public record ReservationPaymentsResource(UUID transactionId, String channel, String status,
                                          BigDecimal initialAmount, String currency, Instant waitingUntil,
                                          List<EvidenceResource> evidences) {

    /**
     * @param status            PENDING, APPROVED or REJECTED
     * @param late              it arrived after the reservation stopped holding its lot
     * @param rejectionReason   why the back office rejected it; send a substitute voucher (US-25, Scenario 2)
     * @param downloadUrl       the voucher file, only once approved; it works until {@code downloadExpiresAt}
     */
    public record EvidenceResource(Long id, UUID reference, String status, BigDecimal amount, String currency,
                                   LocalDate operationDate, String operationCode, boolean late, Instant submittedAt,
                                   Instant reviewedAt, String rejectionReason, String downloadUrl,
                                   Instant downloadExpiresAt) {
    }
}
