package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;

/**
 * Asks for a PDF without waiting for it (US-45): records the request and queues it for the document consumer.
 */
public interface DocumentRequestCommandService {

    /**
     * Records a request of {@code requesterId} for the PDF of {@code reference} and queues its content. Answers at
     * once with the request, processing; when the queue refuses it, the request comes back failed.
     */
    DocumentRequest request(DocumentKind kind, String reference, Long requesterId, DocumentContent content);
}
