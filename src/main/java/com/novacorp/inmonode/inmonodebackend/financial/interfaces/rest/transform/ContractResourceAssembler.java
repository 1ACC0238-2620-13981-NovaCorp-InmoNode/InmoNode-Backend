package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractUpload;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractUploadResource;

public final class ContractResourceAssembler {

    private ContractResourceAssembler() {}

    public static ContractResource toResourceFromContract(Contract contract) {
        return new ContractResource(contract.getId(), contract.getTransactionId(), contract.getReservationId(),
                contract.getBuyerId(), contract.getLotId(), contract.getStatus().name(), contract.getSizeBytes(),
                contract.getIssuedAt(), contract.getIssuedBy(), contract.getBuyerAcknowledgedAt());
    }

    public static ContractUploadResource toResourceFromUpload(ContractUpload upload) {
        return new ContractUploadResource(upload.uploadUrl().toString(), upload.objectKey(), upload.expiresAt(),
                upload.headers());
    }
}
