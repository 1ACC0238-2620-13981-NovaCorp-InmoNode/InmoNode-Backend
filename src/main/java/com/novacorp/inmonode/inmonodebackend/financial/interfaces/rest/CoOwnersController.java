package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.AddCoOwnerCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.CoOwner;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.CoOwnerCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.CoOwnerResource;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Contracts")
public class CoOwnersController {
    private final CoOwnerCommandService service;
    public CoOwnersController(CoOwnerCommandService service) { this.service = service; }

    @PostMapping("/{transactionId}/co-owner")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Designate or update my co-owner before contract issuance (US-28)",
            description = "Own web reservation only. DNI: 8 digits; RUC: 11 digits; CE/PASSPORT: 6-20 alphanumeric characters. "
                    + "After issuance returns 409 and requires a legal addendum through customer service.")
    public ResponseEntity<?> add(@PathVariable UUID transactionId, @Valid @RequestBody CoOwnerResource resource) {
        return ResponseEntityAssembler.toResponseEntityFromResult(service.handle(new AddCoOwnerCommand(transactionId,
                new CoOwner(resource.fullName(), resource.documentType(), resource.documentNumber()))),
                value -> new CoOwnerResource(value.fullName(), value.documentType(), value.documentNumber()), HttpStatus.OK);
    }
}
