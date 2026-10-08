package com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.entities.ReservationOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReservationOperationJpaRepository extends JpaRepository<ReservationOperationEntity, Long> {

    Optional<ReservationOperationEntity> findByReservationId(UUID reservationId);

    boolean existsByReservationId(UUID reservationId);
}
