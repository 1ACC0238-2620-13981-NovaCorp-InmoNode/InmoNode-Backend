package com.novacorp.inmonode.inmonodebackend.quoting.domain.services;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.Quotation;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands.SimulateFinancingCommand;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Command side of the quotations.
 */
public interface QuotationCommandService {

    /**
     * US-17: simulates and stores the financing of an available lot. Fails with {@code LOT_NOT_FOUND} when the lot
     * does not exist or its project is not published, and with {@code LOT_CONFLICT} when it is not available.
     *
     * @throws IllegalArgumentException when the down payment or the term break the project's rules
     */
    Result<Quotation, ApplicationError> handle(SimulateFinancingCommand command);
}
