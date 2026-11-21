package com.novacorp.inmonode.inmonodebackend.quoting.application.internal.outboundservices.acl;

import com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl.DocumentGenerationFacade;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.QuotationDocumentRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Anti-corruption layer towards Control Financiero y Documental, which generates and stores the PDFs in the
 * background (US-45); this context only writes what the quotation says.
 */
@Service
public class ExternalDocumentGenerationService {

    private final DocumentGenerationFacade documentGenerationFacade;

    public ExternalDocumentGenerationService(DocumentGenerationFacade documentGenerationFacade) {
        this.documentGenerationFacade = documentGenerationFacade;
    }

    /** Queues the PDF of the buyer's quotation and answers at once with its request. */
    public QuotationDocumentRequest requestPdf(Long quotationId, Long buyerId, String title, List<String> lines) {
        var receipt = documentGenerationFacade.requestQuotationPdf(quotationId, buyerId, title, lines);
        return new QuotationDocumentRequest(receipt.requestId(), quotationId, receipt.status(), receipt.requestedAt(),
                receipt.failureCode());
    }
}
