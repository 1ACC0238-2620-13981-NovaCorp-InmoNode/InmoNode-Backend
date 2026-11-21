package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A PDF the backend generates in the background (US-45).
 *
 * @param requestId            also the {@code documentId} of the stored file: a ready contract is issued with it
 * @param kind                 QUOTATION or CONTRACT
 * @param reference            the quotation id or the reservation's transaction id
 * @param status               PROCESSING ("En proceso"), READY or FAILED
 * @param sizeBytes            size of the PDF, once it is ready; a contract is issued with it
 * @param downloadUrl          a {@code GET} of the PDF straight from the file repository, once it is ready
 * @param downloadUrlExpiresAt ask for the request again after this instant for a new link
 * @param failureCode          DOCUMENT_GENERATION_TIMEOUT, DOCUMENT_GENERATION_FAILED or DOCUMENT_GENERATION_EXPIRED
 * @param statusUrl            where to read this request again
 */
public record DocumentRequestResource(UUID requestId, String kind, String reference, String status,
                                      Instant requestedAt, @Nullable Instant completedAt, @Nullable Long sizeBytes,
                                      @Nullable String downloadUrl, @Nullable Instant downloadUrlExpiresAt,
                                      @Nullable String failureCode, String statusUrl) {
}
