package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers.ReservationEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ReservationEntity;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.ReservationJpaRepository;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageRequest;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationRepositoryImpl implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;
    private final jakarta.persistence.EntityManager entityManager;

    public ReservationRepositoryImpl(ReservationJpaRepository jpaRepository, jakarta.persistence.EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    /** Flushed, so the evidences added to the reservation come back with their generated ids too. */
    @Override
    public Reservation save(Reservation reservation) {
        var entity = reservation.getId() == null
                ? new ReservationEntity()
                : jpaRepository.findById(reservation.getId()).orElseGet(ReservationEntity::new);
        var saved = jpaRepository.saveAndFlush(ReservationEntityAssembler.copyToEntity(reservation, entity));
        return ReservationEntityAssembler.toDomain(saved);
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        return jpaRepository.findById(id).map(ReservationEntityAssembler::toDomain);
    }

    @Override
    public Optional<Reservation> findByIdForUpdate(Long id) {
        return jpaRepository.findById(id).map(entity -> {
            // A previous lookup in this transaction may have preceded a wait for the lot lock.
            // Reload eager children too. Flush preserves other writes before detaching this aggregate.
            entityManager.flush();
            entityManager.detach(entity);
            var fresh = entityManager.find(ReservationEntity.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return ReservationEntityAssembler.toDomain(fresh);
        });
    }

    @Override
    public Optional<Reservation> findBySourceEventId(UUID sourceEventId) {
        return jpaRepository.findBySourceEventId(sourceEventId).map(ReservationEntityAssembler::toDomain);
    }

    @Override
    public Optional<Reservation> findByEvidenceId(Long evidenceId) {
        return jpaRepository.findByEvidenceId(evidenceId).map(ReservationEntityAssembler::toDomain);
    }

    @Override
    public PageResult<ReservationEvidence> findPendingEvidences(PageRequest pagination) {
        var ids = jpaRepository.findEvidenceIdsInStatus(PaymentEvidenceStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(pagination.page() - 1, pagination.limit()));
        var byEvidenceId = new HashMap<Long, ReservationEvidence>();
        if (ids.hasContent()) {
            for (var entity : jpaRepository.findByEvidenceIds(ids.getContent())) {
                var reservation = ReservationEntityAssembler.toDomain(entity);
                reservation.getEvidences().forEach(evidence ->
                        byEvidenceId.put(evidence.getId(), new ReservationEvidence(reservation, evidence)));
            }
        }
        var items = ids.getContent().stream()
                .map(byEvidenceId::get)
                .filter(Objects::nonNull)
                .toList();
        return new PageResult<>(items, ids.getTotalElements(), pagination.page(), pagination.limit());
    }
}
