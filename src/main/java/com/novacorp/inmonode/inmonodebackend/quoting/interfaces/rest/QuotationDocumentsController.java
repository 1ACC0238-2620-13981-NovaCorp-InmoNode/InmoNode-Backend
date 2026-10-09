package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.queries.DownloadQuotationQuery;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.QuotationDocumentQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/quotations")
@Tag(name = "Quotations")
public class QuotationDocumentsController {
    private final QuotationDocumentQueryService service;
    public QuotationDocumentsController(QuotationDocumentQueryService service) { this.service = service; }

    @GetMapping(value = "/{quotationId}/download")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Download my quotation as a read-only PDF (US-18)",
            description = "Includes saved price, down payment, TEA, term, expiry and the full schedule. "
                    + "An unknown quotation or another buyer's quotation returns 404. PDF permissions disallow editing.")
    public ResponseEntity<?> download(@PathVariable Long quotationId) {
        var response = ResponseEntityAssembler.toResponseEntityFromResult(service.handle(new DownloadQuotationQuery(quotationId)),
                bytes -> bytes, HttpStatus.OK);
        if (!response.getStatusCode().is2xxSuccessful()) return response;
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("quotation-" + quotationId + ".pdf").build().toString()).body(response.getBody());
    }
}
