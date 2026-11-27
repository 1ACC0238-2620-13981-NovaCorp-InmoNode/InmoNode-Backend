package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the contract queue: a verified web reservation whose contract the back office can issue now (US-21).
 *
 * @param transactionId the reservation in every contract endpoint: generate the PDF with
 *                      {@code POST /reservations/{transactionId}/contract/document-requests} or upload one, then issue
 *                      it with {@code POST /reservations/{transactionId}/contract}
 * @param buyerEmail    the account of the buyer, who will read and sign the contract; {@code null} when it no longer
 *                      exists
 * @param initialAmount the down payment that was verified, in {@code currency}
 * @param verifiedAt    when the back office approved the payment; the queue is ordered by it, oldest first
 * @param coOwner       the co-owner the buyer designated (US-28), who the contract names too; {@code null} when none
 */
public record ContractToIssueResource(Long reservationId, UUID transactionId, Long buyerId, String buyerEmail,
                                      Long projectId, String projectName, Long lotId, String lotCode,
                                      BigDecimal initialAmount, String currency, Instant verifiedAt,
                                      CoOwnerResource coOwner) {
}
