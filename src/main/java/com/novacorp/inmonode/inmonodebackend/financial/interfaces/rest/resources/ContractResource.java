package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

/**
 * An issued contract, as the back office sees it.
 *
 * @param issuedBy            the back-office user who issued it
 * @param buyerAcknowledgedAt when the buyer gave their preliminary agreement (US-22); {@code null} until then
 */
public record ContractResource(Long id, UUID transactionId, Long reservationId, Long buyerId, Long lotId,
                               String status, long sizeBytes, Instant issuedAt, Long issuedBy,
                               Instant buyerAcknowledgedAt, CoOwnerResource coOwner) {
}
