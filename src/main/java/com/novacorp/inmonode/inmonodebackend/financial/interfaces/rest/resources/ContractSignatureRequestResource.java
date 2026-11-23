package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.time.Instant;

/**
 * A contract and where its request to sign is (US-56).
 *
 * @param deliveryStatus PENDING (waiting for its next attempt), SENT or FAILED; {@code null} when never queued
 * @param attempts       failed attempts so far
 * @param nextAttemptAt  when it is tried again, while PENDING
 * @param lastError      why the last attempt failed
 */
public record ContractSignatureRequestResource(ContractResource contract, String deliveryStatus, Integer attempts,
                                               Instant nextAttemptAt, String lastError) {
}
