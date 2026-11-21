package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentGenerationQueue;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.GenerateDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.DocumentRequestRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentRequestCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Records a PDF request and queues it (US-45). Inside a transaction the message goes out only after the commit, so
 * the consumer never looks for a request that is not there yet.
 */
@Service
public class DocumentRequestCommandServiceImpl implements DocumentRequestCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentRequestCommandServiceImpl.class);

    private final DocumentRequestRepository repository;
    private final DocumentGenerationQueue queue;
    private final TransactionTemplate newTransaction;
    private final Clock clock;

    public DocumentRequestCommandServiceImpl(DocumentRequestRepository repository, DocumentGenerationQueue queue,
                                             PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.queue = queue;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public DocumentRequest request(DocumentKind kind, String reference, Long requesterId, DocumentContent content) {
        var request = DocumentRequest.request(kind, reference, requesterId,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        repository.save(request);
        var command = new GenerateDocumentCommand(request.getId(), content);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(request, command);
                }
            });
        } else {
            publish(request, command);
        }
        return request;
    }

    private void publish(DocumentRequest request, GenerateDocumentCommand command) {
        try {
            queue.publish(command);
        } catch (RuntimeException refused) {
            LOG.error("Document request {} could not be queued: {}", request.getId(), refused.getMessage());
            request.fail(DocumentRequest.GENERATION_FAILED, clock.instant().truncatedTo(ChronoUnit.MICROS));
            newTransaction.executeWithoutResult(status -> repository.save(request));
        }
    }
}
