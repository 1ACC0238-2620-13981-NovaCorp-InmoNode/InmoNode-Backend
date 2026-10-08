package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * A reservation with its payment evidences, as its requester sees them (US-25).
 *
 * @param waitingUntil until when the lot waits for a (substitute) voucher; {@code null} when it does not wait
 */
public record ReservationPayments(Reservation reservation, @Nullable Instant waitingUntil,
                                  List<EvidenceView> evidences) {

    public ReservationPayments {
        evidences = List.copyOf(evidences);
    }

    /**
     * @param downloadUrl       presigned link to the voucher file, only for an approved evidence (US-25, Scenario 1)
     * @param downloadExpiresAt when that link stops working
     */
    public record EvidenceView(PaymentEvidence evidence, @Nullable URI downloadUrl,
                               @Nullable Instant downloadExpiresAt) {
    }
}
