package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands.RequestLotSeparationCommand;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.SeparationRequestCommandService;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources.RequestSeparationResource;
import com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.transform.SeparationRequestResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lots/{lotId}/separation-requests")
@Tag(name = "Separation requests", description = "Separation of lots from the web portal")
public class SeparationRequestsController {

    private final SeparationRequestCommandService separationRequestCommandService;

    public SeparationRequestsController(SeparationRequestCommandService separationRequestCommandService) {
        this.separationRequestCommandService = separationRequestCommandService;
    }

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Request the separation of a lot (US-19)",
            description = "Buyers only (BUYER), with a valid quotation of the same lot. Answers 201 with status "
                    + "BLOCKED: the lot is held for one hour and the payment voucher can be uploaded with the "
                    + "transactionId as reservationId. A buyer who already holds the lot gets the same request back. "
                    + "Answers 409 LOT_CONFLICT when another operation took the lot first (choose another lot), "
                    + "404 QUOTATION_NOT_FOUND when the quotation is not the buyer's or not of this lot, 404 "
                    + "LOT_NOT_FOUND when the lot is gone, and 422 BUSINESS_RULE_VIOLATION when the quotation "
                    + "expired (simulate again).")
    public ResponseEntity<?> requestSeparation(@PathVariable Long lotId,
                                               @Valid @RequestBody RequestSeparationResource resource) {
        var result = separationRequestCommandService.handle(
                new RequestLotSeparationCommand(lotId, resource.quotationId()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, SeparationRequestResourceAssembler::toResourceFromRequest, HttpStatus.CREATED);
    }
}
