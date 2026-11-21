package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;

/**
 * Outbound port to the PDF renderer of the document consumer, which aborts a rendering that takes too long
 * (US-45, Scenario 2: a corrupt template looping forever).
 */
public interface DocumentRenderer {

    /**
     * @return the read-only PDF
     * @throws DocumentRenderingTimeoutException when rendering takes longer than its limit
     * @throws RuntimeException                  when rendering fails
     */
    byte[] render(DocumentContent content);
}
