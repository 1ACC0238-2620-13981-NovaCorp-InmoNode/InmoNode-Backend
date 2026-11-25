package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.scheduling;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.SendDueSignatureRequestsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractSignatureCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * US-56: sends the issued contracts to the signature provider every minute. A failed request waits longer each time
 * before it is tried again, so a provider that is down is not flooded.
 */
@Component
@ConditionalOnProperty(name = "financial.signature.dispatch-job.enabled", havingValue = "true", matchIfMissing = true)
public class ContractSignatureJob {

    private static final Logger LOG = LoggerFactory.getLogger(ContractSignatureJob.class);
    private static final int BATCH = 20;

    private final ContractSignatureCommandService contractSignatureCommandService;

    public ContractSignatureJob(ContractSignatureCommandService contractSignatureCommandService) {
        this.contractSignatureCommandService = contractSignatureCommandService;
    }

    @Scheduled(fixedDelayString = "${financial.signature.dispatch-interval:PT1M}")
    public void sendDueRequests() {
        try {
            var sent = contractSignatureCommandService.handle(new SendDueSignatureRequestsCommand(BATCH));
            if (sent > 0) {
                LOG.info("Sent {} contract(s) to signature", sent);
            }
        } catch (RuntimeException failure) {
            LOG.error("Could not send the contracts to signature ({})", failure.getClass().getSimpleName());
        }
    }
}
