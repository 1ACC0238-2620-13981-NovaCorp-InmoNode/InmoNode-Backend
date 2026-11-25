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
     * Writes a file the backend produced itself, such as a generated PDF (US-45), under {@code key}. Writing the
     * same key again replaces it, so a retried generation leaves no orphans.
     */
    void store(String key, String contentType, byte[] content);

    /**
     * The bytes of the object under {@code key}, for files the backend must read itself, such as a contract whose
     * hash the signature sheet cites (US-56). Meant for small files, up to the 10 MB of a contract.
     *
     * @return empty when nothing is stored there
     */
    Optional<byte[]> read(String key);

    /**
     * Signs a {@code GET} of the object under {@code key}, so a client reads it straight from the storage while the
     * bucket stays private. Signing does not check the object exists; the storage answers 404 if it does not.
     *
     * @param validity how long the URL can be used
     */
    PresignedDownload presignDownload(String key, Duration validity);

    /**
     * @param headers headers the client must send with the {@code PUT}, exactly as given
     */
    record PresignedUpload(URI url, Instant expiresAt, Map<String, String> headers) {
    }

    record PresignedDownload(URI url, Instant expiresAt) {
    }

    record StoredObject(long sizeBytes, String contentType) {
    }
}
