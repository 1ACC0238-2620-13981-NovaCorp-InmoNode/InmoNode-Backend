package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.documents.pdf;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentRenderer;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.documents.DocumentRenderingTimeoutException;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentContent;
import com.novacorp.inmonode.inmonodebackend.shared.application.documents.PdfGenerator;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Renders with PDFBox ({@link PdfGenerator}) and gives up after {@code financial.documents.render-timeout}, 30 seconds
 * by default (US-45, Scenario 2). Each rendering runs on its own virtual thread, so one that never ends is
 * interrupted and abandoned without holding up the next document.
 */
@Component
public class TimeLimitedPdfRenderer implements DocumentRenderer {

    private final PdfGenerator generator;
    private final Duration timeout;
    private final ExecutorService renderings = Executors.newVirtualThreadPerTaskExecutor();

    public TimeLimitedPdfRenderer(PdfGenerator generator,
                                  @Value("${financial.documents.render-timeout:PT30S}") Duration timeout) {
        this.generator = generator;
        this.timeout = timeout;
    }

    @Override
    public byte[] render(DocumentContent content) {
        var rendering = renderings.submit(() -> generator.generateReadOnly(content.title(), content.lines()));
        try {
            return rendering.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException tooLong) {
            rendering.cancel(true);
            throw new DocumentRenderingTimeoutException(timeout);
        } catch (ExecutionException failed) {
            throw new IllegalStateException("Could not render the PDF", failed.getCause());
        } catch (InterruptedException interrupted) {
            rendering.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rendering the PDF was interrupted", interrupted);
        }
    }

    @PreDestroy
    void stop() {
        renderings.shutdownNow();
    }
}
