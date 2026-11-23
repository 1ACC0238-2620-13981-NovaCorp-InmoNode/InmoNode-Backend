package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

/**
 * An issued contract, as the back office sees it.
 *
 * @param issuedBy               the back-office user who issued it
 * @param buyerAcknowledgedAt    when the buyer gave their preliminary agreement (US-22); {@code null} until then
 * @param status                 ISSUED, SIGNATURE_REQUESTED, SIGNED or SIGNATURE_DECLINED (US-56)
 * @param documentSha256         the SHA-256 of the issued PDF, which the signature sheet cites
 * @param signatureSubmissionId  the provider's submission the buyer signs
 * @param signedSha256           the SHA-256 of the signed signature sheet
 */
public record ContractResource(Long id, UUID transactionId, Long reservationId, Long buyerId, Long lotId,
                               String status, long sizeBytes, Instant issuedAt, Long issuedBy,
                               Instant buyerAcknowledgedAt, CoOwnerResource coOwner, String documentSha256,
                               Long signatureSubmissionId, Instant signatureRequestedAt, Instant signedAt,
                               String signedSha256, Instant signatureDeclinedAt, String signatureDeclineReason) {
}
