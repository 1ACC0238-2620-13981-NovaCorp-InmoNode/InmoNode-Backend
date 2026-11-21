package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.util.UUID;

/**
 * The PDFs the backend generates in the background (US-45). Each kind keeps its files under its own folder of the
 * file repository; a contract goes exactly where a contract uploaded by hand would, so it is issued the same way.
 */
public enum DocumentKind {
    /** A financing quotation, for the buyer who saved it; the reference is the quotation id. */
    QUOTATION("quotations/%s/%s.pdf"),
    /** The preliminary purchase contract of a verified web reservation; the reference is its transaction id. */
    CONTRACT("contracts/%s/%s.pdf");

    private final String keyPattern;

    DocumentKind(String keyPattern) {
        this.keyPattern = keyPattern;
    }

    /** Where the PDF of request {@code requestId} for {@code reference} lives in the file repository. */
    public String objectKey(String reference, UUID requestId) {
        return keyPattern.formatted(reference, requestId);
    }
}
