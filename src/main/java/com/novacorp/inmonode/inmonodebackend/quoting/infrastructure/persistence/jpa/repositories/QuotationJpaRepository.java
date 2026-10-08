package com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.entities.QuotationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationJpaRepository extends JpaRepository<QuotationEntity, Long> {
}
