package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * The PDF of a quotation, requested in the background (US-45).
 *
 * @param status      {@code PROCESSING}, or {@code FAILED} when it could not be queued
 * @param failureCode why it failed; {@code null} unless it failed
 */
public record QuotationDocumentRequest(UUID requestId, Long quotationId, String status, Instant requestedAt,
                                       @Nullable String failureCode) {
}
