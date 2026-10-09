package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ImportLotsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotImportResult;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Command side of the lot inventory.
 */
public interface LotCommandService {
    Result<com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot, ApplicationError>
        handle(com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RegisterLotCommand command);
    Result<com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot, ApplicationError>
        handle(com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.PublishLotCommand command);

    /**
     * US-53, Scenario 2: saves the valid lots of the plan and reports the rejected ones with their reason.
     * Fails only when the project does not exist.
     */
    Result<LotImportResult, ApplicationError> handle(ImportLotsCommand command);
}
