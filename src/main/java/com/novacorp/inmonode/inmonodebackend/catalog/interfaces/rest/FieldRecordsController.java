package com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.catalog.domain.services.FieldSyncCommandService;
import com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest.resources.FieldSyncResource;
import com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest.transform.FieldSyncResultResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest.transform.SyncFieldRecordsCommandFromResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Upload side of the field synchronization. The portfolio download lives in financial, under the same path prefix.
 */
@RestController
@RequestMapping("/api/v1/field-sync")
@Tag(name = "Field sync", description = "Catalog download and synchronization for the field agent app")
public class FieldRecordsController {

    private final FieldSyncCommandService fieldSyncCommandService;

    public FieldRecordsController(FieldSyncCommandService fieldSyncCommandService) {
        this.fieldSyncCommandService = fieldSyncCommandService;
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Batch processed with one outcome per reservation")
    @PreAuthorize("hasRole('FIELD_AGENT')")
    @Operation(summary = "Synchronize the prospects and reservations registered offline (US-11, US-12, US-32)",
            description = "Field agents only (FIELD_AGENT). Answers 201 with one result per reservation: SYNCED (the "
                    + "lot is held for 24 hours), CONFLICT (another operation reached the server first; revert the lot "
                    + "and alert the agent) or DUPLICATE (already processed; idempotent by the device id). Accepted "
                    + "reservations are kept even when others conflict. A structurally invalid payload is rejected "
                    + "as a whole with 400 and nothing is stored.")
    public ResponseEntity<?> sync(@Valid @RequestBody FieldSyncResource resource) {
        var result = fieldSyncCommandService.handle(
                SyncFieldRecordsCommandFromResourceAssembler.toCommandFromResource(resource));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, FieldSyncResultResourceAssembler::toResourceFromResult, HttpStatus.CREATED);
    }
}
