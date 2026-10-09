package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers.ContractEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ContractEntity;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.ContractJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ContractRepositoryImpl implements ContractRepository {

    private final ContractJpaRepository jpaRepository;
    private final jakarta.persistence.EntityManager entityManager;

    public ContractRepositoryImpl(ContractJpaRepository jpaRepository, jakarta.persistence.EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public Contract save(Contract contract) {
        var entity = contract.getId() == null
                ? new ContractEntity()
                : jpaRepository.findById(contract.getId()).orElseGet(ContractEntity::new);
        var saved = jpaRepository.save(ContractEntityAssembler.copyToEntity(contract, entity));
        return ContractEntityAssembler.toDomain(saved);
    }

    @Override
    public Optional<Contract> findById(Long id) {
        return jpaRepository.findById(id).map(ContractEntityAssembler::toDomain);
    }

    @Override
    public Optional<Contract> findByIdForUpdate(Long id) {
        return jpaRepository.findById(id).map(entity -> {
            entityManager.refresh(entity, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return ContractEntityAssembler.toDomain(entity);
        });
    }

    @Override
    public Optional<Contract> findByReservationId(Long reservationId) {
        return jpaRepository.findByReservationId(reservationId).map(ContractEntityAssembler::toDomain);
    }
}
