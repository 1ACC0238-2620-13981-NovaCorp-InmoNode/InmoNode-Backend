package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ApprovePaymentEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RejectPaymentEvidenceCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetPendingVerificationsQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.VerificationCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.VerificationQueryService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ApprovePaymentEvidenceResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.PendingVerificationResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.RejectPaymentEvidenceResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.VerificationResourceAssembler;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/verifications")
@Tag(name = "Verifications", description = "Back-office verification of the payment evidences")
public class VerificationController {

    private final VerificationCommandService verificationCommandService;
    private final VerificationQueryService verificationQueryService;

    public VerificationController(VerificationCommandService verificationCommandService,
                                  VerificationQueryService verificationQueryService) {
        this.verificationCommandService = verificationCommandService;
        this.verificationQueryService = verificationQueryService;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    @Operation(summary = "Get the verification queue",
            description = "Finance back office only (FINANCE_ADMIN). Every payment evidence waiting for a decision, "
                    + "oldest first, with its reservation and lot. Late ones (received after the reservation stopped "
                    + "holding its lot) are listed so they can be rejected.")
    public List<PendingVerificationResource> getPending() {
        return verificationQueryService.handle(new GetPendingVerificationsQuery()).stream()
                .map(VerificationResourceAssembler::toResourceFromPending)
                .toList();
    }

    @PostMapping("/{evidenceId}/approve")
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    @Operation(summary = "Approve a payment evidence",
            description = "Finance back office only (FINANCE_ADMIN). The reservation becomes VERIFIED and its lot "
                    + "RESERVED. Answers 422 BUSINESS_RULE_VIOLATION when the evidence cannot be approved (late, an "
                    + "amount under the down payment or another currency; reject it instead), 409 "
                    + "PAYMENT_EVIDENCE_CONFLICT when it was already decided and 404 PAYMENT_EVIDENCE_NOT_FOUND.")
    public ResponseEntity<?> approve(@PathVariable Long evidenceId,
                                     @Valid @RequestBody(required = false) ApprovePaymentEvidenceResource resource) {
        var note = resource == null ? null : resource.note();
        var result = verificationCommandService.handle(new ApprovePaymentEvidenceCommand(evidenceId, note));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, VerificationResourceAssembler::toResourceFromOutcome, HttpStatus.OK);
    }

    @PostMapping("/{evidenceId}/reject")
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    @Operation(summary = "Reject a payment evidence with a reason (US-25)",
            description = "Finance back office only (FINANCE_ADMIN). The requester sees the reason. When it was the "
                    + "only evidence under review, the reservation goes back to BLOCKED and the lot is held for a new "
                    + "window of its channel (24 h field, 1 h web) while a substitute voucher arrives. Answers 409 "
                    + "PAYMENT_EVIDENCE_CONFLICT when it was already decided and 404 PAYMENT_EVIDENCE_NOT_FOUND.")
    public ResponseEntity<?> reject(@PathVariable Long evidenceId,
                                    @Valid @RequestBody RejectPaymentEvidenceResource resource) {
        var result = verificationCommandService.handle(new RejectPaymentEvidenceCommand(evidenceId, resource.reason()));
        return ResponseEntityAssembler.toResponseEntityFromResult(
                result, VerificationResourceAssembler::toResourceFromOutcome, HttpStatus.OK);
    }
}
