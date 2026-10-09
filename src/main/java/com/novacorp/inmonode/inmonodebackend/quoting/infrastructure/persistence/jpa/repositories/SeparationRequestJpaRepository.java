package com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.SeparationStatus;
import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.entities.SeparationRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeparationRequestJpaRepository extends JpaRepository<SeparationRequestEntity, Long> {

    Optional<SeparationRequestEntity> findFirstByBuyerIdAndLotIdAndStatusOrderByRequestedAtDesc(
            Long buyerId, Long lotId, SeparationStatus status);
}
