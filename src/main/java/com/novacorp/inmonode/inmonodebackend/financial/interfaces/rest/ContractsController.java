package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.AcknowledgeContractCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.IssueContractCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestContractUploadCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetReservationContractQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractQueryService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractDocumentResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.ContractResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.UUID;

/**
 * Contracts of the reservations: the back office issues them and the buyer reads and acknowledges them. A reservation
 * is identified by the transaction id every context shares.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Contracts", description = "Preliminary purchase contracts of the verified reservations")
public class ContractsController {

    private final ContractCommandService contractCommandService;
    private final ContractQueryService contractQueryService;

    public ContractsController(ContractCommandService contractCommandService,
                               ContractQueryService contractQueryService) {
        this.contractCommandService = contractCommandService;
        this.contractQueryService = contractQueryService;
    }

    @PostMapping("/reservations/{transactionId}/contract/upload-url")
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

    @PostMapping("/reservations/{transactionId}/contract")
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

    @GetMapping("/reservations/{transactionId}/contract")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Get the contract of my reservation (US-21)",
            description = "Buyers only (BUYER), for their own reservation. ISSUED comes with a link to the PDF, valid "
                    + "for 10 minutes, and the contractId to register the agreement; IN_PREPARATION means the payment "
                    + "arrived and the contract is being prepared (no download yet); NOT_AVAILABLE means the "
                    + "reservation still waits for its payment or no longer holds the lot. A reservation that is "
                    + "unknown or someone else's answers 404 RESERVATION_NOT_FOUND.")
    public ResponseEntity<?> getContract(@PathVariable UUID transactionId) {
        var result = contractQueryService.handle(new GetReservationContractQuery(transactionId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ContractResourceAssembler::toResourceFromReservationContract, HttpStatus.OK);
    }

    @PostMapping("/contracts/{contractId}/acknowledgment")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Register my preliminary agreement with the contract (US-22)",
            description = "Buyers only (BUYER), for their own contract. Registers when the buyer agreed; repeating it "
                    + "keeps the first date. Requiring the whole document to be read first is up to the portal. A "
                    + "contract that is unknown or someone else's answers 404 CONTRACT_NOT_FOUND.")
    public ResponseEntity<?> acknowledge(@PathVariable Long contractId) {
        var result = contractCommandService.handle(new AcknowledgeContractCommand(contractId));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, ContractResourceAssembler::toAcknowledgmentFromContract, HttpStatus.OK);
    }
}
