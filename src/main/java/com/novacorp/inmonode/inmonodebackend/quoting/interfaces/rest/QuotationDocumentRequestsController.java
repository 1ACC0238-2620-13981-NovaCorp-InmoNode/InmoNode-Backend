package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands.RequestQuotationDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.QuotationDocumentRequest;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.QuotationDocumentCommandService;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources.QuotationDocumentRequestResource;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotations")
@Tag(name = "Quotations")
public class QuotationDocumentRequestsController {

    private final QuotationDocumentCommandService service;

    public QuotationDocumentRequestsController(QuotationDocumentCommandService service) {
        this.service = service;
    }

    @PostMapping("/{quotationId}/document-requests")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Have the PDF of my quotation generated in the background (US-45)",
            description = "Answers 202 at once with the request PROCESSING (\"En proceso\"); the document consumer "
                    + "generates the same read-only PDF as the download and stores it. Read statusUrl until it is "
                    + "READY, with a download link valid for 10 minutes, or FAILED with its failureCode. An unknown "
                    + "quotation or another buyer's returns 404 QUOTATION_NOT_FOUND.")
    public ResponseEntity<?> request(@PathVariable Long quotationId) {
        return ResponseEntityAssembler.toResponseEntityFromResult(
                service.handle(new RequestQuotationDocumentCommand(quotationId)),
                QuotationDocumentRequestsController::toResource, HttpStatus.ACCEPTED);
    }

    private static QuotationDocumentRequestResource toResource(QuotationDocumentRequest request) {
        return new QuotationDocumentRequestResource(request.requestId(), request.quotationId(), request.status(),
                request.requestedAt(), request.failureCode(), "/api/v1/document-requests/" + request.requestId());
    }
}
