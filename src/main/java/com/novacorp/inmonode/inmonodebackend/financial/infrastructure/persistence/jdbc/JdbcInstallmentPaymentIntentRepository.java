package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jdbc;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentPaymentIntentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.InstallmentPaymentIntentRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link InstallmentPaymentIntentRepository} with plain JDBC, like the document requests: an intent is a single row
 * whose progress the gateway's notifications write.
 */
@Repository
public class JdbcInstallmentPaymentIntentRepository implements InstallmentPaymentIntentRepository {

    private static final String SELECT = "SELECT * FROM financial_document_control.installment_payment_intents ";

    private static final RowMapper<InstallmentPaymentIntent> ROW = (row, number) -> InstallmentPaymentIntent.restore(
            row.getObject("id", UUID.class),
            row.getLong("account_statement_id"),
            row.getInt("installment_number"),
            row.getLong("buyer_id"),
            row.getBigDecimal("amount"),
            row.getString("currency"),
            row.getString("idempotency_key"),
            row.getTimestamp("created_at").toInstant(),
            row.getTimestamp("expires_at").toInstant(),
            InstallmentPaymentIntentStatus.valueOf(row.getString("status")),
            row.getString("provider_payment_intent_id"),
            row.getString("provider_charge_id"),
            row.getString("last_failure"),
            instant(row.getTimestamp("confirmed_at")));

    private final JdbcTemplate jdbc;

    public JdbcInstallmentPaymentIntentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(InstallmentPaymentIntent intent) {
        jdbc.update("""
                INSERT INTO financial_document_control.installment_payment_intents
                    (id, account_statement_id, installment_number, buyer_id, amount, currency, status,
                     idempotency_key, provider_payment_intent_id, provider_charge_id, last_failure, created_at,
                     expires_at, confirmed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status,
                    provider_payment_intent_id = EXCLUDED.provider_payment_intent_id,
                    provider_charge_id = EXCLUDED.provider_charge_id, last_failure = EXCLUDED.last_failure,
                    confirmed_at = EXCLUDED.confirmed_at
                """, intent.getId(), intent.getAccountStatementId(), intent.getInstallmentNumber(),
                intent.getBuyerId(), intent.getAmount(), intent.getCurrency(), intent.getStatus().name(),
                intent.getIdempotencyKey(), intent.getProviderPaymentIntentId(), intent.getProviderChargeId(),
                intent.getLastFailure(), Timestamp.from(intent.getCreatedAt()), Timestamp.from(intent.getExpiresAt()),
                timestamp(intent.getConfirmedAt()));
    }

    @Override
    public Optional<InstallmentPaymentIntent> findById(UUID id) {
        return jdbc.query(SELECT + "WHERE id = ?", ROW, id).stream().findFirst();
    }

    @Override
    public Optional<InstallmentPaymentIntent> findByIdForUpdate(UUID id) {
        return jdbc.query(SELECT + "WHERE id = ? FOR UPDATE", ROW, id).stream().findFirst();
    }

    @Override
    public Optional<InstallmentPaymentIntent> findOpen(Long accountStatementId, int installmentNumber) {
        return jdbc.query(SELECT + "WHERE account_statement_id = ? AND installment_number = ? AND status = 'OPEN'",
                ROW, accountStatementId, installmentNumber).stream().findFirst();
    }

    @Override
    public Optional<InstallmentPaymentIntent> findOpenForUpdate(Long accountStatementId, int installmentNumber) {
        return jdbc.query(SELECT + "WHERE account_statement_id = ? AND installment_number = ? AND status = 'OPEN' "
                + "FOR UPDATE", ROW, accountStatementId, installmentNumber).stream().findFirst();
    }

    @Override
    public Optional<InstallmentPaymentIntent> findByIdempotencyKey(Long buyerId, String idempotencyKey) {
        return jdbc.query(SELECT + "WHERE buyer_id = ? AND idempotency_key = ?", ROW, buyerId, idempotencyKey)
                .stream().findFirst();
    }

    @Override
    public Optional<InstallmentPaymentIntent> findByProviderPaymentIntentId(String providerPaymentIntentId) {
        return jdbc.query(SELECT + "WHERE provider_payment_intent_id = ?", ROW, providerPaymentIntentId)
                .stream().findFirst();
    }

    private static @Nullable Timestamp timestamp(@Nullable Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static @Nullable Instant instant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
