package com.novacorp.inmonode.inmonodebackend.catalog.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.aggregates.Prospect;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.repositories.ProspectRepository;
import com.novacorp.inmonode.inmonodebackend.catalog.infrastructure.persistence.jpa.assemblers.ProspectEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.catalog.infrastructure.persistence.jpa.entities.ProspectEntity;
import com.novacorp.inmonode.inmonodebackend.catalog.infrastructure.persistence.jpa.repositories.ProspectJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public class ProspectRepositoryImpl implements ProspectRepository {

    private final ProspectJpaRepository jpaRepository;

    public ProspectRepositoryImpl(ProspectJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Prospect> saveAll(List<Prospect> prospects) {
        var entities = prospects.stream()
                .map(prospect -> ProspectEntityAssembler.copyToEntity(prospect, prospect.getId() == null
                        ? new ProspectEntity()
                        : jpaRepository.findById(prospect.getId()).orElseGet(ProspectEntity::new)))
                .toList();
        return jpaRepository.saveAll(entities).stream().map(ProspectEntityAssembler::toDomain).toList();
    }

    @Override
    public List<Prospect> findByProspectIds(Collection<UUID> prospectIds) {
        if (prospectIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByProspectIdIn(prospectIds).stream().map(ProspectEntityAssembler::toDomain).toList();
    }
}
