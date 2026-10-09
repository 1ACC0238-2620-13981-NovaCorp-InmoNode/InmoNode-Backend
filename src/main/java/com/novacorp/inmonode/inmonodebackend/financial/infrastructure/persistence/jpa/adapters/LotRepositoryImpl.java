package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatistics;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotFilters;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers.LotEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.LotEntity;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.LotJpaRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.LotJpaRepository.LotStatisticsView;
import org.springframework.stereotype.Repository;
import org.springframework.context.ApplicationEventPublisher;
import com.novacorp.inmonode.inmonodebackend.financial.application.catalog.CatalogChangedEvent;
import java.time.Clock;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.util.ArrayList;

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
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final EntityManager entityManager;

    public LotRepositoryImpl(LotJpaRepository jpaRepository, ApplicationEventPublisher events, Clock clock,
                             EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.events = events;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override
    public Set<Long> existingIds(Set<Long> ids) {
        return jpaRepository.findAllById(ids).stream().map(LotEntity::getId).collect(Collectors.toSet());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public List<Lot> saveAll(List<Lot> lots) {
        var entities = lots.stream()
                .map(lot -> LotEntityAssembler.copyToEntity(lot, lot.getId() == null
                        ? new LotEntity()
                        : jpaRepository.findById(lot.getId()).orElseGet(LotEntity::new)))
                .toList();
        var saved = jpaRepository.saveAll(entities).stream().map(LotEntityAssembler::toDomain).toList();
        saved.forEach(lot -> events.publishEvent(new CatalogChangedEvent(lot.getProjectId(), lot.getId(),
                lot.getStatus().name(), clock.instant())));
        return saved;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Lot save(Lot lot) {
        return saveAll(List.of(lot)).getFirst();
    }

    @Override
    public Optional<Lot> findById(Long id) {
        return jpaRepository.findById(id).map(LotEntityAssembler::toDomain);
    }

    @Override
    public Optional<Lot> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id).map(entity -> {
            entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
            return LotEntityAssembler.toDomain(entity);
        });
    }

    @Override
    public List<Long> findIdsWithExpiredBlock(Instant now) {
        return jpaRepository.findIdsWithExpiredBlock(LotStatus.BLOCKED, LotStatus.PENDING_VERIFICATION, now);
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
    public List<Lot> findByProjectId(Long projectId, LotFilters filters) {
        Specification<LotEntity> specification = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("projectId"), projectId));
            predicates.add(builder.notEqual(root.get("status"), LotStatus.DRAFT));
            if (filters.minArea() != null) predicates.add(builder.greaterThanOrEqualTo(root.get("area"), filters.minArea()));
            if (filters.maxArea() != null) predicates.add(builder.lessThanOrEqualTo(root.get("area"), filters.maxArea()));
            if (filters.minPrice() != null) predicates.add(builder.greaterThanOrEqualTo(root.get("priceAmount"), filters.minPrice()));
            if (filters.maxPrice() != null) predicates.add(builder.lessThanOrEqualTo(root.get("priceAmount"), filters.maxPrice()));
            if (filters.status() != null) predicates.add(builder.equal(root.get("status"), filters.status()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return jpaRepository.findAll(specification, Sort.by("code", "id")).stream()
                .map(LotEntityAssembler::toDomain)
                .filter(lot -> filters.bounds() == null || lot.getBoundary().intersects(filters.bounds()))
                .toList();
    }

    @Override
    public List<Lot> findByProjectIds(Collection<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByProjectIdInOrderByProjectIdAscCodeAsc(projectIds).stream()
                .filter(entity -> entity.getStatus() != LotStatus.DRAFT)
                .map(LotEntityAssembler::toDomain)
                .toList();
    }

    @Override
    public Map<Long, LotStatistics> summarizeByProjectIds(Collection<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.summarizeByProjectIds(projectIds, LotStatus.AVAILABLE, LotStatus.SOLD, LotStatus.DRAFT).stream()
                .collect(Collectors.toMap(LotStatisticsView::getProjectId, LotRepositoryImpl::toStatistics));
    }

    private static LotStatistics toStatistics(LotStatisticsView view) {
        return new LotStatistics(view.getTotalLots(), view.getAvailableLots(), view.getSoldLots(),
                Money.of(view.getMinPrice()), Money.of(view.getMaxPrice()));
    }
}
