package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.AccountStatementEntity;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AccountStatementJpaRepository extends JpaRepository<AccountStatementEntity, Long> {

    Optional<AccountStatementEntity> findByContractId(Long contractId);

    Optional<AccountStatementEntity> findByReservationId(Long reservationId);

    @Query("""
            select distinct i.accountStatement.id from InstallmentEntity i
            where (i.status = :pending and (i.dueDate < :asOfDate
                       or (i.reminderSentAt is null and i.dueDate <= :remindUntil)))
               or (i.status = :overdue and i.overdueNotifiedAt is null)
            order by i.accountStatement.id""")
    List<Long> findIdsToReview(@Param("asOfDate") LocalDate asOfDate, @Param("remindUntil") LocalDate remindUntil,
                               @Param("pending") InstallmentStatus pending,
                               @Param("overdue") InstallmentStatus overdue);
}
