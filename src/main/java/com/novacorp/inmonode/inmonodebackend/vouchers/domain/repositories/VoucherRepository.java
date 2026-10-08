package com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence abstraction for the {@link Voucher} aggregate.
 */
public interface VoucherRepository {

    Voucher save(Voucher voucher);

    Optional<Voucher> findByVoucherId(UUID voucherId);

    boolean existsByVoucherId(UUID voucherId);
}
