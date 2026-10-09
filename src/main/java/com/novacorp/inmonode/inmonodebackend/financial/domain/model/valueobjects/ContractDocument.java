package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.util.UUID;

/**
 * The PDF of a preliminary purchase contract (US-21), uploaded by the back office. It is stored under a key derived
 * from its reservation and its own id, so a retry writes the same object instead of leaving orphans.
 *
 * @param transactionId id of the reservation shared by every context
 * @param documentId    id the back office generates for this upload
 * @param sizeBytes     exact size of the file it will upload, up to 10 MB
 */
public record ContractDocument(UUID transactionId, UUID documentId, long sizeBytes) {

    public static final String CONTENT_TYPE = "application/pdf";
    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    public ContractDocument {
        if (transactionId == null || documentId == null) {
            throw new IllegalArgumentException("a contract document needs its reservation and its id");
        }
        if (sizeBytes < 1 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "sizeBytes must be between 1 and %d (10 MB)".formatted(MAX_SIZE_BYTES));
        }
    }

    /** Where the file lives in the file repository: {@code contracts/{transactionId}/{documentId}.pdf}. */
    public String objectKey() {
        return "contracts/%s/%s.pdf".formatted(transactionId, documentId);
    }
}
