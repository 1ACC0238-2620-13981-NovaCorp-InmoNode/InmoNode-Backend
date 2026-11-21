package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.GenerateDocumentCommand;

/**
 * Outbound port to the queue of the document consumer (US-45): RabbitMQ when a broker is configured, otherwise a
 * worker inside the backend. Either way the caller does not wait for the PDF.
 */
public interface DocumentGenerationQueue {

    /**
     * Queues the generation. Called after the request is saved, so the consumer always finds it.
     *
     * @throws RuntimeException when the queue cannot take it
     */
    void publish(GenerateDocumentCommand command);
}
