package com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories.VoucherRepository;
import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.assemblers.VoucherEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.entities.VoucherEntity;
import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.repositories.VoucherJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class VoucherRepositoryImpl implements VoucherRepository {

    private final VoucherJpaRepository jpaRepository;

    public VoucherRepositoryImpl(VoucherJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Voucher save(Voucher voucher) {
        var entity = voucher.getId() == null
                ? new VoucherEntity()
                : jpaRepository.findById(voucher.getId()).orElseGet(VoucherEntity::new);
        var saved = jpaRepository.save(VoucherEntityAssembler.copyToEntity(voucher, entity));
        return VoucherEntityAssembler.toDomain(saved);
    }

    @Override
    public Optional<Voucher> findByVoucherId(UUID voucherId) {
        return jpaRepository.findByVoucherId(voucherId).map(VoucherEntityAssembler::toDomain);
    }

    @Override
    public boolean existsByVoucherId(UUID voucherId) {
        return jpaRepository.existsByVoucherId(voucherId);
    }
}
