package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentRequestStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentRequestTest {

    private static final Instant NOW = Instant.parse("2026-11-21T15:00:00Z");

    @Test
    void aNewRequestIsProcessingAndItsFileGoesUnderItsKindFolder() {
        var transactionId = UUID.randomUUID().toString();

        var contract = DocumentRequest.request(DocumentKind.CONTRACT, transactionId, 7L, NOW);
        var quotation = DocumentRequest.request(DocumentKind.QUOTATION, "42", 9L, NOW);

        assertEquals(DocumentRequestStatus.PROCESSING, contract.getStatus());
        assertEquals("contracts/%s/%s.pdf".formatted(transactionId, contract.getId()), contract.getObjectKey());
        assertEquals("quotations/42/%s.pdf".formatted(quotation.getId()), quotation.getObjectKey());
        assertNotEquals(contract.getId(), quotation.getId());
        assertTrue(contract.isPending(NOW));
        assertNull(contract.getCompletedAt());
    }

    @Test
    void completingKeepsTheSizeOfTheStoredFile() {
        var request = DocumentRequest.request(DocumentKind.QUOTATION, "42", 9L, NOW);

        request.complete(2048, NOW.plusSeconds(3));

        assertEquals(DocumentRequestStatus.READY, request.getStatus());
        assertEquals(2048L, request.getSizeBytes());
        assertEquals(NOW.plusSeconds(3), request.getCompletedAt());
        assertNull(request.getFailureCode());
        assertFalse(request.isPending(NOW.plusSeconds(3)));
    }

    @Test
    void failingKeepsTheReasonAndTheOutcomeIsFinal() {
        var request = DocumentRequest.request(DocumentKind.CONTRACT, UUID.randomUUID().toString(), 7L, NOW);

        request.fail(DocumentRequest.GENERATION_TIMEOUT, NOW.plusSeconds(30));

        assertEquals(DocumentRequestStatus.FAILED, request.getStatus());
        assertEquals(DocumentRequest.GENERATION_TIMEOUT, request.getFailureCode());
        assertThrows(IllegalStateException.class, () -> request.complete(10, NOW.plusSeconds(31)));
        assertThrows(IllegalStateException.class, () -> request.fail(DocumentRequest.GENERATION_FAILED, NOW));
    }

    @Test
    void aRequestStillProcessingAfterItsDeadlineReadsAsExpired() {
        var request = DocumentRequest.request(DocumentKind.QUOTATION, "42", 9L, NOW);
        var deadline = NOW.plus(DocumentRequest.PROCESSING_DEADLINE);

        assertEquals(DocumentRequestStatus.PROCESSING, request.statusAt(deadline));
        assertTrue(request.isPending(deadline));

        var later = deadline.plus(Duration.ofSeconds(1));
        assertTrue(request.isExpired(later));
        assertFalse(request.isPending(later));
        assertEquals(DocumentRequestStatus.FAILED, request.statusAt(later));
        assertEquals(DocumentRequest.GENERATION_EXPIRED, request.failureCodeAt(later));
    }

    @Test
    void aCompletedRequestNeverExpires() {
        var request = DocumentRequest.request(DocumentKind.QUOTATION, "42", 9L, NOW);
        request.complete(100, NOW.plusSeconds(1));

        var muchLater = NOW.plus(Duration.ofDays(2));
        assertFalse(request.isExpired(muchLater));
        assertEquals(DocumentRequestStatus.READY, request.statusAt(muchLater));
        assertNull(request.failureCodeAt(muchLater));
    }

    @Test
    void aRequestNeedsAReferenceAndAFileOfAtLeastOneByte() {
        assertThrows(IllegalArgumentException.class, () -> DocumentRequest.request(DocumentKind.CONTRACT, " ", 7L, NOW));
        var request = DocumentRequest.request(DocumentKind.QUOTATION, "42", 9L, NOW);
        assertThrows(IllegalArgumentException.class, () -> request.complete(0, NOW));
        assertEquals(DocumentRequestStatus.PROCESSING, request.getStatus());
    }

    @Test
    void aDocumentHasATitleAndAtMostFiveThousandLines() {
        var lines = new ArrayList<String>();
        lines.add("Precio: S/ 45,000.00");
        var content = new DocumentContent("  Contrato preliminar  ", lines);
        lines.add("changed afterwards");

        assertEquals("Contrato preliminar", content.title());
        assertEquals(List.of("Precio: S/ 45,000.00"), content.lines());
        assertThrows(IllegalArgumentException.class, () -> new DocumentContent(" ", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentContent("T", java.util.Collections.nCopies(DocumentContent.MAX_LINES + 1, "x")));
    }
}
