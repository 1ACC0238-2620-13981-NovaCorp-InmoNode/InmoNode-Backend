package com.novacorp.inmonode.inmonodebackend.iam.application.internal.eventhandlers;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.notifications.VerificationEmailSender;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.VerificationTokenRenewedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the new verification link once the token renewal has committed.
 */
@Component
public class VerificationTokenRenewedEventHandler {

    private final VerificationEmailSender emailSender;

    public VerificationTokenRenewedEventHandler(VerificationEmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(VerificationTokenRenewedEvent event) {
        emailSender.send(event.email(), event.verificationToken());
    }
}
