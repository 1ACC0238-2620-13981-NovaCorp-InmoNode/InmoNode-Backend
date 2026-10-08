package com.novacorp.inmonode.inmonodebackend.vouchers.interfaces.rest.resources;

import java.time.Instant;
import java.util.Map;

/**
 * @param uploadUrl send the file here with a single {@code PUT}, straight to the file repository
 * @param objectKey where the file will be stored
 * @param expiresAt the URL is refused after this instant; ask for a new one
 * @param headers   headers the {@code PUT} must carry, exactly as given (Content-Type and Content-Length); the
 *                  storage answers 403 to any other type or size
 */
public record VoucherUploadResource(String uploadUrl, String objectKey, Instant expiresAt,
                                    Map<String, String> headers) {
}
