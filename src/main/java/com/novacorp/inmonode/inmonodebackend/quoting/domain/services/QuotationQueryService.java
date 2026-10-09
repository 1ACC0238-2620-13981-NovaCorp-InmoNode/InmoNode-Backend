package com.novacorp.inmonode.inmonodebackend.quoting.domain.services;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.Quotation;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.queries.GetQuotationQuery;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Query side of the quotations.
 */
public interface QuotationQueryService {

    /**
     * Fails with {@code QUOTATION_NOT_FOUND} when the quotation does not exist or belongs to another buyer, so someone
     * else's quotation cannot be told apart from a missing one.
     */
    Result<Quotation, ApplicationError> handle(GetQuotationQuery query);
}
