package com.novacorp.inmonode.inmonodebackend.iam.application.internal.eventhandlers;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.notifications.VerificationEmailSender;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.UserRegisteredEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the verification link once the registration transaction has committed.
 */
@Component
public class UserRegisteredEventHandler {

    private final VerificationEmailSender emailSender;

    public UserRegisteredEventHandler(VerificationEmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(UserRegisteredEvent event) {
        emailSender.send(event.email(), event.verificationToken());
    }
}
