package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/** What the document consumer did with one message (US-45). */
public enum DocumentGenerationOutcome {
    /** The PDF is stored and its request is ready. */
    GENERATED,
    /** Rendering or storing failed; the request is failed and the back office was notified. */
    FAILED,
    /** Nothing to do: the request is unknown, already finished (a redelivery) or expired. */
    SKIPPED
}
