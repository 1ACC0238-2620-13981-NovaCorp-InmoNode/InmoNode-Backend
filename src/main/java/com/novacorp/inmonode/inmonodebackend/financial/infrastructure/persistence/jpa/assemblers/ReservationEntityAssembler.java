package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ReservationEntity;

/**
 * Translates between the {@link Reservation} aggregate and its JPA persistence entity.
 */
public final class ReservationEntityAssembler {

    private ReservationEntityAssembler() {}

    public static Reservation toDomain(ReservationEntity entity) {
        return Reservation.restore(entity.getId(), entity.getLotId(), entity.getChannel(), entity.getRequesterId(),
                entity.getProspectId(), entity.getSourceEventId(),
                new Money(entity.getInitialAmount(), entity.getInitialAmountCurrency()),
                entity.getReservedAt(), entity.getStatus());
    }

    /** Copies the aggregate state onto the entity; audit columns and id stay untouched. */
    public static ReservationEntity copyToEntity(Reservation reservation, ReservationEntity entity) {
        entity.setLotId(reservation.getLotId());
        entity.setChannel(reservation.getChannel());
        entity.setRequesterId(reservation.getRequesterId());
        entity.setProspectId(reservation.getProspectId());
        entity.setSourceEventId(reservation.getSourceEventId());
        entity.setInitialAmount(reservation.getInitialAmount().amount());
        entity.setInitialAmountCurrency(reservation.getInitialAmount().currency());
        entity.setStatus(reservation.getStatus());
        entity.setReservedAt(reservation.getReservedAt());
        return entity;
    }
}
