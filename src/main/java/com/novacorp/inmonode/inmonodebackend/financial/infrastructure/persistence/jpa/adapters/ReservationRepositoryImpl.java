package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers.ReservationEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ReservationEntity;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.ReservationJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationRepositoryImpl implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;

    public ReservationRepositoryImpl(ReservationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Reservation save(Reservation reservation) {
        var entity = reservation.getId() == null
                ? new ReservationEntity()
                : jpaRepository.findById(reservation.getId()).orElseGet(ReservationEntity::new);
        var saved = jpaRepository.save(ReservationEntityAssembler.copyToEntity(reservation, entity));
        return ReservationEntityAssembler.toDomain(saved);
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        return jpaRepository.findById(id).map(ReservationEntityAssembler::toDomain);
    }

    @Override
    public Optional<Reservation> findBySourceEventId(UUID sourceEventId) {
        return jpaRepository.findBySourceEventId(sourceEventId).map(ReservationEntityAssembler::toDomain);
    }
}
