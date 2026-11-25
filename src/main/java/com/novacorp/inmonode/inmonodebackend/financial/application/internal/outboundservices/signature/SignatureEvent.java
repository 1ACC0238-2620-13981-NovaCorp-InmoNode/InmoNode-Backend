package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.signature;

import org.jspecify.annotations.Nullable;

/**
 * A callback of the signature provider whose signature was verified.
 *
 * @param type          the provider's own name of the event, e.g. {@code form.completed}
 * @param submissionId  the submission it is about
 * @param externalId    the reference of its signer, which ties it to the contract
 * @param declineReason why the buyer declined, for {@link Kind#DECLINED}
 */
public record SignatureEvent(String type, Kind kind, @Nullable Long submissionId, @Nullable String externalId,
                             @Nullable String declineReason) {

    public enum Kind {
        /** The buyer signed. */
        COMPLETED,
        /** The buyer declined to sign. */
        DECLINED,
        /** Anything else: opened, started, created, templates. */
        OTHER
    }

    /** The provider sends no event id, so a callback is known by its type and submission. */
    public String eventKey() {
        return type + ":" + (submissionId == null ? "-" : submissionId);
    }
}
