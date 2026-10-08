package com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.notifications;

/**
 * Outbound port that delivers the email verification link (US-14, Scenario 1).
 */
public interface VerificationEmailSender {

    void send(String email, String verificationToken);
}
