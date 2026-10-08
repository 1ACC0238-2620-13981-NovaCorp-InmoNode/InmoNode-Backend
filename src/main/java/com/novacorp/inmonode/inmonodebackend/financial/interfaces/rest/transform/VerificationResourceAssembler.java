package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PendingVerification;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.VerificationOutcome;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.PendingVerificationResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.VerificationDecisionResource;

public final class VerificationResourceAssembler {

    private VerificationResourceAssembler() {}

    public static PendingVerificationResource toResourceFromPending(PendingVerification pending) {
        var evidence = pending.evidence();
        var reservation = pending.reservation();
        return new PendingVerificationResource(evidence.getId(), evidence.getReference(), evidence.getSource().name(),
                evidence.getAmount().amount(), evidence.getAmount().currency(), evidence.getOperationDate(),
                evidence.getOperationCode(), evidence.isManuallyCorrected(), evidence.isLate(),
                evidence.getSubmittedAt(), reservation.getId(), reservation.getSourceEventId(),
                reservation.getChannel().name(), reservation.getRequesterId(), reservation.getStatus().name(),
                reservation.getInitialAmount().amount(), pending.lot().getId(), pending.lot().getCode());
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
