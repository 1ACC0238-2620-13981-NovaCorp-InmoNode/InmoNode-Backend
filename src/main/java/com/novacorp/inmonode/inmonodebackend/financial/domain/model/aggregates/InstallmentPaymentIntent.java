package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.Installment;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentPaymentIntentStatus;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The charge of one installment through the payment gateway (US-55). Starting the payment opens it with what the
 * installment owes at that moment; only the gateway's confirmation credits the installment, so the buyer's app never
 * marks it as paid. It refers to its {@link AccountStatement} by id and lives apart from it: the statement's rules do
 * not need it, and its id is the order reference sent to the gateway.
 */
public class InstallmentPaymentIntent {

    /** The longest reason kept from the gateway. */
    private static final int MAX_FAILURE_LENGTH = 500;

    private final UUID id;
    private final Long accountStatementId;
    private final int installmentNumber;
    private final Long buyerId;
    private final BigDecimal amount;
    private final String currency;
    private final @Nullable String idempotencyKey;
    private final Instant createdAt;
    private final Instant expiresAt;
    private InstallmentPaymentIntentStatus status;
    private @Nullable String providerPaymentIntentId;
    private @Nullable String providerChargeId;
    private @Nullable String lastFailure;
    private @Nullable Instant confirmedAt;

    private InstallmentPaymentIntent(UUID id, Long accountStatementId, int installmentNumber, Long buyerId,
                                     BigDecimal amount, String currency, @Nullable String idempotencyKey,
                                     Instant createdAt, Instant expiresAt, InstallmentPaymentIntentStatus status,
                                     @Nullable String providerPaymentIntentId, @Nullable String providerChargeId,
                                     @Nullable String lastFailure, @Nullable Instant confirmedAt) {
        this.id = id;
        this.accountStatementId = accountStatementId;
        this.installmentNumber = installmentNumber;
        this.buyerId = buyerId;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.providerPaymentIntentId = providerPaymentIntentId;
        this.providerChargeId = providerChargeId;
        this.lastFailure = lastFailure;
        this.confirmedAt = confirmedAt;
    }

    /**
     * Opens the charge of an installment the buyer has not paid yet, for exactly what it owes now (its amount plus
     * its late fee) in the currency of the statement. It expires {@code timeToLive} after {@code now}.
     *
     * @throws IllegalArgumentException when the statement is not saved or not of the buyer, the installment is not
     *                                  of the statement, or the time to live is not positive
     * @throws IllegalStateException    when the installment is already paid
     */
    public static InstallmentPaymentIntent open(UUID id, AccountStatement statement, Installment installment,
                                                Long buyerId, @Nullable String idempotencyKey, Instant now,
                                                Duration timeToLive) {
        if (statement.getId() == null || !statement.belongsTo(buyerId)
                || statement.findInstallment(installment.getNumber()).orElse(null) != installment) {
            throw new IllegalArgumentException("a payment is opened for an installment of a saved statement of the buyer");
        }
        if (installment.isPaid()) {
            throw new IllegalStateException("installment %d is already paid".formatted(installment.getNumber()));
        }
        if (timeToLive == null || timeToLive.isNegative() || timeToLive.isZero()) {
            throw new IllegalArgumentException("the time to live of a payment must be positive");
        }
        var key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey;
        return new InstallmentPaymentIntent(Objects.requireNonNull(id), statement.getId(), installment.getNumber(),
                buyerId, installment.amountDue(), statement.getCurrency(), key, now, now.plus(timeToLive),
                InstallmentPaymentIntentStatus.OPEN, null, null, null, null);
    }

    /** Rebuilds an already persisted intent. */
    public static InstallmentPaymentIntent restore(UUID id, Long accountStatementId, int installmentNumber,
                                                   Long buyerId, BigDecimal amount, String currency,
                                                   @Nullable String idempotencyKey, Instant createdAt,
                                                   Instant expiresAt, InstallmentPaymentIntentStatus status,
                                                   @Nullable String providerPaymentIntentId,
                                                   @Nullable String providerChargeId, @Nullable String lastFailure,
                                                   @Nullable Instant confirmedAt) {
        return new InstallmentPaymentIntent(id, accountStatementId, installmentNumber, buyerId, amount, currency,
                idempotencyKey, createdAt, expiresAt, status, providerPaymentIntentId, providerChargeId, lastFailure,
                confirmedAt);
    }

