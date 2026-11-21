package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.documents.queue;

import com.novacorp.inmonode.inmonodebackend.S3TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentRequestStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.DocumentRequestRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentRequestCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Without a message broker, the worker inside the backend renders the PDF of a request and stores it in a real
 * S3-compatible storage (US-45, Scenario 1).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
class InProcessDocumentGenerationIntegrationTest {

    @Autowired
    private DocumentRequestCommandService documents;

    @Autowired
    private DocumentRequestRepository requests;

    @Autowired
    private ObjectStorage objectStorage;

    @Test
    void theRequestAnswersAtOnceAndTheConsumerStoresAReadOnlyPdf() throws Exception {
        var lines = new ArrayList<String>();
        for (int line = 1; line <= 400; line++) lines.add("Cláusula %d del contrato de prueba.".formatted(line));
        var reference = UUID.randomUUID().toString();

        var request = documents.request(DocumentKind.CONTRACT, reference, 77L,
                new DocumentContent("Contrato preliminar de compraventa", lines));

        assertEquals(DocumentRequestStatus.PROCESSING, request.getStatus());
        var ready = awaitFinished(request);
        assertEquals(DocumentRequestStatus.READY, ready.getStatus());
        var stored = objectStorage.describe("contracts/%s/%s.pdf".formatted(reference, request.getId())).orElseThrow();
        assertEquals(ready.getSizeBytes(), stored.sizeBytes());
        assertEquals("application/pdf", stored.contentType());
        var pdf = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        objectStorage.presignDownload(ready.getObjectKey(), Duration.ofMinutes(1)).url()).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body();
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
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
