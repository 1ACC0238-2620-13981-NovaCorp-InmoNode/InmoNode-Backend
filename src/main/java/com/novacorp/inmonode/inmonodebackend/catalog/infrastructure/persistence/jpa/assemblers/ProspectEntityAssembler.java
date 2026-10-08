package com.novacorp.inmonode.inmonodebackend.catalog.infrastructure.persistence.jpa.assemblers;

import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.aggregates.Prospect;
import com.novacorp.inmonode.inmonodebackend.catalog.infrastructure.persistence.jpa.entities.ProspectEntity;

/**
 * Translates between the {@link Prospect} aggregate and its JPA persistence entity.
 */
public final class ProspectEntityAssembler {

    private ProspectEntityAssembler() {}

    public static Prospect toDomain(ProspectEntity entity) {
        return Prospect.restore(entity.getId(), entity.getProspectId(), entity.getAgentId(), entity.getDocument(),
                entity.getFullName(), entity.getPhone(), entity.getRegisteredAt());
    }

    /** Copies the aggregate state onto the entity; audit columns and id stay untouched. */
    public static ProspectEntity copyToEntity(Prospect prospect, ProspectEntity entity) {
        entity.setProspectId(prospect.getProspectId());
        entity.setAgentId(prospect.getAgentId());
        entity.setDocument(prospect.getDocument());
        entity.setFullName(prospect.getFullName());
        entity.setPhone(prospect.getPhone());
        entity.setRegisteredAt(prospect.getRegisteredAt());
        return entity;
    }
}
