package com.novacorp.inmonode.inmonodebackend.iam.application.internal.eventhandlers;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.PasswordResetEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Leaves a security record when a password is replaced through an emailed link (US-52).
 */
@Component
public class PasswordResetEventHandler {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.audit");

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PasswordResetEvent event) {
        SECURITY_LOG.info("event=PASSWORD_RESET email={}", event.email());
    }
}
