package com.novacorp.inmonode.inmonodebackend.quoting.domain.services;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.SeparationRequest;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands.RequestLotSeparationCommand;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Command side of the web separation requests.
 */
public interface SeparationRequestCommandService {

    /**
     * US-19: blocks the lot for one hour and announces "Solicitud de separación registrada". A buyer who already holds
     * the lot gets that same request back. Fails with {@code QUOTATION_NOT_FOUND} when the quotation is not the
     * buyer's or not of that lot, {@code BUSINESS_RULE_VIOLATION} when it expired, {@code LOT_NOT_FOUND} when the lot
     * is gone, and {@code LOT_CONFLICT} when another operation took it; that rejection is stored.
     */
    Result<SeparationRequest, ApplicationError> handle(RequestLotSeparationCommand command);
}
