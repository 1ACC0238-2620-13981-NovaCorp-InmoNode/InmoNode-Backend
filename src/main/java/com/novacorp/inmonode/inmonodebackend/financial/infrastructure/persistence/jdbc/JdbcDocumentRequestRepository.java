package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jdbc;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentRequestStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.DocumentRequestRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link DocumentRequestRepository} with plain JDBC, like the notification outbox: a request is a single row whose
 * status the document consumer writes.
 */
@Repository
public class JdbcDocumentRequestRepository implements DocumentRequestRepository {

    private static final RowMapper<DocumentRequest> ROW = (row, number) -> DocumentRequest.restore(
            row.getObject("id", UUID.class),
            DocumentKind.valueOf(row.getString("kind")),
            row.getString("reference"),
            row.getLong("requested_by"),
            row.getString("object_key"),
            row.getTimestamp("requested_at").toInstant(),
            DocumentRequestStatus.valueOf(row.getString("status")),
            row.getObject("size_bytes", Long.class),
            row.getString("failure_code"),
            instant(row.getTimestamp("completed_at")));

    private final JdbcTemplate jdbc;

    public JdbcDocumentRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(DocumentRequest request) {
        jdbc.update("""
                INSERT INTO financial_document_control.document_requests
                    (id, kind, reference, requested_by, object_key, status, size_bytes, failure_code, requested_at,
                     completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, size_bytes = EXCLUDED.size_bytes,
                    failure_code = EXCLUDED.failure_code, completed_at = EXCLUDED.completed_at
                """, request.getId(), request.getKind().name(), request.getReference(), request.getRequestedBy(),
                request.getObjectKey(), request.getStatus().name(), request.getSizeBytes(), request.getFailureCode(),
                Timestamp.from(request.getRequestedAt()), timestamp(request.getCompletedAt()));
    }

    @Override
    public Optional<DocumentRequest> findById(UUID id) {
        return jdbc.query("SELECT * FROM financial_document_control.document_requests WHERE id = ?", ROW, id)
                .stream().findFirst();
    }

    private static @Nullable Timestamp timestamp(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static @Nullable Instant instant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
