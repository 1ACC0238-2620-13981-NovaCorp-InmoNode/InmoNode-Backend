package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.time.Instant;
import java.util.UUID;

/**
 * The contract of a reservation, as its buyer sees it (US-21).
 *
 * @param availability      ISSUED (read and download it), IN_PREPARATION ("Contrato en elaboración": the payment
 *                          arrived and the back office is preparing it) or NOT_AVAILABLE (no payment yet, or the
 *                          reservation no longer holds the lot)
 * @param contractId        send it to register the agreement; only when ISSUED
 * @param downloadUrl       the PDF, only when ISSUED; it works until {@code downloadExpiresAt}
 * @param acknowledgedAt    when the buyer gave their preliminary agreement (US-22); {@code null} until then
 */
public record BuyerContractResource(UUID transactionId, String availability, Long contractId, Instant issuedAt,
                                    String downloadUrl, Instant downloadExpiresAt, Instant acknowledgedAt, CoOwnerResource coOwner) {
}
