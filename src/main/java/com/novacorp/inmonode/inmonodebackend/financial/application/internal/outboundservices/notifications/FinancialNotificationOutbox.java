package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications;

/** Enqueues inside the business transaction; delivery happens independently after commit. */
public interface FinancialNotificationOutbox {
    void enqueue(String eventKey, String recipient, String subject, String text);
}
