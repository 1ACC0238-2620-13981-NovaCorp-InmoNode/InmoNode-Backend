package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An authentic callback of the signature provider and what it did.
 *
 * @param eventKey its type and submission, since the provider sends no event id
 */
public record SignatureEventRecord(String eventKey, String eventType, @Nullable Long submissionId,
                                   @Nullable Long contractId, SignatureEventOutcome outcome, @Nullable String detail,
                                   Instant receivedAt) {

    private static final int MAX_DETAIL_LENGTH = 500;

    public SignatureEventRecord {
        if (detail != null && detail.length() > MAX_DETAIL_LENGTH) {
            detail = detail.substring(0, MAX_DETAIL_LENGTH);
        }
    }
}
