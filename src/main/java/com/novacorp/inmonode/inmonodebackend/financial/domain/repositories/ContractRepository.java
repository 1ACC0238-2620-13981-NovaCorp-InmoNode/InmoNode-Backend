package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;

import java.util.Optional;

/**
 * Persistence abstraction for the {@link Contract} aggregate.
 */
public interface ContractRepository {

    /**
     * @return the persisted contract, with its generated id
     */
    Contract save(Contract contract);

    Optional<Contract> findById(Long id);

    Optional<Contract> findByIdForUpdate(Long id);

    /** The contract of the reservation, if one was issued: there is at most one. */
    Optional<Contract> findByReservationId(Long reservationId);
}
