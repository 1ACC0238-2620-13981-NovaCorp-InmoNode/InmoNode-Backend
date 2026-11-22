package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

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

    public static final long MAX_SIZE_BYTES = ImageFile.MAX_SIZE_BYTES;

    public IdentityDocument {
        if (transactionId == null || documentId == null) {
            throw new IllegalArgumentException("an identity document needs its reservation and its id");
        }
        contentType = new ImageFile(contentType, sizeBytes).contentType();
    }

    /** Where the file lives in the file repository: {@code identity-documents/{transactionId}/{documentId}.{ext}}. */
    public String objectKey() {
        return "identity-documents/%s/%s.%s".formatted(transactionId, documentId,
                new ImageFile(contentType, sizeBytes).extension());
    }
}
