package com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.entities.VoucherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface VoucherJpaRepository extends JpaRepository<VoucherEntity, Long> {

    Optional<VoucherEntity> findByVoucherId(UUID voucherId);

    boolean existsByVoucherId(UUID voucherId);

    @Query(value = """
            select v from VoucherEntity v
             where exists (select r.id from ReservationOperationEntity r
                            where r.reservationId = v.reservationId and r.ownerId = :ownerId)
            """, countQuery = """
            select count(v) from VoucherEntity v
             where exists (select r.id from ReservationOperationEntity r
                            where r.reservationId = v.reservationId and r.ownerId = :ownerId)
            """)
    Page<VoucherEntity> findByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);
}
