package com.novacorp.inmonode.inmonodebackend.iam.infrastructure.notifications.smtp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpPasswordResetEmailSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpPasswordResetEmailSender sender = new SmtpPasswordResetEmailSender(
            mailSender, "no-reply@inmonode.dev", "https://portal.inmonode.dev/reset-password", 30);

    @Test
    void sendsLinkToThePortalResetPageWithItsValidity() {
        sender.send("ana@mail.com", "abc123");

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        var message = captor.getValue();
        assertEquals("no-reply@inmonode.dev", message.getFrom());
        assertArrayEquals(new String[]{"ana@mail.com"}, message.getTo());
        assertEquals(SmtpPasswordResetEmailSender.SUBJECT, message.getSubject());
        assertTrue(message.getText().contains("https://portal.inmonode.dev/reset-password?token=abc123"));
        assertTrue(message.getText().contains("El enlace vence en 30 minutos y solo se puede usar una vez."));
        assertTrue(message.getText().contains("Si no lo pediste, ignora este mensaje: tu contraseña no cambia."));
    }

    @Test
    void smtpFailureIsNotPropagated() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> sender.send("ana@mail.com", "abc123"));
    }
}
