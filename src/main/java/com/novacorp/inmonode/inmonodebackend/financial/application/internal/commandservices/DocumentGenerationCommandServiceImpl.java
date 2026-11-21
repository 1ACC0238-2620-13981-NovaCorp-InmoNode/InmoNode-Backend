package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentRenderer;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentRenderingTimeoutException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.FinancialNotificationOutbox;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.GenerateDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentGenerationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.DocumentRequestRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentGenerationCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The document consumer (US-45). It renders outside any transaction, stores the file, and then records the outcome
 * together with its notice in one transaction, re-reading the request so a concurrent outcome is never overwritten.
 */
@Service
public class DocumentGenerationCommandServiceImpl implements DocumentGenerationCommandService {

    static final String PDF = "application/pdf";
    private static final Logger LOG = LoggerFactory.getLogger(DocumentGenerationCommandServiceImpl.class);

    private final DocumentRequestRepository repository;
    private final DocumentRenderer renderer;
    private final ObjectStorage objectStorage;
    private final FinancialNotificationOutbox notifications;
    private final TransactionOperations transactions;
    private final Clock clock;

    public DocumentGenerationCommandServiceImpl(DocumentRequestRepository repository, DocumentRenderer renderer,
                                                ObjectStorage objectStorage, FinancialNotificationOutbox notifications,
                                                TransactionOperations transactions, Clock clock) {
        this.repository = repository;
        this.renderer = renderer;
        this.objectStorage = objectStorage;
        this.notifications = notifications;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public DocumentGenerationOutcome handle(GenerateDocumentCommand command) {
        var request = repository.findById(command.requestId()).orElse(null);
        if (request == null) {
            LOG.warn("Document request {} is unknown; its message is dropped", command.requestId());
            return DocumentGenerationOutcome.SKIPPED;
        }
        var now = now();
        if (request.isExpired(now)) {
            request.fail(DocumentRequest.GENERATION_EXPIRED, now);
            repository.save(request);
            LOG.warn("Document request {} reached the consumer after its deadline and was not generated", request.getId());
            return DocumentGenerationOutcome.SKIPPED;
        }
        if (!request.isPending(now)) {
            return DocumentGenerationOutcome.SKIPPED;
        }
        try {
            var pdf = renderer.render(command.content());
            objectStorage.store(request.getObjectKey(), PDF, pdf);
            var completedAt = now();
            transactions.executeWithoutResult(status -> repository.findById(request.getId())
                    .filter(current -> current.isPending(completedAt))
                    .ifPresent(current -> {
                        current.complete(pdf.length, completedAt);
                        repository.save(current);
                        if (current.getKind() == DocumentKind.CONTRACT) notifyContractReady(current);
                    }));
            return DocumentGenerationOutcome.GENERATED;
        } catch (DocumentRenderingTimeoutException timeout) {
            return fail(request, DocumentRequest.GENERATION_TIMEOUT, timeout);
        } catch (RuntimeException error) {
            return fail(request, DocumentRequest.GENERATION_FAILED, error);
        }
    }

    /** US-45, Scenario 2: the request fails and the finance back office gets a critical failure notice. */
    private DocumentGenerationOutcome fail(DocumentRequest request, String failureCode, RuntimeException cause) {
        LOG.error("Critical: the PDF of document request {} ({} {}) was not generated: {}", request.getId(),
                request.getKind(), request.getReference(), failureCode, cause);
        var failedAt = now();
        transactions.executeWithoutResult(status -> repository.findById(request.getId())
                .filter(current -> current.isPending(failedAt))
                .ifPresent(current -> {
                    current.fail(failureCode, failedAt);
                    repository.save(current);
                    notifications.enqueue("document-failed:" + current.getId(), "@finance",
                            "Fallo crítico al generar un PDF",
                            "No se pudo generar el PDF solicitado.\nTipo: " + current.getKind()
                                    + "\nReferencia: " + current.getReference() + "\nSolicitud: " + current.getId()
                                    + "\nMotivo: " + failureCode + "\nDetalle: " + cause.getMessage()
                                    + "\nLa solicitud quedó como fallida; puede pedirse de nuevo.");
                }));
        return DocumentGenerationOutcome.FAILED;
    }

    private void notifyContractReady(DocumentRequest request) {
        notifications.enqueue("document-ready:" + request.getId(), "@finance", "Contrato listo para emitir",
                "El contrato preliminar de la separación " + request.getReference() + " se generó y puede emitirse."
                        + "\nSolicitud: " + request.getId()
                        + "\nEmítelo con documentId " + request.getId() + " y sizeBytes " + request.getSizeBytes() + ".");
    }

    private Instant now() {
        // PostgreSQL keeps microseconds: the dates answered now must equal the ones read back later.
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
