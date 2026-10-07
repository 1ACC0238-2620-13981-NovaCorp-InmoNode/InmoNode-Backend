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

class SmtpVerificationEmailSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpVerificationEmailSender sender = new SmtpVerificationEmailSender(
            mailSender, "no-reply@inmonode.dev", "https://portal.inmonode.dev/verify-email");

    @Test
    void sendsLinkToThePortalVerificationPage() {
        sender.send("ana@mail.com", "abc123");

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        var message = captor.getValue();
        assertEquals("no-reply@inmonode.dev", message.getFrom());
        assertArrayEquals(new String[]{"ana@mail.com"}, message.getTo());
        assertEquals(SmtpVerificationEmailSender.SUBJECT, message.getSubject());
        assertTrue(message.getText().contains("https://portal.inmonode.dev/verify-email?token=abc123"));
    }

    @Test
    void smtpFailureIsNotPropagated() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> sender.send("ana@mail.com", "abc123"));
    }
}
