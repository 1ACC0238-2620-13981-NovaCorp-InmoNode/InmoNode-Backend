package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PendingVerification;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.VerificationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.PendingVerificationPageResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.PendingVerificationResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.VerificationDecisionResource;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;

public final class VerificationResourceAssembler {

    private VerificationResourceAssembler() {}

    public static PendingVerificationPageResource toResourceFromPage(PageResult<PendingVerification> page) {
        return new PendingVerificationPageResource(
                page.items().stream().map(VerificationResourceAssembler::toResourceFromPending).toList(),
                page.totalCount(), page.page(), page.limit());
    }

    public static PendingVerificationResource toResourceFromPending(PendingVerification pending) {
        var evidence = pending.evidence();
        var reservation = pending.reservation();
        var terms = reservation.getNegotiationTerms();
        var identityDocument = pending.identityDocument();
        return new PendingVerificationResource(evidence.getId(), evidence.getReference(), evidence.getSource().name(),
                evidence.getAmount().amount(), evidence.getAmount().currency(), evidence.getOperationDate(),
                evidence.getOperationCode(), evidence.isManuallyCorrected(), evidence.isLate(),
                evidence.getSubmittedAt(), reservation.getId(), reservation.getSourceEventId(),
                reservation.getChannel().name(), reservation.getRequesterId(), reservation.getStatus().name(),
                reservation.getInitialAmount().amount(), pending.lot().getId(), pending.lot().getCode(),
                terms.discountAmount(), terms.bonus(), terms.notes(), reservation.isIdentityDocumentPending(),
                identityDocument == null ? null : identityDocument.url().toString(),
                identityDocument == null ? null : identityDocument.expiresAt());
    }

    public static VerificationDecisionResource toResourceFromOutcome(VerificationOutcome outcome) {
        var evidence = outcome.evidence();
        var reservation = outcome.reservation();
        var lot = outcome.lot();
        return new VerificationDecisionResource(evidence.getId(), evidence.getStatus().name(),
                evidence.getReviewerNote(), evidence.getReviewedAt(), reservation.getId(),
                reservation.getSourceEventId(), reservation.getStatus().name(), lot.getId(), lot.getStatus().name(),
                lot.getBlockedUntil());
    }
}
