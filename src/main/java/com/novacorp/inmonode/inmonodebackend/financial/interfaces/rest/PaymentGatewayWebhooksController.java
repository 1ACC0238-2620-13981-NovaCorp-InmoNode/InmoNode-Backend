package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceivePaymentGatewayEventCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.InstallmentPaymentCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.InstallmentPaymentIntentResourceAssembler;
import com.novacorp.inmonode.inmonodebackend.shared.interfaces.rest.transform.ResponseEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Notifications of external services, authenticated by their signature")
public class PaymentGatewayWebhooksController {

    private final InstallmentPaymentCommandService installmentPaymentCommandService;

    public PaymentGatewayWebhooksController(InstallmentPaymentCommandService installmentPaymentCommandService) {
        this.installmentPaymentCommandService = installmentPaymentCommandService;
    }

    @PostMapping("/stripe")
    @SecurityRequirements
    @Operation(summary = "Receive the notifications of Stripe about the payment of installments (US-55)",
            description = "Called by Stripe, not by the apps: there is no token; the Stripe-Signature header "
                    + "authenticates it, computed over the raw body with the endpoint's signing secret, and it is "
                    + "rejected after 5 minutes. payment_intent.succeeded credits the installment once, after "
                    + "checking with Stripe that the charge succeeded for its amount and currency; when the "
                    + "installment no longer owes it, it is kept as UNAPPLIED and finance is told to refund it. "
                    + "payment_intent.payment_failed keeps the reason and leaves the charge open; "
                    + "payment_intent.canceled closes it. Every notification is logged by its event id: a "
                    + "redelivered one gets its first outcome and changes nothing. 200 for every authentic "
                    + "notification, also for the ignored ones (other types, other apps sharing the Stripe account, "
                    + "charges of other environments), so Stripe does not send them again; 400 "
                    + "INVALID_WEBHOOK_SIGNATURE when it is not signed by Stripe; 502 PAYMENT_GATEWAY_ERROR or 500 "
                    + "when it could not be processed, and Stripe sends it again later; 503 PAYMENTS_UNAVAILABLE "
                    + "without a configured gateway.")
    public ResponseEntity<?> receiveStripe(@RequestBody(required = false) String payload,
                                           @RequestHeader(name = "Stripe-Signature", required = false)
                                           String signature) {
        var result = installmentPaymentCommandService.handle(new ReceivePaymentGatewayEventCommand(payload, signature));
        return ResponseEntityAssembler.toResponseEntityFromResult(result,
                InstallmentPaymentIntentResourceAssembler::toResourceFromEvent, HttpStatus.OK);
    }
}
