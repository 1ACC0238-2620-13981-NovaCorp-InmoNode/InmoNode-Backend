package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.documents.queue.rabbitmq;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentGenerationQueue;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.GenerateDocumentCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;

/**
 * Publishes PDF requests to RabbitMQ (US-45). When the broker cannot take a message, e.g. it is unreachable, the
 * worker inside the backend takes it instead, so a request is never lost to a broker outage.
 */
public class RabbitDocumentGenerationQueue implements DocumentGenerationQueue {

    private static final Logger LOG = LoggerFactory.getLogger(RabbitDocumentGenerationQueue.class);

    private final AmqpTemplate amqpTemplate;
    private final DocumentGenerationQueue fallback;

    public RabbitDocumentGenerationQueue(AmqpTemplate amqpTemplate, DocumentGenerationQueue fallback) {
        this.amqpTemplate = amqpTemplate;
        this.fallback = fallback;
    }

    @Override
    public void publish(GenerateDocumentCommand command) {
        try {
            amqpTemplate.convertAndSend(RabbitDocumentQueueConfiguration.EXCHANGE,
                    RabbitDocumentQueueConfiguration.ROUTING_KEY, DocumentGenerationMessage.from(command));
        } catch (AmqpException unavailable) {
            LOG.warn("The message broker did not take document request {}; the in-process consumer will: {}",
                    command.requestId(), unavailable.getMessage());
            fallback.publish(command);
        }
    }
}
