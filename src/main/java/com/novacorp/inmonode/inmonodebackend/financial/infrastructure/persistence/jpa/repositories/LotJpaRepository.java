package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.LotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface LotJpaRepository extends JpaRepository<LotEntity, Long> {

    @Query("select l.code from LotEntity l where l.projectId = :projectId")
    Set<String> findCodesByProjectId(@Param("projectId") Long projectId);

    long countByProjectId(Long projectId);
}
