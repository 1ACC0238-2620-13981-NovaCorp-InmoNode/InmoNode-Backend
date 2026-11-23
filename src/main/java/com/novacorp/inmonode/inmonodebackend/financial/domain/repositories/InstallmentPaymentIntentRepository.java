package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.InstallmentPaymentIntent;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence abstraction for the {@link InstallmentPaymentIntent} aggregate.
 */
public interface InstallmentPaymentIntentRepository {

    /** Inserts a new intent, or writes the progress of an existing one. */
    void save(InstallmentPaymentIntent intent);

    Optional<InstallmentPaymentIntent> findById(UUID id);

    /** Locks the intent until the transaction ends, after its lot and its statement. */
    Optional<InstallmentPaymentIntent> findByIdForUpdate(UUID id);

    /** The open intent of an installment; there is at most one. */
    Optional<InstallmentPaymentIntent> findOpen(Long accountStatementId, int installmentNumber);

    /** The open intent of an installment, locked until the transaction ends. */
    Optional<InstallmentPaymentIntent> findOpenForUpdate(Long accountStatementId, int installmentNumber);

    /** The intent a buyer opened with that idempotency key, for any installment. */
    Optional<InstallmentPaymentIntent> findByIdempotencyKey(Long buyerId, String idempotencyKey);

    /** The intent the gateway opened with that id. */
    Optional<InstallmentPaymentIntent> findByProviderPaymentIntentId(String providerPaymentIntentId);
}
