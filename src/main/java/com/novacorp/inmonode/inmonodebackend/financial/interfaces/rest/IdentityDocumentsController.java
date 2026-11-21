package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.AttachIdentityDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestIdentityDocumentUploadCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.IdentityDocumentCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.IdentityDocumentFileResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.IdentityDocumentResourceAssembler;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/field-sync/reservations/{transactionId}/identity-document")
@Tag(name = "Field sync", description = "Catalog download and synchronization for the field agent app")
public class IdentityDocumentsController {

    private final IdentityDocumentCommandService identityDocumentCommandService;

    public IdentityDocumentsController(IdentityDocumentCommandService identityDocumentCommandService) {
        this.identityDocumentCommandService = identityDocumentCommandService;
    }

    @PostMapping("/upload-url")
    @PreAuthorize("hasRole('FIELD_AGENT')")
    @Operation(summary = "Get a presigned URL to upload the photo of the buyer's identity document (US-59)",
            description = "Field agents only (FIELD_AGENT), for a field reservation they synchronized; transactionId "
                    + "is the reservationId the device generated. Upload the photo with a single PUT to uploadUrl, "
                    + "sending the returned headers exactly as given; the URL is valid for 10 minutes and the storage "
                    + "answers 403 to any other type or size. Accepts image/jpeg and image/png up to 5 MB (400 "
                    + "otherwise). A reservation that is unknown, from the web or of another agent answers 404 "
                    + "RESERVATION_NOT_FOUND; one whose photo is already registered answers 409 "
                    + "IDENTITY_DOCUMENT_CONFLICT, since that photo is part of the buyer's file.")
    public ResponseEntity<?> requestUploadUrl(@PathVariable UUID transactionId,
                                              @Valid @RequestBody IdentityDocumentFileResource resource) {
        var document = IdentityDocumentResourceAssembler.toDocument(transactionId, resource);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                identityDocumentCommandService.handle(new RequestIdentityDocumentUploadCommand(document)),
                IdentityDocumentResourceAssembler::toResourceFromUpload, HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasRole('FIELD_AGENT')")
    @Operation(summary = "Register the uploaded photo of the buyer's identity document (US-59)",
            description = "Field agents only (FIELD_AGENT), for their own field reservation. Send the same "
                    + "documentId, contentType and sizeBytes used to ask for the upload URL. Answers 201 with result "
                    + "RECEIVED, or DUPLICATE when this photo was already registered (idempotent by documentId; the "
                    + "original date is returned). From then on the reservation is no longer \"Documento de "
                    + "identidad pendiente\". Answers 400 IDENTITY_DOCUMENT_FILE_NOT_UPLOADED when the photo is not "
                    + "in the file repository or differs from the declared type or size, 404 RESERVATION_NOT_FOUND "
                    + "for a reservation that is unknown, from the web or of another agent, and 409 "
                    + "IDENTITY_DOCUMENT_CONFLICT when the reservation already has another photo.")
    public ResponseEntity<?> register(@PathVariable UUID transactionId,
                                      @Valid @RequestBody IdentityDocumentFileResource resource) {
        var document = IdentityDocumentResourceAssembler.toDocument(transactionId, resource);
        return ResponseEntityAssembler.toResponseEntityFromResult(
                identityDocumentCommandService.handle(new AttachIdentityDocumentCommand(document)),
                IdentityDocumentResourceAssembler::toResourceFromRegistration, HttpStatus.CREATED);
    }
}
