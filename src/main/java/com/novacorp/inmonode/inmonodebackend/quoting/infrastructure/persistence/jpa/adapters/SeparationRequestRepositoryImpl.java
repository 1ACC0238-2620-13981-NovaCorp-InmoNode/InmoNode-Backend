package com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.SeparationRequest;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.SeparationStatus;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.repositories.SeparationRequestRepository;
import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.assemblers.SeparationRequestEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.entities.SeparationRequestEntity;
import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.repositories.SeparationRequestJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SeparationRequestRepositoryImpl implements SeparationRequestRepository {

    private final SeparationRequestJpaRepository jpaRepository;

    public SeparationRequestRepositoryImpl(SeparationRequestJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SeparationRequest save(SeparationRequest request) {
        var entity = request.getId() == null
                ? new SeparationRequestEntity()
                : jpaRepository.findById(request.getId()).orElseGet(SeparationRequestEntity::new);
        var saved = jpaRepository.save(SeparationRequestEntityAssembler.copyToEntity(request, entity));
        return SeparationRequestEntityAssembler.toDomain(saved);
    }

    @Override
    public Optional<SeparationRequest> findLatestBlocked(Long buyerId, Long lotId) {
        return jpaRepository.findFirstByBuyerIdAndLotIdAndStatusOrderByRequestedAtDesc(buyerId, lotId,
                SeparationStatus.BLOCKED).map(SeparationRequestEntityAssembler::toDomain);
    }
}
