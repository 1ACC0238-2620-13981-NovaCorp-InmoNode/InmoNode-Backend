package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractUpload;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationContract;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.BuyerContractResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractAcknowledgmentResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.CoOwnerResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractUploadResource;

public final class ContractResourceAssembler {

    private ContractResourceAssembler() {}

    public static ContractResource toResourceFromContract(Contract contract) {
        return new ContractResource(contract.getId(), contract.getTransactionId(), contract.getReservationId(),
                contract.getBuyerId(), contract.getLotId(), contract.getStatus().name(), contract.getSizeBytes(),
                contract.getIssuedAt(), contract.getIssuedBy(), contract.getBuyerAcknowledgedAt(),
                contract.getCoOwner() == null ? null : new CoOwnerResource(contract.getCoOwner().fullName(),
                        contract.getCoOwner().documentType(), contract.getCoOwner().documentNumber()));
    }

    public static BuyerContractResource toResourceFromReservationContract(ReservationContract view) {
        var contract = view.contract();
        return new BuyerContractResource(view.reservation().getSourceEventId(), view.availability().name(),
                contract == null ? null : contract.getId(), contract == null ? null : contract.getIssuedAt(),
                view.downloadUrl() == null ? null : view.downloadUrl().toString(), view.downloadExpiresAt(),
                contract == null ? null : contract.getBuyerAcknowledgedAt(),
                contract == null || contract.getCoOwner() == null ? null : new CoOwnerResource(contract.getCoOwner().fullName(),
                        contract.getCoOwner().documentType(), contract.getCoOwner().documentNumber()));
    }

    public static ContractAcknowledgmentResource toAcknowledgmentFromContract(Contract contract) {
        return new ContractAcknowledgmentResource(contract.getId(), contract.getBuyerAcknowledgedAt());
    }

    public static ContractUploadResource toResourceFromUpload(ContractUpload upload) {
        return new ContractUploadResource(upload.uploadUrl().toString(), upload.objectKey(), upload.expiresAt(),
                upload.headers());
    }
}
