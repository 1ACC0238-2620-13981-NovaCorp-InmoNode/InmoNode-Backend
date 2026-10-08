package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers.LotEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.LotEntity;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.LotJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class LotRepositoryImpl implements LotRepository {

    private final LotJpaRepository jpaRepository;

    public LotRepositoryImpl(LotJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Lot> saveAll(List<Lot> lots) {
        var entities = lots.stream()
                .map(lot -> LotEntityAssembler.copyToEntity(lot, lot.getId() == null
                        ? new LotEntity()
                        : jpaRepository.findById(lot.getId()).orElseGet(LotEntity::new)))
                .toList();
        return jpaRepository.saveAll(entities).stream().map(LotEntityAssembler::toDomain).toList();
    }

    @Override
    public Set<String> findCodesByProjectId(Long projectId) {
        return jpaRepository.findCodesByProjectId(projectId);
    }
}
