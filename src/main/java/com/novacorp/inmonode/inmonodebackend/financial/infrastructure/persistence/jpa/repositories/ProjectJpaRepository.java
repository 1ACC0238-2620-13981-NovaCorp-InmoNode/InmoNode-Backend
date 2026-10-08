package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ProjectStatus;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, Long> {

    List<ProjectEntity> findByStatusOrderByNameAsc(ProjectStatus status);
}
