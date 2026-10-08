package com.novacorp.inmonode.inmonodebackend.catalog.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.aggregates.Prospect;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persistence abstraction for the {@link Prospect} aggregate.
 */
public interface ProspectRepository {

    /**
     * @return the persisted prospects, with their generated ids
     */
    List<Prospect> saveAll(List<Prospect> prospects);

    /** The stored prospects among these device ids, in one query. */
    List<Prospect> findByProspectIds(Collection<UUID> prospectIds);
}
