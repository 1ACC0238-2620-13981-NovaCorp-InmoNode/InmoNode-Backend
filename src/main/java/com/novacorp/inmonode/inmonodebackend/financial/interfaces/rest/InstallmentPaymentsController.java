package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.StartInstallmentPaymentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetInstallmentPaymentIntentQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentPaymentStart;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.InstallmentPaymentCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.InstallmentPaymentQueryService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.InstallmentPaymentIntentResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/account-statements/{accountStatementId}/installments/{installmentNumber}/payment-intents")
@Tag(name = "Installment payments", description = "Installments paid online through the payment gateway (US-55)")
public class InstallmentPaymentsController {

    private final InstallmentPaymentCommandService installmentPaymentCommandService;
    private final InstallmentPaymentQueryService installmentPaymentQueryService;

    public InstallmentPaymentsController(InstallmentPaymentCommandService installmentPaymentCommandService,
                                         InstallmentPaymentQueryService installmentPaymentQueryService) {
        this.installmentPaymentCommandService = installmentPaymentCommandService;
        this.installmentPaymentQueryService = installmentPaymentQueryService;
    }

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Start paying one of my installments online (US-55)",
            description = "Opens the charge of the installment in the payment gateway (Stripe) for exactly what it "
                    + "owes now, its amount plus its late fee, and answers what the app needs to show the gateway's "
                    + "form: clientSecret and publishableKey. The app never marks the installment as paid: only the "
                    + "gateway's signed notification credits it, and the app checks it with GET .../{paymentIntentId}. "
                    + "201 when it was opened now; 200 when it was already open, which is also what a retry with "
                    + "the same Idempotency-Key gets, even once it is closed (then without clientSecret). The "
                    + "charge expires after 30 minutes; starting again after that opens another one. Errors: 404 "
                    + "for an unknown statement or installment or another buyer's; 409 INSTALLMENT_CONFLICT when it "
                    + "is already paid; 409 PAYMENT_INTENT_CONFLICT when the key was used for another installment; "
                    + "503 PAYMENTS_UNAVAILABLE without a configured gateway; 502 PAYMENT_GATEWAY_ERROR when the "
                    + "gateway fails, which leaves nothing open.")
    public ResponseEntity<?> start(@PathVariable Long accountStatementId, @PathVariable int installmentNumber,
                                   @Parameter(description = "Chosen by the app for one attempt to pay, up to 255 "
                                           + "characters; a retry with it gets the same payment")
                                   @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        var result = installmentPaymentCommandService.handle(
                new StartInstallmentPaymentCommand(accountStatementId, installmentNumber, idempotencyKey));
        if (result instanceof Result.Success<InstallmentPaymentStart, ApplicationError> success) {
            var start = success.value();
            // It carries the secret that pays the charge: never kept by a cache.
            return ResponseEntity.status(start.created() ? HttpStatus.CREATED : HttpStatus.OK)
                    .cacheControl(CacheControl.noStore())
                    .body(InstallmentPaymentIntentResourceAssembler.toResourceFromStart(start));
        }
        return ResponseEntityAssembler.toResponseEntityFromResult(result,
                InstallmentPaymentIntentResourceAssembler::toResourceFromStart, HttpStatus.OK);
    }

    @GetMapping("/{paymentIntentId}")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Check how the online payment of one of my installments went (US-55)",
            description = "What the gateway's notifications recorded so far: OPEN while the confirmation has not "
                    + "come, with failureReason when an attempt was declined and the buyer can try again; SUCCEEDED "
                    + "once the installment was credited; UNAPPLIED when it was charged but the installment no "
                    + "longer owed it, and finance refunds it; CANCELED or EXPIRED when it was closed without a "
                    + "charge. It never calls the gateway, so the app can ask again after paying. 404 "
                    + "PAYMENT_INTENT_NOT_FOUND when it is not of that installment or of the buyer.")
    public ResponseEntity<?> get(@PathVariable Long accountStatementId, @PathVariable int installmentNumber,
                                 @PathVariable UUID paymentIntentId) {
        var result = installmentPaymentQueryService.handle(
                new GetInstallmentPaymentIntentQuery(accountStatementId, installmentNumber, paymentIntentId));
        return ResponseEntityAssembler.toResponseEntityFromResult(result,
                InstallmentPaymentIntentResourceAssembler::toResourceFromIntent, HttpStatus.OK);
    }
}
