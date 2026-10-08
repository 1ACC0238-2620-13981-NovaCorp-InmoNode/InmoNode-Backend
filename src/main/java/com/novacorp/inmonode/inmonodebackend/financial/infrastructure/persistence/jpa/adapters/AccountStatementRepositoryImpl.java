package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers.AccountStatementEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.AccountStatementEntity;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.AccountStatementJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class AccountStatementRepositoryImpl implements AccountStatementRepository {

    private final AccountStatementJpaRepository jpaRepository;

    public AccountStatementRepositoryImpl(AccountStatementJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /** Flushed, so new installments come back with their generated ids too. */
    @Override
    public AccountStatement save(AccountStatement statement) {
        var entity = statement.getId() == null
                ? new AccountStatementEntity()
                : jpaRepository.findById(statement.getId()).orElseGet(AccountStatementEntity::new);
        var saved = jpaRepository.saveAndFlush(AccountStatementEntityAssembler.copyToEntity(statement, entity));
        return AccountStatementEntityAssembler.toDomain(saved);
    }

    @Override
    public Optional<AccountStatement> findById(Long id) {
        return jpaRepository.findById(id).map(AccountStatementEntityAssembler::toDomain);
    }

    @Override
    public Optional<AccountStatement> findByContractId(Long contractId) {
        return jpaRepository.findByContractId(contractId).map(AccountStatementEntityAssembler::toDomain);
    }

    @Override
    public Optional<AccountStatement> findByReservationId(Long reservationId) {
        return jpaRepository.findByReservationId(reservationId).map(AccountStatementEntityAssembler::toDomain);
    }

    @Override
    public List<Long> findIdsToReview(LocalDate asOfDate, int reminderDays) {
        return jpaRepository.findIdsToReview(asOfDate, asOfDate.plusDays(reminderDays), InstallmentStatus.PENDING,
                InstallmentStatus.OVERDUE);
    }
}
