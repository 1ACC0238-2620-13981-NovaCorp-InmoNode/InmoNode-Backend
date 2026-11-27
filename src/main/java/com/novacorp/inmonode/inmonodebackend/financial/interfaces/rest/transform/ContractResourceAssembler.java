package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractSignatureProgress;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractToIssue;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ContractUpload;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationContract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureDeliveryStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.SignatureEventRecord;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.BuyerContractResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractAcknowledgmentResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractSignatureRequestResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractToIssuePageResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractToIssueResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.CoOwnerResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ContractUploadResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.SignatureEventResource;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;

public final class ContractResourceAssembler {

    private ContractResourceAssembler() {}

    public static ContractToIssuePageResource toResourceFromToIssuePage(PageResult<ContractToIssue> page) {
        return new ContractToIssuePageResource(
                page.items().stream().map(ContractResourceAssembler::toResourceFromToIssue).toList(),
                page.totalCount(), page.page(), page.limit());
    }

    public static ContractToIssueResource toResourceFromToIssue(ContractToIssue toIssue) {
        var reservation = toIssue.reservation();
        var coOwner = reservation.getCoOwner();
        return new ContractToIssueResource(reservation.getId(), reservation.getSourceEventId(),
                reservation.getRequesterId(), toIssue.buyerEmail(), toIssue.project().getId(),
                toIssue.project().getName(), toIssue.lot().getId(), toIssue.lot().getCode(),
                reservation.getInitialAmount().amount(), reservation.getInitialAmount().currency(),
                reservation.getVerifiedAt(),
                coOwner == null ? null
                        : new CoOwnerResource(coOwner.fullName(), coOwner.documentType(), coOwner.documentNumber()));
    }

    public static ContractResource toResourceFromContract(Contract contract) {
        var signed = contract.getSignedDocument();
        return new ContractResource(contract.getId(), contract.getTransactionId(), contract.getReservationId(),
                contract.getBuyerId(), contract.getLotId(), contract.getStatus().name(), contract.getSizeBytes(),
                contract.getIssuedAt(), contract.getIssuedBy(), contract.getBuyerAcknowledgedAt(),
                coOwnerOf(contract), contract.getDocumentSha256(), contract.getSignatureSubmissionId(),
                contract.getSignatureRequestedAt(), contract.getSignedAt(), signed == null ? null : signed.sha256(),
                contract.getSignatureDeclinedAt(), contract.getSignatureDeclineReason());
    }

    public static BuyerContractResource toResourceFromReservationContract(ReservationContract view) {
        var contract = view.contract();
        return new BuyerContractResource(view.reservation().getSourceEventId(), view.availability().name(),
                contract == null ? null : contract.getId(), contract == null ? null : contract.getIssuedAt(),
                view.downloadUrl() == null ? null : view.downloadUrl().toString(), view.downloadExpiresAt(),
                contract == null ? null : contract.getBuyerAcknowledgedAt(),
                contract == null ? null : coOwnerOf(contract),
                contract == null ? null : contract.getStatus().name(),
                contract == null ? null : contract.getDocumentSha256(),
                contract == null || !contract.isAwaitingSignature() ? null : contract.getSigningUrl(),
                contract == null ? null : contract.getSignedAt(),
                view.signedDownloadUrl() == null ? null : view.signedDownloadUrl().toString(),
                view.signedDownloadExpiresAt(),
                contract == null ? null : contract.getSignatureDeclinedAt());
    }

    public static ContractSignatureRequestResource toResourceFromProgress(ContractSignatureProgress progress) {
        var delivery = progress.delivery();
        return new ContractSignatureRequestResource(toResourceFromContract(progress.contract()),
                delivery == null ? null : delivery.status().name(), delivery == null ? null : delivery.attempts(),
                delivery == null || delivery.status() != SignatureDeliveryStatus.PENDING ? null : delivery.nextAttemptAt(),
                delivery == null ? null : delivery.lastError());
    }

    public static SignatureEventResource toResourceFromSignatureEvent(SignatureEventRecord event) {
        return new SignatureEventResource(event.eventKey(), event.outcome().name());
    }

    public static ContractAcknowledgmentResource toAcknowledgmentFromContract(Contract contract) {
        return new ContractAcknowledgmentResource(contract.getId(), contract.getBuyerAcknowledgedAt());
    }

    public static ContractUploadResource toResourceFromUpload(ContractUpload upload) {
        return new ContractUploadResource(upload.uploadUrl().toString(), upload.objectKey(), upload.expiresAt(),
                upload.headers());
    }

    private static CoOwnerResource coOwnerOf(Contract contract) {
        return contract.getCoOwner() == null ? null : new CoOwnerResource(contract.getCoOwner().fullName(),
                contract.getCoOwner().documentType(), contract.getCoOwner().documentNumber());
    }
}
