package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.documents.queue.rabbitmq;

import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentGenerationQueue;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentRequestStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.DocumentRequestRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentRequestCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * With a broker configured, PDF requests travel through a real RabbitMQ to the consumer inside the backend, and a
 * message that cannot be processed ends in the dead letter queue (US-45; US-37, Scenario 2).
 *
 * <p>The broker URL turns the RabbitMQ queue on through a bean condition, which is evaluated before any
 * {@code DynamicPropertyRegistrar} bean runs; so this RabbitMQ starts with the class and registers its URL here.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class RabbitDocumentQueueIntegrationTest {

    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-alpine");

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void broker(DynamicPropertyRegistry registry) {
        var url = "amqp://%s:%s@%s:%d".formatted(RABBIT.getAdminUsername(), RABBIT.getAdminPassword(),
                RABBIT.getHost(), RABBIT.getAmqpPort());
        registry.add("financial.documents.broker-url", () -> url);
        registry.add("spring.rabbitmq.addresses", () -> url);
    }

    @Autowired
    private DocumentGenerationQueue queue;

    @Autowired
    private DocumentRequestCommandService documents;

    @Autowired
    private DocumentRequestRepository requests;

    @Autowired
    private ObjectStorage objectStorage;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void aRequestTravelsThroughTheBrokerAndItsPdfIsStored() throws Exception {
        assertInstanceOf(RabbitDocumentGenerationQueue.class, queue, "the broker is the queue once it is configured");

        var request = documents.request(DocumentKind.QUOTATION, "501", 41L,
                new DocumentContent("Cotización de financiamiento", List.of("Precio: S/ 45,000.00")));

        assertEquals(DocumentRequestStatus.PROCESSING, request.getStatus());
        var ready = awaitFinished(request);
        assertEquals(DocumentRequestStatus.READY, ready.getStatus());
        assertEquals(ready.getSizeBytes(), objectStorage.describe(ready.getObjectKey()).orElseThrow().sizeBytes());
        assertNotNull(amqpAdmin.getQueueInfo(RabbitDocumentQueueConfiguration.QUEUE));
    }

    @Test
    void aMessageTheConsumerCannotReadIsDeadLettered() {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        var broken = new Message("{\"requestId\": \"not-a-uuid\"".getBytes(StandardCharsets.UTF_8), properties);

        rabbitTemplate.send(RabbitDocumentQueueConfiguration.EXCHANGE, RabbitDocumentQueueConfiguration.ROUTING_KEY, broken);

        var deadLettered = rabbitTemplate.receive(RabbitDocumentQueueConfiguration.DEAD_LETTER_QUEUE,
                Duration.ofSeconds(20).toMillis());
        assertNotNull(deadLettered, "the unreadable message went to the dead letter queue");
        assertArrayEquals(broken.getBody(), deadLettered.getBody());
    }

    private DocumentRequest awaitFinished(DocumentRequest request) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            var current = requests.findById(request.getId()).orElseThrow();
            if (current.getStatus() != DocumentRequestStatus.PROCESSING) return current;
            Thread.sleep(100);
        }
        return fail("document request " + request.getId() + " was still processing after 30 seconds");
    }
}
