package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jdbc;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractSignatureDelivery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureDeliveryStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractSignatureDeliveryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link ContractSignatureDeliveryRepository} with plain JDBC, like the notification outbox: a sender locks a due
 * request with {@code SKIP LOCKED}, so two instances never send the same contract at once.
 */
@Repository
public class JdbcContractSignatureDeliveryRepository implements ContractSignatureDeliveryRepository {

    private static final RowMapper<ContractSignatureDelivery> ROW = (row, number) -> new ContractSignatureDelivery(
            row.getLong("contract_id"),
            SignatureDeliveryStatus.valueOf(row.getString("status")),
            row.getInt("attempts"),
            row.getTimestamp("next_attempt_at").toInstant(),
            row.getString("last_error"),
            row.getTimestamp("requested_at").toInstant(),
            toInstant(row.getTimestamp("sent_at")));

    private final JdbcTemplate jdbc;

    public JdbcContractSignatureDeliveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(ContractSignatureDelivery delivery) {
        jdbc.update("""
                INSERT INTO financial_document_control.contract_signature_requests
                    (contract_id, status, attempts, next_attempt_at, last_error, requested_at, sent_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (contract_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    attempts = EXCLUDED.attempts,
                    next_attempt_at = EXCLUDED.next_attempt_at,
                    last_error = EXCLUDED.last_error,
                    requested_at = EXCLUDED.requested_at,
                    sent_at = EXCLUDED.sent_at
                """, delivery.contractId(), delivery.status().name(), delivery.attempts(),
                Timestamp.from(delivery.nextAttemptAt()), delivery.lastError(), Timestamp.from(delivery.requestedAt()),
                delivery.sentAt() == null ? null : Timestamp.from(delivery.sentAt()));
    }

    @Override
    public Optional<ContractSignatureDelivery> findByContractId(Long contractId) {
        return jdbc.query("SELECT * FROM financial_document_control.contract_signature_requests WHERE contract_id = ?",
                ROW, contractId).stream().findFirst();
    }

    @Override
    public List<Long> findDueContractIds(Instant now, int limit) {
        return jdbc.queryForList("""
                SELECT contract_id FROM financial_document_control.contract_signature_requests
                WHERE status = 'PENDING' AND next_attempt_at <= ?
                ORDER BY next_attempt_at, contract_id LIMIT ?
                """, Long.class, Timestamp.from(now), limit);
    }

    @Override
    public Optional<ContractSignatureDelivery> lockIfDue(Long contractId, Instant now) {
        return jdbc.query("""
                SELECT * FROM financial_document_control.contract_signature_requests
                WHERE contract_id = ? AND status = 'PENDING' AND next_attempt_at <= ?
                FOR UPDATE SKIP LOCKED
                """, ROW, contractId, Timestamp.from(now)).stream().findFirst();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
