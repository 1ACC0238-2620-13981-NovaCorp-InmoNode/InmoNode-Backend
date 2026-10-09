package com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.Voucher;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.ExtractedData;
import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.VoucherContentType;
import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.entities.VoucherEntity;

/**
 * Translates between the {@link Voucher} aggregate and its JPA persistence entity.
 */
public final class VoucherEntityAssembler {

    private VoucherEntityAssembler() {}

    public static Voucher toDomain(VoucherEntity entity) {
        var extractedData = new ExtractedData(entity.getAmount(), entity.getCurrency(), entity.getOperationDate(),
                entity.getOperationCode(), entity.getOcrConfidence());
        return Voucher.restore(entity.getId(), entity.getVoucherId(), entity.getReservationId(),
                entity.getObjectKey(), VoucherContentType.fromMediaType(entity.getContentType()),
                entity.getSizeBytes(), extractedData, entity.isManuallyCorrected(), entity.getStatus(),
                entity.getReceivedAt());
    }

    /** Copies the aggregate state onto the entity; audit columns and id stay untouched. */
    public static VoucherEntity copyToEntity(Voucher voucher, VoucherEntity entity) {
        var data = voucher.getExtractedData();
        entity.setVoucherId(voucher.getVoucherId());
        entity.setReservationId(voucher.getReservationId());
        entity.setObjectKey(voucher.getObjectKey());
        entity.setContentType(voucher.getContentType().mediaType());
        entity.setSizeBytes(voucher.getSizeBytes());
        entity.setAmount(data.amount());
        entity.setCurrency(data.currency());
        entity.setOperationDate(data.operationDate());
        entity.setOperationCode(data.operationCode());
        entity.setOcrConfidence(data.confidence());
        entity.setManuallyCorrected(voucher.isManuallyCorrected());
        entity.setStatus(voucher.getStatus());
        entity.setReceivedAt(voucher.getReceivedAt());
        return entity;
    }
}
