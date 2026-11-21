package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jdbc;

import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentRequestStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.DocumentRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Document requests (US-45) are written and read back from a real PostgreSQL with the V4_15 table. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JdbcDocumentRequestRepositoryIntegrationTest {

    @Autowired
    private DocumentRequestRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aNewRequestIsReadBackAsItWasSaved() {
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var request = DocumentRequest.request(DocumentKind.CONTRACT, UUID.randomUUID().toString(), 77L, now);

        repository.save(request);

        var found = repository.findById(request.getId()).orElseThrow();
        assertEquals(DocumentKind.CONTRACT, found.getKind());
        assertEquals(request.getReference(), found.getReference());
        assertEquals(77L, found.getRequestedBy());
        assertEquals(request.getObjectKey(), found.getObjectKey());
        assertEquals(now, found.getRequestedAt());
        assertEquals(DocumentRequestStatus.PROCESSING, found.getStatus());
        assertNull(found.getSizeBytes());
        assertNull(found.getCompletedAt());
    }

    @Test
    void savingAgainWritesTheOutcome() {
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var ready = DocumentRequest.request(DocumentKind.QUOTATION, "15", 41L, now);
        var failed = DocumentRequest.request(DocumentKind.QUOTATION, "16", 41L, now);
        repository.save(ready);
        repository.save(failed);

        ready.complete(4096, now.plusSeconds(2));
        failed.fail(DocumentRequest.GENERATION_TIMEOUT, now.plusSeconds(30));
        repository.save(ready);
        repository.save(failed);

        var readyAgain = repository.findById(ready.getId()).orElseThrow();
        assertEquals(DocumentRequestStatus.READY, readyAgain.getStatus());
        assertEquals(4096L, readyAgain.getSizeBytes());
        assertEquals(now.plusSeconds(2), readyAgain.getCompletedAt());
        var failedAgain = repository.findById(failed.getId()).orElseThrow();
        assertEquals(DocumentRequestStatus.FAILED, failedAgain.getStatus());
        assertEquals(DocumentRequest.GENERATION_TIMEOUT, failedAgain.getFailureCode());
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void theTableRefusesAReadyRequestWithoutItsSize() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO financial_document_control.document_requests
                    (id, kind, reference, requested_by, object_key, status, requested_at, completed_at)
                VALUES (?, 'QUOTATION', '1', 1, 'quotations/1/x.pdf', 'READY', now(), now())
                """, UUID.randomUUID()));
    }
}
