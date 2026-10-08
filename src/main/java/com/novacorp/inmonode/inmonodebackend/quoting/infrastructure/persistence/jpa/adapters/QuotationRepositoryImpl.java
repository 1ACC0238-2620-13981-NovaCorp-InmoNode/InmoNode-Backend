package com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.adapters;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.Quotation;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.repositories.QuotationRepository;
import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.assemblers.QuotationEntityAssembler;
import com.novacorp.inmonode.inmonodebackend.quoting.infrastructure.persistence.jpa.repositories.QuotationJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class QuotationRepositoryImpl implements QuotationRepository {

    private final QuotationJpaRepository jpaRepository;

    public QuotationRepositoryImpl(QuotationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * @throws IllegalArgumentException when the quotation was already saved: a quotation never changes
     */
    @Override
    public Quotation save(Quotation quotation) {
        if (quotation.getId() != null) {
            throw new IllegalArgumentException("quotation %d is already saved".formatted(quotation.getId()));
        }
        return QuotationEntityAssembler.toDomain(jpaRepository.save(QuotationEntityAssembler.toEntity(quotation)));
    }

    @Override
    public Optional<Quotation> findById(Long id) {
        return jpaRepository.findById(id).map(QuotationEntityAssembler::toDomain);
    }
}
