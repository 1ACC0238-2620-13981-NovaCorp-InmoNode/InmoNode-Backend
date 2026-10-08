package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * What Control Financiero y Documental answered when asked to block a lot, in this context's terms.
 *
 * @param blockedUntil until when the lot is held for the request; {@code null} unless it was blocked
 */
public record LotBlockOutcome(Result result, @Nullable Instant blockedUntil) {

    public enum Result {
        BLOCKED,
        UNAVAILABLE,
        NOT_FOUND
    }
}
