package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;

import java.util.Optional;

/**
 * Domain service of 2.6.4.1: contrasts a payment evidence with what its reservation expects before the back office
 * can approve it. A rejection needs no check: any pending evidence can be rejected with a reason. Stateless, so it is
 * used without an instance.
 */
public final class FinancialVerificationService {

    private FinancialVerificationService() {}

    /**
     * @return why the evidence cannot be approved, to tell the reviewer to reject it instead; empty when it can be
     */
    public static Optional<String> approvalObstacle(Reservation reservation, PaymentEvidence evidence) {
        if (evidence.isLate()) {
            return Optional.of("the evidence is late: it arrived after the reservation stopped holding its lot; "
                    + "reject it");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_VERIFICATION) {
            return Optional.of("the reservation is %s, not waiting for verification"
                    .formatted(reservation.getStatus()));
        }
        var paid = evidence.getAmount();
        var expected = reservation.getInitialAmount();
        if (!paid.currency().equals(expected.currency())) {
            return Optional.of("the evidence is in %s but the down payment is in %s"
                    .formatted(paid.currency(), expected.currency()));
        }
        if (paid.amount().compareTo(expected.amount()) < 0) {
            return Optional.of("the evidence amount %s is less than the down payment %s %s"
                    .formatted(paid.amount().toPlainString(), expected.amount().toPlainString(),
                            expected.currency()));
        }
        return Optional.empty();
    }
}
