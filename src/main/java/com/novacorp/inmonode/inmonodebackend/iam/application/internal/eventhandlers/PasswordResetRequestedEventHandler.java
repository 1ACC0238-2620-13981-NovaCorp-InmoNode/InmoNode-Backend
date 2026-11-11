package com.novacorp.inmonode.inmonodebackend.iam.application.internal.eventhandlers;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.notifications.PasswordResetEmailSender;
import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.PasswordResetRequestedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the password reset link once the new link has committed, so an emailed link always works.
 */
@Component
public class PasswordResetRequestedEventHandler {

    private final PasswordResetEmailSender emailSender;

    public PasswordResetRequestedEventHandler(PasswordResetEmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PasswordResetRequestedEvent event) {
        emailSender.send(event.email(), event.resetToken());
    }
}
