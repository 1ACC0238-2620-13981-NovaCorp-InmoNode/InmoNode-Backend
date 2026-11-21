package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.documents.queue;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentGenerationQueue;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.GenerateDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentGenerationCommandService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The document queue when no message broker is configured, and the fallback when the broker cannot take a message:
 * one worker thread inside the backend, like the broker consumer's concurrency of 1 (US-45). Messages live in
 * memory, so a restart loses the ones waiting; their requests then expire after 10 minutes and can be asked again.
 */
@Component
public class InProcessDocumentGenerationQueue implements DocumentGenerationQueue {

    /** Messages waiting at most; beyond it the request fails at once instead of waiting without end. */
    static final int CAPACITY = 100;
    private static final Logger LOG = LoggerFactory.getLogger(InProcessDocumentGenerationQueue.class);

    private final DocumentGenerationCommandService consumer;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(CAPACITY), runnable -> {
                var thread = new Thread(runnable, "document-consumer");
                thread.setDaemon(true);
                return thread;
            });

    public InProcessDocumentGenerationQueue(DocumentGenerationCommandService consumer) {
        this.consumer = consumer;
    }

    /**
     * @throws java.util.concurrent.RejectedExecutionException when {@link #CAPACITY} messages are already waiting
     */
    @Override
    public void publish(GenerateDocumentCommand command) {
        worker.execute(() -> {
            try {
                consumer.handle(command);
            } catch (RuntimeException unexpected) {
                LOG.error("Document request {} broke the in-process consumer", command.requestId(), unexpected);
            }
        });
    }

    @PreDestroy
    void stop() {
        worker.shutdown();
    }
}
