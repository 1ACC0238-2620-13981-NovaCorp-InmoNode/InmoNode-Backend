package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.AccountStatementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountStatementJpaRepository extends JpaRepository<AccountStatementEntity, Long> {

    Optional<AccountStatementEntity> findByContractId(Long contractId);

    Optional<AccountStatementEntity> findByReservationId(Long reservationId);
}
