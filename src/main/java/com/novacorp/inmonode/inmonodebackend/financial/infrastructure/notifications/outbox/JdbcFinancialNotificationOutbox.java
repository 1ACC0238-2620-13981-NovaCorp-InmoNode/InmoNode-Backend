package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.notifications.outbox;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.notifications.FinancialNotificationOutbox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFinancialNotificationOutbox implements FinancialNotificationOutbox {
    private final JdbcTemplate jdbc;
    public JdbcFinancialNotificationOutbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void enqueue(String eventKey, String recipient, String subject, String text) {
        jdbc.update("""
                INSERT INTO financial_document_control.notification_outbox
                    (event_key, recipient, subject, body, created_at, next_attempt_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (event_key) DO NOTHING
                """, eventKey, recipient, subject, text);
    }
}
