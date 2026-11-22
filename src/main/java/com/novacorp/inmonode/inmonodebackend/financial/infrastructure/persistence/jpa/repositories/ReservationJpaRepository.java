package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ReservationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, Long> {

    Optional<ReservationEntity> findBySourceEventId(UUID sourceEventId);

    @Query("select r from ReservationEntity r join r.evidences e where e.id = :evidenceId")
    Optional<ReservationEntity> findByEvidenceId(@Param("evidenceId") Long evidenceId);

    /** Ids of one page of the evidences in this status, oldest first; the count query sizes the whole listing. */
    @Query(value = "select e.id from PaymentEvidenceEntity e where e.status = :status order by e.submittedAt, e.id",
            countQuery = "select count(e) from PaymentEvidenceEntity e where e.status = :status")
    Page<Long> findEvidenceIdsInStatus(@Param("status") PaymentEvidenceStatus status, Pageable pageable);

    @Query("select distinct r from ReservationEntity r join r.evidences e where e.id in :evidenceIds")
    List<ReservationEntity> findByEvidenceIds(@Param("evidenceIds") Collection<Long> evidenceIds);
}
