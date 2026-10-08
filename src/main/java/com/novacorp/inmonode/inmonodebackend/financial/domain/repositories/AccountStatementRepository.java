package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;

import java.time.LocalDate;
import java.util.List;
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

    /** Every statement of the buyer, oldest first (US-27). */
    List<AccountStatement> findByBuyerId(Long buyerId);

    /**
     * Ids of the statements with something to do in the daily review on {@code asOfDate} (US-24): a pending
     * installment past due or due within {@code reminderDays} days and not reminded, or an overdue one not notified.
     * Only ids: each statement is then read under the lock of its lot.
     */
    List<Long> findIdsToReview(LocalDate asOfDate, int reminderDays);
}
