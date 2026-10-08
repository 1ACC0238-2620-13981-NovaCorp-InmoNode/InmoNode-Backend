package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Project;

import java.util.Optional;

/**
 * Persistence abstraction for the {@link Project} aggregate.
 */
public interface ProjectRepository {

    /**
     * @return the persisted project, with its generated id
     */
    Project save(Project project);

    Optional<Project> findById(Long id);
}
