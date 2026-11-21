package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * The PDF of a quotation, requested in the background (US-45).
 *
 * @param status      PROCESSING ("En proceso"), or FAILED when it could not be queued
 * @param failureCode why it failed; {@code null} unless it failed
 * @param statusUrl   read it there until it is READY, with its download link
 */
public record QuotationDocumentRequestResource(UUID requestId, Long quotationId, String status, Instant requestedAt,
                                               @Nullable String failureCode, String statusUrl) {
}
