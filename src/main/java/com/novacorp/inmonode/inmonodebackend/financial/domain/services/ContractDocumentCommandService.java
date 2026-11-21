package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestContractDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * The back office has the backend generate the contract PDF of a reservation (US-45), as an alternative to uploading
 * one made by hand (US-21).
 */
public interface ContractDocumentCommandService {

    /**
     * Writes the preliminary contract from the reservation's data and queues its PDF; answers at once with the
     * request, processing. Once it is ready, the contract is issued with the request id as {@code documentId} and its
     * size. Fails like the contract upload: {@code RESERVATION_NOT_FOUND}, {@code BUSINESS_RULE_VIOLATION} when the
     * reservation is not a verified web one, and {@code CONTRACT_CONFLICT} when its contract was already issued.
     */
    Result<DocumentRequest, ApplicationError> handle(RequestContractDocumentCommand command);
}
