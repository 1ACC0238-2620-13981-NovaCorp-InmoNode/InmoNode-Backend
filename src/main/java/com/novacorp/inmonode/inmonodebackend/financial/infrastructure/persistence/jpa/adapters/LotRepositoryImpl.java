package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatistics;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers.LotEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.LotEntity;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.LotJpaRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.LotJpaRepository.LotStatisticsView;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    public Lot save(Lot lot) {
        return saveAll(List.of(lot)).getFirst();
    }

    @Override
    public Optional<Lot> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id).map(LotEntityAssembler::toDomain);
    }

    @Override
    public List<Lot> findWithExpiredBlock(Instant now) {
        return jpaRepository.findExpiredBlocks(LotStatus.BLOCKED, now).stream()
                .map(LotEntityAssembler::toDomain)
                .toList();
    }

    @Override
    public Set<String> findCodesByProjectId(Long projectId) {
        return jpaRepository.findCodesByProjectId(projectId);
    }

    @Override
    public long countByProjectId(Long projectId) {
        return jpaRepository.countByProjectId(projectId);
    }

    @Override
    public List<Lot> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectIdOrderByCodeAsc(projectId).stream()
                .map(LotEntityAssembler::toDomain)
                .toList();
    }

    @Override
    public List<Lot> findByProjectIds(Collection<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByProjectIdInOrderByProjectIdAscCodeAsc(projectIds).stream()
                .map(LotEntityAssembler::toDomain)
                .toList();
    }

    @Override
    public Map<Long, LotStatistics> summarizeByProjectIds(Collection<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.summarizeByProjectIds(projectIds, LotStatus.AVAILABLE, LotStatus.SOLD).stream()
                .collect(Collectors.toMap(LotStatisticsView::getProjectId, LotRepositoryImpl::toStatistics));
    }

    private static LotStatistics toStatistics(LotStatisticsView view) {
        return new LotStatistics(view.getTotalLots(), view.getAvailableLots(), view.getSoldLots(),
                Money.of(view.getMinPrice()), Money.of(view.getMaxPrice()));
    }
}
