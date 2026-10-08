package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Permission to upload one contract PDF straight to the file repository.
 *
 * @param uploadUrl presigned URL for a single {@code PUT} of the file
 * @param expiresAt after this instant the URL is refused and a new one must be requested
 * @param headers   headers the {@code PUT} must carry, exactly as given; the storage rejects any other type or size
 */
public record ContractUpload(URI uploadUrl, String objectKey, Instant expiresAt, Map<String, String> headers) {

    public ContractUpload {
        headers = Map.copyOf(headers);
    }
}
