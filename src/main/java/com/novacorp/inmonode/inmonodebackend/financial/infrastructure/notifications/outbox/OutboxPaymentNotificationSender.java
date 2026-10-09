package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.notifications.outbox;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.*;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.notifications.smtp.SmtpPaymentNotificationSender;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class OutboxPaymentNotificationSender implements PaymentNotificationSender {
    private final FinancialNotificationOutbox outbox;
    public OutboxPaymentNotificationSender(FinancialNotificationOutbox outbox) { this.outbox = outbox; }
    @Override
    public boolean remindUpcoming(PaymentNotice notice) {
        var message = SmtpPaymentNotificationSender.formatReminder(notice);
        outbox.enqueue("installment-upcoming:" + notice.transactionId() + ":" + notice.installmentNumber(),
                message.email(), message.subject(), message.text());
        return true;
    }
    @Override
    public boolean notifyOverdue(PaymentNotice notice) {
        var message = SmtpPaymentNotificationSender.formatOverdue(notice);
        outbox.enqueue("installment-overdue:" + notice.transactionId() + ":" + notice.installmentNumber(),
                message.email(), message.subject(), message.text());
        return true;
    }
}
