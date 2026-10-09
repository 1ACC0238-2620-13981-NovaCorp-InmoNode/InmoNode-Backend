package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.transform;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationPayments;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationPayments.EvidenceView;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ReservationPaymentsResource;
import com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources.ReservationPaymentsResource.EvidenceResource;

public final class ReservationPaymentsResourceAssembler {

    private ReservationPaymentsResourceAssembler() {}

    public static ReservationPaymentsResource toResourceFromPayments(ReservationPayments payments) {
        var reservation = payments.reservation();
        return new ReservationPaymentsResource(reservation.getSourceEventId(), reservation.getChannel().name(),
                reservation.getStatus().name(), reservation.getInitialAmount().amount(),
                reservation.getInitialAmount().currency(), payments.waitingUntil(),
                payments.evidences().stream().map(ReservationPaymentsResourceAssembler::toResource).toList());
    }

    /** The reviewer's note is shown only as the reason of a rejection; an approval note stays internal. */
    private static EvidenceResource toResource(EvidenceView view) {
        var evidence = view.evidence();
        var rejected = evidence.getStatus() == PaymentEvidenceStatus.REJECTED;
        return new EvidenceResource(evidence.getId(), evidence.getReference(), evidence.getStatus().name(),
                evidence.getAmount().amount(), evidence.getAmount().currency(), evidence.getOperationDate(),
                evidence.getOperationCode(), evidence.isLate(), evidence.getSubmittedAt(), evidence.getReviewedAt(),
                rejected ? evidence.getReviewerNote() : null,
                view.downloadUrl() == null ? null : view.downloadUrl().toString(), view.downloadExpiresAt());
    }
}
