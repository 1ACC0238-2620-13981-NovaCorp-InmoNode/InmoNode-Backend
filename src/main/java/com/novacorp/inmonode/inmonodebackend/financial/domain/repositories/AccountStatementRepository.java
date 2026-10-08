package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;

import java.util.Optional;

/**
 * Persistence abstraction for the {@link AccountStatement} aggregate.
 */
public interface AccountStatementRepository {

    /**
     * @return the persisted statement, with the generated ids of the statement and its installments
     */
    AccountStatement save(AccountStatement statement);

    Optional<AccountStatement> findById(Long id);

    /** The statement opened for the contract, if any: there is at most one. */
    Optional<AccountStatement> findByContractId(Long contractId);

    Optional<AccountStatement> findByReservationId(Long reservationId);
}
