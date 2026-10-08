package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.notifications.smtp;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.PaymentNotice;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.PaymentNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Tells buyers about their installments over SMTP (US-24), through the same server as the verification emails:
 * Mailpit in development, Brevo in production. A failure is logged and answered as not delivered, so the review tries
 * again the next day.
 */
@Component
public class SmtpPaymentNotificationSender implements PaymentNotificationSender {

    static final String REMINDER_SUBJECT = "Tu cuota %d de %d vence el %s";
    static final String OVERDUE_SUBJECT = "Tu cuota %d de %d está vencida";

    private static final Logger LOG = LoggerFactory.getLogger(SmtpPaymentNotificationSender.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPaymentNotificationSender(JavaMailSender mailSender,
                                         @Value("${authorization.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public boolean remindUpcoming(PaymentNotice notice) {
        return send(notice, REMINDER_SUBJECT.formatted(notice.installmentNumber(), notice.termMonths(),
                DATE.format(notice.dueDate())), """
                Hola,

                Te recordamos que la cuota %d de %d de tu lote %s en %s vence el %s.

                Monto a pagar: %s

                Si ya la pagaste, ignora este mensaje. Puedes revisar tu estado de cuenta en el portal de inmoNode.
                """.formatted(notice.installmentNumber(), notice.termMonths(), notice.lotCode(),
                notice.projectName(), DATE.format(notice.dueDate()), money(notice.amountDue(), notice.currency())));
    }

    @Override
    public boolean notifyOverdue(PaymentNotice notice) {
        return send(notice, OVERDUE_SUBJECT.formatted(notice.installmentNumber(), notice.termMonths()), """
                Hola,

                La cuota %d de %d de tu lote %s en %s venció el %s sin registrarse su pago.

                Cuota: %s
                Mora: %s
                Total a pagar: %s

                Regulariza el pago lo antes posible. Si ya pagaste, comunícate con nosotros para registrarlo.
                """.formatted(notice.installmentNumber(), notice.termMonths(), notice.lotCode(),
                notice.projectName(), DATE.format(notice.dueDate()), money(notice.amount(), notice.currency()),
                money(notice.penalty(), notice.currency()), money(notice.amountDue(), notice.currency())));
    }

    private boolean send(PaymentNotice notice, String subject, String text) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notice.email());
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
            return true;
        } catch (MailException ex) {
            LOG.error("Could not send the notice of installment {} of reservation {}", notice.installmentNumber(),
                    notice.transactionId(), ex);
            return false;
        }
    }

    static String money(BigDecimal amount, String currency) {
        return "PEN".equals(currency) ? "S/ %s".formatted(amount.toPlainString())
                : "%s %s".formatted(currency, amount.toPlainString());
    }
}
