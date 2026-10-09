package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.notifications.smtp;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.notifications.VerificationEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the verification link over SMTP (US-14, Scenario 1): Mailpit in development, Brevo in production.
 *
 * <p>The link points to the web portal page, which posts the token to {@code /api/v1/auth/verify-email}.
 * Delivery runs after the registration commits, so an SMTP failure is logged instead of propagated.</p>
 */
@Component
public class SmtpVerificationEmailSender implements VerificationEmailSender {

    static final String SUBJECT = "Verifica tu cuenta de inmoNode";

    private static final Logger LOG = LoggerFactory.getLogger(SmtpVerificationEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String verificationUrl;
    private final long tokenExpirationHours;

    public SmtpVerificationEmailSender(JavaMailSender mailSender,
                                       @Value("${authorization.mail.from}") String from,
                                       @Value("${authorization.verification.url}") String verificationUrl,
                                       @Value("${authorization.verification.token-expiration-hours:24}")
                                       long tokenExpirationHours) {
        this.mailSender = mailSender;
        this.from = from;
        this.verificationUrl = verificationUrl;
        this.tokenExpirationHours = tokenExpirationHours;
    }

    @Override
    public void send(String email, String verificationToken) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(SUBJECT);
        message.setText("""
                Hola,

                Para activar tu cuenta de inmoNode, abre el siguiente enlace:

                %s

                El enlace vence en %s. Si vence, pide uno nuevo desde la página de inicio de sesión.

                Si no creaste esta cuenta, ignora este mensaje.
                """.formatted(verificationLink(verificationToken), validity()));
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            LOG.error("Could not send the verification email to {}", email, ex);
        }
    }

    String verificationLink(String verificationToken) {
        return "%s?token=%s".formatted(verificationUrl, verificationToken);
    }

    private String validity() {
        return tokenExpirationHours == 1 ? "1 hora" : "%d horas".formatted(tokenExpirationHours);
    }
}
