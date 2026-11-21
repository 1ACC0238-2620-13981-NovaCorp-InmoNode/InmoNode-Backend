package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.documents.queue.rabbitmq;

import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentGenerationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.documents.queue.InProcessDocumentGenerationQueue;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * RabbitMQ as the queue of the background PDFs (US-45), on only when {@code financial.documents.broker-url} is set
 * ({@code CLOUDAMQP_URL}). Without it no connection is ever opened and the worker inside the backend is the queue.
 *
 * <p>Requests go through the direct exchange {@value #EXCHANGE} to the durable queue {@value #QUEUE}; messages that
 * fail are dead-lettered to {@value #DEAD_LETTER_QUEUE} for the back office to inspect.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${financial.documents.broker-url:}' != ''")
public class RabbitDocumentQueueConfiguration {

    public static final String EXCHANGE = "inmonode.documents";
    public static final String QUEUE = "inmonode.documents.pdf";
    public static final String ROUTING_KEY = "pdf.requested";
    public static final String DEAD_LETTER_EXCHANGE = "inmonode.documents.dlx";
    public static final String DEAD_LETTER_QUEUE = "inmonode.documents.pdf.dlq";
    static final String DEAD_LETTER_ROUTING_KEY = "pdf.failed";
    static final String CONTAINER_FACTORY = "documentListenerContainerFactory";

    @Bean
    DirectExchange documentsExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    Queue documentsQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding documentsBinding() {
        return BindingBuilder.bind(documentsQueue()).to(documentsExchange()).with(ROUTING_KEY);
    }

    @Bean
    DirectExchange documentsDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    Queue documentsDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding documentsDeadLetterBinding() {
        return BindingBuilder.bind(documentsDeadLetterQueue()).to(documentsDeadLetterExchange())
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    /** JSON on the wire; Spring Boot's RabbitTemplate uses it too. */
    @Bean
    MessageConverter documentMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /** One consumer taking one message at a time; a rejected message is dead-lettered, never requeued. */
    @Bean(CONTAINER_FACTORY)
    SimpleRabbitListenerContainerFactory documentListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                          MessageConverter documentMessageConverter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(documentMessageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    @Primary
    RabbitDocumentGenerationQueue rabbitDocumentGenerationQueue(AmqpTemplate amqpTemplate,
                                                                InProcessDocumentGenerationQueue fallback) {
        return new RabbitDocumentGenerationQueue(amqpTemplate, fallback);
    }

    @Bean
    RabbitDocumentConsumer rabbitDocumentConsumer(DocumentGenerationCommandService consumer) {
        return new RabbitDocumentConsumer(consumer);
    }
}
