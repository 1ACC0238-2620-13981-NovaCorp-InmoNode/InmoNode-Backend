package com.novacorp.inmonode.inmonodebackend.shared.application.storage;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound port to the file repository (S3 in production): clients upload files straight to it with a presigned
 * URL, so large files never travel through the API (US-33).
 */
public interface ObjectStorage {

    /**
     * Signs a {@code PUT} of exactly {@code sizeBytes} bytes of {@code contentType} under {@code key}. The storage
     * rejects any other type or size, because both are part of the signature (US-33, Scenario 2).
     *
     * @param validity how long the URL can be used
     */
    PresignedUpload presignUpload(String key, String contentType, long sizeBytes, Duration validity);

    /** The stored object under {@code key}; empty when nothing was uploaded there. */
    Optional<StoredObject> describe(String key);

    /**
     * @param headers headers the client must send with the {@code PUT}, exactly as given
     */
    record PresignedUpload(URI url, Instant expiresAt, Map<String, String> headers) {
    }

    record StoredObject(long sizeBytes, String contentType) {
    }
}
