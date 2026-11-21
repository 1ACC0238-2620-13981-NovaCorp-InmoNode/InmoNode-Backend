package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentRequestStatus;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A PDF generated in the background (US-45): the API answers at once that it is processing, and the document
 * consumer renders it, stores it in the file repository and marks it ready, or marks it failed.
 *
 * <p>Its id is also the id of the stored file, so a contract generated this way is issued with that id as its
 * {@code documentId}.</p>
 */
public class DocumentRequest {

    /** Rendering broke, e.g. a corrupt template, or the file could not be stored. */
    public static final String GENERATION_FAILED = "DOCUMENT_GENERATION_FAILED";
    /** Rendering took longer than its limit (US-45, Scenario 2) and was aborted. */
    public static final String GENERATION_TIMEOUT = "DOCUMENT_GENERATION_TIMEOUT";
    /** Still processing after {@link #PROCESSING_DEADLINE}: its message was lost, e.g. in a restart. */
    public static final String GENERATION_EXPIRED = "DOCUMENT_GENERATION_EXPIRED";
    /** How long a request may wait for the consumer, including a queue that waits for a sleeping instance. */
    public static final Duration PROCESSING_DEADLINE = Duration.ofMinutes(10);

    private final UUID id;
    private final DocumentKind kind;
    private final String reference;
    private final Long requestedBy;
    private final String objectKey;
    private final Instant requestedAt;
    private DocumentRequestStatus status;
    private @Nullable Long sizeBytes;
    private @Nullable String failureCode;
    private @Nullable Instant completedAt;

    private DocumentRequest(UUID id, DocumentKind kind, String reference, Long requestedBy, String objectKey,
                            Instant requestedAt, DocumentRequestStatus status, @Nullable Long sizeBytes,
                            @Nullable String failureCode, @Nullable Instant completedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.reference = Objects.requireNonNull(reference, "reference");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.sizeBytes = sizeBytes;
        this.failureCode = failureCode;
        this.completedAt = completedAt;
    }

    /** A new request of {@code requestedBy}, processing from {@code now}. */
    public static DocumentRequest request(DocumentKind kind, String reference, Long requestedBy, Instant now) {
        if (kind == null || reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("a document request needs its kind and the reference of its document");
        }
        var id = UUID.randomUUID();
        return new DocumentRequest(id, kind, reference, requestedBy, kind.objectKey(reference, id), now,
                DocumentRequestStatus.PROCESSING, null, null, null);
    }

    /** A request as it was persisted. */
    public static DocumentRequest restore(UUID id, DocumentKind kind, String reference, Long requestedBy,
                                          String objectKey, Instant requestedAt, DocumentRequestStatus status,
                                          @Nullable Long sizeBytes, @Nullable String failureCode,
                                          @Nullable Instant completedAt) {
        return new DocumentRequest(id, kind, reference, requestedBy, objectKey, requestedAt, status, sizeBytes,
                failureCode, completedAt);
    }

    /**
     * The PDF of {@code sizeBytes} bytes is stored.
     *
     * @throws IllegalStateException when the request is no longer processing
     */
    public void complete(long sizeBytes, Instant now) {
        requireProcessing();
        if (sizeBytes < 1) throw new IllegalArgumentException("a generated document has at least one byte");
        this.status = DocumentRequestStatus.READY;
        this.sizeBytes = sizeBytes;
        this.completedAt = now;
    }

    /**
     * The PDF was not generated, for {@code failureCode}.
     *
     * @throws IllegalStateException when the request is no longer processing
     */
    public void fail(String failureCode, Instant now) {
        requireProcessing();
        if (failureCode == null || failureCode.isBlank()) throw new IllegalArgumentException("a failure needs its code");
        this.status = DocumentRequestStatus.FAILED;
        this.failureCode = failureCode;
        this.completedAt = now;
    }

    /** Whether it still waits for the consumer at {@code now}: processing and within its deadline. */
    public boolean isPending(Instant now) {
        return status == DocumentRequestStatus.PROCESSING && !isExpired(now);
    }

    /** Processing past {@link #PROCESSING_DEADLINE}: nothing will complete it any more. */
    public boolean isExpired(Instant now) {
        return status == DocumentRequestStatus.PROCESSING && requestedAt.plus(PROCESSING_DEADLINE).isBefore(now);
    }

    /** The status a reader sees at {@code now}: an expired request reads as failed. */
    public DocumentRequestStatus statusAt(Instant now) {
        return isExpired(now) ? DocumentRequestStatus.FAILED : status;
    }

    /** Why it failed, as a reader sees it at {@code now}; {@code null} unless it failed. */
    public @Nullable String failureCodeAt(Instant now) {
        return isExpired(now) ? GENERATION_EXPIRED : failureCode;
    }

    private void requireProcessing() {
        if (status != DocumentRequestStatus.PROCESSING) {
            throw new IllegalStateException("document request %s is already %s".formatted(id, status));
        }
    }

    public UUID getId() { return id; }
    public DocumentKind getKind() { return kind; }
    public String getReference() { return reference; }
    public Long getRequestedBy() { return requestedBy; }
    public String getObjectKey() { return objectKey; }
    public Instant getRequestedAt() { return requestedAt; }
    public DocumentRequestStatus getStatus() { return status; }
    public @Nullable Long getSizeBytes() { return sizeBytes; }
    public @Nullable String getFailureCode() { return failureCode; }
    public @Nullable Instant getCompletedAt() { return completedAt; }
}
