package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.documents.queue.rabbitmq;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentGenerationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentGenerationCommandService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * The document consumer on RabbitMQ (US-45). It runs inside the backend's process with a concurrency of 1: a free
 * Render instance sleeps without HTTP traffic, so a separate consumer would leave the queue unattended. A message
 * whose PDF fails, or that cannot be read, is not requeued: it goes to the dead letter queue.
 */
public class RabbitDocumentConsumer {

    private final DocumentGenerationCommandService consumer;

    public RabbitDocumentConsumer(DocumentGenerationCommandService consumer) {
        this.consumer = consumer;
    }

    @RabbitListener(queues = RabbitDocumentQueueConfiguration.QUEUE,
            containerFactory = RabbitDocumentQueueConfiguration.CONTAINER_FACTORY)
    public void consume(DocumentGenerationMessage message) {
        if (consumer.handle(message.toCommand()) == DocumentGenerationOutcome.FAILED) {
            throw new AmqpRejectAndDontRequeueException(
                    "The PDF of document request %s failed".formatted(message.requestId()));
        }
    }
}
