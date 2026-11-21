package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.documents.pdf;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentRenderingTimeoutException;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.shared.infrastructure.documents.PdfBoxGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** US-45, Scenario 2: a rendering that never ends is aborted at its limit. */
class TimeLimitedPdfRendererTest {

    private static final DocumentContent CONTENT = new DocumentContent("Cotización", List.of("Precio: S/ 45,000.00"));

    private TimeLimitedPdfRenderer renderer;

    @AfterEach
    void stop() {
        renderer.stop();
    }

    @Test
    void aNormalDocumentIsRenderedWithPdfBox() {
        renderer = new TimeLimitedPdfRenderer(new PdfBoxGenerator(), Duration.ofSeconds(30));

        var pdf = renderer.render(CONTENT);

        assertTrue(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
    }

    @Test
    void aRenderingThatLoopsForeverIsAbortedAtItsLimit() {
        renderer = new TimeLimitedPdfRenderer((title, lines) -> {
            while (true) {
                Thread.onSpinWait();
                if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("interrupted");
            }
        }, Duration.ofMillis(300));
        var start = System.nanoTime();

        var error = assertThrows(DocumentRenderingTimeoutException.class, () -> renderer.render(CONTENT));

        var elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertTrue(elapsed.compareTo(Duration.ofSeconds(5)) < 0, "gave up after " + elapsed);
        assertTrue(error.getMessage().contains("aborted"));
    }

    @Test
    void aBrokenTemplateFailsWithItsCause() {
        renderer = new TimeLimitedPdfRenderer((title, lines) -> {
            throw new IllegalArgumentException("corrupt template");
        }, Duration.ofSeconds(30));

        var error = assertThrows(IllegalStateException.class, () -> renderer.render(CONTENT));

        assertEquals("corrupt template", error.getCause().getMessage());
    }
}
