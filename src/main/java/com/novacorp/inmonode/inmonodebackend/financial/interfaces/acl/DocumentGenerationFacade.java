package com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl;

import java.util.List;

/**
 * Open Host Service of the background PDFs (US-45): the other contexts hand over the content of a document and this
 * context, which owns the file repository and the document consumer, generates and stores it. It exposes plain
 * values only.
 */
public interface DocumentGenerationFacade {

    /**
     * Queues the PDF of a buyer's quotation and answers at once; the buyer follows it at
     * {@code /api/v1/document-requests/{requestId}}.
     *
     * @param title a title of at most 200 characters
     * @param lines the body, at most 5000 lines
     * @throws IllegalArgumentException when the title or the lines are out of range
     */
    DocumentRequestReceipt requestQuotationPdf(Long quotationId, Long buyerId, String title, List<String> lines);
}
