package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.LotEntity;

/**
 * Translates between the {@link Lot} aggregate and its JPA persistence entity.
 */
public final class LotEntityAssembler {

    private LotEntityAssembler() {}

    public static Lot toDomain(LotEntity entity) {
        return Lot.withStage(Lot.restore(entity.getId(), entity.getProjectId(), entity.getCode(),
                new LotDimensions(entity.getArea(), entity.getFront(), entity.getDepth()),
                new Money(entity.getPriceAmount(), entity.getPriceCurrency()),
                LotBoundary.fromWkt(entity.getBoundaryWkt()), entity.getStatus(),
                entity.getCurrentReservationId(), entity.getBlockedUntil()), entity.getStageName());
    }

    /** Copies the aggregate state onto the entity; audit columns and id stay untouched. */
    public static LotEntity copyToEntity(Lot lot, LotEntity entity) {
        entity.setProjectId(lot.getProjectId());
        entity.setCode(lot.getCode());
        entity.setStageName(lot.getStageName());
        entity.setArea(lot.getDimensions().area());
        entity.setFront(lot.getDimensions().front());
        entity.setDepth(lot.getDimensions().depth());
        entity.setPriceAmount(lot.getPrice().amount());
        entity.setPriceCurrency(lot.getPrice().currency());
        entity.setStatus(lot.getStatus());
        entity.setBoundaryWkt(lot.getBoundary().toWkt());
        entity.setCurrentReservationId(lot.getCurrentReservationId());
        entity.setBlockedUntil(lot.getBlockedUntil());
        return entity;
    }
}
