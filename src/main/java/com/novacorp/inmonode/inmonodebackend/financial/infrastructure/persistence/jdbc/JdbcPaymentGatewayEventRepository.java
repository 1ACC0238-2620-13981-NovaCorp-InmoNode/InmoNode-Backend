package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jdbc;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentGatewayEventOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentGatewayEventRecord;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.PaymentGatewayEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

/**
 * {@link PaymentGatewayEventRepository} with plain JDBC: the gateway's event id is the key, so recording it twice
 * keeps the first outcome.
 */
@Repository
public class JdbcPaymentGatewayEventRepository implements PaymentGatewayEventRepository {

    private static final RowMapper<PaymentGatewayEventRecord> ROW = (row, number) -> new PaymentGatewayEventRecord(
            row.getString("event_id"),
            row.getString("type"),
            row.getString("provider_payment_intent_id"),
            PaymentGatewayEventOutcome.valueOf(row.getString("outcome")),
            row.getString("detail"),
            row.getTimestamp("received_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcPaymentGatewayEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PaymentGatewayEventRecord> findById(String eventId) {
        return jdbc.query("SELECT * FROM financial_document_control.payment_gateway_events WHERE event_id = ?", ROW,
                eventId).stream().findFirst();
    }

    @Override
    public boolean record(PaymentGatewayEventRecord event) {
        return jdbc.update("""
                INSERT INTO financial_document_control.payment_gateway_events
                    (event_id, type, provider_payment_intent_id, outcome, detail, received_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, event.eventId(), event.type(), event.providerPaymentIntentId(), event.outcome().name(),
                truncate(event.detail()), Timestamp.from(event.receivedAt())) == 1;
    }

    private static String truncate(String detail) {
        return detail == null || detail.length() <= 500 ? detail : detail.substring(0, 500);
    }
}
