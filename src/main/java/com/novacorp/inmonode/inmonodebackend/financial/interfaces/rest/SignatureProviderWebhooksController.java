package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveSignatureEventCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractSignatureCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform.ContractResourceAssembler;
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
public class SignatureProviderWebhooksController {

    private final ContractSignatureCommandService contractSignatureCommandService;

    public SignatureProviderWebhooksController(ContractSignatureCommandService contractSignatureCommandService) {
        this.contractSignatureCommandService = contractSignatureCommandService;
    }

    @PostMapping("/docuseal")
    @SecurityRequirements
    @Operation(summary = "Receive the callbacks of DocuSeal about the signature of contracts (US-56)",
            description = "Called by DocuSeal, not by the apps: there is no token; the X-Docuseal-Signature header "
                    + "({timestamp}.{HMAC-SHA256 of \"{timestamp}.{body}\"} with the webhook secret) authenticates "
                    + "it, and it is rejected after 5 minutes. form.completed or submission.completed registers "
                    + "the signature once, after checking with DocuSeal that the submission of the contract is "
                    + "completed: the signed sheet is stored and the lot becomes SOLD. form.declined keeps the "
                    + "reason and tells finance. Callbacks are logged by type and submission, since DocuSeal sends "
                    + "no event id: a redelivered one gets its first outcome and changes nothing, except a REJECTED "
                    + "one, which is checked with DocuSeal again. 200 for every "
                    + "authentic callback, also for the ignored ones; 400 INVALID_WEBHOOK_SIGNATURE when it is not "
                    + "signed by DocuSeal; 502 SIGNATURE_PROVIDER_ERROR when DocuSeal could not confirm it, and it "
                    + "sends it again later; 503 SIGNATURES_UNAVAILABLE without a configured secret.")
    public ResponseEntity<?> receiveDocuSeal(@RequestBody(required = false) String payload,
                                             @RequestHeader(name = "X-Docuseal-Signature", required = false)
                                             String signature) {
        var result = contractSignatureCommandService.handle(new ReceiveSignatureEventCommand(payload, signature));
        return ResponseEntityAssembler.toResponseEntityFromResult(result,
                ContractResourceAssembler::toResourceFromSignatureEvent, HttpStatus.OK);
    }
}
