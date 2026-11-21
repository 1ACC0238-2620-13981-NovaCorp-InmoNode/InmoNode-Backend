package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/** Where a background PDF stands (US-45). */
public enum DocumentRequestStatus {
    /** Accepted and waiting for, or going through, the document consumer: "En proceso". */
    PROCESSING,
    /** Rendered and stored in the file repository. */
    READY,
    /** Not generated; the request says why. Asking again starts a new request. */
    FAILED
}
