package com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.ReservationOperation;
import com.novacorp.inmonode.inmonodebackend.vouchers.infrastructure.persistence.jpa.entities.ReservationOperationEntity;

/**
 * Translates between the {@link ReservationOperation} aggregate and its JPA persistence entity.
 */
public final class ReservationOperationEntityAssembler {

    private ReservationOperationEntityAssembler() {}

    public static ReservationOperation toDomain(ReservationOperationEntity entity) {
        return ReservationOperation.restore(entity.getId(), entity.getReservationId(), entity.getChannel(),
                entity.getOwnerId(), entity.getLotId(), entity.getInitialAmount(), entity.getReservedAt(),
                entity.getEvidenceDueAt());
    }

    /** Copies the aggregate state onto the entity; audit columns and id stay untouched. */
    public static ReservationOperationEntity copyToEntity(ReservationOperation operation,
                                                          ReservationOperationEntity entity) {
        entity.setReservationId(operation.getReservationId());
        entity.setChannel(operation.getChannel());
        entity.setOwnerId(operation.getOwnerId());
        entity.setLotId(operation.getLotId());
        entity.setInitialAmount(operation.getInitialAmount());
        entity.setReservedAt(operation.getReservedAt());
        entity.setEvidenceDueAt(operation.getEvidenceDueAt());
        return entity;
    }
}
