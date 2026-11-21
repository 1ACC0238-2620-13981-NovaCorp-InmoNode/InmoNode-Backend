package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The photo of the buyer's identity document that the field agent captured for a reservation (US-59), kept in the file
 * repository. It is stored under a key derived from its reservation and its own id, so a retry writes the same object
 * instead of leaving orphans.
 *
 * @param transactionId id of the reservation shared by every context: the one the device generated
 * @param documentId    id the device generated for the photo
 * @param contentType   {@code image/jpeg} or {@code image/png}, in lower case
 * @param sizeBytes     exact size of the file, up to 5 MB
 */
public record IdentityDocument(UUID transactionId, UUID documentId, String contentType, long sizeBytes) {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png");

    public IdentityDocument {
        if (transactionId == null || documentId == null) {
            throw new IllegalArgumentException("an identity document needs its reservation and its id");
        }
        contentType = normalized(contentType);
        if (!EXTENSIONS.containsKey(contentType)) {
            throw new IllegalArgumentException("contentType must be image/jpeg or image/png");
        }
        if (sizeBytes < 1 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "sizeBytes must be between 1 and %d (5 MB)".formatted(MAX_SIZE_BYTES));
        }
    }

    /** Where the file lives in the file repository: {@code identity-documents/{transactionId}/{documentId}.{ext}}. */
    public String objectKey() {
        return "identity-documents/%s/%s.%s".formatted(transactionId, documentId, EXTENSIONS.get(contentType));
    }

    private static String normalized(@Nullable String contentType) {
        return contentType == null ? "" : contentType.strip().toLowerCase(Locale.ROOT);
    }
}
