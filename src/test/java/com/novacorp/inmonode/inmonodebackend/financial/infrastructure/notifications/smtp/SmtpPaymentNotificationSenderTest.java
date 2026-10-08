package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.notifications.smtp;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.PaymentNotice;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpPaymentNotificationSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpPaymentNotificationSender sender =
            new SmtpPaymentNotificationSender(mailSender, "no-reply@inmonode.dev");

    @Test
    void remindsTheBuyerOfWhatToPayAndWhen() {
        assertTrue(sender.remindUpcoming(notice(new BigDecimal("0.00"))));

        var message = sent();
        assertEquals("no-reply@inmonode.dev", message.getFrom());
        assertArrayEquals(new String[]{"ana@mail.com"}, message.getTo());
        assertEquals("Tu cuota 3 de 12 vence el 08/01/2027", message.getSubject());
        assertTrue(message.getText().contains("lote A-01 en Los Olivos de Chilca vence el 08/01/2027"),
                message.getText());
        assertTrue(message.getText().contains("Monto a pagar: S/ 3198.56"), message.getText());
    }

    @Test
    void tellsTheBuyerTheInstallmentIsOverdueWithItsLateFee() {
        assertTrue(sender.notifyOverdue(notice(new BigDecimal("47.98"))));

        var message = sent();
        assertEquals("Tu cuota 3 de 12 está vencida", message.getSubject());
        assertTrue(message.getText().contains("venció el 08/01/2027"), message.getText());
        assertTrue(message.getText().contains("Cuota: S/ 3198.56"), message.getText());
        assertTrue(message.getText().contains("Mora: S/ 47.98"), message.getText());
        assertTrue(message.getText().contains("Total a pagar: S/ 3246.54"), message.getText());
    }

    @Test
    void anSmtpFailureIsAnsweredAsNotDelivered() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertFalse(sender.remindUpcoming(notice(new BigDecimal("0.00"))));
        assertFalse(sender.notifyOverdue(notice(new BigDecimal("47.98"))));
    }

    @Test
    void otherCurrenciesShowTheirCode() {
        assertEquals("USD 10.50", SmtpPaymentNotificationSender.money(new BigDecimal("10.50"), "USD"));
    }

    private SimpleMailMessage sent() {
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static PaymentNotice notice(BigDecimal penalty) {
        var amount = new BigDecimal("3198.56");
        return new PaymentNotice("ana@mail.com", UUID.randomUUID(), "Los Olivos de Chilca", "A-01", 3, 12,
                LocalDate.parse("2027-01-08"), amount, penalty, amount.add(penalty), "PEN");
    }
}
