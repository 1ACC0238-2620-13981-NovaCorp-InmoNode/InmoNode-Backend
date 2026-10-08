package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.time.Instant;
import java.util.Map;

/**
 * @param uploadUrl send the PDF here with a single {@code PUT}, straight to the file repository
 * @param expiresAt the URL is refused after this instant; ask for a new one
 * @param headers   headers the {@code PUT} must carry, exactly as given (Content-Type and Content-Length)
 */
public record ContractUploadResource(String uploadUrl, String objectKey, Instant expiresAt,
                                     Map<String, String> headers) {
}
