package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.notifications.outbox;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.*;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialNotificationDispatcherTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final Instant now = Instant.parse("2026-10-09T06:00:00Z");

    @SuppressWarnings("unchecked")
    private FinancialNotificationDispatcher dispatcher(String finance, String recipient, int attempts) throws Exception {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Timestamp.class))).thenReturn(List.of(9L));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(9L), any(Timestamp.class))).thenAnswer(call -> {
            var row = mock(ResultSet.class);
            when(row.getLong("id")).thenReturn(9L);
            when(row.getString("recipient")).thenReturn(recipient);
            when(row.getString("subject")).thenReturn("Separation notice");
            when(row.getString("body")).thenReturn("Durable message");
            when(row.getInt("attempts")).thenReturn(attempts);
            return List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0));
        });
        return new FinancialNotificationDispatcher(jdbc, mail, manager, Clock.fixed(now, ZoneOffset.UTC),
                "noreply@inmonode.dev", finance, "legal@example.test");
    }

    @Test void successResolvesTheConfiguredRecipientAndPersistsDelivery() throws Exception {
        dispatcher("finance@example.test", "@finance", 0).dispatch();
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertArrayEquals(new String[]{"finance@example.test"}, message.getValue().getTo());
        var order = inOrder(mail, jdbc, manager);
        order.verify(mail).send(any(SimpleMailMessage.class));
        order.verify(jdbc).update(contains("delivered_at ="), eq(Timestamp.from(now)), eq(9L));
        order.verify(manager).commit(any());
    }

    @Test void smtpFailureRetainsTheNoticeWithBackoffWithoutClaimingDelivery() throws Exception {
        var dispatcher = dispatcher("finance@example.test", "@finance", 2);
        doThrow(new MailSendException("unavailable")).when(mail).send(any(SimpleMailMessage.class));
        dispatcher.dispatch();
        verify(jdbc).update(contains("SMTP_DELIVERY_FAILED"), eq(3), eq(Timestamp.from(now.plusSeconds(240))), eq(9L));
        verify(jdbc, never()).update(contains("delivered_at ="), any(Timestamp.class), anyLong());
        verify(manager).commit(any());
    }

    @Test void missingRecipientKeepsTheDurableNoticeWithoutUsingRetries() throws Exception {
        dispatcher("", "@finance", 0).dispatch();
        verifyNoInteractions(mail);
        verify(jdbc).update(contains("next_attempt_at ="), eq(Timestamp.from(now.plusSeconds(3600))), eq(9L));
    }
}
