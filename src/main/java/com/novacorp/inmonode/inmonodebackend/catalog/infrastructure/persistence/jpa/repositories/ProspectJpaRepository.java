package com.novacorp.inmonode.inmonodebackend.catalog.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.catalog.infrastructure.persistence.jpa.entities.ProspectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProspectJpaRepository extends JpaRepository<ProspectEntity, Long> {

    List<ProspectEntity> findByProspectIdIn(Collection<UUID> prospectIds);
}
