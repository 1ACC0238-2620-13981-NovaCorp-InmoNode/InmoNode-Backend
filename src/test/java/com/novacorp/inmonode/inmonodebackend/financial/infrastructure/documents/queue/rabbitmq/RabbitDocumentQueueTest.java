package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.documents.queue.rabbitmq;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentGenerationQueue;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.GenerateDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentGenerationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentGenerationCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;

import java.net.ConnectException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The RabbitMQ side of US-45 without a broker: fallback on publish and dead-lettering on failure. */
class RabbitDocumentQueueTest {

    private static final GenerateDocumentCommand COMMAND = new GenerateDocumentCommand(UUID.randomUUID(),
            new DocumentContent("Cotización", List.of("Precio: S/ 45,000.00")));

    private final AmqpTemplate amqp = mock(AmqpTemplate.class);
    private final DocumentGenerationQueue fallback = mock(DocumentGenerationQueue.class);
    private final DocumentGenerationCommandService service = mock(DocumentGenerationCommandService.class);

    @Test
    void aRequestIsPublishedAsJsonToTheDocumentsExchange() {
        new RabbitDocumentGenerationQueue(amqp, fallback).publish(COMMAND);

        verify(amqp).convertAndSend(RabbitDocumentQueueConfiguration.EXCHANGE, RabbitDocumentQueueConfiguration.ROUTING_KEY,
                new DocumentGenerationMessage(COMMAND.requestId(), "Cotización", List.of("Precio: S/ 45,000.00")));
        verifyNoInteractions(fallback);
    }

    @Test
    void whenTheBrokerIsUnreachableTheInProcessConsumerTakesTheRequest() {
        doThrow(new AmqpConnectException(new ConnectException("refused"))).when(amqp)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        new RabbitDocumentGenerationQueue(amqp, fallback).publish(COMMAND);

        verify(fallback).publish(COMMAND);
    }

    @Test
    void aMessageWhosePdfFailsIsRejectedWithoutRequeueSoItIsDeadLettered() {
        var consumer = new RabbitDocumentConsumer(service);
        var message = DocumentGenerationMessage.from(COMMAND);
        when(service.handle(COMMAND)).thenReturn(DocumentGenerationOutcome.FAILED);

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.consume(message));

        when(service.handle(COMMAND)).thenReturn(DocumentGenerationOutcome.SKIPPED);
        assertDoesNotThrow(() -> consumer.consume(message));
    }
}
