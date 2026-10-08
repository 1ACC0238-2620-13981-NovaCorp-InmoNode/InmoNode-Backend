package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatistics;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Persistence abstraction for the {@link Lot} aggregate.
 */
public interface LotRepository {

    /**
     * @return the persisted lots, with their generated ids
     */
    List<Lot> saveAll(List<Lot> lots);

    Lot save(Lot lot);

    /** Reads the lot without locking it, for queries; decisions about its availability lock it first. */
    Optional<Lot> findById(Long id);

    /**
     * Reads the lot and locks it until the current transaction ends, so concurrent reservations of the same lot
     * are decided one after the other: the first to reach the server wins (US-12).
     */
    Optional<Lot> findByIdForUpdate(Long id);

    /**
     * Ids of the lots whose block ran out at {@code now}, still waiting to be released. Only ids: each lot is then
     * read under lock, so its state is current and not the one loaded with the list.
     */
    List<Long> findIdsWithExpiredBlock(Instant now);

    /** Codes already used in the project, to reject duplicates before saving. */
    Set<String> findCodesByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    /** Lots of the project ordered by code. */
    List<Lot> findByProjectId(Long projectId);

    /** Lots of several projects in one query, ordered by project and code. */
    List<Lot> findByProjectIds(Collection<Long> projectIds);

    /**
     * Inventory figures per project, computed in the database. Projects without lots are absent from the map.
     */
    Map<Long, LotStatistics> summarizeByProjectIds(Collection<Long> projectIds);
}
