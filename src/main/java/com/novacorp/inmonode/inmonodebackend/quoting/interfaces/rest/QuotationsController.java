package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.queries.GetQuotationQuery;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.QuotationCommandService;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.QuotationQueryService;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources.QuotationResource;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources.SimulateFinancingResource;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.transform.QuotationResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.transform.SimulateFinancingCommandFromResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.resources.ErrorResource;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Quotations", description = "Financing simulations of the lots for buyers")
public class QuotationsController {

    private final QuotationCommandService quotationCommandService;
    private final QuotationQueryService quotationQueryService;

    public QuotationsController(QuotationCommandService quotationCommandService,
                                QuotationQueryService quotationQueryService) {
        this.quotationCommandService = quotationCommandService;
        this.quotationQueryService = quotationQueryService;
    }

    @PostMapping("/lots/{lotId}/quotations")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Simulate the financing of a lot (US-17)",
            description = "Buyers only (BUYER). Projects a schedule of fixed monthly installments (French "
                    + "amortization) with the rate of the lot's project, and keeps it valid for 7 days to back a "
                    + "separation request. Answers 400 VALIDATION_ERROR when the down payment is under the project's "
                    + "minimum share of the price (the minimum amount is in details), covers the whole price, or the "
                    + "term exceeds the project's maximum; 404 LOT_NOT_FOUND when the lot does not exist or its "
                    + "project is not published; 409 LOT_CONFLICT when the lot is not available.")
    @ApiResponse(responseCode = "201", description = "Quotation created with its schedule of installments",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuotationResource.class)))
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: down payment under the minimum or covering the "
            + "whole price, or term over the project's maximum; details names the rule broken",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid or expired token",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: the token's role is not BUYER",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    @ApiResponse(responseCode = "404", description = "LOT_NOT_FOUND: the lot does not exist or its project is not published",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    @ApiResponse(responseCode = "409", description = "LOT_CONFLICT: the lot is not available",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    public ResponseEntity<?> simulate(@PathVariable Long lotId, @Valid @RequestBody SimulateFinancingResource resource) {
        var result = quotationCommandService.handle(
                SimulateFinancingCommandFromResourceAssembler.toCommandFromResource(lotId, resource));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, QuotationResourceAssembler::toResourceFromQuotation, HttpStatus.CREATED);
    }

    @GetMapping("/quotations/{quotationId}")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Get one of the buyer's quotations with its schedule",
            description = "Buyers only (BUYER). A quotation of another buyer answers 404 QUOTATION_NOT_FOUND, like a "
                    + "missing one.")
    @ApiResponse(responseCode = "200", description = "The quotation with its schedule of installments",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuotationResource.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: missing, invalid or expired token",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: the token's role is not BUYER",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    @ApiResponse(responseCode = "404", description = "QUOTATION_NOT_FOUND: missing, or it belongs to another buyer",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResource.class)))
    public ResponseEntity<?> getQuotation(@PathVariable Long quotationId) {
        var result = quotationQueryService.handle(new GetQuotationQuery(quotationId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, QuotationResourceAssembler::toResourceFromQuotation, HttpStatus.OK);
    }
}
