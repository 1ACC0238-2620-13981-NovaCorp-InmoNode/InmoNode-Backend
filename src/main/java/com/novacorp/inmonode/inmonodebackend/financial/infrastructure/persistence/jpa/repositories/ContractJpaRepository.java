package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ContractEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContractJpaRepository extends JpaRepository<ContractEntity, Long> {

    Optional<ContractEntity> findByReservationId(Long reservationId);
}
