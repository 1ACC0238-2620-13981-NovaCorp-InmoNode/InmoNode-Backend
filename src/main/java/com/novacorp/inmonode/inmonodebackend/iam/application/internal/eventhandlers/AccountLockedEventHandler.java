package com.novacorp.inmonode.inmonodebackend.iam.application.internal.eventhandlers;

import com.novacorp.inmonode.inmonodebackend.iam.domain.model.events.AccountLockedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emits the security record required by US-01, Scenario 2, when an account gets locked.
 */
@Component
public class AccountLockedEventHandler {

    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.audit");

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AccountLockedEvent event) {
        SECURITY_LOG.warn("event=ACCOUNT_LOCKED email={} lockedUntil={}", event.email(), event.lockedUntil());
    }
}
