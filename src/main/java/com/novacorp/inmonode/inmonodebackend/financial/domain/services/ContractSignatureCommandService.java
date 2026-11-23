package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReceiveSignatureEventCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestContractSignatureCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.SendDueSignatureRequestsCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractSignatureProgress;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureEventRecord;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * The electronic signature of the issued contracts (US-56).
 */
public interface ContractSignatureCommandService {

    /**
     * Queues a contract to be sent to signature again. Fails with {@code CONTRACT_NOT_FOUND},
     * {@code SIGNATURE_CONFLICT} when it is already signed, and {@code CONTRACT_FILE_NOT_UPLOADED} when the PDF
     * whose hash the sheet cites is missing.
     */
    Result<ContractSignatureProgress, ApplicationError> handle(RequestContractSignatureCommand command);

    /**
     * Sends the requests whose attempt is due; a failure waits longer before the next one.
     *
     * @return how many were sent
     */
    int handle(SendDueSignatureRequestsCommand command);

    /**
     * A callback of the provider. Fails with {@code INVALID_WEBHOOK_SIGNATURE}, {@code SIGNATURES_UNAVAILABLE}
     * without its secret, and {@code SIGNATURE_PROVIDER_ERROR} when the provider could not confirm it, so that it
     * sends it again.
     */
    Result<SignatureEventRecord, ApplicationError> handle(ReceiveSignatureEventCommand command);
}
