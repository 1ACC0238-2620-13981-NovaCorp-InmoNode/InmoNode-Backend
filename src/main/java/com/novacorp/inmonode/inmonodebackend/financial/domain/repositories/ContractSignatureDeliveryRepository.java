package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractSignatureDelivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The requests to sign the contracts on their way to the provider (US-56, Scenario 2): one per contract.
 */
public interface ContractSignatureDeliveryRepository {

    /** Inserts the request, or replaces the one of the same contract. */
    void save(ContractSignatureDelivery delivery);

    Optional<ContractSignatureDelivery> findByContractId(Long contractId);

    /** The contracts whose request is due at {@code now}, oldest first. */
    List<Long> findDueContractIds(Instant now, int limit);

    /**
     * Locks the request of the contract when it is still due, skipping it when another sender holds it.
     *
     * @return empty when it is no longer due or is being sent
     */
    Optional<ContractSignatureDelivery> lockIfDue(Long contractId, Instant now);
}
