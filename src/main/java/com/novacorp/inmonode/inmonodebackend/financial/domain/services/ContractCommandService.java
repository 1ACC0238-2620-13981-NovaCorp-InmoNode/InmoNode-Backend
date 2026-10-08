package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.IssueContractCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestContractUploadCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractUpload;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;

/**
 * Command side of the contracts (2.6.4: ContractCommandService), for the back office.
 */
public interface ContractCommandService {

    /**
     * Signs the upload of the contract PDF of a verified web reservation, valid for 10 minutes. Fails with
     * {@code RESERVATION_NOT_FOUND}, {@code BUSINESS_RULE_VIOLATION} when the reservation is not a verified web one,
     * and {@code CONTRACT_CONFLICT} when its contract was already issued.
     *
     * @throws IllegalArgumentException when the size is over 10 MB
     */
    Result<ContractUpload, ApplicationError> handle(RequestContractUploadCommand command);

    /**
     * Issues the contract with the uploaded PDF. Fails like the upload, and with {@code CONTRACT_FILE_NOT_UPLOADED}
     * when the PDF is not in the file repository or differs from the declared size.
     */
    Result<Contract, ApplicationError> handle(IssueContractCommand command);
}
