package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents;

import java.time.Duration;

/** A PDF took longer than its limit to render and was aborted (US-45, Scenario 2). */
public class DocumentRenderingTimeoutException extends RuntimeException {

    public DocumentRenderingTimeoutException(Duration limit) {
        super("Rendering the PDF took longer than %d seconds and was aborted".formatted(limit.toSeconds()));
    }
}
