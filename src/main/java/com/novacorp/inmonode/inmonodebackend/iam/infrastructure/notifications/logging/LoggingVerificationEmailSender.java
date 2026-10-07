package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.notifications.logging;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.notifications.VerificationEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Development implementation: logs the verification link instead of sending an email.
 * Replace it with an SMTP adapter (Mailpit locally, Brevo in production) behind the same port.
 */
@Component
public class LoggingVerificationEmailSender implements VerificationEmailSender {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingVerificationEmailSender.class);

    private final String verificationUrl;

    public LoggingVerificationEmailSender(
            @Value("${authorization.verification.url:http://localhost:8080/api/v1/auth/verify-email}") String verificationUrl) {
        this.verificationUrl = verificationUrl;
    }

    @Override
    public void send(String email, String verificationToken) {
        LOG.info("Verification email for {}: {}?token={}", email, verificationUrl, verificationToken);
    }
}
