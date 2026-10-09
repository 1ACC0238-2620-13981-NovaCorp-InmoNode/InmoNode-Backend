package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.assemblers.ProjectEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ProjectEntity;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories.ProjectJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.context.ApplicationEventPublisher;
import com.novacorp.inmonode.inmonodebackend.financial.application.catalog.CatalogChangedEvent;
import java.time.Clock;

import java.util.List;
import java.util.Optional;

@Repository
public class ProjectRepositoryImpl implements ProjectRepository {

    private final ProjectJpaRepository jpaRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;

    public ProjectRepositoryImpl(ProjectJpaRepository jpaRepository, ApplicationEventPublisher events, Clock clock, jakarta.persistence.EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.events = events;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Project save(Project project) {
        var entity = project.getId() == null
                ? new ProjectEntity()
                : jpaRepository.findById(project.getId()).orElseGet(ProjectEntity::new);
        var saved = jpaRepository.save(ProjectEntityAssembler.copyToEntity(project, entity));
        events.publishEvent(new CatalogChangedEvent(saved.getId(), null, saved.getStatus().name(), clock.instant()));
        return ProjectEntityAssembler.toDomain(saved);
    }

    @Override
    public Optional<Project> findById(Long id) {
        return jpaRepository.findById(id).map(ProjectEntityAssembler::toDomain);
    }

    @Override
    public Optional<Project> findByIdForUpdate(Long id) {
        return jpaRepository.findById(id).map(entity -> {
            entityManager.refresh(entity, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return ProjectEntityAssembler.toDomain(entity);
        });
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public List<Project> findAllPublished() {
        return jpaRepository.findByStatusOrderByNameAsc(ProjectStatus.PUBLISHED).stream()
                .map(ProjectEntityAssembler::toDomain)
                .toList();
    }
}
