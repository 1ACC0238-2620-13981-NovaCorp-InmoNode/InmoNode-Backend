package com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.notifications;

/**
 * Outbound port that delivers the link to reset a forgotten password (US-52, Scenario 1).
 */
public interface PasswordResetEmailSender {

    void send(String email, String resetToken);
}
