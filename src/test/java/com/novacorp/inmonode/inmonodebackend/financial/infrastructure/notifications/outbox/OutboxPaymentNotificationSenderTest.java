package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.notifications.outbox;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxPaymentNotificationSenderTest {
    @Test void upcomingAndOverdueHaveIndependentDurableKeysForTheSameInstallment() {
        var outbox = mock(FinancialNotificationOutbox.class);
        var sender = new OutboxPaymentNotificationSender(outbox);
        var transaction = UUID.randomUUID();
        var notice = new PaymentNotice("buyer@example.test", transaction, "Proyecto", "A-01", 1, 12,
                LocalDate.parse("2026-10-15"), new BigDecimal("100"), new BigDecimal("2"), new BigDecimal("102"), "PEN");
        assertTrue(sender.remindUpcoming(notice));
        assertTrue(sender.notifyOverdue(notice));
        verify(outbox).enqueue(eq("installment-upcoming:" + transaction + ":1"), eq(notice.email()), anyString(), anyString());
        verify(outbox).enqueue(eq("installment-overdue:" + transaction + ":1"), eq(notice.email()), anyString(), anyString());
    }
}
