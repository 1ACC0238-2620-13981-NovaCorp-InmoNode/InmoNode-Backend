package com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.SeparationRequest;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.entities.SeparationRequestEntity;

/**
 * Translates between the {@link SeparationRequest} aggregate and its JPA persistence entity.
 */
public final class SeparationRequestEntityAssembler {

    private SeparationRequestEntityAssembler() {}

    public static SeparationRequest toDomain(SeparationRequestEntity entity) {
        return SeparationRequest.restore(entity.getId(), entity.getTransactionId(), entity.getLotId(),
                entity.getBuyerId(), entity.getQuotationId(), new Money(entity.getInitialAmount(), entity.getCurrency()),
                entity.getStatus(), entity.getRequestedAt(), entity.getLockExpiresAt(), entity.getRejectionReason());
    }

    /** Copies the aggregate state onto the entity; audit columns and id stay untouched. */
    public static SeparationRequestEntity copyToEntity(SeparationRequest request, SeparationRequestEntity entity) {
        entity.setTransactionId(request.getTransactionId());
        entity.setLotId(request.getLotId());
        entity.setBuyerId(request.getBuyerId());
        entity.setQuotationId(request.getQuotationId());
        entity.setInitialAmount(request.getInitialAmount().amount());
        entity.setCurrency(request.getInitialAmount().currency());
        entity.setStatus(request.getStatus());
        entity.setRequestedAt(request.getRequestedAt());
        entity.setLockExpiresAt(request.getLockExpiresAt());
        entity.setRejectionReason(request.getRejectionReason());
        return entity;
    }
}
