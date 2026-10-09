package com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.ReservationOperation;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.ReservationOperationRepository;
import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.assemblers.ReservationOperationEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.entities.ReservationOperationEntity;
import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.repositories.ReservationOperationJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationOperationRepositoryImpl implements ReservationOperationRepository {

    private final ReservationOperationJpaRepository jpaRepository;

    public ReservationOperationRepositoryImpl(ReservationOperationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ReservationOperation save(ReservationOperation operation) {
        var entity = operation.getId() == null
                ? new ReservationOperationEntity()
                : jpaRepository.findById(operation.getId()).orElseGet(ReservationOperationEntity::new);
        var saved = jpaRepository.save(ReservationOperationEntityAssembler.copyToEntity(operation, entity));
        return ReservationOperationEntityAssembler.toDomain(saved);
    }

    @Override
    public Optional<ReservationOperation> findByReservationId(UUID reservationId) {
        return jpaRepository.findByReservationId(reservationId).map(ReservationOperationEntityAssembler::toDomain);
    }

    @Override
    public boolean existsByReservationId(UUID reservationId) {
        return jpaRepository.existsByReservationId(reservationId);
    }
}
