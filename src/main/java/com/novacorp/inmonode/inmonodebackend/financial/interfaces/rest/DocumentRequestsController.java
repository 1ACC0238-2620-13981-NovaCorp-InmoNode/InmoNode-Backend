package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetDocumentRequestQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentRequestQueryService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.DocumentRequestResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The PDFs the backend generates in the background (US-45): a request answers 202 at once, and its client reads it
 * here until it is ready.
 */
@RestController
@RequestMapping("/api/v1/document-requests")
@Tag(name = "Documents", description = "PDFs generated in the background")
public class DocumentRequestsController {

    private final DocumentRequestQueryService documentRequestQueryService;

    public DocumentRequestsController(DocumentRequestQueryService documentRequestQueryService) {
        this.documentRequestQueryService = documentRequestQueryService;
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAnyRole('BUYER', 'FINANCE_ADMIN')")
    @Operation(summary = "Read a PDF requested in the background (US-45)",
            description = "PROCESSING means the document consumer has not finished; ask again in a few seconds. "
                    + "READY comes with downloadUrl, valid for 10 minutes, and the size of the PDF; a ready contract "
                    + "is issued with requestId as documentId and that sizeBytes. FAILED comes with failureCode: "
                    + "DOCUMENT_GENERATION_TIMEOUT (rendering took over 30 seconds), DOCUMENT_GENERATION_FAILED or "
                    + "DOCUMENT_GENERATION_EXPIRED (still processing after 10 minutes); request the PDF again. Only "
                    + "whoever asked for it reads it, and the finance back office reads every contract; anyone else "
                    + "gets 404 DOCUMENT_REQUEST_NOT_FOUND.")
    public ResponseEntity<?> get(@PathVariable UUID requestId) {
        return ResponseEntityAssembler.toResponseEntityFromResult(
                documentRequestQueryService.handle(new GetDocumentRequestQuery(requestId)),
                DocumentRequestResourceAssembler::toResourceFromView, HttpStatus.OK);
    }
}
