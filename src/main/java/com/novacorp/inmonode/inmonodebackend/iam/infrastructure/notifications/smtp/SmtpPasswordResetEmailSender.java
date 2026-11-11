package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.notifications.smtp;

import com.novacorp.inmonode.inmonodebackend.iam.application.internal.outboundservices.notifications.PasswordResetEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the password reset link over SMTP (US-52, Scenario 1): Mailpit in development, Brevo in production.
 *
 * <p>The link points to the web portal page, which asks for the new password and posts it with the token to
 * {@code /api/v1/auth/reset-password}. Delivery runs after the request commits, so an SMTP failure is logged
 * instead of propagated.</p>
 */
@Component
public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {

    static final String SUBJECT = "Restablece tu contraseña de inmoNode";

    private static final Logger LOG = LoggerFactory.getLogger(SmtpPasswordResetEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String resetUrl;
    private final long tokenExpirationMinutes;

    public SmtpPasswordResetEmailSender(JavaMailSender mailSender,
                                        @Value("${authorization.mail.from}") String from,
                                        @Value("${authorization.password-reset.url}") String resetUrl,
                                        @Value("${authorization.password-reset.token-expiration-minutes:30}")
                                        long tokenExpirationMinutes) {
        this.mailSender = mailSender;
        this.from = from;
        this.resetUrl = resetUrl;
        this.tokenExpirationMinutes = tokenExpirationMinutes;
    }

    @Override
    public void send(String email, String resetToken) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(SUBJECT);
        message.setText("""
                Hola,

                Recibimos un pedido para restablecer la contraseña de tu cuenta de inmoNode. Para elegir una nueva, abre el siguiente enlace:

                %s

                El enlace vence en %s y solo se puede usar una vez. Si vence, pide uno nuevo desde la página de inicio de sesión.

                Si no lo pediste, ignora este mensaje: tu contraseña no cambia.
                """.formatted(resetLink(resetToken), validity()));
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            LOG.error("Could not send the password reset email to {}", email, ex);
        }
    }

    String resetLink(String resetToken) {
        return "%s?token=%s".formatted(resetUrl, resetToken);
    }

    private String validity() {
        return tokenExpirationMinutes == 1 ? "1 minuto" : "%d minutos".formatted(tokenExpirationMinutes);
    }
}
