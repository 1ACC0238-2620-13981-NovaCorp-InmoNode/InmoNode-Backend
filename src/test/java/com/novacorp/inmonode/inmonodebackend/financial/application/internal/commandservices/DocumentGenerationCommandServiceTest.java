package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentGenerationQueue;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentRenderer;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentRenderingTimeoutException;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.FinancialNotificationOutbox;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.GenerateDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentGenerationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentRequestStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.DocumentRequestRepository;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The document consumer and the request side of US-45, with the storage, renderer and queue replaced. */
class DocumentGenerationCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T15:00:00Z");
    private static final DocumentContent CONTENT = new DocumentContent("Contrato preliminar", List.of("Lote C-01"));
    private static final byte[] PDF = "%PDF-1.7 generated".getBytes();

    private final InMemoryDocumentRequests requests = new InMemoryDocumentRequests();
    private final DocumentRenderer renderer = mock(DocumentRenderer.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final FinancialNotificationOutbox notifications = mock(FinancialNotificationOutbox.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final DocumentGenerationCommandServiceImpl consumer = new DocumentGenerationCommandServiceImpl(requests,
            renderer, storage, notifications, TransactionOperations.withoutTransaction(), clock);

    @Test
    void theConsumerStoresThePdfAndTellsFinanceThatTheContractCanBeIssued() {
        var request = saved(DocumentKind.CONTRACT);
        when(renderer.render(CONTENT)).thenReturn(PDF);

        assertEquals(DocumentGenerationOutcome.GENERATED, consumer.handle(new GenerateDocumentCommand(request.getId(), CONTENT)));

        verify(storage).store(request.getObjectKey(), "application/pdf", PDF);
        var ready = requests.findById(request.getId()).orElseThrow();
        assertEquals(DocumentRequestStatus.READY, ready.getStatus());
        assertEquals((long) PDF.length, ready.getSizeBytes());
        verify(notifications).enqueue(eq("document-ready:" + request.getId()), eq("@finance"),
                eq("Contrato listo para emitir"), contains("sizeBytes " + PDF.length));
    }

    @Test
    void aReadyQuotationNotifiesNobody() {
        var request = saved(DocumentKind.QUOTATION);
        when(renderer.render(CONTENT)).thenReturn(PDF);

        consumer.handle(new GenerateDocumentCommand(request.getId(), CONTENT));

        assertEquals(DocumentRequestStatus.READY, requests.findById(request.getId()).orElseThrow().getStatus());
        verifyNoInteractions(notifications);
    }

    @Test
    void aRenderingOverItsLimitFailsTheRequestWithACriticalNotice() {
        var request = saved(DocumentKind.CONTRACT);
        when(renderer.render(CONTENT)).thenThrow(new DocumentRenderingTimeoutException(Duration.ofSeconds(30)));

        assertEquals(DocumentGenerationOutcome.FAILED, consumer.handle(new GenerateDocumentCommand(request.getId(), CONTENT)));

        var failed = requests.findById(request.getId()).orElseThrow();
        assertEquals(DocumentRequestStatus.FAILED, failed.getStatus());
        assertEquals(DocumentRequest.GENERATION_TIMEOUT, failed.getFailureCode());
        verify(storage, never()).store(any(), any(), any());
        verify(notifications).enqueue(eq("document-failed:" + request.getId()), eq("@finance"),
                eq("Fallo crítico al generar un PDF"), contains(DocumentRequest.GENERATION_TIMEOUT));
    }

    @Test
    void aStorageThatIsDownFailsTheRequestToo() {
        var request = saved(DocumentKind.QUOTATION);
        when(renderer.render(CONTENT)).thenReturn(PDF);
        doThrow(new IllegalStateException("storage down")).when(storage).store(any(), any(), any());

        assertEquals(DocumentGenerationOutcome.FAILED, consumer.handle(new GenerateDocumentCommand(request.getId(), CONTENT)));

        assertEquals(DocumentRequest.GENERATION_FAILED, requests.findById(request.getId()).orElseThrow().getFailureCode());
        verify(notifications).enqueue(eq("document-failed:" + request.getId()), eq("@finance"), any(),
                contains("storage down"));
    }

    @Test
    void aRedeliveredMessageOfAFinishedRequestDoesNothing() {
        var request = saved(DocumentKind.QUOTATION);
        request.complete(10, NOW);
        requests.save(request);

        assertEquals(DocumentGenerationOutcome.SKIPPED, consumer.handle(new GenerateDocumentCommand(request.getId(), CONTENT)));
        assertEquals(DocumentGenerationOutcome.SKIPPED,
                consumer.handle(new GenerateDocumentCommand(UUID.randomUUID(), CONTENT)));

        verifyNoInteractions(renderer, storage, notifications);
    }

    @Test
    void aMessageThatArrivesAfterTheDeadlineExpiresItsRequestWithoutRendering() {
        var request = DocumentRequest.request(DocumentKind.QUOTATION, "42", 9L,
                NOW.minus(DocumentRequest.PROCESSING_DEADLINE).minusSeconds(1));
        requests.save(request);

        assertEquals(DocumentGenerationOutcome.SKIPPED, consumer.handle(new GenerateDocumentCommand(request.getId(), CONTENT)));

        var expired = requests.findById(request.getId()).orElseThrow();
        assertEquals(DocumentRequestStatus.FAILED, expired.getStatus());
        assertEquals(DocumentRequest.GENERATION_EXPIRED, expired.getFailureCode());
        verifyNoInteractions(renderer);
    }

    @Test
    void aRequestIsSavedProcessingAndItsContentIsQueued() {
        var queue = mock(DocumentGenerationQueue.class);
        var service = new DocumentRequestCommandServiceImpl(requests, queue, new NoTransactions(), clock);

        var request = service.request(DocumentKind.CONTRACT, "tx-1", 77L, CONTENT);

        assertEquals(DocumentRequestStatus.PROCESSING, request.getStatus());
        assertEquals(NOW, request.getRequestedAt());
        assertTrue(requests.findById(request.getId()).isPresent());
        verify(queue).publish(new GenerateDocumentCommand(request.getId(), CONTENT));
    }

    @Test
    void aRequestTheQueueRefusesComesBackFailed() {
        var queue = mock(DocumentGenerationQueue.class);
        doThrow(new RejectedExecutionException("full")).when(queue).publish(any());
        var service = new DocumentRequestCommandServiceImpl(requests, queue, new NoTransactions(), clock);

        var request = service.request(DocumentKind.QUOTATION, "42", 9L, CONTENT);

        assertEquals(DocumentRequestStatus.FAILED, request.getStatus());
        assertEquals(DocumentRequestStatus.FAILED, requests.findById(request.getId()).orElseThrow().getStatus());
    }

    private DocumentRequest saved(DocumentKind kind) {
        var request = DocumentRequest.request(kind, kind == DocumentKind.CONTRACT ? UUID.randomUUID().toString() : "42",
                7L, NOW.minusSeconds(2));
        requests.save(request);
        return request;
    }

    /** Keeps copies, as a database would: changing a request does nothing until it is saved again. */
    private static final class InMemoryDocumentRequests implements DocumentRequestRepository {
        private final Map<UUID, DocumentRequest> rows = new HashMap<>();

        @Override
        public void save(DocumentRequest request) {
            rows.put(request.getId(), copy(request));
        }

        @Override
        public Optional<DocumentRequest> findById(UUID id) {
            return Optional.ofNullable(rows.get(id)).map(InMemoryDocumentRequests::copy);
        }

        private static DocumentRequest copy(DocumentRequest r) {
            return DocumentRequest.restore(r.getId(), r.getKind(), r.getReference(), r.getRequestedBy(),
                    r.getObjectKey(), r.getRequestedAt(), r.getStatus(), r.getSizeBytes(), r.getFailureCode(),
                    r.getCompletedAt());
        }
    }

    private static final class NoTransactions implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
