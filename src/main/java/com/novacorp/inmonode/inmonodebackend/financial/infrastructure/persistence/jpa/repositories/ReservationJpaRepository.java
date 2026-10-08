package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, Long> {

    Optional<ReservationEntity> findBySourceEventId(UUID sourceEventId);

    @Query("select r from ReservationEntity r join r.evidences e where e.id = :evidenceId")
    Optional<ReservationEntity> findByEvidenceId(@Param("evidenceId") Long evidenceId);

    @Query("select distinct r from ReservationEntity r join r.evidences e where e.status = :status")
    List<ReservationEntity> findWithEvidenceInStatus(@Param("status") PaymentEvidenceStatus status);
}