    /** The amount in the smallest unit of its currency, as the gateway charges it: cents of PEN or USD. */
    public long amountInMinorUnits() {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    public boolean isOpen() {
        return status == InstallmentPaymentIntentStatus.OPEN;
    }

    /** Whether the gateway already confirmed the charge, credited or not. */
    public boolean isConfirmed() {
        return status == InstallmentPaymentIntentStatus.SUCCEEDED || status == InstallmentPaymentIntentStatus.UNAPPLIED;
    }

    /** Open, but past its time to live: the buyer starts the payment again. */
    public boolean isExpired(Instant now) {
        return isOpen() && !now.isBefore(expiresAt);
    }

    /** Whether it is of that installment of that statement. */
    public boolean isFor(Long statementId, int number) {
        return accountStatementId.equals(statementId) && installmentNumber == number;
    }

    public boolean belongsTo(Long userId) {
        return buyerId.equals(userId);
    }

    /**
     * The gateway opened the charge with {@code providerId}. Opening it again with the same id changes nothing.
     *
     * @throws IllegalStateException when it is not open or the gateway opened it with another id
     */
    public void attach(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("the gateway's id is required");
        }
        if (!isOpen()) {
            throw new IllegalStateException("payment %s is %s, not open".formatted(id, status));
        }
        if (providerPaymentIntentId != null && !providerPaymentIntentId.equals(providerId)) {
            throw new IllegalStateException("payment %s was opened in the gateway as %s".formatted(id,
                    providerPaymentIntentId));
        }
        providerPaymentIntentId = providerId;
    }

    /**
     * The gateway confirmed the charge and the installment was credited with it. Money already moved, so it is
     * confirmed even if it had expired or been canceled here meanwhile.
     *
     * @throws IllegalStateException when it was already confirmed
     */
    public void succeed(@Nullable String chargeId, Instant at) {
        confirm(InstallmentPaymentIntentStatus.SUCCEEDED, chargeId, at, null);
    }

    /**
     * The gateway confirmed the charge, but it no longer pays the installment: it was paid some other way, or what
     * it owes changed. The money is kept with the reason, for the finance back office to refund it.
     *
     * @throws IllegalStateException when it was already confirmed
     */
    public void markUnapplied(@Nullable String chargeId, Instant at, String reason) {
        confirm(InstallmentPaymentIntentStatus.UNAPPLIED, chargeId, at, reason);
    }

    private void confirm(InstallmentPaymentIntentStatus confirmed, @Nullable String chargeId, Instant at,
                         @Nullable String reason) {
        if (isConfirmed()) {
            throw new IllegalStateException("payment %s was already confirmed".formatted(id));
        }
        status = confirmed;
        providerChargeId = chargeId;
        confirmedAt = Objects.requireNonNull(at);
        if (reason != null) {
            lastFailure = truncate(reason);
        }
    }

    /**
     * The gateway declined an attempt; the buyer can try again with the same charge while it is open.
     *
     * @throws IllegalStateException when it is not open
     */
    public void recordFailure(String reason) {
        requireOpen();
        lastFailure = truncate(reason);
    }

    /**
     * Closed without a charge: canceled in the gateway, never opened there, or replaced because what the installment
     * owes changed.
     *
     * @throws IllegalStateException when it is not open
     */
    public void cancel(String reason) {
        requireOpen();
        status = InstallmentPaymentIntentStatus.CANCELED;
        lastFailure = truncate(reason);
    }

    /**
     * Closed because its time to live passed without a charge.
     *
     * @throws IllegalStateException when it is not open
     */
    public void expire() {
        requireOpen();
        status = InstallmentPaymentIntentStatus.EXPIRED;
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new IllegalStateException("payment %s is %s, not open".formatted(id, status));
        }
    }

    private static @Nullable String truncate(@Nullable String reason) {
        if (reason == null || reason.length() <= MAX_FAILURE_LENGTH) return reason;
        return reason.substring(0, MAX_FAILURE_LENGTH);
    }

    public UUID getId() { return id; }
    public Long getAccountStatementId() { return accountStatementId; }
    public int getInstallmentNumber() { return installmentNumber; }
    public Long getBuyerId() { return buyerId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public @Nullable String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public InstallmentPaymentIntentStatus getStatus() { return status; }
    public @Nullable String getProviderPaymentIntentId() { return providerPaymentIntentId; }
    public @Nullable String getProviderChargeId() { return providerChargeId; }
    public @Nullable String getLastFailure() { return lastFailure; }
    public @Nullable Instant getConfirmedAt() { return confirmedAt; }
}
