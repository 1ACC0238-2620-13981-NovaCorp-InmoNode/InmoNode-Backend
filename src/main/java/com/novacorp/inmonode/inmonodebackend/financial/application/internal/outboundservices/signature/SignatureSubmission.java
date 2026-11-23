package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A submission as the provider has it.
 *
 * @param externalId  the reference of its signer, which ties it to the contract
 * @param documentUrl the signed document, once completed
 */
public record SignatureSubmission(long id, Status status, @Nullable String externalId, @Nullable Instant completedAt,
                                  @Nullable String declineReason, @Nullable String documentUrl) {

    public enum Status { PENDING, COMPLETED, DECLINED, EXPIRED }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public boolean isDeclined() {
        return status == Status.DECLINED;
    }
}
