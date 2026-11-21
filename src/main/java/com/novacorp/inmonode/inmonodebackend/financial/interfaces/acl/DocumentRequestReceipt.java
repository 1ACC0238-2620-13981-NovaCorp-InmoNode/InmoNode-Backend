package com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Answer of {@link DocumentGenerationFacade} in plain values.
 *
 * @param status      {@code PROCESSING}, or {@code FAILED} when the queue could not take the request
 * @param failureCode why it failed; {@code null} unless it failed
 */
public record DocumentRequestReceipt(UUID requestId, String status, Instant requestedAt, @Nullable String failureCode) {
}
