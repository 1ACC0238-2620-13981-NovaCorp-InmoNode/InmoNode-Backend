package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.IssueContractCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestContractUploadCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractDocumentResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.ContractResourceAssembler;
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

/**
 * Contracts of the reservations, identified by the transaction id every context shares.
 */
@RestController
@RequestMapping("/api/v1/reservations/{transactionId}/contract")
@Tag(name = "Contracts", description = "Preliminary purchase contracts of the verified reservations")
public class ContractsController {

    private final ContractCommandService contractCommandService;

    public ContractsController(ContractCommandService contractCommandService) {
        this.contractCommandService = contractCommandService;
    }

    @PostMapping("/upload-url")
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    @Operation(summary = "Get a presigned URL to upload the contract PDF of a reservation (US-21)",
            description = "Finance back office only (FINANCE_ADMIN), for a web reservation whose payment was "
                    + "verified. Upload the PDF with a single PUT to uploadUrl, sending the returned headers exactly "
                    + "as given; the URL is valid for 10 minutes. Answers 400 for a size over 10 MB, 404 "
                    + "RESERVATION_NOT_FOUND, 422 BUSINESS_RULE_VIOLATION when the reservation is not verified or "
                    + "was made in the field, and 409 CONTRACT_CONFLICT when its contract was already issued.")
    public ResponseEntity<?> requestUploadUrl(@PathVariable UUID transactionId,
                                              @Valid @RequestBody ContractDocumentResource resource) {
        var result = contractCommandService.handle(new RequestContractUploadCommand(transactionId,
                resource.documentId(), resource.sizeBytes()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ContractResourceAssembler::toResourceFromUpload, HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    @Operation(summary = "Issue the contract of a reservation with the uploaded PDF (US-21)",
            description = "Finance back office only (FINANCE_ADMIN). Send the same documentId and sizeBytes used to "
                    + "ask for the upload URL. Answers 201; the buyer can then read and download it. Answers 400 "
                    + "CONTRACT_FILE_NOT_UPLOADED when the PDF is not in the file repository or differs from the "
                    + "declared size, and the same errors as the upload URL.")
    public ResponseEntity<?> issue(@PathVariable UUID transactionId,
                                   @Valid @RequestBody ContractDocumentResource resource) {
        var result = contractCommandService.handle(new IssueContractCommand(transactionId, resource.documentId(),
                resource.sizeBytes()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ContractResourceAssembler::toResourceFromContract, HttpStatus.CREATED);
    }
}
