package com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.entities.VoucherEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VoucherJpaRepository extends JpaRepository<VoucherEntity, Long> {

    Optional<VoucherEntity> findByVoucherId(UUID voucherId);

    boolean existsByVoucherId(UUID voucherId);
}
