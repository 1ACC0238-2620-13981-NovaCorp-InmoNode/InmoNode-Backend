package com.novacorp.inmonode.inmonodebackend.quoting.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.Quotation;

import java.util.Optional;

/**
 * Persistence abstraction for the {@link Quotation} aggregate.
 */
public interface QuotationRepository {

    /**
     * @return the persisted quotation, with its generated id
     */
    Quotation save(Quotation quotation);

    Optional<Quotation> findById(Long id);
}
