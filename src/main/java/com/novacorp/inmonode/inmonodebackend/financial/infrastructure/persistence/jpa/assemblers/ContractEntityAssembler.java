package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.CoOwner;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ContractEntity;

/**
 * Translates between the {@link Contract} aggregate and its JPA persistence entity.
 */
public final class ContractEntityAssembler {

    private ContractEntityAssembler() {}

    public static Contract toDomain(ContractEntity entity) {
        return Contract.restore(entity.getId(), entity.getReservationId(), entity.getTransactionId(),
                entity.getBuyerId(), entity.getLotId(), entity.getDocumentId(), entity.getObjectKey(),
                entity.getSizeBytes(), entity.getStatus(), entity.getIssuedAt(), entity.getIssuedBy(),
                entity.getBuyerAcknowledgedAt(), entity.getCoOwnerFullName() == null ? null :
                        new CoOwner(entity.getCoOwnerFullName(), entity.getCoOwnerDocumentType(), entity.getCoOwnerDocumentNumber()));
    }

    /** Copies the aggregate state onto the entity; audit columns and id stay untouched. */
    public static ContractEntity copyToEntity(Contract contract, ContractEntity entity) {
        entity.setReservationId(contract.getReservationId());
        entity.setTransactionId(contract.getTransactionId());
        entity.setBuyerId(contract.getBuyerId());
        entity.setLotId(contract.getLotId());
        entity.setDocumentId(contract.getDocumentId());
        entity.setObjectKey(contract.getObjectKey());
        entity.setSizeBytes(contract.getSizeBytes());
        entity.setStatus(contract.getStatus());
        entity.setIssuedAt(contract.getIssuedAt());
        entity.setIssuedBy(contract.getIssuedBy());
        entity.setBuyerAcknowledgedAt(contract.getBuyerAcknowledgedAt());
        var coOwner = contract.getCoOwner();
        entity.setCoOwnerFullName(coOwner == null ? null : coOwner.fullName());
        entity.setCoOwnerDocumentType(coOwner == null ? null : coOwner.documentType());
        entity.setCoOwnerDocumentNumber(coOwner == null ? null : coOwner.documentNumber());
        return entity;
    }
}
