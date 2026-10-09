package com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageRequest;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence abstraction for the {@link Voucher} aggregate.
 */
public interface VoucherRepository {

    Voucher save(Voucher voucher);

    Optional<Voucher> findByVoucherId(UUID voucherId);

    boolean existsByVoucherId(UUID voucherId);

    PageResult<Voucher> findByOwnerId(Long ownerId, PageRequest pagination);
}
