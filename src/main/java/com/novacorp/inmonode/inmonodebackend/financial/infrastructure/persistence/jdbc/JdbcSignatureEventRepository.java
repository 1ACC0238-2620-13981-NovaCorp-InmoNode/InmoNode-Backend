package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jdbc;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureEventOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureEventRecord;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.SignatureEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

/**
 * {@link SignatureEventRepository} with plain JDBC: the event key is the primary key, so recording it twice keeps
 * the first outcome, unless that one was REJECTED.
 */
@Repository
public class JdbcSignatureEventRepository implements SignatureEventRepository {

    private static final RowMapper<SignatureEventRecord> ROW = (row, number) -> new SignatureEventRecord(
            row.getString("event_key"),
            row.getString("event_type"),
            row.getObject("submission_id", Long.class),
            row.getObject("contract_id", Long.class),
            SignatureEventOutcome.valueOf(row.getString("outcome")),
            row.getString("detail"),
            row.getTimestamp("received_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcSignatureEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SignatureEventRecord> findByKey(String eventKey) {
        return jdbc.query("SELECT * FROM financial_document_control.signature_provider_events WHERE event_key = ?",
                ROW, eventKey).stream().findFirst();
    }

    @Override
    public boolean record(SignatureEventRecord event) {
        return jdbc.update("""
                INSERT INTO financial_document_control.signature_provider_events
                    (event_key, event_type, submission_id, contract_id, outcome, detail, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_key) DO UPDATE
                    SET contract_id = EXCLUDED.contract_id, outcome = EXCLUDED.outcome, detail = EXCLUDED.detail,
                        received_at = EXCLUDED.received_at
                    WHERE signature_provider_events.outcome = 'REJECTED'
                """, event.eventKey(), event.eventType(), event.submissionId(), event.contractId(),
                event.outcome().name(), event.detail(), Timestamp.from(event.receivedAt())) == 1;
    }
}
