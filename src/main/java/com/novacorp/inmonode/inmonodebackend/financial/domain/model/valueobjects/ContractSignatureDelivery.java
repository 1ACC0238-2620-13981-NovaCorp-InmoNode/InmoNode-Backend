package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * The request to sign a contract on its way to the provider (US-56, Scenario 2). A failed attempt is retried after a
 * growing wait, and after {@link #MAX_ATTEMPTS} the request is {@code FAILED} until the back office sends it again.
 *
 * @param attempts     the failed attempts so far
 * @param requestedAt  when it was queued, or queued again by the back office
 */
public record ContractSignatureDelivery(Long contractId, SignatureDeliveryStatus status, int attempts,
                                        Instant nextAttemptAt, @Nullable String lastError, Instant requestedAt,
                                        @Nullable Instant sentAt) {

    public static final int MAX_ATTEMPTS = 8;
    private static final Duration FIRST_WAIT = Duration.ofMinutes(1);
    private static final Duration LONGEST_WAIT = Duration.ofHours(1);
    private static final int MAX_ERROR_LENGTH = 500;

    /** A request to send now. */
    public static ContractSignatureDelivery queue(Long contractId, Instant now) {
        return new ContractSignatureDelivery(contractId, SignatureDeliveryStatus.PENDING, 0, now, null, now, null);
    }

    /** The wait after the given number of failed attempts: 1, 2, 4, 8, 16, 32 minutes, then 1 hour. */
    public static Duration waitAfter(int failedAttempts) {
        if (failedAttempts < 1) {
            throw new IllegalArgumentException("there is no wait before the first failure");
        }
        var minutes = FIRST_WAIT.toMinutes() << Math.min(failedAttempts - 1, 10);
        var wait = Duration.ofMinutes(minutes);
        return wait.compareTo(LONGEST_WAIT) > 0 ? LONGEST_WAIT : wait;
    }

    public boolean isDue(Instant now) {
        return status == SignatureDeliveryStatus.PENDING && !now.isBefore(nextAttemptAt);
    }

    public ContractSignatureDelivery sent(Instant now) {
        requirePending();
        return new ContractSignatureDelivery(contractId, SignatureDeliveryStatus.SENT, attempts, nextAttemptAt, null,
                requestedAt, now);
    }

    /** One more failed attempt: waits longer, or fails for good after {@link #MAX_ATTEMPTS}. */
    public ContractSignatureDelivery failedAttempt(String error, Instant now) {
        requirePending();
        var failed = attempts + 1;
        var reason = error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        if (failed >= MAX_ATTEMPTS) {
            return new ContractSignatureDelivery(contractId, SignatureDeliveryStatus.FAILED, failed, now, reason,
                    requestedAt, null);
        }
        return new ContractSignatureDelivery(contractId, SignatureDeliveryStatus.PENDING, failed,
                now.plus(waitAfter(failed)), reason, requestedAt, null);
    }

    public boolean isExhausted() {
        return status == SignatureDeliveryStatus.FAILED;
    }

    private void requirePending() {
        if (status != SignatureDeliveryStatus.PENDING) {
            throw new IllegalStateException("the request to sign is %s, not PENDING".formatted(status));
        }
    }
}
