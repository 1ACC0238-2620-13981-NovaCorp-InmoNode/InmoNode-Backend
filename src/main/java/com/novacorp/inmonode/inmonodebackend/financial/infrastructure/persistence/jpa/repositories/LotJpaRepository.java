package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.infrastructure.persistence.jpa.entities.LotEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LotJpaRepository extends JpaRepository<LotEntity, Long>, JpaSpecificationExecutor<LotEntity> {

    @Query("select l.code from LotEntity l where l.projectId = :projectId")
    Set<String> findCodesByProjectId(@Param("projectId") Long projectId);

    long countByProjectId(Long projectId);

    List<LotEntity> findByProjectIdOrderByCodeAsc(Long projectId);

    List<LotEntity> findByProjectIdInOrderByProjectIdAscCodeAsc(Collection<Long> projectIds);

    /** {@code SELECT ... FOR UPDATE}: must run inside the caller's read-write transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LotEntity l where l.id = :id")
    Optional<LotEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("select l.id from LotEntity l where (l.status = :blocked or l.status = :pending) and l.blockedUntil <= :now order by l.id")
    List<Long> findIdsWithExpiredBlock(@Param("blocked") LotStatus blocked, @Param("pending") LotStatus pending, @Param("now") Instant now);

    @Query("""
            select l.projectId as projectId,
                   count(l) as totalLots,
                   sum(case when l.status = :available then 1 else 0 end) as availableLots,
                   sum(case when l.status = :sold then 1 else 0 end) as soldLots,
                   min(l.priceAmount) as minPrice,
                   max(l.priceAmount) as maxPrice
              from LotEntity l
             where l.projectId in :projectIds and l.status <> :draft
             group by l.projectId""")
    List<LotStatisticsView> summarizeByProjectIds(@Param("projectIds") Collection<Long> projectIds,
                                                  @Param("available") LotStatus available,
                                                  @Param("sold") LotStatus sold, @Param("draft") LotStatus draft);

    /** Row of {@link #summarizeByProjectIds}: lot figures of one project. */
    interface LotStatisticsView {
        Long getProjectId();
        Long getTotalLots();
        Long getAvailableLots();
        Long getSoldLots();
        BigDecimal getMinPrice();
        BigDecimal getMaxPrice();
    }
}
